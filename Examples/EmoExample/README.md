# EmoExample (Android)

A tiny todo app that suggests an emoji for each item as you type, using the
[`emo`](../..) on-device library. It mirrors the iOS `EmoExample` in `emo-swift`.

As you type a todo, the app calls `Emo.suggestions(...)` (debounced ~200 ms) and
shows the top emoji live; saving stores it alongside the title.

```kotlin
val emoji = Emo.suggestions(text, limit = 1).firstOrNull()?.emoji
```

## Run it

Open the `Examples/EmoExample` folder in Android Studio and run the `app`
configuration, or from the command line:

```sh
./gradlew :app:installDebug
```

The example builds the local library straight from source via a Gradle composite
build (`includeBuild("../..")` in `settings.gradle.kts`), so it always reflects
the current `emo-kotlin` checkout — the same way the iOS example references the
local Swift package at `../..`.

## What it shows

- Calling the suspending `Emo.suggestions(...)` API from Compose coroutines.
- Live, debounced emoji prediction while typing.
- A release build with R8 (`minifyEnabled true`) and **no Emo-specific keep
  rules** — `assembleRelease` works as-is.

Minimum SDK is 21, matching the library.
