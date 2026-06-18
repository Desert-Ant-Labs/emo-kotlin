package ai.desertant.emo

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

/** Splits a string into one-code-point strings (keeps astral characters intact). */
internal fun String.codePointStrings(): List<String> {
    val out = ArrayList<String>(length)
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        out.add(String(Character.toChars(cp)))
        i += Character.charCount(cp)
    }
    return out
}

/** Hashed, script-aware character n-gram features. Mirrors the Swift/JS ports byte-for-byte. */
internal class NGramFeatures(
    val buckets: List<IntArray>,
    val signs: List<FloatArray>,
    val importance: IntArray,
)

internal object NGram {
    private val BUCKET_SEEDS = arrayOf(
        0x9E3779B97F4A7C15uL, 0xC2B2AE3D27D4EB4FuL, 0x165667B19E3779F9uL,
        0x27D4EB2F165667C5uL, 0x85EBCA77C2B2AE63uL,
    )
    private const val IMP_SEED = 0xFF51AFD7ED558CCDuL

    // (lo, hi) gram lengths per script class.
    private val NA = 3 to 5 // latin / default
    private val NC = 1 to 2 // CJK
    private val NJ = 2 to 4 // hangul jamo
    private val NS = 2 to 4 // SE-Asian
    private val NI = 1 to 3 // indic clusters

    fun encode(
        text: String,
        nBuckets: Int,
        nHashes: Int,
        nImportance: Int,
        maxFeatures: Int,
    ): NGramFeatures {
        var fs = feats(text)
        if (fs.size > maxFeatures) fs = fs.subList(0, maxFeatures)
        val buckets = ArrayList<IntArray>(fs.size)
        val signs = ArrayList<FloatArray>(fs.size)
        val importance = IntArray(fs.size)
        val nb = nBuckets.toULong()
        val ni = nImportance.toULong()
        for (f in fs.indices) {
            val x = fs[f]
            val bk = IntArray(nHashes)
            val sg = FloatArray(nHashes)
            for (k in 0 until nHashes) {
                val h = fnv64(x, BUCKET_SEEDS[k])
                bk[k] = (h % nb).toInt()
                sg[k] = if ((h shr 63) and 1uL == 1uL) 1f else -1f
            }
            buckets.add(bk)
            signs.add(sg)
            importance[f] = (fnv64(x, IMP_SEED) % ni).toInt()
        }
        return NGramFeatures(buckets, signs, importance)
    }

    private fun fnv64(s: String, seed: ULong): ULong {
        var h = 0xCBF29CE484222325uL xor seed
        for (b in s.toByteArray(Charsets.UTF_8)) {
            h = (h xor (b.toULong() and 0xFFuL)) * 0x100000001B3uL
        }
        return h
    }

    private fun feats(text: String): List<String> {
        val out = ArrayList<String>()
        for (run in tokens(normalize(text))) {
            when {
                run.any { isSEA(it) } -> out += charGrams(run, NS.first, NS.second, "s:")
                run.any { isIndic(it) } -> {
                    val cl = clusters(run)
                    out += "a:" + run.joinToString("")
                    out += clusterGrams(cl, 1, 1, "k:")
                    out += clusterGrams(listOf(listOf("<")) + cl + listOf(listOf(">")), 2, NI.second, "k:")
                }
                run.any { isCJK(it) } -> {
                    val ex = ArrayList<String>()
                    for (c in run) {
                        if (isHangul(c)) out += charGrams(jamo(c), NJ.first, NJ.second, "j:")
                        ex.add(c)
                    }
                    out += charGrams(ex, NC.first, NC.second, "c:")
                }
                else -> {
                    out += "w:" + run.joinToString("")
                    out += charGrams(listOf("<") + run + listOf(">"), NA.first, NA.second, "g:")
                }
            }
        }
        return if (out.isEmpty()) listOf("w:\u0000") else out
    }

    private fun normalize(text: String): String {
        val n = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        return n.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun tokens(text: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (isWordCodePoint(cp)) {
                cur.add(String(Character.toChars(cp)))
            } else if (cur.isNotEmpty()) {
                out.add(cur); cur = ArrayList()
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    private fun clusters(s: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        for (c in s) {
            if (cur.isEmpty()) { cur = arrayListOf(c); continue }
            val p = cur.last().codePointAt(0)
            val virama = p in 0x0900..0x0DFF && ((p and 0xFF) == 0x4D || (p and 0xFF) == 0xCD)
            if (isMark(c) || virama) cur.add(c) else { out.add(cur); cur = arrayListOf(c) }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    private fun jamo(c: String): List<String> {
        val cp = c.codePointAt(0)
        if (cp !in 0xAC00..0xD7A3) return listOf(c)
        val s = cp - 0xAC00
        val r = arrayListOf(codePoint(0x1100 + s / 588), codePoint(0x1161 + (s % 588) / 28))
        if (s % 28 != 0) r.add(codePoint(0x11A7 + s % 28))
        return r
    }

    private fun charGrams(s: List<String>, lo: Int, hi: Int, tag: String): List<String> {
        val r = ArrayList<String>()
        var n = lo
        while (n <= hi) {
            if (s.size >= n) for (i in 0..s.size - n) r.add(tag + s.subList(i, i + n).joinToString(""))
            n++
        }
        return r
    }

    private fun clusterGrams(cl: List<List<String>>, lo: Int, hi: Int, tag: String): List<String> {
        val r = ArrayList<String>()
        var n = lo
        while (n <= hi) {
            if (cl.size >= n) for (i in 0..cl.size - n) r.add(tag + cl.subList(i, i + n).flatten().joinToString(""))
            n++
        }
        return r
    }

    private fun codePoint(v: Int) = String(Character.toChars(v))

    private fun isWordCodePoint(cp: Int): Boolean = when (Character.getType(cp)) {
        Character.UPPERCASE_LETTER.toInt(), Character.LOWERCASE_LETTER.toInt(), Character.TITLECASE_LETTER.toInt(),
        Character.MODIFIER_LETTER.toInt(), Character.OTHER_LETTER.toInt(),
        Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
        Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }

    private fun isMark(c: String): Boolean = when (Character.getType(c.codePointAt(0))) {
        Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt() -> true
        else -> false
    }

    private fun isHangul(c: String) = c.codePointAt(0) in 0xAC00..0xD7A3

    private fun isCJK(c: String): Boolean {
        val v = c.codePointAt(0)
        return v in 0x4E00..0x9FFF || v in 0x3400..0x4DBF || v in 0x20000..0x2A6DF ||
            v in 0xF900..0xFAFF || v in 0x3040..0x30FF || v in 0x31F0..0x31FF || isHangul(c)
    }

    private fun isSEA(c: String): Boolean {
        val v = c.codePointAt(0)
        return v in 0x0E00..0x0EFF || v in 0x1000..0x109F || v in 0x1780..0x17FF
    }

    private fun isIndic(c: String) = c.codePointAt(0) in 0x0900..0x0DFF
}

/**
 * Self-contained unigram (Viterbi) tokenizer over the pruned SentencePiece vocab,
 * reading the same `EMTK` binary the Swift and Python builds use. Token ids are the
 * rows of the semantic table.
 */
internal class SemTokenizer(data: ByteArray) {
    private val scores: FloatArray
    private val index: HashMap<String, Int>
    private val unkId: Int
    private val unkScore: Double
    private val maxLen: Int

    init {
        require(data.size >= 14 && data[0] == 0x45.toByte() && data[1] == 0x4D.toByte() &&
            data[2] == 0x54.toByte() && data[3] == 0x4B.toByte()) { "emo: bad tokenizer file" }
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        unkId = bb.getInt(6)
        val k = bb.getInt(10)
        var off = 14
        val sc = FloatArray(k)
        for (i in 0 until k) { sc[i] = bb.getFloat(off); off += 4 }
        val lens = IntArray(k)
        for (i in 0 until k) { lens[i] = bb.getShort(off).toInt() and 0xFFFF; off += 2 }
        val idx = HashMap<String, Int>(k * 2)
        var maxL = 1
        for (i in 0 until k) {
            val piece = String(data, off, lens[i], Charsets.UTF_8)
            off += lens[i]
            idx[piece] = i
            val n = piece.codePointCount(0, piece.length)
            if (n > maxL) maxL = n
        }
        scores = sc
        index = idx
        unkScore = sc[unkId].toDouble()
        maxLen = minOf(maxL, 24)
    }

    fun encode(text: String): IntArray {
        val norm = "\u2581" + Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKC).replace(" ", "\u2581")
        val s = norm.codePointStrings()
        val n = s.size
        if (n == 0) return IntArray(0)
        val neg = -1e18
        val best = DoubleArray(n + 1) { if (it == 0) 0.0 else neg }
        val backPos = IntArray(n + 1) { -1 }
        val backId = IntArray(n + 1) { -1 }
        for (i in 1..n) {
            val lo = maxOf(0, i - maxLen)
            for (j in lo until i) {
                val tid = index[joinRange(s, j, i)]
                if (tid != null) {
                    val sc = best[j] + scores[tid]
                    if (sc > best[i]) { best[i] = sc; backPos[i] = j; backId[i] = tid }
                }
            }
            val cand = best[i - 1] + unkScore
            if (cand > best[i]) { best[i] = cand; backPos[i] = i - 1; backId[i] = unkId }
        }
        val ids = ArrayList<Int>()
        var i = n
        while (i > 0) { ids.add(backId[i]); i = backPos[i] }
        ids.reverse()
        return ids.toIntArray()
    }

    private fun joinRange(s: List<String>, from: Int, to: Int): String {
        val sb = StringBuilder()
        for (i in from until to) sb.append(s[i])
        return sb.toString()
    }
}
