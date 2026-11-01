// iOS: TripManager.swift (nameCheckpointFromPlace, applyPlaceName)
package com.onezee.triptrack.location.world

import com.onezee.triptrack.data.PlaceManager
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.logic.CheckpointDefaultName
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.logic.coordinate
import com.onezee.triptrack.logic.hasCoordinate
import com.onezee.triptrack.model.TripCheckpoint
import java.util.UUID
import kotlinx.coroutines.*

/** A warm cache names before registration; a network answer must still respect a later hand edit. */
class CheckpointNamer(
    private val repository: TripRepository,
    private val places: PlaceManager,
    private val geocoder: ReverseGeocoder,
    private val checkpointWords: () -> Collection<String>,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun name(checkpoint: TripCheckpoint, tripId: UUID): Unit = withContext(io) {
        if (!checkpoint.hasCoordinate) return@withContext
        val cell = GeohashEncoder.encode(checkpoint.latitude, checkpoint.longitude, 5)
        val cached = repository.cachedLocality(cell)
        if (!cached.isNullOrEmpty()) {
            apply(cached, checkpoint.id, tripId)
        } else {
            // The button is used while driving; never wait for the platform geocoder here.
            scope.launch(io) {
                val mark = try { geocoder.placemark(checkpoint.coordinate) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                if (mark == null) return@launch
                val name = (mark.locality ?: mark.subLocality ?: mark.name)?.takeIf { it.isNotEmpty() }
                    ?: return@launch
                repository.saveGeocode(cell, mark.locality, mark.administrativeArea)
                apply(name, checkpoint.id, tripId)
            }
        }
        Unit
    }

    private suspend fun apply(name: String, checkpointId: UUID, tripId: UUID) {
        val checkpoint = repository.fetchTripDetail(tripId)?.checkpoints?.firstOrNull { it.id == checkpointId } ?: return
        if (!CheckpointDefaultName.looksLikeDefaultCheckpointName(checkpoint.name.orEmpty(), checkpointWords())) return
        repository.updateCheckpoint(checkpointId, name, checkpoint.photoId, checkpoint.photoIds)
        places.adoptName(name, checkpointId, tripId)
    }
}
