package com.monandroido.app

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import com.monandroido.app.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeRendersWithoutOnboarding() {
        val sessionTitle = composeRule.string(R.string.home_section_session)
        composeRule.waitUntilNodeWithText(sessionTitle)
        assertTrue(composeRule.onAllNodesWithText(sessionTitle).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun topLevelNavigationRendersCoreScreens() {
        val homeTitle = composeRule.string(R.string.home_section_session)
        val profilesTab = composeRule.string(R.string.nav_profiles)
        val benchmarkTab = composeRule.string(R.string.nav_benchmark)
        val settingsTab = composeRule.string(R.string.nav_settings)
        val homeTab = composeRule.string(R.string.nav_home)
        composeRule.waitUntilNodeWithText(homeTitle)

        composeRule.onNodeWithText(profilesTab).performClick()
        composeRule.waitUntilNodeWithText(composeRule.string(R.string.profiles_library_title))

        composeRule.onNodeWithText(benchmarkTab).performClick()
        composeRule.waitUntilNodeWithText(composeRule.string(R.string.benchmark_run_title))

        composeRule.onNodeWithText(settingsTab).performClick()
        composeRule.waitUntilNodeWithText(composeRule.string(R.string.settings_support_title))

        composeRule.onNodeWithText(homeTab).performClick()
        composeRule.waitUntilNodeWithText(homeTitle)
    }

    @Test
    fun profileEditorOpensFromProfilesFab() {
        val homeTitle = composeRule.string(R.string.home_section_session)
        val profilesTab = composeRule.string(R.string.nav_profiles)
        val libraryTitle = composeRule.string(R.string.profiles_library_title)
        composeRule.waitUntilNodeWithText(homeTitle)
        composeRule.onNodeWithText(profilesTab).performClick()
        composeRule.waitUntilNodeWithText(libraryTitle)

        composeRule.onNodeWithContentDescription(composeRule.string(R.string.profiles_create_content_description)).performClick()
        composeRule.waitUntilNodeWithText(composeRule.string(R.string.profile_editor_create_title))

        pressBack()
        composeRule.waitUntilNodeWithText(libraryTitle)
    }
}

private fun ComposeContentTestRule.waitUntilNodeWithText(text: String) {
    waitUntil(timeoutMillis = 10_000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun AndroidComposeTestRule<*, *>.string(resId: Int): String = activity.getString(resId)
