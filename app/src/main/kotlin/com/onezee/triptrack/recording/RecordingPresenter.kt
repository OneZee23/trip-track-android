// iOS: MapViewModel.swift (recording presentation and finish confirmation).
package com.onezee.triptrack.recording

import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.location.recording.FinishConfirmation
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.recording.StartResult
import com.onezee.triptrack.logic.coordinate
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.map.route.RouteShape
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RecordingFinishPreview
import com.onezee.triptrack.model.TrackPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// iOS: TrackingView.swift, MapViewModel.swift. SI values cross the boundary into Measure at the view.
data class TrackingState(
    val recorder: RecorderState = RecorderState(),
    val route: RouteShape = RouteShape.empty,
    val coordinates: List<GeoPoint> = emptyList(),
    val head: List<GeoPoint> = emptyList(),
    val totalMetres: Double = 0.0,
    val tripCount: Int = 0,
    val pendingTransfer: Boolean = false,
    val refusal: StartRefusal? = null,
    val stopPreview: RecordingFinishPreview? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
)

class RecordingPresenter(
    private val location: LocationGraph,
    private val trips: TripRepository,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(TrackingState(recorder = location.recorder.state.value))
    val state = mutable.asStateFlow()

    init {
        scope.launch {
            location.recorder.state.collect { recorder ->
                mutable.update { it.copy(recorder = recorder,
                    stopPreview = it.stopPreview.takeIf { recorder.isRecording }) }
            }
        }
        scope.launch {
            location.recorder.track.collectLatest { points ->
                val route = withContext(Dispatchers.Default) { recordingRoute(points) }
                val tail = points.lastOrNull()?.recordingSegmentIndex
                mutable.update { it.copy(route = route, coordinates = points.map { point -> point.coordinate },
                    head = points.takeLast(5).filter { point -> point.recordingSegmentIndex == tail }.map { point -> point.coordinate }) }
            }
        }
    }

    fun refresh() { scope.launch {
        try {
            val metres = trips.fetchTotalDistance()
            val count = trips.fetchTripCount()
            mutable.update { it.copy(totalMetres = metres, tripCount = count) }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(failed = true) } }
    } }

    fun setTransfer(value: Boolean) { mutable.update { it.copy(pendingTransfer = value) } }
    fun clearRefusal() { mutable.update { it.copy(refusal = null) } }
    fun clearError() { mutable.update { it.copy(failed = false) } }
    fun blockedStart() { mutable.update { it.copy(refusal = StartRefusal.noFix) } }

    fun start(force: Boolean = false) {
        if (mutable.value.busy || location.recorder.state.value.isRecording) return
        if (!force && location.hasLocationPermission() && location.recorder.state.value.gpsAccuracy <= 0) {
            blockedStart()
            return
        }
        val transfer = mutable.value.pendingTransfer
        operation {
            when (val result = location.startRecording(isTransfer = transfer)) {
                is StartResult.Refused -> mutable.update { it.copy(refusal = result.reason) }
                is StartResult.Started -> mutable.update { it.copy(refusal = null, pendingTransfer = false) }
            }
        }
    }

    fun pause() = operation { location.togglePause() }
    fun checkpoint() = operation {
        if (location.recorder.markCheckpoint() == null) mutable.update { it.copy(failed = true) }
    }
    fun askFinish() = operation {
        val preview = location.recorder.finishPreview()
        mutable.update { it.copy(stopPreview = preview) }
    }
    fun cancelFinish() { mutable.update { it.copy(stopPreview = null) } }
    fun pauseInstead() { cancelFinish(); pause() }
    fun finish() {
        val preview = mutable.value.stopPreview ?: return
        operation {
            when (val result = location.recorder.stopConfirming(preview)) {
                is FinishConfirmation.Changed -> mutable.update { it.copy(stopPreview = result.preview) }
                is FinishConfirmation.Finished -> { cancelFinish(); refresh() }
            }
        }
    }

    private fun operation(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, failed = false) }
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(failed = true) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}

// iOS: RouteMapView.swift. Manual pause boundaries never become a line across the parked stretch.
fun recordingRoute(points: List<TrackPoint>): RouteShape {
    val shapes = points.groupBy { it.recordingSegmentIndex }.values.map { run ->
        RouteGeometry.build(run.map { it.coordinate }, run.map { it.speed })
    }
    val bounds = RouteGeometry.build(points.map { it.coordinate }, preview = true).bounds
    return RouteShape(shapes.flatMap { it.lines }, bounds)
}
