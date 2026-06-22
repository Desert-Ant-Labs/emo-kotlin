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

The example fetches the published library from JitPack
(`implementation("com.github.Desert-Ant-Labs:emo-kotlin:0.1.0")`, with the
`https://jitpack.io` repository added in `settings.gradle.kts`) — the same way a
real consumer would depend on it.

## What it shows

- Calling the suspending `Emo.suggestions(...)` API from Compose coroutines.
- Live, debounced emoji prediction while typing.
- A release build with R8 (`minifyEnabled true`) and **no Emo-specific keep
  rules** — `assembleRelease` works as-is.

Minimum SDK is 21, matching the library.
