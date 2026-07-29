# Dory Brain

A very simple Android app for dumping whatever's on your mind before you forget it. Type or dictate a thought, hit send, and it gets automatically sorted into a bucket (Work, Personal, Shopping, Ideas, Health, Finance, Reminders, Other) so your list stays organized without any manual filing.

## Features

- **Quick capture** — a dedicated New Thought screen with a big mic button; type it or say it and send.
- **Voice input** — dictate instead of typing. Words stream into the field live as you speak, and dictation appends to whatever is already there.
- **Automatic categorization** — every note is bucketed for you, with the sorting step shown as it happens. Tap a note's category pill to override it by hand.
- **AI rewrite** — tap ✨ on a note to clean up spelling/grammar, turn it into a checklist, shorten it, or add detail. The rewrite is always shown next to the original for you to accept or reject, so nothing is replaced unseen.
- **Browse and search** — a Notes tab with full-text search, per-bucket filtering, and day grouping; a Home tab listing every bucket with live counts.
- **Light and dark** — follows the system by default, or lock it in Settings.

## Screens

Three tabs plus two pushed screens:

- **Home** — "What's on your mind?", shortcuts to type or speak, and the bucket list with counts. Tapping a bucket jumps to Notes filtered to it.
- **Notes** — search, bucket filter, notes grouped by day with a coloured accent per bucket.
- **Settings** — NVIDIA API key/model with a real connection test, on-device-only toggle, appearance, version.
- **New Thought** — the capture screen, with the live "Categorizing…" card and the resulting bucket.
- **Note detail** — the note, its bucket pill, inline editing, AI rewrite, share, and delete.

## How categorization works

- **With an NVIDIA API key** (Settings screen): each note is sent to an NVIDIA NIM model (OpenAI-compatible chat completions API) which picks the bucket.
- **Without a key, or if the cloud call fails for any reason** (offline, bad key, timeout): the app falls back to a built-in on-device keyword classifier, so the app always works and notes are never left unsorted.

Get an API key at [build.nvidia.com](https://build.nvidia.com) and paste it into Settings. The default model is `meta/llama-3.1-8b-instruct`, but the model id is editable in Settings.

The API key is stored encrypted on-device via `EncryptedSharedPreferences` and is excluded from Android backups.

**Rewriting requires a key.** Unlike categorization, the AI rewrite feature has no offline fallback — meaningfully rewriting prose needs a language model, and quietly substituting a regex tidy-up would misrepresent what happened. Without a key, the rewrite action tells you to add one instead.

## Voice input

Dictation uses Android's built-in `SpeechRecognizer`, so it relies on whatever recognition service the device provides (usually Google's) rather than sending audio to NVIDIA. The app requests `RECORD_AUDIO` the first time you tap the mic. Depending on the device and language, recognition may require a network connection.

## Project structure

- `data/` — Room entity/DAO/database (`Note`, `NoteDao`, `AppDatabase`), the `Category` enum, and `SettingsStore` for the API key, model, theme, and fallback preference.
- `data/nvidia/` — `NvidiaChatClient`, the shared wrapper over NVIDIA's OpenAI-compatible chat completions endpoint, plus `NvidiaConnectionTester` behind the Test Connection row.
- `data/categorize/` — the `Categorizer` interface, `NvidiaCategorizer` (cloud), `LocalKeywordCategorizer` (offline fallback), and `CategorizerRepository`, which picks between them.
- `data/refine/` — `RefineMode` (the rewrite presets and their prompts) and `RefinerRepository`.
- `ui/` — `MainActivity` (bottom-nav scaffold + NavHost), `NoteListViewModel`, `SettingsViewModel`, and `CategoryVisuals` mapping each bucket to its colour and glyph.
- `ui/components/` — the shared card, accent-bar card, icon tile, bucket pill, and settings row.
- `ui/screens/` — `HomeScreen`, `NotesScreen`, `NoteDetailScreen`, `ComposeThoughtScreen`, `SettingsScreen`.
- `ui/speech/` — `SpeechInputController`, which drives dictation and the mic permission prompt.
- `ui/theme/` — the violet palette, typography, shapes, and `ThemeMode`.

Material You dynamic colour is deliberately off: the palette is a fixed brand look, and dynamic colour would repaint it from the device wallpaper.

## Building

```
./gradlew :app:assembleDebug
```

Requires an Android SDK (`compileSdk`/`targetSdk` 34, `minSdk` 26). Point `local.properties` at your SDK (`sdk.dir=/path/to/sdk`) or set `ANDROID_HOME`.
