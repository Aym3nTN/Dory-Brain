# Dory Brain

A very simple Android app for dumping whatever's on your mind before you forget it. Type a thought, hit send, and it gets automatically sorted into a bucket (Work, Personal, Shopping, Ideas, Health, Finance, Reminders, Other) so your list stays organized without any manual filing.

## How categorization works

- **With an NVIDIA API key** (Settings screen): each note is sent to an NVIDIA NIM model (OpenAI-compatible chat completions API) which picks the bucket.
- **Without a key, or if the cloud call fails for any reason** (offline, bad key, timeout): the app falls back to a built-in on-device keyword classifier, so the app always works and notes are never left unsorted.

Get an API key at [build.nvidia.com](https://build.nvidia.com) and paste it into Settings. The default model is `meta/llama-3.1-8b-instruct`, but the model id is editable in Settings.

The API key is stored encrypted on-device via `EncryptedSharedPreferences` and is excluded from Android backups.

## Project structure

- `data/` — Room entity/DAO/database (`Note`, `NoteDao`, `AppDatabase`), the `Category` enum, and `SettingsStore` for the API key/model.
- `data/categorize/` — the `Categorizer` interface, `NvidiaCategorizer` (cloud), `LocalKeywordCategorizer` (offline fallback), and `CategorizerRepository`, which picks between them.
- `ui/` — Jetpack Compose UI: `MainActivity` (nav host), `MainScreen` (quick-capture + grouped list), `SettingsScreen`, and `NoteListViewModel`.

## Building

```
./gradlew :app:assembleDebug
```

Requires an Android SDK (`compileSdk`/`targetSdk` 34, `minSdk` 26). Point `local.properties` at your SDK (`sdk.dir=/path/to/sdk`) or set `ANDROID_HOME`.
