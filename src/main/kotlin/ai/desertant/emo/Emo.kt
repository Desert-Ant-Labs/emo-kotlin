package ai.desertant.emo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single emoji suggestion returned by [Emo.suggestions]. */
data class EmoSuggestion(
    /** The suggested emoji. */
    val emoji: String,
    /** The model's normalized confidence for this suggestion, from `0.0` to `1.0`. */
    val confidence: Double,
)

/** Thrown when a bundled model resource cannot be found or loaded. */
class EmoException(message: String) : Exception(message)

/**
 * Predicts emojis for short task, calendar, note, or message text.
 *
 * Emo runs fully on-device using a small bundled model (~3.8 MB total) across
 * 22 languages, with no network access. The model is loaded once on first use.
 *
 * [suggestions] is a suspending function that runs the load and inference on a
 * background dispatcher, so it's safe to call from any coroutine.
 *
 * ```kotlin
 * val suggestions = Emo.suggestions("Pay my bills")
 * val emoji = Emo.suggestions("犬の散歩", limit = 1).firstOrNull()?.emoji // "🐕"
 * val toned = Emo.suggestions("go for a run", limit = 1, skinTone = EmojiSkinTone.MEDIUM).firstOrNull()?.emoji // "🏃🏽"
 * ```
 */
object Emo {
    /**
     * Returns emoji suggestions for a phrase, sorted from most to least likely.
     *
     * @param text A short task, calendar entry, note, or message draft.
     * @param limit The maximum number of suggestions to return. Pass `1` for only the best emoji.
     * @param skinTone Preferred skin tone for skin-tone-capable emoji. Defaults to [EmojiSkinTone.DEFAULT].
     * @return Up to [limit] suggestions. Empty or blank input returns an empty list.
     */
    suspend fun suggestions(text: String, limit: Int = 3, skinTone: EmojiSkinTone = EmojiSkinTone.DEFAULT): List<EmoSuggestion> =
        withContext(Dispatchers.Default) { model.suggestions(text, limit, skinTone) }

    private val model: EmoModel by lazy {
        EmoModel(
            weights = resource("emo.safetensors"),
            tokenizer = resource("emo_tokenizer.bin"),
            meta = EmoMeta.parse(resource("emo_meta.json").toString(Charsets.UTF_8)),
        )
    }

    private fun resource(name: String): ByteArray =
        (Emo::class.java.getResourceAsStream("/$name")
            ?: throw EmoException("Emo resource not found in package: $name"))
            .use { it.readBytes() }
}
