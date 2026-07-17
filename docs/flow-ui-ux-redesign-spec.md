# Flow UI/UX Redesign Specification

**Status:** Design approved; implementation plan pending written-spec review  
**Repository:** `PastaHimself/Flow` only  
**Branch:** `codex/flow-ui-ux-revamp`  
**Design baseline:** `1f9292d75c5062f13cdab57584cc2b60b1e0ef48`  
**Platforms:** Android phone, tablet, foldable, landscape, and Android TV

## 1. Purpose and constraints

This specification defines a complete visual and interaction refresh for Flow. The result must feel calm, capable, and Android-native while preserving the existing feature set, routes, playback behavior, themes, locales, RTL support, and supported form factors.

The redesign is deliberately not a rewrite of media, data, recommendation, or playback logic. It changes visual hierarchy, component ownership, interaction affordances, accessibility, and adaptive layouts without removing capabilities such as background playback, PiP, queueing, comments, casting, downloads, gestures, Shorts, music, personality, settings, sync, or TV remote control.

All code changes must remain in the `PastaHimself/Flow` fork and must never update `main` directly or modify `A-EDev/Flow`.

## 2. Audit summary

| Area | Current strength | Refresh need |
| --- | --- | --- |
| Themes | Broad Material 3, dynamic color, custom palettes, light/dark/OLED variants | Make every new surface semantic and remove screen-level color dependence |
| Navigation | Full route coverage, global player overlays, recent navigation fixes | Establish a consistent adaptive shell and restore 48 dp targets |
| Content | Rich Home, Search, subscriptions, channel, music, Shorts, and player functionality | Consolidate divergent cards, headers, chips, and state treatments |
| Responsive UI | Feed grids and video player have useful width handling | Bring settings, library, personality, and shell layouts to tablets/foldables |
| TV | Separate Compose root, rail, focus cards, player, and core screens | Apply the full system at ten-foot scale and validate remote behavior |
| Accessibility | Much localization and some good control labels | Fix undersized targets, missing semantics, hard-coded UI strings, and weak headings/selection metadata |
| Motion | Strong player and navigation spatial behavior | Centralize durations/easing, avoid repeated paging entrances, add a reduced-motion policy |
| Performance | Lazy lists frequently use keys and player pooling is mature | Avoid broad state reads, main-thread artwork work, expensive persistent effects, and unnecessary animation |

Recent work in `NavigationComponents.kt`, `VideoCard.kt`, `HistoryScreen.kt`, `ShortVideoPlayer.kt`, `VideoAmbientBackground.kt`, `ChannelScreen.kt`, and player/PiP code must be preserved and regression-tested during the refresh.

## 3. Design direction: Flow Current

Flow Current is content-first Material 3. Media supplies emotional richness; surrounding UI is composed, tonal, and consistent. Flow identity comes from continuity of navigation, playback context, and progress, not decorative gradients, glass, glow, or a fixed red brand dependency.

The system must not introduce:
- New gradients outside the existing, active-media treatments
- Glassmorphism, blur-heavy persistent chrome, glows, or colored outlines used only as decoration
- Arbitrary inline colors for semantic UI
- Nested cards used only to manufacture hierarchy
- Touch-only controls in TV surfaces

## 4. Shared design system

### 4.1 Semantic color and surface roles

All components consume `MaterialTheme.colorScheme`, extended semantic colors where already available, or a small shared semantic extension. The app must remain legible in every supported theme, including dynamic and custom palettes.

- Background: page canvas
- Surface and surface container roles: app bars, navigation, cards, sheets, selected and supporting groups
- Primary: selection, progress, focused state, and primary action only
- Error, warning, success, and disabled roles: semantic feedback only
- Media scrims: allowed only over currently displayed media when needed for contrast

No UI behavior may rely on “red means selected.” Theme roles provide the selection state.

### 4.2 Shape, spacing, type, and touch roles

Introduce shared tokens rather than per-screen literals.

| Role | Target |
| --- | --- |
| Compact control radius | 8 dp |
| Chip/input radius | 12 dp |
| Card radius | 16 dp |
| Major container/sheet radius | 24–28 dp |
| Minimum interactive area | 48 × 48 dp |
| Visible compact icon | 20–24 dp inside its 48 dp target |
| Page spacing scale | 4, 8, 12, 16, 24, 32 dp |
| Card/media spacing | 8–16 dp |
| Motion feedback | 120–180 ms |
| Motion route/shell | 200–280 ms |

Typography uses Material 3 semantic styles. Screen code should not routinely create ad-hoc `sp` sizes or weights. Use a small set of editorial display/section/title/metadata roles with truncation and line-height rules.

### 4.3 Shared components

Consolidate and reuse:
- Adaptive app bar and back/search/action variants
- Bottom navigation, compact rail, expanded rail, TV rail
- Video card in list, grid, compact, and TV density modes
- Music/playlist/artist rows and cards where entities overlap
- Section headers, chips, filter controls, empty/error/loading states
- Settings rows, grouped sections, and search results
- Modal/bottom sheets and action menus
- Focus treatment for TV and selected treatment for touch

## 5. Adaptive shell and navigation

Existing routes and navigation destinations remain intact.

| Form factor | Shell | Content behavior |
| --- | --- | --- |
| Compact phone | Bottom navigation: five destinations or four plus More | One column, horizontal shelves, cards sized for thumb reach |
| Foldable/medium tablet | Compact rail | Wider feed rows, two panes only where task benefit is clear |
| Expanded tablet/landscape | Compact or expanded rail | Intentional grids, constrained reading widths, supporting detail panes |
| Android TV | Persistent focus-aware rail | Large lanes/grids, remote-first actions, focus restoration |

The bottom navigation keeps current destination behavior and overflow support, but uses a full 48 dp target, tonal selected indicator, meaningful selected semantics, and stable labels. Player overlays remain above the shell and keep their current collapse, expansion, and PiP lifecycle.

## 6. Screen and flow requirements

### 6.1 Discovery and content

Home uses a compact branded app bar, topic controls, identifiable shelves, reusable video cards, and useful offline/empty/error recovery. It must retain current list/grid preferences, continuation watching, Shorts, refresh, and interaction tracking behavior.

Search prioritizes query entry, suggestions, filters, and result type. Remove duplicate card visual systems and repeated paging entrance animations. History and search actions must remain discoverable and accessible.

Subscriptions distinguishes watch feed from subscription management. Group actions retain their behavior but receive full touch targets and action labels.

Channels, artists, playlists, and community use a shared editorial header: identity, primary actions, tabs, and reusable lanes. Preserve all current tabs, sort/filter controls, playlist actions, subscription controls, and related content.

### 6.2 Personal library, settings, personality, and onboarding

Library becomes a personal-media hub: recent activity and quick actions lead into the existing history, likes, playlists, downloads, local media, saved Shorts, and music paths. It must not remove these routes.

Settings becomes a searchable, grouped control centre. Existing settings routes and preferences remain untouched. Settings search, alerts, dialogs, toggles, custom theme editing, and updater behavior remain functional.

Flow Personality preserves its data-rich value and export/import/reset flows. Its bespoke gradient hero is replaced by the shared tonal system while dashboards retain readable charts and metric hierarchy.

Onboarding uses the same app shell, clear step progress, accessible selection controls, and explicit skip/back/continue actions. Existing import, channel subscription, and interest-selection behavior remains.

### 6.3 Video, music, mini player, and Shorts

The global mini player becomes a compact tonal transport surface with artwork, title, progress, play/pause, and obvious expansion. Replace persistent blur-heavy/shadow-heavy chrome with theme-aware surfaces.

The video player remains media-first:
- Top: navigation and context
- Centre/bottom: playback and seek
- Secondary actions: predictable menu or sheet
- Phone: focused media sequence
- Tablet landscape: retain supporting content pane
- TV: remote-first control order and visible focus

Do not remove gestures, SponsorBlock, chapters, quality/speed controls, captions, comments, live chat, queue, casting, downloads, lock mode, PiP, or related video behavior.

Expanded music may retain its current artwork-based immersive treatment only while active. Lyrics, queue, device output, and player appearance settings use shared sheets and controls.

Shorts remains full-screen media with a contrast-safe metadata region and a 48 dp action rail. Preserve the player pool, preloading, gestures, comments, description, options, save/like/share, and simple-UI preference.

## 7. Motion and feedback

Motion communicates location and state:
- Navigation and shell: short fade/slide continuity
- Player expansion/collapse: one spatial transform
- Selection/focus: small tonal/scale response
- Loading: geometry-matching skeletons rather than generic placeholders
- Paging: no visible=true per-item entrances that replay as data changes

Create a central motion policy. Nonessential infinite motion must pause or become static when reduced motion is requested; artwork and background processing must not continuously consume UI resources when not visible.

## 8. Accessibility, localization, and RTL

Every actionable control must provide a meaningful label and a 48 × 48 dp target. Decorative media retains null descriptions only when it conveys no information. Add selected, heading, tab, progress, dismiss, and error semantics where screen readers need them.

All new user-facing text belongs in resources. Replace discovered hard-coded strings in redesigned areas. UI inherits layout direction by default; media-specific gesture regions may explicitly maintain logical playback direction. Check string expansion, bi-directional text, and mirrored navigation icons.

## 9. State quality and performance

Each redesigned screen must define loading, empty, error, disabled, offline, and populated states. Empty states must provide a recovery action when one exists. Skeletons mirror final layout geometry.

Performance constraints:
- Keep stable keys in lazy content
- Split oversized visual components where this reduces broad recomposition
- Move bitmap/palette extraction off the main thread
- Constrain image effects to active media
- Avoid persistent blur/shadow layers in global chrome
- Preserve existing player pooling, lifecycle, services, and caching behavior

## 10. Implementation boundaries and migration sequence

1. Add semantic tokens for shapes, spacing, touch, icon sizes, and motion; strengthen theme mapping.
2. Build shared navigation, app-bar, card, section, state, and TV focus primitives with focused tests/previews.
3. Migrate the touch shell: Home, Search, Subscriptions, Library/history, channels, playlists, artists, settings/personality/onboarding.
4. Migrate player-adjacent UI: global overlay, mini player, video player controls/content, music, Shorts.
5. Migrate TV rail, cards, Home, Search, Library, Subscriptions, Settings, player, and state surfaces.
6. Remove superseded duplicate components only after consumers migrate and behavior is verified.

Each slice must preserve public composable contracts where practical, use state hoisting, and avoid coupling unrelated view models to design components.

## 11. Validation and acceptance

Before a pull request:
- Run the project’s flavor-specific debug build, unit tests, and lint tasks
- Run the relevant existing unit and Compose/instrumentation tests
- Add focused tests for navigation target/selection semantics, adaptive shell rules, shared card/state behavior, and TV focus/key navigation
- Verify phone portrait, phone landscape, compact tablet, expanded tablet/foldable, and TV
- Verify light, dark, OLED, dynamic, and custom themes
- Verify LTR and RTL
- Verify loading, empty, error, offline, disabled, and populated states
- Smoke-test video, music, Shorts, background playback, PiP, queue, downloads, comments, settings, and navigation

Suggested commands, subject to local task discovery:
```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
./gradlew :app:lintGithubDebug
```

The initial local audit could not run Gradle because the environment could not download the Gradle distribution. That infrastructure limitation is not a repository failure and must be rechecked before implementation sign-off.

## 12. Non-goals

This work does not redesign backend APIs, recommendation algorithms, persistence formats, media service architecture, or route semantics. It does not remove existing themes, locales, user preferences, or player capabilities. It does not merge to `main` or upstream.

## 13. Pull request requirements

The eventual draft PR targets `PastaHimself/Flow:main`, contains a concise before/after UI summary, lists exact validation commands and results, calls out the initial Gradle-environment limitation if still present, and remains unmerged for maintainer review.
