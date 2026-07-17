# Flow Foundation and Adaptive Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the shared Flow Current tokens, motion/accessibility policy, adaptive navigation shell, and reusable screen-state components without changing routes or playback behavior.

**Architecture:** Retain Material 3 and `FlowTheme` as the color authority. Add small semantic token and component modules, then migrate the existing app shell and navigation components to use them. `FlowApp` keeps route ownership and global player overlays.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose animation, Hilt, Compose UI testing.

## Global Constraints

- Commit only to `PastaHimself/Flow:flow-ui-ux-revamp`. Do not update either `main` branch or `A-EDev/Flow`.
- Support phones, tablets, foldables, landscape, RTL, all current Flow themes, and Android TV.
- Use Material 3 semantic colors and string resources. Do not add gradients, glass, glows, or arbitrary semantic inline colors.
- Preserve routes, callbacks, player overlays, PiP, theme preferences, and locales.
- Make every touch target at least 48 × 48 dp. Keep TV focus-first.
- Write tests first; run targeted tests before each commit.

---

### Task 1: Add design-token and motion-policy modules

**Files:**
- Create: `app/src/main/java/io/github/aedev/flow/ui/theme/FlowDesignTokens.kt`
- Create: `app/src/main/java/io/github/aedev/flow/ui/theme/FlowMotion.kt`
- Create: `app/src/test/java/io/github/aedev/flow/ui/theme/FlowDesignTokensTest.kt`

**Interfaces:**
- Produces `FlowSpacing`, `FlowShapeTokens`, `FlowTouchTarget`, `FlowIconSize`, and `FlowMotion`.
- Consumes Compose `Dp`, `Duration`, and animation specs only.

- [ ] **Step 1: Write the failing token test**

```kotlin
class FlowDesignTokensTest {
    @Test fun minimumTouchTargetIs48Dp() {
        assertEquals(48.dp, FlowTouchTarget.minimum)
    }

    @Test fun spacingScaleIsStable() {
        assertEquals(listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp), FlowSpacing.all)
    }
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.theme.FlowDesignTokensTest`  
Expected: FAIL because `FlowTouchTarget` and `FlowSpacing` are absent.

- [ ] **Step 3: Add the minimal API**

```kotlin
object FlowSpacing {
    val xxs = 4.dp; val xs = 8.dp; val sm = 12.dp
    val md = 16.dp; val lg = 24.dp; val xl = 32.dp
    val all = listOf(xxs, xs, sm, md, lg, xl)
}
object FlowTouchTarget { val minimum = 48.dp }
object FlowIconSize { val compact = 20.dp; val standard = 24.dp }
object FlowShapeTokens {
    val compact = 8.dp; val control = 12.dp
    val card = 16.dp; val sheet = 24.dp
}
```

Create `FlowMotion` with feedback duration 150 ms, shell duration 240 ms, and helpers that return Compose `tween` specs. Keep reduced-motion resolution in one function.

- [ ] **Step 4: Run the test and confirm success**

Run: `./gradlew :app:testGithubDebugUnitTest --tests io.github.aedev.flow.ui.theme.FlowDesignTokensTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/theme/FlowDesignTokens.kt app/src/main/java/io/github/aedev/flow/ui/theme/FlowMotion.kt app/src/test/java/io/github/aedev/flow/ui/theme/FlowDesignTokensTest.kt
git commit -m "feat(ui): add shared Flow design tokens"
```

### Task 2: Add shared screen-state components

**Files:**
- Create: `app/src/main/java/io/github/aedev/flow/ui/components/FlowScreenState.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/components/FlowScreenStateTest.kt`

**Interfaces:**
- Produces `FlowLoadingState`, `FlowEmptyState`, and `FlowErrorState`.
- Consumers pass resource-backed labels and `onRetry: (() -> Unit)?`.

- [ ] **Step 1: Write the failing Compose test**

```kotlin
@get:Rule val composeRule = createComposeRule()

@Test fun errorStateExposesRetryAction() {
    var retries = 0
    composeRule.setContent { FlowErrorState(message = "Offline", onRetry = { retries++ }) }
    composeRule.onNodeWithText("Retry").performClick()
    assertEquals(1, retries)
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.components.FlowScreenStateTest`  
Expected: FAIL because `FlowErrorState` is absent.

- [ ] **Step 3: Implement the semantic components**

```kotlin
@Composable
fun FlowErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().semantics { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.sizeIn(
                    minWidth = FlowTouchTarget.minimum,
                    minHeight = FlowTouchTarget.minimum,
                ),
            ) { Text(stringResource(R.string.retry)) }
        }
    }
}
```

Implement loading and empty variants with the same vertical rhythm, semantic heading, and optional recovery action. Do not add new illustration assets.

- [ ] **Step 4: Run the test and confirm success**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.components.FlowScreenStateTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/components/FlowScreenState.kt app/src/androidTest/java/io/github/aedev/flow/ui/components/FlowScreenStateTest.kt
git commit -m "feat(ui): add accessible screen state components"
```

### Task 3: Fix the bottom-navigation interaction layer

**Files:**
- Modify: `app/src/main/java/io/github/aedev/flow/ui/components/NavigationComponents.kt`
- Modify: `app/src/androidTest/java/io/github/aedev/flow/ui/components/NavigationComponentsTest.kt`

**Interfaces:**
- Preserve `FloatingBottomNavBar(selectedIndex, onItemSelected, ...)`.
- Preserve item ordering, More-menu behavior, and resource labels.

- [ ] **Step 1: Add a failing target-size and selected-semantics test**

```kotlin
@Test fun selectedNavigationItemHas48DpTargetAndSelectedSemantics() {
    val homeLabel = InstrumentationRegistry.getInstrumentation()
        .targetContext.getString(R.string.nav_home)
    composeRule.setContent { FloatingBottomNavBar(selectedIndex = 0, onItemSelected = {}) }
    composeRule.onNodeWithContentDescription(homeLabel)
        .assertIsSelected()
        .assertHeightIsAtLeast(48.dp)
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.components.NavigationComponentsTest`  
Expected: FAIL because the current clickable content is shorter than 48 dp and has no selected semantics.

- [ ] **Step 3: Migrate `BottomNavItem`**

Use `Modifier.sizeIn(minHeight = FlowTouchTarget.minimum)`, `semantics { selected = selected; role = Role.Tab }`, `FlowShapeTokens.compact`, `FlowIconSize.standard`, and `FlowMotion`. Retain `MAX_VISIBLE_NAV_ITEMS` and the overflow menu.

- [ ] **Step 4: Run the test and confirm success**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.components.NavigationComponentsTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/components/NavigationComponents.kt app/src/androidTest/java/io/github/aedev/flow/ui/components/NavigationComponentsTest.kt
git commit -m "fix(ui): make bottom navigation accessible"
```

### Task 4: Add the adaptive mobile navigation shell

**Files:**
- Create: `app/src/main/java/io/github/aedev/flow/ui/components/AdaptiveNavigationShell.kt`
- Modify: `app/src/main/java/io/github/aedev/flow/ui/FlowApp.kt`
- Create: `app/src/androidTest/java/io/github/aedev/flow/ui/components/AdaptiveNavigationShellTest.kt`

**Interfaces:**
- `AdaptiveNavigationShell(compactNavigation, railNavigation, content)` renders compact navigation below 600 dp and a rail at or above 600 dp.
- `FlowApp` remains the owner of selected destination and global overlays.

- [ ] **Step 1: Write the failing shell-selection test**

```kotlin
@Test fun mediumWidthUsesNavigationRail() {
    composeRule.setContent {
        Box(Modifier.requiredWidth(700.dp)) {
            AdaptiveNavigationShell(
                compactNavigation = { Text("bottom") },
                railNavigation = { Text("rail") },
            ) { Text("content") }
        }
    }
    composeRule.onNodeWithText("rail").assertExists()
    composeRule.onNodeWithText("bottom").assertDoesNotExist()
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.components.AdaptiveNavigationShellTest`  
Expected: FAIL because `AdaptiveNavigationShell` is absent.

- [ ] **Step 3: Implement and wire the shell**

Use `BoxWithConstraints`. Below 600 dp render the existing `FloatingBottomNavBar`; at 600 dp or wider render a Material 3 `NavigationRail` with the same enabled items, labels, and selection callback. Do not move route logic from `FlowApp`; pass slot lambdas from it. Keep global mini-player and player overlays above the shell.

- [ ] **Step 4: Run the shell and navigation tests**

Run: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.ui.components.AdaptiveNavigationShellTest,io.github.aedev.flow.ui.components.NavigationComponentsTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/components/AdaptiveNavigationShell.kt app/src/main/java/io/github/aedev/flow/ui/FlowApp.kt app/src/androidTest/java/io/github/aedev/flow/ui/components/AdaptiveNavigationShellTest.kt
git commit -m "feat(ui): add adaptive navigation shell"
```

### Task 5: Verify the foundation slice

**Files:**
- Modify: `docs/flow-ui-ux-redesign-spec.md` only when its validation commands differ from the discovered Gradle task graph.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testGithubDebugUnitTest :app:connectedGithubDebugAndroidTest --tests io.github.aedev.flow.ui.theme.FlowDesignTokensTest`  
Expected: PASS.

- [ ] **Step 2: Run build and lint**

Run: `./gradlew :app:assembleGithubDebug :app:lintGithubDebug`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manually verify compact and medium shells**

Check Home, Shorts, Music, Subscriptions, Library, Search/More, rotation, light/dark/custom themes, RTL, and TalkBack.

- [ ] **Step 4: Commit verification corrections**

```bash
git add app/src/main/java/io/github/aedev/flow/ui/theme app/src/main/java/io/github/aedev/flow/ui/components app/src/main/java/io/github/aedev/flow/ui/FlowApp.kt app/src/test app/src/androidTest
git commit -m "test(ui): verify adaptive shell foundations"
```
