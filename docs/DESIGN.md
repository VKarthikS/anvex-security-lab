# ANVEX Design Direction

ANVEX is presented as a **signal room**: a defensive observation console that looks like calibrated instrumentation rather than a college dashboard. The two interfaces (defender and simulator) are two stations in the same system and share one identity.

## 1. Identity

* **Name:** ANVEX — presented in wordmark plus a compact "AVX" monogram used for the mark.
* **Voice:** precise, engineering-grade, calm. Copy states what happened, not what might feel scary. No alarmist red-screen theatre, no invented confidence scores.
* **Palette (CSS custom properties, tokens package):**
  | Token | Value | Use |
  |---|---|---|
  | `--ink-900` | `#05070a` | Page base |
  | `--ink-800` | `#0a0e14` | Panel base |
  | `--ink-700` | `#111722` | Raised surface |
  | `--line` | `rgba(148,163,184,0.16)` | Hairline rules |
  | `--text-hi` | `#e8eef7` | Primary text |
  | `--text-lo` | `#8b9ab0` | Secondary text |
  | `--signal` | `#43e8c8` | Primary accent / live data |
  | `--signal-soft` | `rgba(67,232,200,0.14)` | Accent washes |
  | `--warn` | `#f5b74e` | Suspected activity / observation |
  | `--alert` | `#ff5d73` | Enforced block / critical |
  | `--violet` | `#9d7bff` | Evidence / drill-down accent |
* **Typography:** Space Grotesk (display, headings, numerals) and IBM Plex Mono (data, ids, timestamps, counters). Both are vendored as local `woff2` files inside `frontend/packages/shared/assets/fonts` — no CDN, no remote font requests.
* **Iconography:** hand-built inline SVG strokes (1.5px, geometric) instead of an icon font, so icons inherit the palette exactly.

## 2. Layout principles

* **Asymmetric 12-column grid.** Defender: a wide "live activity" canvas on the left, a narrow, high-contrast "protection state" column on the right that is always visible, and a bottom evidence rail. Simulator: a control column (target, scenario, parameters) and a wide telemetry canvas.
* **Strong vertical rhythm:** 4/8/12/24/40/64px spacing scale; hairline separators instead of heavy card borders; panels are tonal (surface elevation) rather than boxed.
* **Large editorial numerals** for counters (48–72px, tabular) with unit and context in small mono caps beneath.
* **Calibration strip:** a thin, full-width "instrument bar" under the header showing protection mode, event-rate sparkline, queue depth and connection health — the visual equivalent of a hardware status row.

## 3. Defender screens

1. **Monitor** — live event ribbon, incoming vs admitted traffic split, rule matches, active blocks with countdown rings, protection ON/OFF banner (OFF is visually loud, amber-outlined, "OBSERVATION ONLY").
2. **Sources** — source/account drill-down: username-normalised account heat, rule outcomes, timeline of supporting events, block history.
3. **Evidence** — decision dossier: matched rules with exact thresholds and windows, policy snapshot, action taken, supporting event references (drill down to individual events).
4. **Alerts** — alert history with severity, first/last seen, occurrence count, and a link into the evidence dossier.
5. **Settings** — threshold/window/rate configuration with validation feedback, policy version history, protection toggle, manual unblock and reset controls.
6. **Diagnostics** — analyst-only runtime view: Java version, named worker threads and states, queue depth/capacity, persistence lag, history gaps.
7. **Dummy login** (`/login`) — a real, minimal authenticated screen plus account-scoped awareness notices (also shown on the monitor for the affected account).

## 4. Simulator screens

1. **Target** — defender base URL, handshake result, version match, LAN/local hints, troubleshooting affordances.
2. **Scenario** — strategy selection with honest description of what it will do, rate/duration/account controls, guard-rail caps.
3. **Run** — start/stop, progress, live counters (planned/sent/completed/cancelled/errors/outcomes), achieved rate, latency percentiles, run state machine badges.
4. **History / comparison** — retained runs, OFF vs ON comparison with measured reductions and frozen settings snapshot.

## 5. Motion and data honesty

* Motion is purposeful: counters tick with a 120ms eased transition, new events slide into the ribbon, block rings sweep while a block is live.
* **Every animated quantity is a real backend value.** Ambient motion (background gradient drift, idle shimmer) is low-contrast and cannot be mistaken for activity; the idle state explicitly reads "no traffic observed".
* Live animations pause and the header shows "STALE — RECONNECTING" when the stream drops; the last-known snapshot stays visible but greyed, never presented as live.
* `prefers-reduced-motion` disables transitions, sweeping rings and shimmer; all information remains available statically.
* Chart history is bounded (fixed rolling window, e.g. 120 samples) and event lists are virtualised/paginated.

## 6. Accessibility

* Contrast: body text ≥ 4.5:1 on panel surfaces; accent text used only at ≥ 4.5:1 on dark.
* Full keyboard access with visible focus rings; all icon-only controls carry labels and `aria-label`.
* Status is never colour-only: ON/OFF, alert severity and run state always carry text.
* Live regions announce meaningful state changes (protection toggled, run stopped) politely.

## 7. Responsive behaviour

Designed for 1280×800 and 1440×900 laptops first; degrades to a stacked single-column layout below 1024px. Dense tables collapse into stacked key/value rows; the protection state column docks to the top on narrow screens.

## 8. Local assets

* Fonts: vendored `woff2` in `frontend/packages/shared/assets/fonts`.
* Icons/charts: inline SVG components in the shared package.
* No analytics, no external requests, no cloud APIs at runtime.

## 9. Screenshot review findings

To be filled during M8 with the exact screenshot paths, the states reviewed (idle, OFF attack, ON enforcement, evidence drill-down, recovery) and the refinements made. Nothing is marked as reviewed before the real connected application is captured.
