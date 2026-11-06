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
    val legacyMakes: List<VehicleCatalog.Make> = emptyList(),
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
    private val legacy: LegacyVehicleCatalog = LegacyVehicleCatalog(),
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(VehicleCatalogState())
    val state = mutableState.asStateFlow()
    private var searchTask: Job? = null
    init { search("") }
    fun search(query: String) {
        mutableState.update { it.copy(query = query) }
        searchTask?.cancel()
        searchTask = viewModelScope.launch {
            val hits = if (type == VehicleType.car) catalog.search(query) else emptyList()
            val makes = legacy.search(query, type)
            ensureActive()
            mutableState.update { it.copy(hits = hits, legacyMakes = makes) }
        }
    }
    fun chooseLegacy(make: VehicleCatalog.Make, model: VehicleCatalog.Model) {
        mutableState.update { it.copy(selection = VehicleCatalogSelection(make.name, model.name, legacy.defaultBody(model, type))) }
    }
    fun chooseTyped() {
        val make = state.value.query.trim()
        if (make.isNotEmpty()) mutableState.update { it.copy(selection = VehicleCatalogSelection(make, "", VehicleAvatar.styles(type).first())) }
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
    val energy: VehicleEnergyDraft = VehicleEnergyDraft.from(vehicle, DistanceUnit.km),
) { val canSave get() = vehicle.name.trim().isNotEmpty() && !saving }

// iOS: VehicleEditFormView.swift (passport, catalog, dashboard and manual odometer fields).
class VehicleEditViewModel(
    vehicle: Vehicle?,
    private val vehicleStore: VehicleStore,
    private val appUnit: DistanceUnit,
    dispatcher: CoroutineDispatcher,
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val existing = vehicle
    private var persistedId = vehicle?.id
    private val initial = vehicle ?: Vehicle(avatarEmoji = "pixel_car_orange")
    private val text = OdometerField.fieldText(initial.manualOdometerKm, initial.dashboardUnit(appUnit))
    private val mutableState = MutableStateFlow(VehicleEditState(initial.copy(), text, text, energy = VehicleEnergyDraft.from(initial, initial.dashboardUnit(appUnit))))
    val state = mutableState.asStateFlow()
    fun setName(name: String) { mutableState.update { it.copy(vehicle = it.vehicle.copy(name = name)) } }
    fun setAvatar(color: String, style: String) { mutableState.update { it.copy(vehicle = it.vehicle.copy(avatarEmoji = "pixel_car_$color", avatarStyle = style)) } }
    fun setPlate(plate: String) { mutableState.update { it.copy(vehicle = it.vehicle.copy(plate = plate)) } }
    fun setPowertrain(powertrain: Powertrain) { mutableState.update { it.copy(vehicle = it.vehicle.copy(powertrain = powertrain)) } }
    fun setCurrency(symbol: String) { mutableState.update { it.copy(vehicle = it.vehicle.copy(fuelCurrency = symbol)) } }
    fun setCardStyle(style: VehicleCardStyle) { mutableState.update { it.copy(vehicle = it.vehicle.copy(cardStyle = style.rawValue.takeIf(String::isNotEmpty))) } }
    fun setFinish(finish: VehicleFinish) { mutableState.update { it.copy(vehicle = it.vehicle.copy(paintFinish = finish.rawValue)) } }
    fun setModTags(tags: List<String>) { mutableState.update { it.copy(vehicle = it.vehicle.copy(modTags = tags.take(3))) } }
    internal fun setEnergy(field: VehicleEnergyField, text: String) { mutableState.update { it.copy(energy = it.energy.edit(field, text, it.vehicle.dashboardUnit(appUnit))) } }
    fun setVisibility(visible: Boolean = state.value.vehicle.visibleToOthers, plate: Boolean = state.value.vehicle.plateVisible,
                      map: Boolean = state.value.vehicle.mapVisible, photos: Boolean = state.value.vehicle.photosVisible) {
        mutableState.update { it.copy(vehicle = it.vehicle.copy(visibleToOthers = visible, plateVisible = plate, mapVisible = map, photosVisible = photos)) }
    }
    fun setOdometer(text: String) { mutableState.update { it.copy(odometerText = text.filter { char -> char in '0'..'9' }) } }
    fun setDashboardUnits(units: DashboardUnits) {
        mutableState.update { old ->
            val previous = old.vehicle.dashboardUnit(appUnit)
            val next = units.resolved(appUnit)
            old.copy(vehicle = old.vehicle.copy(dashboardUnits = units),
                energy = old.energy.converted(previous, next),
                odometerText = OdometerField.fieldText(OdometerField.storedKm(old.odometerText, previous), next),
                initialOdometerText = OdometerField.fieldText(OdometerField.storedKm(old.initialOdometerText, previous), next))
        }
    }
    fun selectCatalog(selection: VehicleCatalogSelection) { mutableState.update { it.copy(catalogSelection = selection.copy(),
        vehicle = it.vehicle.copy(make = selection.make, model = selection.model, year = selection.year ?: it.vehicle.year, bodyType = selection.body,
            avatarStyle = selection.body), showsCatalog = false) } }
    fun setPassport(make: String, model: String, year: Int, body: String, about: String) { mutableState.update {
        val changed = it.vehicle.make != make || it.vehicle.model != model || it.vehicle.year != year || it.vehicle.bodyType != body
        it.copy(vehicle = it.vehicle.copy(make = make, model = model, year = year, bodyType = body, about = about),
            catalogSelection = if (changed) VehicleCatalogSelection(make, model, body, year, "", "", "") else it.catalogSelection)
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
                val id = persistedId ?: run {
                    check(vehicleStore.vehicles().count { it.soldAt == null } < 5)
                    vehicleStore.addVehicle(vehicle.name, vehicle.avatarEmoji, vehicle.avatarStyle, vehicle.type,
                        vehicle.plate, vehicle.plateVisible, vehicle.visibleToOthers).also { persistedId = it }
                }
                vehicleStore.updateVehicleIdentity(id, vehicle.name, vehicle.avatarEmoji, vehicle.type,
                    vehicle.plate, vehicle.plateVisible, vehicle.visibleToOthers, vehicle.avatarStyle)
                if (existing?.cardStyle != vehicle.cardStyle) vehicleStore.setCardStyle(id, VehicleCardStyle.fromRawValue(vehicle.cardStyle.orEmpty()) ?: VehicleCardStyle.none)
                if (existing?.paintFinish != vehicle.paintFinish) vehicleStore.updateVehicleGarage(id, null, null, VehicleFinish.effective(vehicle.paintFinish, true), null, null, null, null)
                if (existing?.modTags != vehicle.modTags) vehicleStore.updateVehicleGarage(id, modTags = VehicleModTags.sanitized(vehicle.modTags))
                vehicleStore.updateVehiclePassport(id, vehicle.make, vehicle.model, vehicle.year, vehicle.bodyType, vehicle.about)
                if (existing == null || existing.visibleToOthers != vehicle.visibleToOthers || existing.plateVisible != vehicle.plateVisible || existing.mapVisible != vehicle.mapVisible || existing.photosVisible != vehicle.photosVisible)
                    vehicleStore.updateVehicleVisibility(id, vehicle.visibleToOthers, vehicle.plateVisible, vehicle.mapVisible, vehicle.photosVisible)
                if (existing?.dashboardUnits != vehicle.dashboardUnits) vehicleStore.setDashboardUnits(id, vehicle.dashboardUnits)
                if (existing?.fuelCurrency != vehicle.fuelCurrency) vehicleStore.updateVehicleCurrency(id, vehicle.fuelCurrency)
                if (existing?.powertrain != vehicle.powertrain) vehicleStore.setPowertrain(id, vehicle.powertrain)
                val draft = snapshot.energy
                val fuelUnit = ConsumptionUnit.forDashboard(vehicle.dashboardUnit(appUnit))
                val electricUnit = ElectricUnit.forDashboard(vehicle.dashboardUnit(appUnit))
                fun number(field: VehicleEnergyField) = VehicleEnergyDraft.number(draft.text(field))
                if (listOf(VehicleEnergyField.city, VehicleEnergyField.highway, VehicleEnergyField.price).any(draft::changed))
                    vehicleStore.updateVehicleFuel(id,
                        if (draft.changed(VehicleEnergyField.city)) number(VehicleEnergyField.city)?.let(fuelUnit::toPer100) ?: vehicle.cityConsumption else vehicle.cityConsumption,
                        if (draft.changed(VehicleEnergyField.highway)) number(VehicleEnergyField.highway)?.let(fuelUnit::toPer100) ?: vehicle.highwayConsumption else vehicle.highwayConsumption,
                        if (draft.changed(VehicleEnergyField.price)) number(VehicleEnergyField.price)?.let(fuelUnit::priceToPerLitre) ?: vehicle.fuelPrice else vehicle.fuelPrice)
                if (listOf(VehicleEnergyField.electricConsumption, VehicleEnergyField.electricityPrice, VehicleEnergyField.electricRange).any(draft::changed))
                    vehicleStore.updateVehicleEnergy(id,
                        if (draft.changed(VehicleEnergyField.electricConsumption)) number(VehicleEnergyField.electricConsumption)?.let(electricUnit::toPer100) ?: 0.0 else vehicle.electricConsumption,
                        if (draft.changed(VehicleEnergyField.electricityPrice)) number(VehicleEnergyField.electricityPrice) ?: 0.0 else vehicle.electricityPrice,
                        if (draft.changed(VehicleEnergyField.electricRange)) OdometerField.storedKm(draft.text(VehicleEnergyField.electricRange), vehicle.dashboardUnit(appUnit)) ?: 0.0 else vehicle.electricRangeKm)
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
