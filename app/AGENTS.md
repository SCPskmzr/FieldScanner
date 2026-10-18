# FieldScanner — Codex project instructions

## Product

FieldScanner is a prototype autonomous children's field AI scanner built around a Samsung Galaxy XCover 4s smartphone used as the hardware core.

Target donor device:

- Samsung Galaxy XCover 4s
- SM-G398F
- 3 GB RAM / 32 GB storage

The phone hardware is intentionally retained as much as possible.

Read `field_scanner_project_summary-1.md` for the product architecture and roadmap.

## Android project

Preferred project constraints:

- Kotlin
- traditional Android Views/XML
- no Jetpack Compose unless explicitly approved
- minSdk 24 unless a verified technical requirement forces a change
- optimize for the physical XCover 4s rather than modern flagship phones
- keep RAM, dependency count and application complexity low

Before changing Android/Gradle/Kotlin versions, explain why the change is required.

Do not perform broad dependency or tooling upgrades without a concrete reason.

## Current implementation

The codebase already contains rear-camera capture, OpenAI image analysis and speech,
a persisted discovery journal with optional location, rune-driven fullscreen UI, and a
whitelist-based phone UI. Their implemented behavior and device-dependent limitations
are described in `docs/README.md` and `docs/modules/`.

Before starting a feature, treat the relevant module document and the source code as
the current baseline. Do not assume that a planned or partially implemented feature is
production-ready without device verification.

## Engineering rules

Prefer simple, maintainable Android code.

Use official Android/AndroidX APIs when practical.

Avoid unnecessary third-party dependencies.

Do not hardcode secrets.

Handle runtime permissions and unavailable hardware gracefully.

Do not assume code works: build/test after meaningful changes.

Keep changes narrowly scoped.

Do not refactor unrelated code.

## Physical device safety

The target is real hardware.

Never:

- factory-reset the phone
- unlock the bootloader
- flash firmware
- erase user partitions
- run destructive ADB commands

without explicit user instruction.

Normal debug APK installation and ADB inspection are allowed.

## Git

Prefer small, reviewable changes.

Before large changes ensure work can be reverted with Git.

Do not rewrite Git history unless explicitly requested.

## Source of truth

Product requirements:
`field_scanner_project_summary-1.md`

Repository state and source files are the source of truth for the actual implementation.

If documentation and repository state disagree, report the discrepancy instead of silently choosing one.
