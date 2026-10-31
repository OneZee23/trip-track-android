// iOS: PrivacySettingsView.swift, AuthService.setVisibility generation rules.
package com.onezee.triptrack.feature

import androidx.lifecycle.ViewModelStore
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.model.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySettingsTests : WorldTest() {
    @Test fun missingAllBlocksKeepsSwitchesUnavailable() = runTest(dispatcher) {
        val saved = mutableListOf<ProfileUpdateRequest>()
        val vm = own(PrivacySettingsViewModel(object : ProfilePrivacyService {
            override suspend fun load() = MeResponse(isPublic = true)
            override suspend fun save(patch: ProfileUpdateRequest) { saved += patch }
        }, dispatcher))
        runCurrent(); assertTrue(vm.state.value.loaded); assertFalse(vm.state.value.supportsBlocks)
        vm.setVisibility(ProfileVisibilityBlock.map, false); runCurrent(); assertTrue(saved.isEmpty())
        vm.setPublicProfile(false); runCurrent(); assertEquals(false, saved.single().isPublic)
        assertNull(saved.single().mapPublic)
    }
    @Test fun blockWritesAreIndependentAndFailureRevertsOnlyThatBlock() = runTest(dispatcher) {
        val saved = mutableListOf<ProfileUpdateRequest>()
        val vm = own(PrivacySettingsViewModel(object : ProfilePrivacyService {
            override suspend fun load() = MeResponse(isPublic = true, countersPublic = true, mapPublic = true)
            override suspend fun save(patch: ProfileUpdateRequest) {
                saved += patch
                if (patch.mapPublic != null) throw IOException("offline")
            }
        }, dispatcher))
        runCurrent(); vm.setVisibility(ProfileVisibilityBlock.map, false); vm.setVisibility(ProfileVisibilityBlock.counters, false)
        assertEquals(false, vm.state.value.visibility[ProfileVisibilityBlock.map])
        runCurrent()
        assertEquals(true, vm.state.value.visibility[ProfileVisibilityBlock.map])
        assertEquals(false, vm.state.value.visibility[ProfileVisibilityBlock.counters])
        assertEquals(2, saved.size)
        assertNull(saved.first().isPublic)
        assertNull(saved.last().mapPublic)
    }
    @Test fun earlierBlockResponseCannotClearNewerOptimisticChoice() = runTest(dispatcher) {
        val first = CompletableDeferred<Unit>(); val second = CompletableDeferred<Unit>()
        var calls = 0
        val vm = own(PrivacySettingsViewModel(object : ProfilePrivacyService {
            override suspend fun load() = MeResponse(isPublic = true, mapPublic = true)
            override suspend fun save(patch: ProfileUpdateRequest) { if (++calls == 1) first.await() else second.await() }
        }, dispatcher))
        runCurrent(); vm.setVisibility(ProfileVisibilityBlock.map, false); runCurrent()
        vm.setVisibility(ProfileVisibilityBlock.map, true); runCurrent()
        first.complete(Unit); runCurrent()
        assertEquals(true, vm.state.value.visibility[ProfileVisibilityBlock.map])
        assertTrue(ProfileVisibilityBlock.map in vm.state.value.pending)
        second.complete(Unit); runCurrent(); assertTrue(vm.state.value.pending.isEmpty())
    }
}
