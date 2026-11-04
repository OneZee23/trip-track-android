// iOS: GarageView.swift, VehicleDetailView.swift
package com.onezee.triptrack.feature.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.*
import com.onezee.triptrack.feature.settings.WorldChange
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.logic.RegionAtlas
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: GarageView.swift.
data class GarageVehicle(val vehicle: Vehicle, val tripCount: Int, val mainPhoto: VehiclePhoto?)
// iOS: GarageView.swift.
data class GarageState(
    val loaded: Boolean = false,
    val vehicles: List<GarageVehicle> = emptyList(),
    val selectedVehicleId: UUID? = null,
    val active: GarageVehicle? = null,
    val others: List<GarageVehicle> = emptyList(),
    val sold: List<GarageVehicle> = emptyList(),
    val passportId: UUID? = null,
    val showsAddForm: Boolean = false,
    val deleteCandidateId: UUID? = null,
    val failed: Boolean = false,
)

// iOS: GarageView.swift.
class GarageViewModel(
    private val vehicleStore: VehicleStore,
    private val photoStore: VehiclePhotoStore,
    private val repository: TripRepository,
    dispatcher: CoroutineDispatcher,
    selectedVehicleId: StateFlow<UUID?>,
    events: Flow<WorldChange> = emptyFlow(),
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(GarageState())
    val state = mutableState.asStateFlow()
    private var loadTask: Job? = null
    init {
        viewModelScope.launch { selectedVehicleId.collect { id -> mutableState.update { sections(it.copy(selectedVehicleId = id)) } } }
        viewModelScope.launch { events.filter { it in setOf(WorldChange.garage, WorldChange.trips, WorldChange.sync) }.collect { reload() } }
        reload()
    }
    private fun sections(value: GarageState): GarageState {
        val active = value.vehicles.firstOrNull { it.vehicle.id == value.selectedVehicleId && it.vehicle.soldAt == null }
        return value.copy(active = active,
            others = value.vehicles.filter { it.vehicle.soldAt == null && it.vehicle.id != active?.vehicle?.id }.sortedByDescending { it.vehicle.displayOdometerKm },
            sold = value.vehicles.filter { it.vehicle.soldAt != null }.sortedByDescending { it.vehicle.displayOdometerKm })
    }
    fun reload() {
        loadTask?.cancel()
        loadTask = viewModelScope.launch {
            try {
                val vehicles = vehicleStore.vehicles()
                val photos = photoStore.mainPhotos()
                val counts = repository.fetchAllTrips().filter { !it.isTransfer }.groupingBy { it.vehicleId }.eachCount()
                ensureActive()
                mutableState.update { sections(it.copy(loaded = true, vehicles = vehicles.map { car -> GarageVehicle(car, counts[car.id] ?: 0, photos[car.id]) }, failed = false)) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    }
    fun openPassport(id: UUID) { mutableState.update { it.copy(passportId = id) } }
    fun addVehicle() { mutableState.update { it.copy(showsAddForm = true) } }
    fun dismiss() { mutableState.update { it.copy(passportId = null, showsAddForm = false, deleteCandidateId = null) } }
    fun askDelete(id: UUID) { mutableState.update { it.copy(deleteCandidateId = id) } }
    fun confirmDelete(id: UUID? = state.value.deleteCandidateId) {
        id ?: return
        mutableState.update { it.copy(deleteCandidateId = null) }
        viewModelScope.launch {
            try { vehicleStore.deleteVehicle(id); reload() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    }
}

// iOS: VehicleDetailView.Passport.
data class VehiclePassport(
    val tripCount: Int = 0,
    val regionCount: Int = 0,
    val daysOnRoad: Int = 0,
    val distanceMetres: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val recent: List<Trip> = emptyList(),
    val routes: List<List<GeoPoint>> = emptyList(),
    val cityCount: Int = 0,
    val regions: List<MapRegionStat> = emptyList(),
    val years: List<VehicleYearDistance> = emptyList(),
    val photos: List<TripPhoto> = emptyList(),
    val highestTrip: Trip? = null,
    val longestTrip: Trip? = null,
    val maxDayDistanceMetresThisYear: Double = 0.0,
    val maxDayDurationSeconds: Double = 0.0,
)
// iOS: VehicleDetailView.swift.
enum class VehicleSheet { edit, drive2, dashboardUnits, photos, tripPhotos, trips, map, autoRecord, level, delete }
// iOS: VehicleDetailView.swift.
data class VehiclePassportState(
    val vehicle: Vehicle? = null,
    val passport: VehiclePassport = VehiclePassport(),
    val mainPhoto: VehiclePhoto? = null,
    val catalogVariant: com.onezee.triptrack.model.CarCatalog.Variant? = null,
    val dashboardUnit: DistanceUnit = DistanceUnit.km,
    val sheet: VehicleSheet? = null,
    val loaded: Boolean = false,
    val failed: Boolean = false,
    val selectedVehicleId: UUID? = null,
)

// iOS: VehicleDetailView.swift.
class VehiclePassportViewModel(
    val vehicleId: UUID,
    private val vehicleStore: VehicleStore,
    private val photoStore: VehiclePhotoStore,
    private val repository: TripRepository,
    private val catalog: com.onezee.triptrack.logic.CarCatalog,
    dispatcher: CoroutineDispatcher,
    appUnit: StateFlow<DistanceUnit>,
    events: Flow<WorldChange> = emptyFlow(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Instant = Instant::now,
    private val atlasProvider: () -> RegionAtlas = { RegionAtlas.shared },
    selectedVehicleId: StateFlow<UUID?> = MutableStateFlow(null),
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(VehiclePassportState())
    val state = mutableState.asStateFlow()
    private var unit = appUnit.value
    private var loadTask: Job? = null
    init {
        viewModelScope.launch { selectedVehicleId.collect { id -> mutableState.update { it.copy(selectedVehicleId = id) } } }
        viewModelScope.launch { appUnit.collect { value -> unit = value; mutableState.update { it.copy(dashboardUnit = it.vehicle?.dashboardUnit(value) ?: value) } } }
        viewModelScope.launch { events.filter { it in setOf(WorldChange.trips, WorldChange.sync, WorldChange.garage) }.collect { load() } }
        load()
    }
    fun load() {
        loadTask?.cancel()
        loadTask = viewModelScope.launch {
            try {
                val vehicle = vehicleStore.vehicle(vehicleId)
                val mine = repository.fetchVehicleGarageTrips(vehicleId)
                mine.forEach { Trip.invalidatePreviewCache(it.id) }
                val routes = mine.map { it.previewCoordinates }
                val atlas = atlasProvider()
                val hashes = routes.flatten().mapTo(mutableSetOf()) { GeohashEncoder.encode(it.latitude, it.longitude, 6) }
                val exploration = MapExploration.build(mine, hashes, atlas)
                val days = mine.groupBy { it.startDate.atZone(zone).toLocalDate() }
                val currentYear = now().atZone(zone).year
                val passport = VehiclePassport(mine.size, exploration.regions.size,
                    mine.map { it.startDate.atZone(zone).toLocalDate() }.toSet().size, mine.sumOf { it.distance },
                    mine.sumOf { it.duration },
                    mine.take(3), routes.take(120).filter { it.size >= 2 }, exploration.regions.sumOf { it.visitedCityCount },
                    exploration.regions, vehicleYearDistances(mine, vehicleId, zone), mine.flatMap { it.photos },
                    mine.maxByOrNull { it.elevation }, mine.maxByOrNull { it.distance },
                    days.filterKeys { it.year == currentYear }.values.maxOfOrNull { day -> day.sumOf { it.distance } } ?: 0.0,
                    days.values.maxOfOrNull { day -> day.sumOf { it.duration } } ?: 0.0)
                val mainPhoto = photoStore.mainPhoto(vehicleId)
                val variant = vehicle?.let { catalog.resolve(it.catalogModelId, it.catalogGenerationId, it.catalogVariantId) }
                ensureActive()
                mutableState.update { it.copy(vehicle = vehicle, passport = passport, mainPhoto = mainPhoto, catalogVariant = variant,
                    dashboardUnit = vehicle?.dashboardUnit(unit) ?: unit, loaded = true, failed = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    }
    fun openSheet(sheet: VehicleSheet) { mutableState.update { it.copy(sheet = sheet) } }
    fun dismissSheet() { mutableState.update { it.copy(sheet = null) } }
    fun setDashboardUnits(units: DashboardUnits) = edit { vehicleStore.setDashboardUnits(vehicleId, units) }
    fun setDrive2(input: String): Boolean {
        val link = if (input.trim().isEmpty()) "" else com.onezee.triptrack.logic.Drive2Link.normalized(input) ?: return false
        edit { vehicleStore.updateVehicleGarage(vehicleId, drive2URL = link) }
        return true
    }
    fun setModTags(tags: List<String>) = edit { vehicleStore.updateVehicleGarage(vehicleId, modTags = VehicleModTags.sanitized(tags)) }
    fun setSold(at: Instant?) = edit { vehicleStore.setVehicleSold(vehicleId, at) }
    private fun edit(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); load() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
    } }
}
