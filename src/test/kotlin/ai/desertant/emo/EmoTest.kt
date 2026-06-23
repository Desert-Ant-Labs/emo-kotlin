package ai.desertant.emo

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmoTest {
    private suspend fun top(task: String): String = Emo.suggestions(task, limit = 1).firstOrNull()?.emoji ?: ""

    @Test
    fun englishPredictions() = runBlocking {
        assertTrue(top("Pay my bills") in setOf("💰", "💳", "🧾", "🏦"))
        assertTrue(top("walk the dog") in setOf("🐕", "🐾", "🚶"))
        assertEquals("✈️", top("book a flight to Tokyo"))
        assertTrue(top("dentist appointment") in setOf("🦷", "📅", "🏥"))
    }

    @Test
    fun multilingualPredictions() = runBlocking {
        assertTrue(top("犬の散歩") in setOf("🐕", "🐾"))
        assertTrue(top("café con leche") in setOf("☕", "🍵", "🥛"))
        assertEquals("✈️", top("réserver un vol pour Tokyo"))
    }

    @Test
    fun ranking() = runBlocking {
        val results = Emo.suggestions("Pay my bills", limit = 3)
        assertEquals(3, results.size)
        assertTrue(results[0].confidence >= results[1].confidence)
        assertTrue(results.all { it.confidence in 0.0..1.0 })
    }

    @Test
    fun emptyInput() = runBlocking {
        assertTrue(Emo.suggestions("   ").isEmpty())
    }

    @Test
    fun skinTonePostprocessing() {
        assertEquals("🏃🏽", "🏃".applyingSkinTone(EmojiSkinTone.MEDIUM))
        assertEquals("🧑🏿‍🍳", "🧑‍🍳".applyingSkinTone(EmojiSkinTone.DARK))
        assertEquals("✍🏻", "✍️".applyingSkinTone(EmojiSkinTone.LIGHT))
        assertEquals("🐕", "🐕".applyingSkinTone(EmojiSkinTone.MEDIUM))
    }
}
