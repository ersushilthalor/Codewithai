# REA Studio Fix Tasks Progress

## GROUP A: Bugs that break correctness
- [x] A1: AxmlParser.kt string pool bug (5 ints always, type rawType ushr 16, unit test, package mismatch warning)
- [x] A2: Main-thread blocking (withContext(Dispatchers.IO) in ApkAnalysisService, delete inspected_target_*.apk cache files)
- [x] A3: ProjectRepository.applyCodeDiff exact path match, exact single occurrence, sealed result Applied/Failed, Undo button
- [x] A4: ProjectRepository file ids use relative path instead of name.hashCode()
- [x] A5: AgentService FILE: block format prompt & parser, full content, preview before apply

## GROUP B: Make APK analysis honest and real (ApkAnalysisService.kt)
- [x] B1: Remove fake fallbacks ("com.app.analyzed", fake INTERNET, fake MainActivity; throw IllegalArgumentException if null)
- [x] B2: Set appInfo.sourceDir and appInfo.publicSourceDir to apkFile.absolutePath before loadLabel(pm)
- [x] B3: compileSdkVersion when SDK_INT >= 31, else 0, show "n/a" when 0
- [x] B4: DexReader.kt for real DEX parsing (classes, methods, descriptors, frameworks, obfuscation %, MVVM count)
- [x] B5: Launcher detection via XML pull parser on decoded AXML; extract debuggable, allowBackup, usesCleartextTraffic, networkSecurityConfig
- [x] B6: Real signing certificate parsing (GET_SIGNING_CERTIFICATES / GET_SIGNATURES, APK Sig Block v2/v3/v3.1 detection, v1 META-INF check, honest status text)
- [x] B7: Remove analyzeSampleTarget fake targets ("AuraCam", "CryptoVault", "PulseTrack"); keep only self-analysis
- [x] B8: Local security audit rule engine (dangerous permissions, exported without permission, debuggable, allowBackup, cleartext, targetSdk < 33)

## GROUP C: AI chat
- [x] C1: Chat history support (last 10 turns, role formatting for Gemini / OpenAI / Anthropic)
- [x] C2: Context expansion (manifest XML up to 12k chars, permissions max 60, components max 60, frameworks, security findings, FILE: format)
- [x] C3: isAgentThinking StateFlow, disable send button, show "Thinking...", prevent concurrent requests
- [x] C4: Claude default model names ("claude-opus-5-5", "claude-sonnet-5-5"), editable models, GET /models for live list & free test ping
- [x] C5: Gemini x-goog-api-key header instead of URL query, never log keys or URLs
- [x] C6: Real screenshot to Compose base64 conversion & multi-modal payloads on Dispatchers.IO, vision check for providers

## GROUP D: Reconstruction (ReconstructionService.kt)
- [x] D1: Relabel user-visible text to "Generated scaffold", add header comment in generated templates
- [x] D2: Real AI reconstruction prompt with strict JSON output, path validation, copy versions from this app's gradle
- [x] D3: Compute completionPercentage dynamically from declared vs generated activities; honesty note on business logic/native code
- [x] D4: Add UI notice: "Use only on apps you own or are authorized to analyze."

## GROUP E: Persistence and secrets
- [x] E1: Save/load project_meta.json atomically (temp file then rename) with full analysis data
- [x] E2: Persist chat messages to chat.json (last 200) and build jobs to builds.json; reload on start
- [x] E3: Encrypt API keys and tokens with Android Keystore AES-256-GCM (KeyStoreCrypto.kt); persist providers in SharedPreferences
- [x] E4: Clean Models.kt defaults (empty engine version strings, empty activeTools until worker returns, clean GitHubConfig defaults)

## GROUP F: GitHub and real APK build (no PC needed)
- [x] F1: GitHubService.kt with OkHttp (validateToken, createRepo, pushFiles with SHA check, progress reporting)
- [x] F2: GitHub Actions cloud build in CloudBuilderService.kt (push build.yml, trigger dispatch, poll run, job steps, logs, download artifact APK)
- [x] F3: Export button: "Save APK" if real APK exists in artifacts, else "Save project ZIP"; remove unbacked "Final APK" text

## GROUP G: CI, cleanup, tests
- [x] G1: .github/workflows/android.yml fix (workflow_dispatch, setup-gradle@v4, debug.keystore generation step)
- [x] G2: Remove unused dependencies and compress ic_rea_foreground jpg to small PNG/WebP
- [x] G3: Unit tests (AXML synthetic test, DexReader test, applyCodeDiff test, key rotation mock test, KeyStoreCrypto, FILE: parser)
- [x] G4: Final grep verification (zero hits for forbidden strings in app/src/main)
