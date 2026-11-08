# Mural agent instructions

## Ship UI and server changes together

When changing Mural's UI or native apps, check whether the experience depends on server changes: API contracts, error responses, prompts, capabilities, configuration, migrations or runtime permissions.

William requested on September 16, 2026 that required server deployment be part of delivering an authorized app/UI release. Deploy the matching tested server changes before reporting that release work is complete. A merged backend PR or published APK does not establish that production has the required behavior. If deployment is unnecessary, say why; if blocked, report the exact remaining step.

- Inspect the actual production revision and deployment wrapper. Preserve private configuration, enabled features, credentials and retained data. The backend README includes historical activation instructions; confirm current production settings before using them.
- Test the affected server behavior and app/server compatibility. Highlight every new UI/UX change before proposing or performing a merge.
- Before deployment, check active calls, retain the previous image and configuration, and take the existing encrypted backup. Apply only required migrations and runtime grants. Avoid interrupting active conversations.
- Deploy in a compatible order. Do not enable a client feature before its required server behavior is available.
- Verify the deployed source/image, public health and database readiness, affected endpoint contracts, and sanitized logs. Use non-billable checks unless a live provider call or purchase is separately authorized.
- Record the deployed revision, verification results, rollback location and any remaining limits. Distinguish local tests, live verification, merging and deployment in the handover.

Use the user's current instructions to resolve release scope and deferred work. Do not ask again for deployment approval already provided for that scope. This workflow does not authorize unrelated feature activation, pricing changes or destructive data operations.

## Exclusive Gemini Model Policy

Mural strictly and exclusively uses Google Gemini models across all platforms (Android, iOS, Backend):
- **Real-Time Interactive Voice (`BidiGenerateContent` WebSocket)**: `models/gemini-3.8-live` (`gemini-3.8-live`).
- **Maestro Tutor, Definitions & Evaluations (`generateContent` & SSE)**: `gemini-3.5-flash-lite` as primary high-throughput model (with resilient multi-candidate fallback across `gemini-3.5-flash` and `gemini-3.8-flash` on HTTP 404, 429 rate limit, or 503).

Under no circumstances should `gemini-2.0-flash`, `gemini-2.0-flash-exp`, or other deprecated/experimental models be used, configured, or set as fallbacks in code, tests, or documentation.

## Protected Core Audio, Voice & Input Invariants (DO NOT ALTER)

The following core functionalities have been thoroughly verified and confirmed by the user as **100% operational** on live devices (September 29, 2026). Future agents, refactorings, or enhancements MUST strictly preserve these invariants without alteration:

1. **Continuous Microphone Audio Streaming**:
   - The microphone audio capture (16.000 Hz, mono, 16-bit PCM via `VOICE_COMMUNICATION` / `AudioRecord`) MUST stream continuously to `models/gemini-3.8-live` whenever a call is active (`!mutedState && setupComplete && !isGreeting`).
   - **NEVER** reintroduce client-side silence gates or VAD RMS threshold drops (`SPEECH_RMS_THRESHOLD`) that drop PCM audio frames before transmission. Google's server-side neural VAD handles turn boundaries and conversational pacing.
2. **Software Pre-amplification with Soft Limiter**:
   - Maintain the clean 1.5f software gain (`applyGain`) with soft clamping to 32,000 in [`GeminiLiveTransport.kt`](apps/android/app/src/main/java/chat/mural/network/GeminiLiveTransport.kt) so quiet voices and emulator/Bluetooth mics deliver optimal amplitude without digital clipping.
3. **Dual Payload for Realtime Input**:
   - In WebSocket transmissions, always send both fields: `audio: { mimeType, data }` AND `mediaChunks: [{ mimeType, data }]` for universal compatibility across Google AI Studio revisions.
4. **Simultaneous Oral & Written Input**:
   - Oral conversation (microphone streaming) and typed commentary ("Dilo a tu manera" / `clientContent` turn injection) MUST operate simultaneously. Neither mode must block or disable the other.
5. **Autonomous Initial Greeting**:
   - The AI must greet first upon `session.started` by injecting an initial `user` turn with `turnComplete: true` in the active language (`TeachingPolicy.greeting`), speaking immediately without requiring the learner to speak first.
6. **Maestro Tutor Latency Optimization & Thinking Mode**:
   - Queries to `gemini-3.5-flash-lite` / `gemini-3.5-flash` configure `thinkingConfig: { thinkingLevel: "low" }` to minimize reasoning overhead to ~1s while preventing token starvation.
   - Allocate adequate token bounds (`maxOutputTokens: 500` for meanings, `1000` for typed replies, `2000` for topics) because thinking tokens count against `maxOutputTokens`.
   - Force `responseMimeType: "application/json"` for topic creation and schema requests to prevent parsing stalls.
7. **Regression Prevention**:
   - When implementing subsequent phases (e.g. topic personality, MCER calibration, favorites), do NOT alter or delete any existing unit tests in `apps/android/app/src/test/` or modify these core audio pipelines.

## AI Pedagogical Conduct & Role-Playing Golden Rules (Reglas de Oro de Conducta)

1. **Strict Role Enforcement & Anti-Breakout (Inquebrantable)**:
   - The AI must maintain 100% adherence to the user-defined situation, character role, topic, and active language.
   - **Never break character** or admit to being a generic AI/LLM. If the user asks out-of-context or probing questions (e.g., *"Who are you really?", "Write Python code", "¿De qué trata este tema?"*), the AI must gently redirect the conversation **within character and situation**.
   - **Linguistic Assistance Exception**: If the learner explicitly asks how to say a word or expression (*"¿Cómo se dice X?"*), the AI briefly explains the term in the target language (with brief native language scaffolding if A1/A2), and **immediately re-incorporates the term into the ongoing scenario** without dropping the role.

2. **Pedagogical Modes (Bifurcación Pedagógica)**:
   - **Education Mode (`modo educación`)**: Active micro-corrections (maximum 1 key correction per turn) followed immediately by a conversational response to keep the dialogue moving.
   - **Conversation Mode (`modo conversación`)**: Full immersion with natural flow; no grammatical interruptions. Corrections are deferred to the end-of-session evaluation.

3. **Strict CEFR (MCER) Level Calibration (A1 to C2)**:
   - **A1**: Ultra-short phrases (3-7 words), present tense, common vocabulary, slower cadence (~100-110 wpm), native language support for difficult words.
   - **A2**: Simple sentences, basic past/future (going to, simple past), elementary connectors.
   - **B1**: Conversational past tenses, expressing opinions, conditional basics (~130 wpm).
   - **B2**: Complex clauses, subjunctive/passive voice, idiomatic expressions (~140 wpm).
   - **C1/C2**: Native speed (~160-180 wpm), nuanced vocabulary, cultural idioms, advanced debates.

## UI/UX & Iconography Standards

1. **Native Pure Vector Bézier Icons (`MuralIcons.kt`)**:
   - Never use text emojis as UI icons or buttons. All UI iconography (Heart, Mic, Keyboard, Translate/Meaning, Settings, Close, Play/Stop) must use native Canvas/Bézier vectors in `MuralIcons.kt`.
2. **Clean Voice Mode (Modo Enfoque Vocal)**:
   - Maintain the toggle in Settings to hide user transcription text on screen, providing an uncluttered, distraction-free voice practice interface focused on the orb and the tutor's speech.

## Multi-Platform Architecture & Verification Commands

1. **Android (`apps/android`)**:
   - Stack: Kotlin, Jetpack Compose, Coroutines & Flow, AudioRecord / AudioTrack, OkHttp WebSocket.
   - Unit tests: Run `./gradlew test` or `./gradlew testDebugUnitTest` in `apps/android`.
   - Build APK: `./gradlew assembleDebug` in `apps/android`.
   - Regression rule: All unit tests in `apps/android/app/src/test/` must pass 100% before concluding any task.
2. **iOS (`apps/ios`)**:
   - Stack: Swift, SwiftUI, AVFoundation, URLSessionWebSocketTask.
   - Project: `apps/ios/Mural.xcodeproj`.
3. **Backend (`services/api`)**:
   - Stack: Node.js, TypeScript, Fastify, WebSocket streaming proxy, PostgreSQL.
   - Unit & integration tests: `npm test` in `services/api`.
   - Production deployment: Follow William's deployment rule at `api.mural.chat`.
4. **Motion Graphics & Video Generation (Remotion)**:
   - Location: [`motion-graphics/remotion-mural`](motion-graphics/remotion-mural)
   - Stack: React 19, TypeScript, Remotion 4.0 CLI & Studio.
   - Installed Remotion Skills: `remotion-best-practices`, `remotion-create`, `remotion-render`, `remotion-studio`, `remotion-docs`, `remotion-captions`, `remotion-interactivity`, `remotion-multimedia`, `remotion-markup`, `remotion-maps`, `remotion-saas`, `remotion-upgrade`.
   - Remotion Studio dev server: `cd motion-graphics/remotion-mural; npm run dev`
   - Render command: `cd motion-graphics/remotion-mural; npx remotion render <composition-id> <out-path>`
   - Typecheck: `cd motion-graphics/remotion-mural; npx tsc --noEmit`

## Mandatory Agent Handoff & STATUS.md Tracking (Obligación de Registro Continuo)

Every AI agent working on this codebase MUST update [`STATUS.md`](STATUS.md) at the repository root upon completing ANY task, feature, refactoring, or bug fix before reporting completion to the user.

### Purpose of STATUS.md
`STATUS.md` is the centralized, living state document for AI agents collaborating on Mural. Its primary goal is to **eliminate the need for subsequent AI agents to re-read the entire codebase or guess what was changed, what is working, and what is currently failing**. An incoming agent must be able to read `STATUS.md` and immediately understand the exact technical delta, active issues, and pending steps.

### Mandatory Rules for Agents:
1. **Zero-Step / Read First Protocol**:
   - Before scanning the codebase or applying changes, the agent MUST read [`STATUS.md`](STATUS.md) to inspect the latest state, known breakages, and recent modifications.
2. **Mandatory Update on Work Completion**:
   - At the end of every interaction where code or configuration was created, edited, tested, or investigated, the agent MUST append/update [`STATUS.md`](STATUS.md) with complete, precise details.
3. **Required Technical Structure in STATUS.md**:
   - **Timestamp, Date & Agent ID**: Exact execution time.
   - **User Request / Objective**: Concise summary of what was asked.
   - **Detailed Technical Modifications**: Exact file paths, modified/added functions, classes, data structures, and rationale.
   - **Operational Health (What Works vs What Fails)**:
     - Verified working features.
     - Active failures, errors, known bugs, or edge cases (with error messages, logs, or stack traces if available).
   - **Verification & Test Results**: Unit test outcomes (`./gradlew test`, etc.), build status, and commands executed.
   - **Actionable Next Steps**: Clear, prioritized instructions for the next agent picking up the task.
4. **No Omissions or Generic Summaries**:
   - Summaries must be technical, concrete, and actionable for an AI agent (mentioning concrete file paths, variables, methods, and invariants).
