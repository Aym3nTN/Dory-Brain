# Dory Brain

A very simple Android app for dumping whatever's on your mind before you forget it. Type or dictate a thought, hit send, and it gets automatically sorted into a bucket (Work, Personal, Shopping, Ideas, Health, Finance, Reminders, Other) so your list stays organized without any manual filing.

## Features

- **Quick capture** — one field at the top; type it and send.
- **Voice input** — tap the mic to dictate instead of typing. Words stream into the field live as you speak, and dictation appends to whatever is already there.
- **Automatic categorization** — every note is bucketed for you; tap a note's category chip to override it by hand.
- **AI rewrite** — tap the ✨ icon on any note to clean up spelling/grammar, turn it into a checklist, shorten it, or add detail. The rewrite is always shown next to the original for you to accept or reject, so nothing is replaced unseen.

## How categorization works

- **With an NVIDIA API key** (Settings screen): each note is sent to an NVIDIA NIM model (OpenAI-compatible chat completions API) which picks the bucket.
- **Without a key, or if the cloud call fails for any reason** (offline, bad key, timeout): the app falls back to a built-in on-device keyword classifier, so the app always works and notes are never left unsorted.

Get an API key at [build.nvidia.com](https://build.nvidia.com) and paste it into Settings. The default model is `meta/llama-3.1-8b-instruct`, but the model id is editable in Settings.

The API key is stored encrypted on-device via `EncryptedSharedPreferences` and is excluded from Android backups.

**Rewriting requires a key.** Unlike categorization, the AI rewrite feature has no offline fallback — meaningfully rewriting prose needs a language model, and quietly substituting a regex tidy-up would misrepresent what happened. Without a key, the rewrite action tells you to add one instead.

## Voice input

Dictation uses Android's built-in `SpeechRecognizer`, so it relies on whatever recognition service the device provides (usually Google's) rather than sending audio to NVIDIA. The app requests `RECORD_AUDIO` the first time you tap the mic. Depending on the device and language, recognition may require a network connection.

## Project structure

- `data/` — Room entity/DAO/database (`Note`, `NoteDao`, `AppDatabase`), the `Category` enum, and `SettingsStore` for the API key/model.
- `data/nvidia/` — `NvidiaChatClient`, the shared wrapper over NVIDIA's OpenAI-compatible chat completions endpoint.
- `data/categorize/` — the `Categorizer` interface, `NvidiaCategorizer` (cloud), `LocalKeywordCategorizer` (offline fallback), and `CategorizerRepository`, which picks between them.
- `data/refine/` — `RefineMode` (the rewrite presets and their prompts) and `RefinerRepository`.
- `ui/` — Jetpack Compose UI: `MainActivity` (nav host), `MainScreen` (quick-capture + grouped list + rewrite review dialog), `SettingsScreen`, and `NoteListViewModel`.
- `ui/speech/` — `SpeechInputController`, which drives dictation and the mic permission prompt.

## Building

```
./gradlew :app:assembleDebug
```

Requires an Android SDK (`compileSdk`/`targetSdk` 34, `minSdk` 26). Point `local.properties` at your SDK (`sdk.dir=/path/to/sdk`) or set `ANDROID_HOME`.
