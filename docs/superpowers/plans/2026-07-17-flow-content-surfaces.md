# Flow Content Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate discovery, library, settings, onboarding, personality, channel, artist, and playlist surfaces to the shared Flow Current component system while preserving all existing routes and actions.

**Architecture:** Build on the token, navigation, and state modules from the foundation plan. Keep view models and repositories in place. Replace screen-local card/header/state rendering with shared components, then make layout choices from the available width rather than spreading phone lists across large screens.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Paging Compose, Hilt, Coil, Compose UI testing.

## Global Constraints

- Work only on `PastaHimself/Flow:flow-ui-ux-revamp`; do not update either `main` branch or `A-EDev/Flow`.
- Preserve each existing route, view-model call, list/grid preference, sort/filter action, theme preference, locale, and RTL behavior.
- Use the token modules from `FlowDesignTokens.kt`, resource strings, 48 dp touch targets, and semantic colors.
- Keep lazy-list keys stable and avoid per-item paging entrance effects.
- Each migrated screen must cover loading, empty, error, offline/disabled where supported, and populated states.

---

### Task 1: Define reusable content-card and editorial-header contracts

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/components/VideoCard.kt`
- Create: `app/src/main/java/io/github/aedev/flow/ui/components/FlowEditorialHeader.kt`
- Create: `app/src/test/java/io/github/aedev/flow/ui/components/FlowContentComponentTest.kt`
- Create: `app/src/test/java/io/github/aedev/flow/ui/components/FlowContentTestFixtures.kt`

**Interfaces:**
- Preserve existing public video-card callbacks.
- Add `enum class FlowCardDensity { LIST, GRID, COMPACT }`.
- Add `FlowEditorialHeader(title, subtitle, avatarUrl, actions, tabs, modifier)`.
- Define `sampleVideo` in `FlowContentTestFixtures.kt` with `Video(id = "video-1", title = "Sample video", channelName = "Flow", channelId = "channel-1", thumbnailUrl = "", duration = 60, viewCount = 1L, uploadDate = "")`.

- [ ] **Step 1: Write failing component tests**

```kotlin
@Test fun compactCardUsesOneLineTitleAndAccessibleOverflowAction() {
    composeRule.setContent {
        FlowVideoCard(video = sampleVideo, density = FlowCardDensity.COMPACT, onClick = {}, onMoreClick = {})
    }
    composeRule.onNodeWithContentDescription("More options").assertHeightIsAtLeast(48.dp)
    composeRule.onNodeWithText(sampleVideo.title).assertExists()
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.components.FlowContentComponentTest`  
Expected: FAIL because `FlowCardDensity` and `FlowVideoCard` do not exist.

- [ ] **Step 3: Implement the shared contracts**

Keep thumbnail loading, duration/live badges, DeArrow behavior, quick actions, and existing click callbacks. Use one metadata renderer for title, channel, and views/date. Apply `FlowShapeTokens.card`, semantic colors, and a 48 dp action target. Make the header a slot-based composable so channel, artist, and playlist screens keep their own actions and tabs.

- [ ] **Step 4: Run the component test and confirm success**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.components.FlowContentComponentTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/components/VideoCard.kt app/src/main/java/io/github/aedev/flow/ui/components/FlowEditorialHeader.kt app/src/test/java/io/github/aedev/flow/ui/components/FlowContentComponentTest.kt
git commit -m "feat(ui): add shared content card contracts"
```

### Task 2: Migrate Home, Search, and Subscriptions

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/search/SearchScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/subscriptions/SubscriptionsScreen.kt`
- Modify: `app/src/androidTest/java/io/github/aedev/flow/ui/components/NavigationComponentsTest.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/screens/content/ContentScreenAccessibilityTest.kt`

**Interfaces:**
- Preserve Home feed initialization, filter/list-grid preference, refresh, continuation watching, Shorts shelf, and impression tracking.
- Preserve Search query, suggestions, filters, paging, history, voice entry, and result-type behavior.
- Preserve subscription feed/manage mode and group editing.
- Extract the current inline group row into `internal fun SubscriptionGroupRow(group: SubscriptionGroup, canMoveUp: Boolean, canMoveDown: Boolean, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit)`.

- [ ] **Step 1: Write failing accessibility tests**

```kotlin
@Test fun subscriptionGroupActionsHaveAccessibleTargets() {
    val group = SubscriptionGroup(name = "Favorites", channelIds = emptyList())
    composeRule.setContent {
        SubscriptionGroupRow(group, false, false, {}, {}, {}, {})
    }
    composeRule.onNodeWithContentDescription("Edit Group").assertHeightIsAtLeast(48.dp)
    composeRule.onNodeWithContentDescription("Delete Group").assertHeightIsAtLeast(48.dp)
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.content.ContentScreenAccessibilityTest`  
Expected: FAIL because the current compact group actions are 32 dp.

- [ ] **Step 3: Migrate the three screens**

Use the shared content card and section-state primitives. Replace Search's duplicated card implementation and per-item visible=true animation with the shared card. Use `BoxWithConstraints` to keep one column on compact screens, scale grid columns deliberately, and constrain readable widths on expanded screens. Keep all callbacks and paging keys unchanged. Use resource strings for the discovered Search and Settings-style hard-coded labels in the migrated scope.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.content.ContentScreenAccessibilityTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/screens/home/HomeScreen.kt app/src/main/java/io/github/aedev/flow/ui/screens/search/SearchScreen.kt app/src/main/java/io/github/aedev/flow/ui/screens/subscriptions/SubscriptionsScreen.kt app/src/androidTest/java/io/github/aedev/flow/ui/screens/content/ContentScreenAccessibilityTest.kt
git commit -m "feat(ui): unify discovery and subscriptions surfaces"
```

### Task 3: Migrate Library, History, Settings, Personality, and Onboarding

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/library/LibraryScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/history/HistoryScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/personality/FlowPersonalityScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/personality/PersonalityDashboardSections.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/onboarding/OnboardingComponents.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/screens/settings/SettingsAccessibilityTest.kt`

**Interfaces:**
- Preserve every library/settings route and every personality export/import/reset callback.
- Preserve onboarding's interest, channel, and import steps.
- Extract `internal fun SettingsSearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit, onClear: () -> Unit)` from `SettingsScreen`.

- [ ] **Step 1: Write failing Settings and onboarding tests**

```kotlin
@Test fun settingsSearchUsesResourceBackedClearAction() {
    val label = InstrumentationRegistry.getInstrumentation()
        .targetContext.getString(R.string.settings_search_clear)
    composeRule.setContent {
        SettingsSearchTopBar(query = "flow", onQueryChange = {}, onClose = {}, onClear = {})
    }
    composeRule.onNodeWithContentDescription(label).assertExists()
}

@Test fun onboardingContinueHas48DpTarget() {
    composeRule.setContent { OnboardingBottomBar(false, false, true, {}, {}, {}) }
    composeRule.onNodeWithText(context.getString(R.string.onboarding_btn_continue)).assertHeightIsAtLeast(48.dp)
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.settings.SettingsAccessibilityTest`  
Expected: FAIL until string resources and semantic targets are applied.

- [ ] **Step 3: Migrate screen hierarchy**

Give Library a recent-activity and quick-action entry layer that links to the existing destinations. Keep History search and filters. Replace the Personality gradient hero with a tonal card while keeping charts and data. Group Settings visually without changing preference ownership. Use shared heading/state components. Make onboarding progress and navigation use tokenized controls and clear semantics.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.settings.SettingsAccessibilityTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/screens/library/LibraryScreen.kt app/src/main/java/io/github/aedev/flow/ui/screens/history/HistoryScreen.kt app/src/main/java/io/github/aedev/flow/ui/screens/settings/SettingsScreen.kt app/src/main/java/io/github/aedev/flow/ui/screens/personality app/src/main/java/io/github/aedev/flow/ui/screens/onboarding/OnboardingComponents.kt app/src/androidTest/java/io/github/aedev/flow/ui/screens/settings/SettingsAccessibilityTest.kt
git commit -m "feat(ui): refresh library settings and onboarding"
```

### Task 4: Migrate Channel, Artist, Playlist, and community headers

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/channel/ChannelScreen.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/music/ArtistPage.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/screens/music/PlaylistPage.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/components/CommunityPostCard.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/screens/channel/EditorialHeaderTest.kt`

**Interfaces:**
- Preserve channel tabs, filters, subscription actions, playlists, community posts, artist actions, playlist queue/search/delete actions, and navigation callbacks.
- Consume `FlowEditorialHeader`.

- [ ] **Step 1: Write failing header tests**

```kotlin
@Test fun channelHeaderExposesSelectedTab() {
    composeRule.setContent { FlowEditorialHeader(title = "Channel", tabs = listOf("Videos", "Shorts"), selectedTab = 0, onTabSelected = {}) }
    composeRule.onNodeWithText("Videos").assertIsSelected()
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.channel.EditorialHeaderTest`  
Expected: FAIL because the reusable header has no tab semantics.

- [ ] **Step 3: Migrate headers and cards**

Adopt the shared editorial header, card density modes, semantic tabs, and width-aware lane/grid layout. Keep existing loaded data, tab pager behavior, filters, and actions. Do not alter channel or music repository APIs.

- [ ] **Step 4: Run test**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.screens.channel.EditorialHeaderTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/screens/channel/ChannelScreen.kt app/src/main/java/io/github/aedev/flow/ui/screens/music/ArtistPage.kt app/src/main/java/io/github/aedev/flow/ui/screens/music/PlaylistPage.kt app/src/main/java/io/github/aedev/flow/ui/components/CommunityPostCard.kt app/src/androidTest/java/io/github/aedev/flow/ui/screens/channel/EditorialHeaderTest.kt
git commit -m "feat(ui): unify editorial content headers"
```

### Task 5: Verify content surfaces

**Files:**
- No source changes required unless test failures expose a regression.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testGithubDebugUnitTest :app:connectedGithubDebugAndroidTest`  
Expected: PASS.

- [ ] **Step 2: Run build and lint**

Run: `./gradlew :app:assembleGithubDebug :app:lintGithubDebug`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual matrix**

Verify list/grid modes, Home shelves, Search history/suggestions/filters, subscription management, library routes, settings search, custom themes, Personality export/import/reset, onboarding, channel tabs, artist actions, playlist actions, RTL, and compact/expanded layouts.

- [ ] **Step 4: Commit the verified slice**

```bash
git add app/src/main/java app/src/main/res app/src/test app/src/androidTest
git commit -m "test(ui): verify Flow content surfaces"
```
