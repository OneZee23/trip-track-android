// iOS: VehicleCatalogPickerView.swift, VehicleCatalogModelSteps.swift, VehicleEditFormView.swift
package com.onezee.triptrack.feature.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.VehicleStore
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.CarCatalog.Generation
import com.onezee.triptrack.model.CarCatalog.ModelHit
import com.onezee.triptrack.model.CarCatalog.Variant
import java.time.Year
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: VehicleCatalogModelSteps.swift.
enum class CatalogStep { model, generation, year, variant }
// iOS: VehicleCatalogPickerView.swift.
data class VehicleCatalogState(
    val query: String = "",
    val hits: List<ModelHit> = emptyList(),
    val hit: ModelHit? = null,
    val generations: List<Generation> = emptyList(),
    val generation: Generation? = null,
    val year: Int? = null,
    val years: List<Int> = emptyList(),
    val variants: List<Variant> = emptyList(),
    val step: CatalogStep = CatalogStep.model,
    val selection: VehicleCatalogSelection? = null,
)

// iOS: VehicleCatalogPickerView.swift. Cancellation rejects the result of an older query.
class VehicleCatalogViewModel(
    private val catalog: com.onezee.triptrack.logic.CarCatalog,
    private val type: VehicleType,
    dispatcher: CoroutineDispatcher,
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(VehicleCatalogState())
    val state = mutableState.asStateFlow()
    private var searchTask: Job? = null
    init { search("") }
    fun search(query: String) {
        mutableState.update { it.copy(query = query) }
        searchTask?.cancel()
        searchTask = viewModelScope.launch {
            val hits = catalog.search(query)
            ensureActive()
            mutableState.update { it.copy(hits = hits) }
        }
    }
    fun chooseModel(hit: ModelHit) { viewModelScope.launch {
        val generations = catalog.generations(hit.model.id, hit.matchedYear)
        mutableState.update { it.copy(hit = hit, year = hit.matchedYear, generation = null, generations = generations, step = CatalogStep.generation) }
    } }
    fun chooseGeneration(generation: Generation) {
        val current = Year.now().value
        val start = generation.yearStart ?: current
        val end = minOf(generation.yearEnd ?: current, current)
        val years = if (start <= end) (start..end).reversed().toList() else emptyList()
        mutableState.update { it.copy(generation = generation, years = years,
            year = it.hit?.matchedYear?.takeIf { value -> value in years }, step = CatalogStep.year) }
    }
    fun chooseYear(year: Int) { viewModelScope.launch {
        val generation = state.value.generation ?: return@launch
        if (year !in state.value.years) return@launch
        val variants = catalog.variants(generation.id, year)
        mutableState.update { it.copy(year = year, variants = variants, step = CatalogStep.variant) }
    } }
    fun chooseVariant(variant: Variant?) {
        val value = state.value
        val hit = value.hit ?: return
        val generation = value.generation ?: return
        val year = value.year ?: return
        mutableState.update { it.copy(selection = catalogSelection(hit, generation, year, variant, type.rawValue)) }
    }
    fun back() { mutableState.update { it.copy(step = when (it.step) {
        CatalogStep.model, CatalogStep.generation -> CatalogStep.model
        CatalogStep.year -> CatalogStep.generation
        CatalogStep.variant -> CatalogStep.year
    }) } }
    fun yearBounds(): IntRange {
        val generation = state.value.generation
        val current = Year.now().value
        return (generation?.yearStart ?: current)..minOf(generation?.yearEnd ?: current, current)
    }
}

// iOS: VehicleEditFormView.swift. All picker choices stay local until Save.
data class VehicleEditState(
    val vehicle: Vehicle = Vehicle(),
    val odometerText: String = "",
    val initialOdometerText: String = "",
    val catalogSelection: VehicleCatalogSelection? = null,
    val showsCatalog: Boolean = false,
    val showsDashboardPicker: Boolean = false,
    val saving: Boolean = false,
    val savedId: UUID? = null,
    val failed: Boolean = false,
) { val canSave get() = vehicle.name.trim().isNotEmpty() && !saving }

// iOS: VehicleEditFormView.swift (passport, catalog, dashboard and manual odometer fields).
class VehicleEditViewModel(
    vehicle: Vehicle?,
    private val vehicleStore: VehicleStore,
    private val appUnit: DistanceUnit,
    dispatcher: CoroutineDispatcher,
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val existing = vehicle
    private val initial = vehicle ?: Vehicle()
    private val text = OdometerField.fieldText(initial.manualOdometerKm, initial.dashboardUnit(appUnit))
    private val mutableState = MutableStateFlow(VehicleEditState(initial.copy(), text, text))
    val state = mutableState.asStateFlow()
    fun setName(name: String) { mutableState.update { it.copy(vehicle = it.vehicle.copy(name = name)) } }
    fun setOdometer(text: String) { mutableState.update { it.copy(odometerText = text.filter { char -> char in '0'..'9' }) } }
    fun setDashboardUnits(units: DashboardUnits) {
        mutableState.update { old ->
            val previous = old.vehicle.dashboardUnit(appUnit)
            val next = units.resolved(appUnit)
            old.copy(vehicle = old.vehicle.copy(dashboardUnits = units),
                odometerText = OdometerField.fieldText(OdometerField.storedKm(old.odometerText, previous), next),
                initialOdometerText = OdometerField.fieldText(OdometerField.storedKm(old.initialOdometerText, previous), next))
        }
    }
    fun selectCatalog(selection: VehicleCatalogSelection) { mutableState.update { it.copy(catalogSelection = selection.copy(),
        vehicle = it.vehicle.copy(make = selection.make, model = selection.model, year = selection.year ?: it.vehicle.year, bodyType = selection.body), showsCatalog = false) } }
    fun setPassport(make: String, model: String, year: Int, body: String, about: String) { mutableState.update {
        it.copy(vehicle = it.vehicle.copy(make = make, model = model, year = year, bodyType = body, about = about),
            catalogSelection = VehicleCatalogSelection(make, model, body, year, "", "", ""))
    } }
    fun setType(type: VehicleType) { mutableState.update { it.copy(vehicle = it.vehicle.copy(type = type, avatarStyle = catalogBody(null, type.rawValue), bodyType = catalogBody(null, type.rawValue)),
        catalogSelection = VehicleCatalogSelection(it.vehicle.make, it.vehicle.model, catalogBody(null, type.rawValue), it.vehicle.year, "", "", "")) } }
    fun showCatalog(show: Boolean) { mutableState.update { it.copy(showsCatalog = show) } }
    fun showDashboardPicker(show: Boolean) { mutableState.update { it.copy(showsDashboardPicker = show) } }
    fun save() {
        if (!state.value.canSave) return
        val snapshot = state.value
        mutableState.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                val vehicle = snapshot.vehicle.copy(name = snapshot.vehicle.name.trim())
                val id = existing?.id ?: vehicleStore.addVehicle(vehicle.name, vehicle.avatarEmoji, vehicle.avatarStyle, vehicle.type,
                    vehicle.plate, vehicle.plateVisible, vehicle.visibleToOthers)
                if (existing != null) vehicleStore.updateVehicleIdentity(id, vehicle.name, vehicle.avatarEmoji, vehicle.type,
                    vehicle.plate, vehicle.plateVisible, vehicle.visibleToOthers, vehicle.avatarStyle)
                vehicleStore.updateVehiclePassport(id, vehicle.make, vehicle.model, vehicle.year, vehicle.bodyType, vehicle.about)
                if (existing?.dashboardUnits != vehicle.dashboardUnits) vehicleStore.setDashboardUnits(id, vehicle.dashboardUnits)
                // Re-parsing rounded miles would create an edit on every name-only save.
                if (snapshot.odometerText.trim() != snapshot.initialOdometerText)
                    vehicleStore.setManualOdometer(id, OdometerField.storedKm(snapshot.odometerText, vehicle.dashboardUnit(appUnit)))
                snapshot.catalogSelection?.let { selection -> vehicleStore.updateVehicleGarage(id,
                    catalogModelId = selection.catalogModelId ?: "", catalogGenerationId = selection.catalogGenerationId ?: "", catalogVariantId = selection.catalogVariantId ?: "") }
                mutableState.update { it.copy(saving = false, savedId = id) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(saving = false, failed = true) } }
        }
    }
}
