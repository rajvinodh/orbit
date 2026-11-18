package io.github.rajvinodh.orbit.testapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rajvinodh.orbit.client.Orbit
import io.github.rajvinodh.orbit.client.OrbitRule
import io.github.rajvinodh.orbit.client.annotation.ExternalId
import org.junit.Assume
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppComposeTest {

    @get:Rule
    val orbitRule = OrbitRule(InstrumentationRegistry.getInstrumentation())

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @ExternalId("12345")
    @Test
    fun mainActivity_displaysContentFromActivityLifecycle() {
        composeTestRule
            .onNodeWithText("Orbit Test App")
            .assertIsDisplayed()

        Orbit.screenshot("main_screen")
        Thread.sleep(10000)
    }

    @ExternalId("123456")
    @Test
    fun mainActivity_displaysContentFromActivityLifecycle_failure() {
        composeTestRule
            .onNodeWithText("Orbit Test App")
            .assertIsDisplayed()

        Orbit.screenshot("main_screen")

        Thread.sleep(10000)

        throw RuntimeException("Test failure")
    }

    @Ignore("Intentionally ignored — verifies orbit captures IGNORED status")
    @Test
    fun mainActivity_ignoredTest() {
    }

    @Test
    fun mainActivity_assumptionFailureTest() {
        Assume.assumeTrue("Intentional assumption failure — verifies orbit captures ASSUMPTION_FAILURE status", false)
    }
}