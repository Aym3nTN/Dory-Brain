# Dory Brain

An Android **and** desktop app for dumping whatever's on your mind before you forget it. Type or dictate a thought, hit send, and it gets automatically sorted into a bucket (Work, Personal, Shopping, Ideas, Health, Finance, Reminders, Other) so your list stays organized without any manual filing.

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
- **New Thought** — the capture screen. Typing shows the field, the "Categorizing…" card and the resulting bucket; dictating switches it to a full-screen listening state with a live waveform.
- **Refine with AI** — pushed from the capture screen: the draft as you said it, the AI's rewrite, then Save Note or Edit Manually.
- **Note detail** — the note, its bucket pill, inline editing, AI rewrite, share, and delete.

### The listening state

While dictating, the capture screen shows a waveform driven by the recognizer's real RMS levels rather than a decorative animation, so it reflects what the microphone is actually picking up. Because levels collapse to near-zero during a pause — which would read as "dead" — a slow idle ripple keeps a minimum of motion, and the caption switches between "Listening…" and "Mic is on / take as long as you like". The running transcript stays on screen above the waveform: with continuous dictation you may speak several sentences, and hiding them makes it impossible to tell whether anything was captured.

### Refining before saving

`Refine with AI` on the capture screen rewrites the draft *before* it becomes a note, which is a separate flow from rewriting a saved note (`DraftRefineState` vs `RefineState`): there's no note to update yet, and the outcome is either Save Note or Edit Manually, the latter dropping the AI's wording back into the field. It uses the Clean up mode; the other modes (checklist, shorten, add detail) remain on saved notes.

Note on the footer text: the design's "happens on-device or securely in the cloud" isn't true of this app — sorting can run on-device, but rewriting always goes to NVIDIA. The screen says that instead of the friendlier-but-false version.

## How categorization works

- **With an NVIDIA API key** (Settings screen): each note is sent to an NVIDIA NIM model (OpenAI-compatible chat completions API) which picks the bucket.
- **Without a key, or if the cloud call fails for any reason** (offline, bad key, timeout): the app falls back to a built-in on-device keyword classifier, so the app always works and notes are never left unsorted.

Get an API key at [build.nvidia.com](https://build.nvidia.com) and paste it into Settings. The default model is `meta/llama-3.1-8b-instruct`, but the model id is editable in Settings.

The API key is stored encrypted on-device via `EncryptedSharedPreferences` and is excluded from Android backups.

**Rewriting requires a key.** Unlike categorization, the AI rewrite feature has no offline fallback — meaningfully rewriting prose needs a language model, and quietly substituting a regex tidy-up would misrepresent what happened. Without a key, the rewrite action tells you to add one instead.

## Voice input (Android only)

Dictation uses Android's built-in `SpeechRecognizer`, so it relies on whatever recognition service the device provides (usually Google's) rather than sending audio to NVIDIA. The app requests `RECORD_AUDIO` the first time you tap the mic. Depending on the device and language, recognition may require a network connection.

**The mic stays open through pauses.** `SpeechRecognizer` is built for a single short utterance — it decides on its own that you've stopped talking and ends the session, and the `EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS` extras that ask for more patience are documented as hints that most recognizers ignore. So one dictation *session* here is not one recognizer session:

- Whenever a segment ends — with a result, a no-match, or a speech timeout from a long pause — the recognizer is started again immediately.
- Each finished segment is appended to a running transcript, so words spoken before a pause are never lost or overwritten.
- Only an explicit stop (the button, sending the note, or leaving the screen) ends it. Real faults — permissions, audio, network — still stop and say why.
- A guard counts failures that arrive too fast to be a human pause and gives up after a handful, so a broken recognizer can't spin in a restart loop holding the mic.

While listening, a status line reads "Mic is on — take as long as you like", switching to "Listening..." when speech is actually being heard, so a pause doesn't look like the recognizer quit.

**Accumulating across pauses is defensive**, because recognizers disagree about what a restarted segment returns. Most give only the new words; some restate the whole utterance so far; some redeliver the previous segment verbatim. Appending blindly duplicates in the second case, replacing blindly erases in the third — so the three are told apart explicitly and the committed text is only ever allowed to grow. The text already in the field before dictation started (the baseline) is held inside `DictationSession` rather than in the UI, so what comes back is always the complete field contents and can't be rebuilt from a stale captured value.

Each segment also gets a freshly created `SpeechRecognizer`. Reusing one whose previous session has finished behaves erratically on several devices — callbacks stop arriving, or arrive against the wrong session.

The decision logic lives in `DictationSession`, deliberately free of framework objects so it's unit tested on the JVM (`./gradlew :app:testDebugUnitTest`) — including that a pause restarts rather than ends, that text accumulates across pauses, and that the transcript never shrinks under any of the three recognizer behaviours above.

The desktop app has no dictation — see the module notes below.

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

## Modules

Compose Multiplatform, with one shared module and two thin platform apps:

| Module | What it is |
| --- | --- |
| `:shared` | KMP module (`androidTarget` + `jvm("desktop")`). Domain models, the NVIDIA client, categorizer, rewriter, the storage/settings **interfaces**, and the whole design system (palette, typography, shapes, bucket colours, shared components). |
| `:app` | Android app. Supplies Room + `EncryptedSharedPreferences` implementations, dictation, and the phone UI (bottom nav). |
| `:desktop` | Compose Desktop app. Supplies SQLite-over-JDBC + a settings file, and a desktop UI (sidebar rail). |

### What is and isn't shared

Shared: every model, all the AI logic (categorize, rewrite, connection test), the NVIDIA HTTP layer, and the design system — so both apps look like the same product by construction rather than by copying.

Not shared, and why:

- **Storage.** Room is Android-only at the version pinned here, so `:shared` declares a `NoteStore` interface and each platform implements it (Room / SQLite-JDBC).
- **Secret storage.** `EncryptedSharedPreferences` has no desktop equivalent — see the security note below.
- **Dictation.** Android's `SpeechRecognizer` is free and built in; there is no desktop counterpart, so **the desktop app has no voice input.** Adding it would mean a paid cloud speech service and its own key.
- **Presentation state.** `androidx.lifecycle.ViewModel` isn't multiplatform at these versions, so the Android `NoteListViewModel` and the desktop `DesktopStore` duplicate the orchestration around otherwise-shared logic. Consolidating these is the obvious next step.

### Security note: the API key is handled differently per platform

- **Android** — encrypted at rest with `EncryptedSharedPreferences` (platform keystore), excluded from backups.
- **Desktop** — a plain properties file in the app data directory, restricted to your user (`0600` where POSIX permissions apply). **It is not encrypted.** Without binding to an OS keychain there's nowhere to put a key that an attacker with your file access couldn't also read, and encrypting with a key stored beside the ciphertext would be obfuscation dressed up as security. The Settings screen says so in the app too.

## Building

Android (requires an Android SDK, `compileSdk`/`targetSdk` 34, `minSdk` 26 — point `local.properties` at it via `sdk.dir=/path/to/sdk`, or set `ANDROID_HOME`):

```
./gradlew :app:assembleDebug
```

Desktop — run it, or build a native installer (`.deb`/`.msi`/`.dmg` for the host OS):

```
./gradlew :desktop:run
./gradlew :desktop:packageDistributionForCurrentOS
```

Desktop data lives in the usual per-OS location: `~/.local/share/DoryBrain` (Linux), `~/Library/Application Support/DoryBrain` (macOS), `%APPDATA%\DoryBrain` (Windows).

Tests for the hand-written desktop storage layer:

```
./gradlew :desktop:jvmTest
```

### Two build notes

- The packaged desktop app needs `java.sql` declared in `nativeDistributions { modules(...) }`. jlink strips it otherwise, and the app dies on `NoClassDefFoundError: java/sql/DriverManager` the moment SQLite opens — a failure that only appears in the distributable, never when running from Gradle.
- The build prints a Kotlin-Multiplatform/AGP compatibility warning (AGP 8.5.2 is newer than the 8.2 that Kotlin 1.9.24's MPP plugin was tested against). Everything compiles and runs; the warning is left visible rather than suppressed because the combination genuinely isn't one JetBrains tested.
