# Apple Design Tokens Reskin

**Date:** 2026-05-17
**Status:** Approved
**Scope:** `UiKit.kt`, `App.kt` — no per-screen layout changes

## Overview

Apply the Apple-inspired design token system from `docs/design/design.md` to the Android app. The reskin is token-only: colors, typography scale, and component sizes are updated in `UiKit.kt` and `AppTheme`; screen layouts and navigation structure are unchanged. A secondary goal is a denser, smaller feel throughout — the current UI is too large on mobile.

---

## 1. Color Tokens

All constants in `UiKit.kt` are replaced with values from `docs/design/design.md`. Semantic colors (success/warning/danger) are unchanged because they are not defined in the doc and are required for status pills.

| Constant | Old | New | Doc token |
|---|---|---|---|
| `AppBackground` | `#F8FAFC` | `#F5F5F7` | `colors.canvas-parchment` |
| `AppSurface` | `#FFFFFF` | `#FFFFFF` | `colors.canvas` |
| `AppText` | `#202634` | `#1D1D1F` | `colors.ink` |
| `AppMuted` | `#767F8E` | `#6E6E73` | Apple standard secondary |
| `AppBorder` | `#E7EAF0` | `#E0E0E0` | `colors.hairline` |
| `AppAccent` | `#486DFF` | `#0066CC` | `colors.primary` (action blue) |
| `AppAccentSoft` | `#EFF3FF` | `#E8F2FF` | tint of action blue |
| `AppSuccess` | `#20A466` | unchanged | — |
| `AppWarning` | `#B7791F` | unchanged | — |
| `AppDanger` | `#D14343` | unchanged | — |

---

## 2. Typography

A custom `Typography` object is passed to `MaterialTheme` inside `AppTheme`. All sizes are one step smaller than Material3 defaults, with negative letter-spacing on body text for the "Apple tight" feel. Weight ladder: 400 / 600 / 700 — no 500.

| Role | Old sp | New sp | Weight | Letter spacing |
|---|---|---|---|---|
| `bodyLarge` | 16sp | 14sp | 400 | −0.2sp |
| `bodyMedium` | 14sp | 13sp | 400 | 0 |
| `bodySmall` | 12sp | 11sp | 400 | 0 |
| `titleSmall` | 14sp | 13sp | 600 | 0 |
| `titleMedium` | 16sp | 14sp | 600 | 0 |
| `labelLarge` | 14sp | 12sp | 600 | 0 |
| `labelMedium` | 12sp | 11sp | 600 | 0 |
| `labelSmall` | 11sp | 10sp | 700 | 0 |

Font family: `system-ui / -apple-system` is the doc's first-choice stack. On Android this resolves to the device default (Roboto / system font). No custom font import is required.

---

## 3. Component Sizing

All changes are in `UiKit.kt` and `ChatScreen.kt` composables. No screen-level layout or navigation changes.

| Component | Old | New |
|---|---|---|
| `AppCard` border radius | 14dp | 18dp (`rounded.lg`) |
| `AppCard` border color | `#E7EAF0` | `#E0E0E0` |
| Chat history dropdown button height | 52dp | 40dp |
| Chat history menu item row height | 64dp | 48dp |
| Chat history delete button size | 42dp | 34dp |
| Composer buttons min height | 50dp | 44dp |
| Composer input border radius | 23dp | 9999dp (full pill) |
| `CompactActionButton` text style | `labelMedium` | `labelSmall` |
| Top bar vertical padding | 12dp | 10dp |
| Top bar horizontal padding | 18dp | 16dp |
| Screen content horizontal padding | 16dp | 14dp |

Components that stay unchanged: `MetricBox` (11dp radius), `StatusPill` (already full pill), `Avatar`, `StatusDot`, nav glyph pill container.

---

## 4. Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/lance/litertchat/ui/UiKit.kt` | Color constants, `AppTheme` typography, `AppCard` radius/border, `CompactActionButton` text style |
| `app/src/main/java/com/lance/litertchat/App.kt` | Top bar padding |
| `app/src/main/java/com/lance/litertchat/ui/ChatScreen.kt` | History bar heights, menu item heights, delete button size, composer heights, input pill radius |
| Screen files (`SettingsScreen.kt`, `ModelManagerScreen.kt`, `DiagnosticsScreen.kt`) | Content horizontal padding (14dp), vertical arrangement spacing (10dp from 11dp) |

---

## 5. Out of Scope

- Dark mode / system theme support (doc is light-mode only)
- Structural layout changes (hero tiles, sub-nav, frosted bars — web-only patterns)
- Custom font import (system font is the correct Android fallback per doc)
- New screens or navigation changes
