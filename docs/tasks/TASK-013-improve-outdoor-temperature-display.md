# TASK-013 - Improve outdoor-temperature display

## Goal

Make the left-zone outdoor-temperature mode easier to read and more expressive
on the Samsung Galaxy XCover 4s, without losing the battery indicator's clear
default role.

## Context

TASK-009 added a temporary temperature scale shown by holding the lower part of
the left zone. It uses Open-Meteo's current 2 m temperature for the device's
last available location and animates the marker after a five-second forced
refresh. The current presentation needs focused visual refinement after use on
the physical device.

## Requirements

- Review the temperature-mode layout on the XCover 4s and improve legibility of
  the −10…+45 °C scale, current marker, and refresh feedback.
- Preserve the left-zone outline above all fills and indicators.
- Preserve the hold interaction: show temperature after a normal long press;
  refresh after five seconds; return to the contextual display on release.
- Keep network/location failures safe and make their visual state unambiguous.
- Do not change the Open-Meteo privacy model: do not persist coordinates or
  weather responses.
- Add or update tests for any changed pure gesture, scale, or state-selection
  logic, and update the UI module documentation.

## Acceptance criteria

- [ ] The temperature scale and current reading are readable on the physical
  XCover 4s without obscuring the left-zone boundary.
- [ ] A five-second refresh provides clear non-text or intentional transient
  feedback while the request is running and when the marker changes.
- [ ] Releasing the hold restores the standard left-zone context immediately.
- [ ] Missing location, network, or weather data has a stable, understandable
  fallback and does not affect rune input.
- [ ] Relevant automated tests pass.

## Out of scope

- Changing the weather provider, API privacy policy, or stored finding location.
- Brightness or volume controls from TASK-010.
- Changes to the central, lower, or right rune-zone geometry.

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt`
- `app/src/main/java/com/fieldscanner/app/display/OutdoorTemperatureSource.kt`
- `app/src/main/java/com/fieldscanner/app/display/zones/LeftContextRenderer.kt`
- `app/src/main/java/com/fieldscanner/app/display/zones/LeftTemperatureGesture.kt`
- `app/src/test/java/com/fieldscanner/app/display/`
- `docs/modules/ui-and-input.md`

## Verification

- [ ] `./scripts/verify-and-install.ps1` on Windows (or `-Target emulator`) or
  `./scripts/verify` on Linux/macOS/CI.
- [ ] Acceptance criteria checked.
- [ ] On the physical XCover 4s: hold the lower-left zone, verify the scale and
  current temperature, then keep holding to verify refresh and marker animation.

## Outcome

Fill in when closing the task, then `git mv` this file to `docs/tasks/done/`:

- What actually changed (files/behavior) and the closing commit SHA.
- Anything still not verified (e.g. manual on-device checks) - carry it here,
  not in chat history.
