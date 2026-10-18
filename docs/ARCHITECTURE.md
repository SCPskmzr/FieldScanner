# FieldScanner architecture

## Application structure

The repository contains one Android application module, `:app`, written in Kotlin with XML/View-based UI. `MainActivity` is a thin lifecycle and Activity Result host. `MainScreenView` owns view binding; `ScannerScreenCoordinator` owns scanner/photo/journal flow and ViewModel effects, and draws the scanner screen in one place — `render(ScannerScreenModel)`, a pure state-to-screen mapping (`scannerScreenModel(state)`) applied to the `View` idempotently and subscribed to `ScannerViewModel.state` in `MainActivity`; `RuneActionRouter` routes resolved rune actions. Feature controllers own CameraX and temporary photos (`CameraController`), journal navigation/filtering (`JournalCoordinator`), audio (`AudioPlaybackController`), speech recognition (`VoiceSearchController`), dialer/permission transitions (`CallUiController`), and call-screen rendering/button/rune handling (`CallScreenPresenter`).

`ScannerViewModel` coordinates scanner state and effects. UI controls are represented by rune bindings in `app/src/main/java/com/fieldscanner/app/runes/` and rendered through `DisplayZonesView`.

## Data and integrations

- Captured findings are modeled in `data/finding/` and persisted through Room (`FindingsDatabase`, DAO, repository, and media storage).
- Image analysis and speech are abstracted under `data/analysis/` and `data/speech/`; remote OpenAI-backed implementations are present.
- Telephony behavior is isolated in `calls/`: allowed contacts, whitelist policy, phone-role handling, call screening, and in-call coordination.

## Main flows

1. Camera preview → image capture → `ScannerViewModel` state (captured photo, journal finding, and mode all live in `ScannerUiState`) → the single `state` collector renders the screen from `scannerScreenModel(state)`; one-shot cues (analysis-result audio, shutter flash, error pulse) stay in `ScannerEffect`. Analysis and optional speech produce a persisted finding.
2. Rune input → `RuneBindings`/`RuneInputDispatcher` → `MainActivity` routing → controller or scanner state update (which re-renders via the `state` collector).
3. Contact selection → `CallUiController` whitelist/role/permission transition → Android Telecom call → `FieldScannerInCallService`/`CallCoordinator` → call UI state.

## Constraints

- The target device is an XCover 4s (minSdk 24; constrained memory/storage).
- Sensitive configuration is supplied through untracked `local.properties`; never commit API keys.
- System telephony behavior depends on Android roles, runtime permissions, and device configuration; it cannot be fully covered by local JVM tests.
