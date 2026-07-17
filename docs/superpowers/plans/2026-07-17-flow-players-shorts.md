# Flow Players, Music, and Shorts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the mini player, video player, expanded music player, and Shorts with the shared Flow Current language while preserving every playback path and control.

**Architecture:** Treat existing player state and media services as fixed boundaries. Refactor only Compose composition, semantics, motion, and artwork presentation. Keep `GlobalPlayerOverlay`, `VideoPlayerViewModel`, `MusicPlayerViewModel`, player pooling, PiP, gestures, and queue contracts intact.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Media3, Coil, Palette, Compose animation, AndroidX test.

## Global Constraints

- Work only on `PastaHimself/Flow:flow-ui-ux-revamp`; never modify `main` or `A-EDev/Flow`.
- Preserve video/music playback, PiP, background play, gestures, SponsorBlock, chapters, captions, comments, live chat, queue, casting, downloads, lock mode, and player settings.
- Keep artwork-driven immersive treatment only while the expanded music player is active.
- Use 48 dp touch targets and resource-backed labels. Respect RTL except for explicitly logical media gestures.
- Do not add persistent blur-heavy/glow effects; move palette/bitmap work off the main thread and suspend effects when hidden.

---

### Task 1: Replace mini-player chrome with a compact tonal transport surface

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/components/PersistentMiniMusicPlayer.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/GlobalPlayerOverlay.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/components/PersistentMiniMusicPlayerTest.kt`

**Interfaces:**
- Preserve existing play/pause, expand, queue, dismiss, and progress callbacks.
- Add an internal `MiniPlayerLayout` that takes title, artwork, progress, and callbacks.

- [ ] **Step 1: Write the failing mini-player test**

```kotlin
@Test fun miniPlayerExposesExpandAndPlayControlsAt48Dp() {
    composeRule.setContent { MiniPlayerLayout(title = "Track", artworkUrl = "", progress = 0.5f, onExpand = {}, onPlayPause = {}) }
    composeRule.onNodeWithContentDescription("Expand player").assertHeightIsAtLeast(48.dp)
    composeRule.onNodeWithContentDescription("Pause").assertHeightIsAtLeast(48.dp)
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.components.PersistentMiniMusicPlayerTest`  
Expected: FAIL because the current component has compact 34–40 dp controls and does not expose the new layout.

- [ ] **Step 3: Implement the tonal surface**

Replace persistent background-image blur and manual 16 dp black shadow with a Material 3 surface container. Keep artwork as a small clipped image. Use the tokenized spacing and touch target. Retain progress updates but remove decorative waveform animation when not expanded.

- [ ] **Step 4: Run test**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.components.PersistentMiniMusicPlayerTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/components/PersistentMiniMusicPlayer.kt app/src/main/java/io/github/aedev/flow/ui/GlobalPlayerOverlay.kt app/src/androidTest/java/io/github/aedev/flow/ui/components/PersistentMiniMusicPlayerTest.kt
git commit -m "feat(player): refresh persistent mini player"
```

### Task 2: Rebuild video-player control hierarchy and semantics

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/player/EnhancedVideoPlayerScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/player/PremiumControlsOverlay.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/player/content/VideoInfoContent.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/screens/player/VideoPlayerControlsTest.kt`

**Interfaces:**
- Preserve `VideoPlayerViewModel` commands and overlay state.
- Preserve tablet landscape supporting-content behavior.

- [ ] **Step 1: Write failing semantic-control tests**

```kotlin
@Test fun playerSecondaryActionsHaveLabelsAndTargets() {
    composeRule.setContent { PlayerActionRow(onSettings = {}, onCaptions = {}, onMore = {}) }
    composeRule.onNodeWithContentDescription("Player settings").assertHeightIsAtLeast(48.dp)
    composeRule.onNodeWithContentDescription("More options").assertHeightIsAtLeast(48.dp)
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.player.VideoPlayerControlsTest`  
Expected: FAIL because the new semantic action row is absent.

- [ ] **Step 3: Implement hierarchy without changing player behavior**

Place navigation/context in the top zone, seek/playback in the centre/bottom zone, and secondary actions in a predictable row/menu/sheet. Replace hard-coded error actions in `VideoInfoContent.kt` with resource-backed text and the shared error state. Keep fullscreen, orientation, gestures, lock, comments, chat, and related content paths intact.

- [ ] **Step 4: Run test**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.player.VideoPlayerControlsTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/screens/player/EnhancedVideoPlayerScreen.kt app/src/main/java/io/github/aedev/flow/ui/screens/player/PremiumControlsOverlay.kt app/src/main/java/io/github/aedev/flow/ui/screens/player/content/VideoInfoContent.kt app/src/androidTest/java/io/github/aedev/flow/ui/screens/player/VideoPlayerControlsTest.kt
git commit -m "feat(player): clarify video player controls"
```

### Task 3: Migrate expanded music presentation and move palette work off the UI thread

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/music/EnhancedMusicPlayerScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/music/player/PlayerControls.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/music/player/PlayerTopBar.kt`
- Create: `app/src/test/java/io/github/aedev/flow/ui/screens/music/MusicPlayerPalettePolicyTest.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/screens/music/MusicPlayerAccessibilityTest.kt`

**Interfaces:**
- Preserve `MusicPlayerViewModel`, lyrics, queue, device output, background-style preference, and artwork loading.
- Add a pure `MusicArtworkPalettePolicy.shouldExtract(isExpanded: Boolean, backgroundStyle: MusicPlayerBackgroundStyle): Boolean`.

- [ ] **Step 1: Write failing policy and accessibility tests**

```kotlin
@Test fun paletteExtractionOnlyRunsForExpandedArtworkBackground() {
    assertFalse(MusicArtworkPalettePolicy.shouldExtract(false, MusicPlayerBackgroundStyle.BLUR_GRADIENT))
    assertTrue(MusicArtworkPalettePolicy.shouldExtract(true, MusicPlayerBackgroundStyle.BLUR_GRADIENT))
}

@Test fun musicPlayerQueueControlHasAccessibleTarget() {
    composeRule.setContent { MusicPlayerTopBar(onQueue = {}, onBack = {}) }
    composeRule.onNodeWithContentDescription("Queue").assertHeightIsAtLeast(48.dp)
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.screens.music.MusicPlayerPalettePolicyTest && ./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.music.MusicPlayerAccessibilityTest`  
Expected: FAIL because the policy and target sizing are absent.

- [ ] **Step 3: Implement the policy and updated layout**

Extract artwork palette on `Dispatchers.Default` only when the expanded player needs an artwork background. Keep the current active-player artwork treatment, but use semantic foreground fallbacks, shared sheets, and tokenized controls. Do not alter queue/lyrics/device-output data flow.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.screens.music.MusicPlayerPalettePolicyTest && ./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.music.MusicPlayerAccessibilityTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/screens/music/EnhancedMusicPlayerScreen.kt app/src/main/java/io/github/aedev/flow/ui/screens/music/player app/src/test/java/io/github/aedev/flow/ui/screens/music/MusicPlayerPalettePolicyTest.kt app/src/androidTest/java/io/github/aedev/flow/ui/screens/music/MusicPlayerAccessibilityTest.kt
git commit -m "feat(music): refine expanded player presentation"
```

### Task 4: Make Shorts contrast-safe, accessible, and motion-aware

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/shorts/ShortVideoPlayer.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/shorts/ShortsScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/player/components/VideoAmbientBackground.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/screens/shorts/ShortsActionAccessibilityTest.kt`

**Interfaces:**
- Preserve player pooling, preloading, ambient preference, gestures, comments, description, options, like/save/share, and simple-UI mode.
- Preserve `ShortsActionButton` while adding tokenized labels and targets.

- [ ] **Step 1: Write the failing action-rail test**

```kotlin
@Test fun shortsActionsHave48DpTargetsAndLabels() {
    composeRule.setContent { ShortsActionButton(icon = Icons.Default.Share, text = "Share", contentDescription = "Share", onClick = {}) }
    composeRule.onNodeWithContentDescription("Share").assertHeightIsAtLeast(48.dp)
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.shorts.ShortsActionAccessibilityTest`  
Expected: FAIL if the visible compact target does not meet the design token.

- [ ] **Step 3: Migrate the action rail and motion**

Use contrast values from the existing ambient-frame work with semantic fallbacks. Ensure descriptive actions, subscription, and top controls meet 48 dp. Replace any repeated, nonessential visual loop with the central reduced-motion policy. Keep player-pool and loading behavior unchanged.

- [ ] **Step 4: Run test**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.shorts.ShortsActionAccessibilityTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/screens/shorts/ShortVideoPlayer.kt app/src/main/java/io/github/aedev/flow/ui/screens/shorts/ShortsScreen.kt app/src/main/java/io/github/aedev/flow/ui/screens/player/components/VideoAmbientBackground.kt app/src/androidTest/java/io/github/aedev/flow/ui/screens/shorts/ShortsActionAccessibilityTest.kt
git commit -m "feat(shorts): improve contrast and accessibility"
```

### Task 5: Verify player surfaces

**Files:**
- No source changes required unless validation exposes a regression.

- [ ] **Step 1: Run player and Shorts unit tests**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.player.* --tests io.github.aedev.flow.ui.screens.shorts.*`  
Expected: PASS.

- [ ] **Step 2: Run build and lint**

Run: `./gradlew :app:assembleGithubDebug :app:lintGithubDebug`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual media smoke test**

Verify video, live video, music, Shorts, mini-player expansion, queue, lyrics, captions, comments, live chat, quality, speed, SponsorBlock, downloads, casting, background playback, PiP, RTL gestures, dark/light/custom themes, and reduced motion.

- [ ] **Step 4: Commit the verified slice**

```bash
git add app/src/main/java app/src/main/res app/src/test app/src/androidTest
git commit -m "test(player): verify media surface refresh"
```
