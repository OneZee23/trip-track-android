package com.onezee.triptrack

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment

/**
 * For tests that open MainActivity as a returning person: the first run shows the onboarding
 * instead of the tab shell (iOS `hasCompletedOnboarding`). Chain it outside the compose rule, so it
 * runs before the activity starts.
 */
class OnboardingDone : TestWatcher() {
    override fun starting(description: Description) {
        (RuntimeEnvironment.getApplication() as TripTrackApplication).appGraph.onboarding.complete()
    }
}
