package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState

/**
 * The regression test for the bug that shipped twice.
 *
 * `SwipeToDismissBox`'s `confirmValueChange` is deprecated without replacement in
 * the current Compose Foundation, so it is never called. Both swipe call sites
 * used it, which made **swipe-to-delete and swipe-to-save silent no-ops**: the row
 * animated to its anchor, painted a green or red background, and changed nothing.
 * Nothing caught it because nothing exercised the gesture.
 *
 * These tests drive a real touch gesture through the same `SwipeToAct` component
 * the app uses and assert the action actually fires.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    // SDK 34 explicitly: compileSdk is 37, which this Robolectric cannot
    // provision, and it fails with an unhelpful UnsupportedOperationException
    // from DefaultSdkProvider. The swipe gesture does not depend on API level.
    sdk = [34],
    qualifiers = "w411dp-h891dp-420dpi",
    application = com.awbuilds.auraspend.TestApplication::class
)
class SwipeToActTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun swipingRightReportsStartToEnd() {
        var fired: SwipeToDismissBoxValue? = null
        compose.setContent {
            SwipeToAct(
                onAction = { fired = it },
                modifier = Modifier.fillMaxWidth().height(120.dp).testTag(SWIPE_TARGET),
                backgroundContent = { _ -> Box(Modifier.fillMaxSize().background(Color.Green)) }
            ) {
                Box(Modifier.fillMaxSize().background(Color.White))
            }
        }
        compose.onNodeWithTag(SWIPE_TARGET).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(SwipeToDismissBoxValue.StartToEnd, fired)
    }

    @Test
    fun swipingLeftReportsEndToStart() {
        var fired: SwipeToDismissBoxValue? = null
        compose.setContent {
            SwipeToAct(
                onAction = { fired = it },
                modifier = Modifier.fillMaxWidth().height(120.dp).testTag(SWIPE_TARGET),
                backgroundContent = { _ -> Box(Modifier.fillMaxSize().background(Color.Red)) }
            ) {
                Box(Modifier.fillMaxSize().background(Color.White))
            }
        }
        compose.onNodeWithTag(SWIPE_TARGET).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(SwipeToDismissBoxValue.EndToStart, fired)
    }

    @Test
    fun aDirectionThatIsDisabledNeverFires() {
        var fired: SwipeToDismissBoxValue? = null
        compose.setContent {
            SwipeToAct(
                onAction = { fired = it },
                modifier = Modifier.fillMaxWidth().height(120.dp).testTag(SWIPE_TARGET),
                // Mirrors the Activity list, which only allows swipe-to-delete.
                enabledFromStartToEnd = false,
                backgroundContent = { _ -> Box(Modifier.fillMaxSize()) }
            ) {
                Box(Modifier.fillMaxSize().background(Color.White))
            }
        }
        compose.onNodeWithTag(SWIPE_TARGET).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue(
            "swipe right must be inert when enableDismissFromStartToEnd = false",
            fired == null
        )
    }

    private companion object {
        const val SWIPE_TARGET = "swipe-target"
    }
}
