package ai.desertant.emo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Emoji labels and n-gram hashing configuration, parsed from `emo_meta.json`. */
internal class EmoMeta(
    val labels: List<String>,
    val nHashes: Int,
    val nBuckets: Int,
    val nImportance: Int,
    val semDim: Int,
    val semPadIndex: Int,
) {
    companion object {
        fun parse(json: String): EmoMeta {
            val m = Json.parseToJsonElement(json).jsonObject
            return EmoMeta(
                labels = m.getValue("labels").jsonArray.map { it.jsonPrimitive.content },
                nHashes = m.getValue("n_hashes").jsonPrimitive.int,
                nBuckets = m.getValue("n_buckets").jsonPrimitive.int,
                nImportance = m.getValue("n_importance").jsonPrimitive.int,
                semDim = m.getValue("sem_dim").jsonPrimitive.int,
                semPadIndex = m.getValue("sem_pad_index").jsonPrimitive.int,
            )
        }
    }
}

/**
 * A weight tensor, either raw F32 or a 4-bit k-means palette kept packed in memory
 * (a U8 index per weight, 2 per byte) and decoded on access — ~7x less heap than
 * expanding to floats, with better cache locality.
 */
private class Tensor(
    val rows: Int,
    val cols: Int,
    private val floats: FloatArray?,
    private val packed: ByteArray?,
    private val palette: FloatArray?,
) {
    fun get(i: Int): Float {
        val f = floats
        if (f != null) return f[i]
        val b = packed!![i shr 1].toInt() and 0xFF
        val nib = if (i and 1 == 1) (b shr 4) and 0xF else b and 0xF
        return palette!![nib]
    }
}

private class Weights(
    val embed: Tensor,
    val sem: Tensor,
    val importance: Tensor,
    val w1: Tensor,
    val b1: Tensor,
    val w2: Tensor,
    val b2: Tensor,
)

/** Runs emoji inference. Construct once (loading is one-time) and reuse. */
internal class EmoModel(weights: ByteArray, tokenizer: ByteArray, private val meta: EmoMeta) {
    private val tok = SemTokenizer(tokenizer)
    private val w = parseWeights(weights)
    private val maxLen = 1024

    fun suggestions(text: String, limit: Int): List<EmoSuggestion> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val embed = w.embed
        val sem = w.sem
        val importance = w.importance
        val ngDim = embed.cols
        val semDim = sem.cols

        val ng = FloatArray(ngDim)
        val f = NGram.encode(trimmed, meta.nBuckets, meta.nHashes, meta.nImportance, maxLen)
        for (i in f.buckets.indices) {
            val im = f.importance[i]
            for (k in 0 until meta.nHashes) {
                val wgt = importance.get(im * importance.cols + k) * f.signs[i][k]
                val base = f.buckets[i][k] * ngDim
                for (c in 0 until ngDim) ng[c] += wgt * embed.get(base + c)
            }
        }
        val fcount = maxOf(1, f.buckets.size)
        for (c in 0 until ngDim) ng[c] /= fcount

        var ids = tok.encode(trimmed)
        if (ids.size > maxLen) ids = ids.copyOf(maxLen)
        if (ids.isEmpty()) ids = intArrayOf(meta.semPadIndex)
        val sv = FloatArray(semDim)
        for (id in ids) {
            if (id >= sem.rows) continue
            val base = id * semDim
            for (c in 0 until semDim) sv[c] += sem.get(base + c)
        }
        for (c in 0 until semDim) sv[c] /= ids.size
        var norm = 0.0
        for (c in 0 until semDim) norm += (sv[c] * sv[c]).toDouble()
        val nrm = (sqrt(norm) + 1e-9).toFloat()
        for (c in 0 until semDim) sv[c] /= nrm

        val inDim = ngDim + semDim
        val x = FloatArray(inDim)
        System.arraycopy(ng, 0, x, 0, ngDim)
        System.arraycopy(sv, 0, x, ngDim, semDim)

        val hid = w.w1.rows
        val h = FloatArray(hid)
        for (o in 0 until hid) {
            var acc = w.b1.get(o)
            val base = o * inDim
            for (i in 0 until inDim) acc += w.w1.get(base + i) * x[i]
            h[o] = gelu(acc)
        }

        val n = w.w2.rows
        val logits = FloatArray(n)
        var maxLogit = Float.NEGATIVE_INFINITY
        for (o in 0 until n) {
            var acc = w.b2.get(o)
            val base = o * hid
            for (i in 0 until hid) acc += w.w2.get(base + i) * h[i]
            logits[o] = acc
            if (acc > maxLogit) maxLogit = acc
        }
        var sum = 0.0
        for (o in 0 until n) {
            val e = exp((logits[o] - maxLogit).toDouble()).toFloat()
            logits[o] = e
            sum += e
        }

        val labels = meta.labels
        val order = (0 until n).sortedByDescending { logits[it] }
        return order.take(maxOf(0, limit)).map { EmoSuggestion(labels[it], logits[it] / sum) }
    }

    private fun gelu(x: Float): Float {
        val xd = x.toDouble()
        return (0.5 * xd * (1.0 + erf(xd / sqrt(2.0)))).toFloat()
    }

    // Abramowitz-Stegun erf approximation (matches the JS port).
    private fun erf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.3275911 * abs(x))
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) *
            t * exp(-x * x)
        return if (x >= 0) y else -y
    }

    /**
     * Minimal safetensors reader: u64 header length, JSON header, then raw tensor bytes.
     * A weight is either raw F32, or a 4-bit k-means palette stored as a packed U8 index
     * tensor plus a "<name>.palette" F32 tensor (logical 2-D shape kept in __metadata__).
     */
    private fun parseWeights(bytes: ByteArray): Weights {
        val headerLen = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong(0).toInt()
        val header = Json.parseToJsonElement(String(bytes, 8, headerLen, Charsets.UTF_8)).jsonObject
        val dataStart = 8 + headerLen
        val meta = header["__metadata__"]?.jsonObject

        fun offsets(name: String): Pair<Int, Int> {
            val d = header.getValue(name).jsonObject.getValue("data_offsets").jsonArray
            return d[0].jsonPrimitive.int to d[1].jsonPrimitive.int
        }

        fun readF32(start: Int, len: Int): FloatArray {
            val out = FloatArray(len / 4)
            ByteBuffer.wrap(bytes, start, len).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
            return out
        }

        fun makeTensor(name: String): Tensor {
            if (header.containsKey("$name.palette")) {
                val (r, c) = meta!!.getValue("shape.$name").jsonPrimitive.content.split(",").map { it.toInt() }
                val (a, b) = offsets(name)
                val (pa, pb) = offsets("$name.palette")
                return Tensor(r, c, null, bytes.copyOfRange(dataStart + a, dataStart + b), readF32(dataStart + pa, pb - pa))
            }
            val (a, b) = offsets(name)
            val shape = header.getValue(name).jsonObject.getValue("shape").jsonArray
            val rows = shape[0].jsonPrimitive.int
            val cols = if (shape.size > 1) shape[1].jsonPrimitive.int else 1
            return Tensor(rows, cols, readF32(dataStart + a, b - a), null, null)
        }

        return Weights(
            embed = makeTensor("embed"), sem = makeTensor("sem"), importance = makeTensor("importance"),
            w1 = makeTensor("w1"), b1 = makeTensor("b1"), w2 = makeTensor("w2"), b2 = makeTensor("b2"),
        )
    }
}
