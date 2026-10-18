# TASK-033 — Aspect-safe scaling for the rune display

## Goal

The rune UI keeps the exact proportions of its artwork on any ordinary phone
(~5"–7", any common aspect ratio, with or without a camera cutout), instead of
stretching to fill. On a device whose screen ratio differs from the XCover 4s the
design still looks right — centred, undistorted, with the black background
extending into the leftover margin — and every touch zone still lines up with
what is drawn.

## Context

`DisplayZonesView` renders the whole child-facing UI onto a `Canvas` in a fixed
virtual coordinate space of `1286 × 745` (`DisplayGeometry.WIDTH/HEIGHT`). It
maps that space to the real view with a **non-uniform** scale:

- draw: `canvas.scale(width / DisplayGeometry.WIDTH, height / DisplayGeometry.HEIGHT)`
  (`app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt:180`)
- touch: `x = event.x * WIDTH / width`, `y = event.y * HEIGHT / height`
  (`DisplayZonesView.kt:199-200`)

Because the X and Y factors differ, the artwork is already slightly stretched on
the XCover 4s and stretches differently on any other aspect ratio. There is also
no display-cutout handling, so on a phone with a corner/notch camera part of the
artwork can sit under the cutout.

Physical size (5" vs 6") is not the real problem — Android already scales by
density, and two phones with the same aspect ratio render identically. The
distortion is purely an aspect-ratio and safe-area problem, so the fix is a
uniform (letterbox) fit inside the cutout-safe area, with one shared transform
used for both drawing and hit-testing.

## Requirements

- Replace the non-uniform scale with a **uniform** scale
  `s = min(viewportW / DisplayGeometry.WIDTH, viewportH / DisplayGeometry.HEIGHT)`
  and centre the scaled artwork; the surrounding margin stays the view's black
  background (the view already draws `Color.BLACK` before the artwork).
- Compute the transform once per size change (a `scale` + `offsetX` + `offsetY`,
  or an equivalent `Matrix`) and use the **same** transform for `onDraw` and for
  mapping `MotionEvent` coordinates back into virtual space. No second copy of
  the math.
- Put the transform math in a small pure helper (e.g.
  `com.fieldscanner.app.display.DisplayViewport` with a function that takes
  view width/height plus optional safe-area insets and returns
  `scale/offsetX/offsetY`), so it is unit-testable without instantiating a View.
- Account for display cutouts: the artwork fits inside the cutout-safe area
  (use `WindowInsets`/`DisplayCutout` safe insets available on the view; guard
  the API level, minSdk is 24 and `DisplayCutout` is API 28). When no cutout is
  present, behaviour is the plain letterbox fit.
- Touch mapping must reject points that fall in the letterbox margin (outside the
  virtual `0..WIDTH` / `0..HEIGHT` range) rather than reporting a clamped or
  negative virtual coordinate.
- Keep text sizes in virtual-space units (they scale with the artwork today —
  that is correct for a fixed-artwork design; do not convert to `sp`).
- Behaviour on a device whose screen ratio equals the current render ratio
  (`1286:745`) is visually unchanged apart from sub-pixel rounding.

## Acceptance criteria

- [x] `DisplayZonesView.onDraw` applies a single uniform scale + centring offset
      (grep shows no `canvas.scale(width / ... , height / ...)` with two
      different arguments).
- [x] `DisplayZonesView.onTouchEvent` derives virtual `x/y` from the same
      helper/transform used by `onDraw`; a tap in the letterbox margin does not
      dispatch a rune/temperature gesture.
- [x] New pure helper (e.g. `DisplayViewport`) exists in
      `com.fieldscanner.app.display` and has unit tests in
      `app/src/test/java/com/fieldscanner/app/display/` covering:
      wider-than-artwork viewport (vertical bars, `offsetX > 0`),
      taller-than-artwork viewport (horizontal bars, `offsetY > 0`),
      exact-ratio viewport (`offsetX == offsetY == 0`),
      a viewport with a non-zero safe-area inset,
      and round-tripping a virtual point → screen → virtual.
- [x] `./scripts/verify.ps1` passes (doc gate + self-check + lint + unit +
      assemble).
- [x] `docs/modules/ui-and-input.md` updated to describe uniform letterbox
      scaling + cutout-safe fit and the shared transform.

## Out of scope

- Any change to the artwork coordinates in `DisplayGeometryCoordinates` or the
  SVG design template.
- `fill`/crop scaling (`max` instead of `min`) or a 9-slice / edge-anchored
  relayout of the zones.
- Removing the forced `android:screenOrientation="landscape"`.
- Portrait support, foldable/multi-window layout, tablet-specific layouts.
- Converting on-canvas text to `sp` or wiring in the system font-scale setting.
- Camera `PreviewView` / `capturedPhoto` `ImageView` sizing (they already fill
  the parent via ConstraintLayout and are unaffected).

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt` — `onDraw`,
  `onTouchEvent`, add `onSizeChanged` (or `onLayout`) to cache the transform.
- `app/src/main/java/com/fieldscanner/app/display/DisplayGeometry.kt` /
  `DisplayGeometryCoordinates.kt` — source of `WIDTH/HEIGHT`; read-only here.
- New: `app/src/main/java/com/fieldscanner/app/display/DisplayViewport.kt` +
  `app/src/test/java/com/fieldscanner/app/display/DisplayViewportTest.kt`.
- Possibly a theme flag for cutout mode:
  `app/src/main/res/values/styles.xml` /
  `values-v27/` (`android:windowLayoutInDisplayCutoutMode` = `shortEdges`) so the
  activity is allowed to draw into the cutout area it then avoids in layout.
- `docs/modules/ui-and-input.md` — covered module doc.

## Plan

### Existing behaviour

- The view fills the screen (`activity_main.xml`, `match_parent`), draws
  `Color.BLACK`, then draws all zones in virtual space scaled non-uniformly to
  the view box. Animations re-invalidate every `FRAME_DELAY_MILLIS`.
- `onTouchEvent` converts pointer coords to virtual space with the inverse
  non-uniform scale and feeds `leftRegion` / rune long-press / temperature
  gesture logic (`DisplayZonesView.kt:197-220`).
- There is no `onSizeChanged`/`onMeasure` override today.
- Tests: `DisplayGeometryCoordinatesTest`, `ScanOutlineGeometryTest` — plain
  JUnit, no Robolectric; a new pure helper fits this pattern.

### Approach

1. Add `DisplayViewport` — a pure object/data holder:
   ```
   data class DisplayTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {
       fun toVirtualX(px: Float) = (px - offsetX) / scale
       fun toVirtualY(px: Float) = (px - offsetY) / scale
       fun contains(px: Float, py: Float): Boolean  // inside 0..WIDTH / 0..HEIGHT
   }
   object DisplayViewport {
       fun of(viewW: Int, viewH: Int,
              insetLeft: Int = 0, insetTop: Int = 0,
              insetRight: Int = 0, insetBottom: Int = 0): DisplayTransform
   }
   ```
   `of` computes the safe box (`viewW - insetL - insetR` etc.), the uniform
   `scale = min(safeW / WIDTH, safeH / HEIGHT)`, then
   `offsetX = insetLeft + (safeW - WIDTH * scale) / 2`, same for Y. Reads
   `DisplayGeometry.WIDTH/HEIGHT` so there is one source of truth.
2. In `DisplayZonesView`:
   - Cache `private var transform = DisplayTransform(1f, 0f, 0f)`.
   - Override `onSizeChanged` (and re-read on `onApplyWindowInsets` /
     `ViewCompat.setOnApplyWindowInsetsListener`) to recompute `transform` from
     size + `rootWindowInsets.displayCutout` safe insets (API 28+ guarded;
     otherwise zero insets), then `invalidate()`.
   - `onDraw`: `canvas.translate(transform.offsetX, transform.offsetY);
     canvas.scale(transform.scale, transform.scale)` in place of the current
     `canvas.scale(...)`. The `saveLayer` in `drawCentralAndOuterZones` still
     uses `DisplayGeometry.WIDTH/HEIGHT` in the now-translated/scaled space —
     unchanged.
   - `onTouchEvent`: `if (!transform.contains(event.x, event.y)) return true`
     (swallow, no gesture) else `x = transform.toVirtualX(event.x).toInt()`, `y`
     likewise.
3. Cutout: add `values-v27/styles.xml` (or a `tools:targetApi` guarded item)
   setting `android:windowLayoutInDisplayCutoutMode` to `shortEdges`, so the
   window may extend under the cutout while the viewport math keeps the artwork
   inside the safe insets. If this proves noisy, fall back to `default` mode and
   rely purely on the letterbox (insets then usually zero) — note the decision in
   the Outcome.
4. Update `docs/modules/ui-and-input.md`.

### Likely files

- `DisplayZonesView.kt` (edit), `DisplayViewport.kt` (new),
  `DisplayViewportTest.kt` (new), `styles.xml` / `values-v27/styles.xml`
  (maybe), `docs/modules/ui-and-input.md` (edit).

### Test / verification strategy

- Unit: `DisplayViewportTest` cases listed in Acceptance criteria — all pure
  arithmetic, no Android framework.
- `./scripts/verify.ps1` for lint + unit + assemble + doc gate.
- On-device (integration gate, repo owner): launch on the XCover 4s and confirm
  the UI is unchanged; if a second device with a different aspect ratio / cutout
  is available, confirm the artwork is centred, undistorted, black margin, and
  rune taps still register. Carry the multi-device check into `## Outcome` if
  only the XCover is available.

### Risks / assumptions

- **Assumption:** the black letterbox margin is acceptable product-wise (the UI
  is already black-on-black, so it should be near-invisible). If the product
  owner wants edge-to-edge, that is a `fill`/crop task, explicitly out of scope
  here.
- **Risk:** `saveLayer(0, 0, WIDTH, HEIGHT)` and `clipPath` calls assume drawing
  starts at virtual origin; the added `translate` keeps that true, but verify the
  camera-in-centre punch-out still aligns.
- **Risk:** window-insets timing — `rootWindowInsets` can be null before attach;
  recompute on `onApplyWindowInsets` and default to zero insets.
- **Assumption:** no existing test asserts the non-uniform scale directly (grep
  before editing); `DisplayGeometryCoordinatesTest` only checks coordinates.

## Verification

- [x] Task gate: `./scripts/verify.ps1 -BaseRef master` — doc gate + self-check +
      lint + unit + assemble, `BUILD SUCCESSFUL`, exit 0 (run on branch
      `task-033`).
- [ ] Integration gate (repo owner, on the merged result): `./scripts/verify`
      after merge, then `./scripts/verify-and-install.ps1` to install and launch
      on the Galaxy XCover 4s. Carry anything still pending here into
      `## Outcome`.
- [x] Acceptance criteria checked
- [ ] Additional scenario-specific check: on the XCover 4s the rune UI looks
      identical to before; if a differently-shaped device is available, artwork
      is centred/undistorted with a black margin and rune + temperature gestures
      still register. — pending, repo-owner device step.

## Outcome

**Changed files / behaviour**

- New `app/src/main/java/com/fieldscanner/app/display/DisplayViewport.kt` — pure
  helper. `DisplayViewport.of(viewW, viewH, insetLeft, insetTop, insetRight,
  insetBottom)` returns `DisplayTransform(scale, offsetX, offsetY)` where
  `scale = min(safeW / DisplayGeometry.WIDTH, safeH / DisplayGeometry.HEIGHT)`
  over the inset-shrunk safe box, centred. `DisplayTransform` exposes
  `toVirtualX/Y`, `toScreenX/Y` and `contains(px, py)` (screen point on the
  artwork, not the letterbox margin). Degenerate (zero) safe box → identity
  `DisplayTransform(1f, 0f, 0f)`.
- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt`:
  - Caches `private var transform`, recomputed by `recomputeTransform()` from
    `onSizeChanged`, `onApplyWindowInsets` and `onAttachedToWindow`. On API 28+
    it reads `rootWindowInsets.displayCutout` safe insets (all four
    `DisplayCutout.safeInset*` calls kept lexically inside the
    `Build.VERSION.SDK_INT >= P` guard so `lintDebug` passes); below API 28 the
    insets are zero and the fit is a plain letterbox.
  - `onDraw` now does `canvas.translate(transform.offsetX, transform.offsetY);
    canvas.scale(transform.scale, transform.scale)` — the non-uniform
    `canvas.scale(width / WIDTH, height / HEIGHT)` is gone.
  - `onTouchEvent` maps pointer coords via `transform.toVirtualX/Y`; a tap where
    `!transform.contains(event.x, event.y)` is swallowed (and cancels any
    in-flight long-press / temperature gesture) instead of producing a clamped
    or negative virtual coordinate.
- `app/src/main/res/values-v27/themes.xml` — new; overrides `Theme.FieldScanner`
  on API 27+ with `android:windowLayoutInDisplayCutoutMode = shortEdges` so the
  window may extend under a cutout while the viewport math keeps the artwork
  inside the safe area. **Kept** (not reverted to plain letterbox).
- Tests: `app/src/test/java/com/fieldscanner/app/display/DisplayViewportTest.kt`
  — wider viewport (vertical bars, `offsetX > 0`), taller viewport (horizontal
  bars, `offsetY > 0`), exact-ratio (`offsetX == offsetY == 0`), non-zero
  safe-area inset (scale unchanged, `offsetX` shifted, margin point rejected),
  virtual → screen → virtual round-trip, and the degenerate fallback.
- Docs: `docs/modules/ui-and-input.md` — new "Масштабирование дисплея" section
  plus a pointer in "Назначение"; `docs/modules/platform-and-build.md` — note on
  the `values-v27` cutout theme (its `res/values*` path is doc-gated there).

**Verification**

- `./scripts/verify.ps1 -BaseRef master` on branch `task-033`: doc gate +
  self-check + `:app:lintDebug` + `:app:testDebugUnitTest` + `:app:assembleDebug`
  — `BUILD SUCCESSFUL`, exit 0. (First run failed on a lint `NewApi` error; fixed
  by moving the cutout-inset reads inside the SDK guard, re-run green.)

**Closing commit SHA:** `3842695` (branch `task-033`).

**Still pending:** integration gate (merge to `master`, `./scripts/verify`, then
`./scripts/verify-and-install.ps1`) and the on-device visual check are the repo
owner's steps. No second device with a different aspect ratio / cutout was
available here, so the centred-and-undistorted-on-other-shapes check rests on the
`DisplayViewportTest` arithmetic; on the XCover 4s (ratio ≈ the render ratio)
behaviour is unchanged apart from sub-pixel rounding.
