# Voice Files 🎙️📂

An Android app that opens your downloaded files when you **say** their name.

Tap **Speak** and say:

- “Open physics notes chapter 3”
- “Open lab report pdf”
- “Open the latest download”  /  “Open the last pdf”
- “Open today’s photo”  /  “Open yesterday’s screenshot”
- “Open uno no mercy apk”
- “Share the resume pdf”

You don’t need the exact file name — close is enough (`Physics_Notes_Ch3.pdf` matches “physics notes chapter three”).
If several files fit, the app asks which one. It speaks back what it’s opening.

Also: a searchable list of everything in **Download** (and its sub-folders), filter chips (PDF, Images, Videos, Audio, Docs, APK), tap to open, long-press to share, and a home-screen shortcut (long-press the icon → **Voice open**).

## Install on your phone

1. Open this repo’s **Releases** page on your phone → **Voice Files (latest build)** → download **VoiceFiles.apk**.
2. Open the APK and allow “Install unknown apps” for your browser if asked.
3. Open Voice Files → **Allow access** → turn on **Allow access to manage all files** → go back.

Android 11+ only lets an app see PDFs and documents downloaded by *other* apps through “All files access”. The app only reads your Download folder, opens files only when you ask, and has **no internet permission** — nothing leaves your phone.

Every push to `main` rebuilds the APK automatically (GitHub Actions → `Build APK`). New builds install over the old one as an update.

## Code

| File | What it does |
|---|---|
| `VoiceCommand.kt` | Turns speech into a query (action, words, file type, “latest”, “today”) and fuzzy-matches it against file names |
| `TextNorm.kt` | Splits names like `LabReport_v2` into words, fuzzy word similarity |
| `FileRepository.kt` | Scans Download recursively |
| `FileOpener.kt` | Opens / shares a file in the right app via `FileProvider` |
| `MainActivity.kt` | UI, permissions, speech recognition, text-to-speech |
