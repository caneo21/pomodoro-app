# Modern Minimal Design Tokens — Design Spec

**Goal:** Establish one shared visual language (color, typography, shape, motion) that PC (WinUI 3), phone (Android/Compose), and watch (Wear OS/Compose) will all express, replacing the current look — flagged by the user as dated and unnatural across color, typography, shape/whitespace, and motion — with a minimal monochrome direction. This spec defines the *decided values*, not the per-platform code changes.

**Non-goals:** Implementing these tokens in any platform's code (each platform gets its own follow-up spec + plan — see "Relationship to existing specs"); changing which screens exist or their navigation (IA); adding/removing color themes (still exactly 4); onboarding flow changes.

## Context

The user reviewed 4 visual directions for the timer screen side by side (minimal monochrome, soft glass, vivid gradient, typography-led editorial) and picked **A — minimal monochrome**. Follow-up choices refined that direction into the token set below. This spec is the shared foundation; PC/phone/watch each apply it to their own component library next, each as its own spec/plan cycle.

## Behavior Summary (agreed with user)

- Base palette goes near-monochrome (near-black / near-white neutrals) with **one** restrained accent color doing all the color work — down from today's two-tone `primary`/`primaryVariant` per theme.
- The existing 4 color themes (`DEFAULT`, `OCEAN`, `FOREST`, `LAVENDER`) are **kept** — this redefines their tone, not their count or the user-facing choice.
- **Light and dark mode both stay** on PC/phone (system-following, as today); the watch stays permanently dark (unchanged, battery-driven).
- Long-break accent stops varying per theme — **one shared indigo** replaces the 4 theme-specific long-break accents, simplifying the palette further.
- General UI text keeps each platform's native system font (Segoe UI Variable / Roboto / system) — no bundling cost, native feel preserved.
- The timer's countdown digits (`MM:SS`) switch to a bundled font, **Manrope** at weight 800 with tabular figures, consistently on all three platforms — the one deliberate typographic accent.
- Shape moves to fully rounded: circular/pill buttons, large-radius cards, a thinner progress ring with more surrounding negative space.
- Motion becomes warm and fluid — spring-based, not linear/mechanical — for ring progress, phase-color transitions, and button press feedback. The watch's ambient mode stays motion-free (existing battery policy, unchanged).

## Section A: Color Tokens

**Neutrals**

| Token | Dark | Light |
|---|---|---|
| `background` | `#0D0D0F` | `#FAFAF9` |
| `surface` (cards/sheets) | `#17171A` | `#FFFFFF` |
| `textPrimary` | `#F2F2F4` | `#17171A` |
| `textSecondary` | `#8A8A92` | `#6B6B72` |
| `border` / divider | `#2A2A2E` | `#E4E4E7` |

**Per-theme accent** (single tone each, replacing the old `primary` + `primaryVariant` pair)

| Theme | Dark-bg accent | Light-bg accent |
|---|---|---|
| Default | `#FF6B57` | `#E14B34` |
| Ocean | `#5AA9FF` | `#1E76E0` |
| Forest | `#6FCB7A` | `#2E8F45` |
| Lavender | `#B08CF0` | `#7C4FD6` |

The accent is used sparingly: progress ring fill, the primary action button, and the active-phase indicator. It does not tint backgrounds or general chrome.

**Long-break accent** — one shared value across all 4 themes (was: 4 separate per-theme values):

| Dark-bg | Light-bg |
|---|---|
| `#6B7BFF` | `#4A57C7` |

## Section B: Typography Tokens

- **General UI text** (labels, settings rows, buttons): unchanged — each platform's native system font stack, at existing weights/sizes. No new font dependency for this layer.
- **Timer countdown digits only**: **Manrope**, weight 800, `tabular-nums`/tabular figures enabled so digit width never shifts as the numbers change. Sized larger than today's countdown text to read as the screen's typographic hero.
- Bundling: Manrope is open-source (Google Fonts, OFL license) — embeddable as a `.ttf`/variable font resource in each platform (WinUI 3 app resource, Android font resource, Wear OS font resource bundled the same way as the phone).

## Section C: Shape Tokens

- **Buttons**: fully circular (icon-only actions) or full pill radius (labeled actions) — no partially-rounded rectangles.
- **Cards / sheets / settings sections**: large corner radius, 24–28px.
- **Progress ring**: thinner stroke than today (roughly 60% of current stroke width) with a larger ring diameter, so the extra room reads as intentional negative space rather than a smaller ring.

## Section D: Motion Tokens

- **Character**: spring-based easing (not linear/ease-in-out), tuned for a warm, slightly bouncy settle — not sluggish, not snappy/mechanical.
- **Applies to**: progress ring value changes, phase-color cross-fades (focus → break → long break), and button press feedback (a small scale-down, e.g. ~0.94, that springs back on release).
- **Exception (unchanged policy)**: the watch's ambient mode renders with zero motion, as today — this token set only governs the interactive/awake states.
- Exact spring constants (stiffness/damping) are a per-platform implementation detail, tuned to feel equivalent using whatever spring primitive each platform offers (WinUI 3 Composition springs, Compose `spring()` specs) — this spec fixes the *character*, not the numbers.

## Relationship to Existing Specs

- **Supersedes the color *values*** in `2026-08-21-color-themes-design.md` — that spec's behavior (4 themes, phone-only selection UI, phone→watch sync, structure) is still correct and unchanged; only the actual hex values and the 2-tone→1-tone accent shape change, per Section A above.
- References the current per-platform theme file locations documented in `2026-08-22-windows-companion-design.md` and `2026-08-13-wear-os-companion-design.md` (and the watch's `wear/.../theme/WearTheme.kt`, most recently touched by the M3 Expressive migration) — those files are where Section A/B/C/D land when each platform's follow-up spec is implemented.

## Testing Strategy

Not applicable to this spec directly — it changes no code. Each platform's follow-up implementation spec inherits the existing project pattern (see `color-themes-design.md`'s Testing Strategy): palette-lookup unit tests, and manual/on-device verification for visual and motion feel, since visual correctness isn't meaningfully unit-testable.

## Out of Scope

- Any code changes to WinUI 3 XAML styles, the Android Compose theme, or the Wear Compose theme — each platform gets its own spec + implementation plan next, in whatever order the user picks.
- Changing the number of color themes, or which themes exist.
- Screen/navigation (IA) restructuring.
- Onboarding flow changes.
- Exact spring-curve constants per platform (implementation detail, decided when each platform's plan is written).
