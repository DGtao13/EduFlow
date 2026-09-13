package com.eduflow.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLogicTest {
    @Test fun freshInstallWithoutFlagShowsGuide() = assertTrue(onboardingShouldAutoShow(false, false))
    @Test fun completedInstallDoesNotShowGuide() = assertFalse(onboardingShouldAutoShow(true, false))
    @Test fun configuredLegacyInstallDoesNotShowGuide() = assertFalse(onboardingShouldAutoShow(false, true))
}
