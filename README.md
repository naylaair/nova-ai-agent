# Nova — multi-agent Android assistant

Nova is a small, lightweight Android app that lets you chat with a tiny pipeline
of three cooperating Claude agents instead of a single LLM call. The pipeline
runs entirely on-device (the agents are just Kotlin code) and only the actual
language-model calls are sent to Anthropic's API.

```
User prompt
   │
   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  Researcher  │ → │   Analyzer   │ → │    Writer    │
│  (key facts) │   │ (synthesise) │   │ (final reply)│
└──────────────┘   └──────────────┘   └──────────────┘
   │                                           │
   ▼                                           ▼
 intermediate notes shown in UI         final answer
```

## Why three agents?

A single prompt often mixes brainstorming, fact-checking and final writing
into one blob, which makes hallucinations harder to catch. Splitting the work
into three short, focused passes gives:

- **Better grounding.** The Researcher only enumerates facets — it never
  commits to an answer, so the final reply is built on top of an explicit
  list of considerations.
- **A self-critique step.** The Analyzer is prompted to fix gaps and remove
  weak claims from the Researcher's notes before anything is shown to the
  user.
- **Auditability.** The intermediate outputs are surfaced in the chat
  bubble, so the user can read *why* Nova answered the way it did.

## Tech stack

- Kotlin 1.9 + Jetpack Compose (Material 3)
- Min SDK 24 / Target SDK 34
- OkHttp for HTTPS, kotlinx-serialization for JSON
- DataStore Preferences for the API key (stored only on device)
- R8 + resource shrinking on release builds

The release APK is small — well under the size of the average chat app on
the Play Store.

## Running locally

1. `git clone https://github.com/naylaair/nova-ai-agent && cd nova-ai-agent`
2. Open in Android Studio Hedgehog or newer.
3. Build > Generate Signed Bundle / APK… or run on a device/emulator.
4. Open the app, tap the gear icon, paste your Anthropic API key (`sk-ant-…`).
5. Send a message. You'll see the Researcher / Analyzer / Writer banner cycle
   through, and the intermediate notes appear above the final answer.

To build a debug APK from the command line (requires Android SDK):

```bash
./gradlew :app:assembleDebug
# APK path: app/build/outputs/apk/debug/app-debug.apk
```

## Privacy

- The API key never leaves your device — it's stored in the app's private
  DataStore.
- Nova does not collect analytics. The only network calls go directly from
  your device to `api.anthropic.com`.

## License

MIT — see [LICENSE](./LICENSE).
