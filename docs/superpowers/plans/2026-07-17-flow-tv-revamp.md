# Flow Android TV Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply Flow Current to Android TV navigation, cards, screen states, search, library, subscriptions, settings, and playback while preserving remote behavior and the existing TV route set.

**Architecture:** Keep `FlowTvApp` as the TV root and retain its existing view models and player handoff. Build on the shared token/state system, then apply a TV-specific focus contract to the rail, cards, and player controls. TV uses large lanes and grids instead of touch UI scaled up.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android TV/Leanback activity support, Media3, Compose UI testing.

## Global Constraints

- Commit only to `PastaHimself/Flow:flow-ui-ux-revamp`; do not modify either `main` branch or `A-EDev/Flow`.
- Keep all current TV destinations: Home, Subscriptions, Search, Library, Settings, and player.
- Keep D-pad navigation available. Back dismisses transient player UI before it leaves the current screen.
- Use visible focus state, 48 dp-equivalent control spacing, semantic labels, and resource-backed text.
- Keep the TV player handoff to the existing `VideoPlayerViewModel` and `GlobalPlayerState`.

---

### Task 1: Define the TV focus and navigation-rail contract

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/components/TvFocusableCard.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/components/TvNavigationRail.kt`
- Create: `app/src/test/java/io/github/aedev/flow/ui/tv/components/TvFocusStyleTest.kt`

**Interfaces:**
- Preserve `TvFocusableCard(onClick, modifier, content)`.
- Add `TvFocusStyle` with focused/unfocused scale, container, content, and border values.
- Preserve `TvNavigationRail(selected, onSelected, modifier)`.

- [ ] **Step 1: Write failing focus-style tests**

```kotlin
class TvFocusStyleTest {
    @Test fun focusedCardUsesVisibleScale() {
        assertEquals(1.05f, TvFocusStyle.focusedScale)
    }

    @Test fun unfocusedCardDoesNotScale() {
        assertEquals(1f, TvFocusStyle.unfocusedScale)
    }
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.tv.components.TvFocusStyleTest`  
Expected: FAIL because `TvFocusStyle` is absent.

- [ ] **Step 3: Implement the contract**

Move the existing local focus constants into `TvFocusStyle`. Use Flow tokens for shape, elevation, and motion. Keep a clear tonal plus scale change when focused; do not rely on a border alone. In the rail, retain current destination order and use a selected semantic state plus focusable rows with resource labels.

- [ ] **Step 4: Run the test and confirm success**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.tv.components.TvFocusStyleTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/tv/components/TvFocusableCard.kt app/src/main/java/io/github/aedev/flow/ui/tv/components/TvNavigationRail.kt app/src/test/java/io/github/aedev/flow/ui/tv/components/TvFocusStyleTest.kt
git commit -m "feat(tv): standardize focus and navigation"
```

### Task 2: Rebuild TV cards and state surfaces

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/components/TvVideoCard.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/components/TvScreenStates.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/tv/components/TvScreenStatesTest.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/tv/components/TvTestFixtures.kt`

**Interfaces:**
- Preserve `TvVideoCard(video, onClick, modifier)` and `TvVideoRow(videos, onVideoClick)`.
- Use the shared loading/empty/error state language at TV scale.
- Define `sampleVideo` in `TvTestFixtures.kt` with `Video(id = "tv-video-1", title = "Sample TV video", channelName = "Flow", channelId = "channel-1", thumbnailUrl = "", duration = 60, viewCount = 1L, uploadDate = "")`.

- [ ] **Step 1: Write failing card/state tests**

```kotlin
@Test fun tvErrorStateExposesRecoveryAction() {
    var retried = 0
    composeRule.setContent { TvMessageState(title = "Offline", onRetry = { retried++ }) }
    composeRule.onNodeWithContentDescription("Retry").performClick()
    assertEquals(1, retried)
}

@Test fun tvVideoCardHasReadableTitle() {
    composeRule.setContent { TvVideoCard(video = sampleVideo, onClick = {}) }
    composeRule.onNodeWithText(sampleVideo.title).assertExists()
}
```

- [ ] **Step 2: Run the tests and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.tv.components.TvScreenStatesTest`  
Expected: FAIL because retry is not part of the current TV state contract.

- [ ] **Step 3: Implement TV-scale cards and states**

Use large thumbnail ratios, readable title/metadata spacing, shared semantic surfaces, and focus-first actions. Extend `TvMessageState` with an optional retry slot so existing no-action messages keep working. Do not add phone-only overflow menus.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.tv.components.TvScreenStatesTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/tv/components/TvVideoCard.kt app/src/main/java/io/github/aedev/flow/ui/tv/components/TvScreenStates.kt app/src/androidTest/java/io/github/aedev/flow/ui/tv/components/TvScreenStatesTest.kt
git commit -m "feat(tv): refresh cards and screen states"
```

### Task 3: Migrate TV Home, Search, Library, Subscriptions, and Settings

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/screens/TvHomeScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/screens/TvSearchScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/screens/TvLibraryScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/screens/TvSubscriptionsScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/screens/TvSettingsScreen.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/tv/screens/TvNavigationSmokeTest.kt`

**Interfaces:**
- Preserve view-model initialization and existing `onVideoClick` callbacks.
- Preserve `TvDestination` values and current rail routing.
- Add `TvDestination.testTag: String` and apply it to each rail item; Library uses `"tv_nav_library"`.

- [ ] **Step 1: Write the failing navigation smoke test**

```kotlin
@Test fun selectingLibraryShowsLibraryHeading() {
    composeRule.setContent { FlowTvApp() }
    composeRule.onNodeWithTag("tv_nav_library").performClick()
    composeRule.onNodeWithText(context.getString(R.string.library)).assertExists()
}
```

- [ ] **Step 2: Run the test and confirm failure or capture the current focus issue**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.tv.screens.TvNavigationSmokeTest`  
Expected: Record the initial state; the test must pass after the migrated rail exposes a unique accessible label for Library.

- [ ] **Step 3: Apply the TV information architecture**

Keep Home as lanes, Search as query plus result grid, Library as category switcher plus grid, and Subscriptions as channels plus feed. Give Settings a remote-friendly grouped list rather than a phone settings clone. Preserve existing data sources and use the shared state components for loading, error, and empty conditions.

- [ ] **Step 4: Run test**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.tv.screens.TvNavigationSmokeTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/tv/screens app/src/androidTest/java/io/github/aedev/flow/ui/tv/screens/TvNavigationSmokeTest.kt
git commit -m "feat(tv): refresh core browse surfaces"
```

### Task 4: Make TV player controls remote-first

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/screens/TvPlayerScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/tv/input/TvPlayerKeyMapper.kt`
- Modify: `app/src/test/java/io/github/aedev/flow/ui/tv/input/TvPlayerKeyMapperTest.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/tv/screens/TvPlayerControlsTest.kt`

**Interfaces:**
- Preserve `TvPlayerScreen(video, viewModel, onClose)`.
- Preserve media-key behavior in `TvPlayerKeyMapper`.
- Add `TvPlayerActionRow(onClose: () -> Unit, onPlayPause: () -> Unit, onMore: () -> Unit)` and labels for play/pause, seek, close, captions, settings, and more actions.

- [ ] **Step 1: Write failing remote-control tests**

```kotlin
@Test fun closePlayerActionInvokesCallback() {
    var closed = 0
    composeRule.setContent { TvPlayerActionRow(onClose = { closed++ }, onPlayPause = {}, onMore = {}) }
    composeRule.onNodeWithContentDescription("Close player").performClick()
    assertEquals(1, closed)
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.tv.screens.TvPlayerControlsTest`  
Expected: FAIL until the close action is explicit and focusable.

- [ ] **Step 3: Implement remote-first control order**

Arrange focused controls in a predictable order: back/close, play-pause, seek, then contextual actions. Keep the existing player and key-mapper behavior. Expose labels for every focusable control and preserve media-key mapping tests.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.tv.input.TvPlayerKeyMapperTest && ./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.tv.screens.TvPlayerControlsTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/tv/screens/TvPlayerScreen.kt app/src/main/java/io/github/aedev/flow/ui/tv/input/TvPlayerKeyMapper.kt app/src/test/java/io/github/aedev/flow/ui/tv/input/TvPlayerKeyMapperTest.kt app/src/androidTest/java/io/github/aedev/flow/ui/tv/screens/TvPlayerControlsTest.kt
git commit -m "feat(tv): make player controls remote first"
```

### Task 5: Verify Android TV end to end

**Files:**
- No source changes required unless validation exposes a regression.

- [ ] **Step 1: Run focused TV tests**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.tv.*`  
Expected: PASS.

- [ ] **Step 2: Build and lint**

Run: `./gradlew :app:assembleGithubDebug :app:lintGithubDebug`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Test on a TV emulator or hardware**

Verify launcher entry, rail focus, D-pad movement, Back behavior, Home/Search/Library/Subscriptions/Settings, loading/error/empty states, playback, media keys, captions, custom themes, and a deep link.

- [ ] **Step 4: Commit the verified slice**

```bash
git add app/src/main/java app/src/main/res app/src/test app/src/androidTest
git commit -m "test(tv): verify Android TV redesign"
```
