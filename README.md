Emo is a small on-device Kotlin library that suggests emojis for short tasks, calendar entries, or phrases.

```kotlin
import ai.desertant.emo.Emo
import ai.desertant.emo.EmojiSkinTone

// suspend function — call from a coroutine
val suggestions = Emo.suggestions("Pay my bills")
// ["💰", "💳", "🧾", ...]

val emoji = Emo.suggestions("犬の散歩", limit = 1).firstOrNull()?.emoji
// "🐕"

val toned = Emo.suggestions("go for a run", limit = 1, skinTone = EmojiSkinTone.MEDIUM).firstOrNull()?.emoji
// "🏃🏽"
```

## Features

- Runs fully on-device — pure Kotlin, no native or network dependencies
- Suggests from a data-driven vocabulary of ~300 task/calendar/message emojis
- Supports 22 languages (incl. CJK, Arabic, Thai, Hindi, …)
- Bundled model + tokenizer are about 5.0 MB (4-bit palettized weights)
- Prediction is well under 2 ms after the one-time model load
- Runs on the JVM and Android 5.0+ (API 21)

## Installation

The library bundles the model in its resources, so it's a single dependency.
It's distributed through [JitPack](https://jitpack.io/#Desert-Ant-Labs/emo-kotlin).

Add the JitPack repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Then add the dependency, replacing `0.1.0` with the
[latest release tag](https://github.com/Desert-Ant-Labs/emo-kotlin/releases):

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.Desert-Ant-Labs:emo-kotlin:0.1.0")
}
```

You can also depend on a specific commit or branch (e.g. `main-SNAPSHOT`) — see the
[JitPack page](https://jitpack.io/#Desert-Ant-Labs/emo-kotlin) for the available builds.

## Usage

`suggestions` is a suspending function, so call it from a coroutine:

```kotlin
import ai.desertant.emo.Emo

val results = Emo.suggestions("Call mom")

for (result in results) {
    println("${result.emoji} ${result.confidence}")
}
```

Limit the number of returned suggestions:

```kotlin
val best = Emo.suggestions("bike to work", limit = 1).firstOrNull()?.emoji
```

The model is loaded once, lazily, on the first call. `suggestions` runs the load and
inference on a background dispatcher (`Dispatchers.Default`), so it's safe to call from
any coroutine, including on the main thread's scope.

## API

```kotlin
object Emo {
    suspend fun suggestions(
        text: String,
        limit: Int = 3,
        skinTone: EmojiSkinTone = EmojiSkinTone.DEFAULT,
    ): List<EmoSuggestion>
}

enum class EmojiSkinTone {
    DEFAULT, LIGHT, MEDIUM_LIGHT, MEDIUM, MEDIUM_DARK, DARK
}

data class EmoSuggestion(
    val emoji: String,
    val confidence: Double,
)
```

Empty or blank input returns an empty list. `skinTone` post-processes skin-tone-capable emoji; the default is `DEFAULT` (no modifier).

## Model

The bundled model is published at [`desert-ant-labs/emo`](https://huggingface.co/desert-ant-labs/emo)
on Hugging Face — full weights, the `emo.safetensors` build used here, and the model card.

## License

See [`LICENSE.md`](LICENSE.md) — Desert Ant Labs Source-Available License v1.0. Free for
commercial use up to 100,000 MAU per Model; <licensing@desertant.ai> above that.
