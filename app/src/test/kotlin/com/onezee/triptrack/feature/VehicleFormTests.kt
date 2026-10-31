// iOS: VehicleEditFormView.swift, OdometerFieldTests.swift, VehicleCatalogSelectionTests.swift
package com.onezee.triptrack.feature

import com.onezee.triptrack.data.VehicleStore
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.model.*
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleFormTests : WorldTest() {
    @Test fun aboutOnlyEditPreservesTheCatalogLinkAndExactConsumption() = runTest(dispatcher) {
        val id = vehicles.addVehicle("Polo", "pixel_car_orange")
        vehicles.updateVehicleGarage(id, catalogModelId = "volkswagen-polo", catalogGenerationId = "volkswagen-polo-sedan-2015", catalogVariantId = "variant")
        vehicles.updateVehicleFuel(id, 9.15, 6.25, 63.7)
        val vm = own(VehicleEditViewModel(vehicles.vehicle(id), vehicles, DistanceUnit.miles, dispatcher))
        val value = vm.state.value.vehicle
        vm.setPassport(value.make, value.model, value.year, value.bodyType, "About")
        vm.setDashboardUnits(DashboardUnits.metric); vm.setDashboardUnits(DashboardUnits.app)
        vm.save(); advanceUntilIdle()
        assertEquals("variant", vehicles.entity(id)?.catalogVariantId)
        assertEquals(9.15, vehicles.vehicle(id)?.cityConsumption)
        assertEquals(6.25, vehicles.vehicle(id)?.highwayConsumption)
    }
    @Test fun retryAfterAPartialSaveUsesTheSameVehicle() = runTest(dispatcher) {
        var fail = true
        val flaky = object : VehicleStore by vehicles {
            override suspend fun updateVehiclePassport(id: UUID, make: String, model: String, year: Int, bodyType: String, about: String) {
                if (fail) { fail = false; throw IOException() }
                vehicles.updateVehiclePassport(id, make, model, year, bodyType, about)
            }
        }
        val vm = own(VehicleEditViewModel(null, flaky, DistanceUnit.km, dispatcher))
        vm.setName("Polo"); vm.save(); advanceUntilIdle()
        assertTrue(vm.state.value.failed); assertEquals(1, vehicles.vehicles().size)
        vm.setName("Renamed"); vm.save(); advanceUntilIdle()
        assertNotNull(vm.state.value.savedId); assertEquals("Renamed", vehicles.vehicles().single().name)
    }
    @Test fun canceledFormDoesNotWriteEnergyOrUnits() = runTest(dispatcher) {
        val id = vehicles.addVehicle("Polo", "pixel_car_orange")
        val before = vehicles.vehicle(id)
        val vm = own(VehicleEditViewModel(before, vehicles, DistanceUnit.km, dispatcher))
        vm.setDashboardUnits(DashboardUnits.imperial); vm.setPowertrain(Powertrain.electric)
        vm.setEnergy(VehicleEnergyField.electricConsumption, "12")
        advanceUntilIdle(); assertEquals(before, vehicles.vehicle(id))
    }
}
