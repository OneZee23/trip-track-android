// iOS: PlacesView's @State (stop, stopBeforeSelection, stopBeforeSearch, selectedPlaceId, selectedHintId)
package com.onezee.triptrack.feature.places

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onezee.triptrack.logic.id
import java.util.UUID

/**
 * Where the Places panel stands and what is chosen on its map. ONE source of truth: a pin's choice
 * and the empty tab write here too and keep no state of their own: the tab has ONE panel (spec
 * principle 1). Held by the feature, like the tab's stack, so it survives a tab switch.
 */
@Stable
class PlacesPanelModel {
    /** Null until the first frame knows the screen and the library. */
    var stop: PlacesPanelStop? by mutableStateOf(null)
    var stopBeforeSelection: PlacesPanelStop by mutableStateOf(PlacesPanelStop.half)
        private set
    var stopBeforeSearch: PlacesPanelStop by mutableStateOf(PlacesPanelStop.list)
        private set
    var selectedPlace: UUID? by mutableStateOf(null)
        private set
    var selectedHint: UUID? by mutableStateOf(null)
        private set

    val hasSelection: Boolean get() = selectedPlace != null || selectedHint != null

    fun clearSelection() {
        selectedPlace = null
        selectedHint = null
    }

    /** The switch does not change screens, it moves the panel (spec §3.2). */
    fun move(target: PlacesPanelStop) {
        clearSelection()
        stop = target
    }

    /**
     * A pin's tap CHANGES THE PANEL, it opens no screen: the row in the panel does, so a missed pin
     * does not carry the person off the tab. A suggestion has its card with «Сохранить как место».
     */
    fun selectPin(id: UUID, state: PlacesTabState) {
        val isPlace = state.items.any { it.place.id == id }
        val isHint = state.knownSuggestions.any { it.id == id }
        if (!isPlace && !isHint) return
        stop?.takeIf { it.isDraggable }?.let { stopBeforeSelection = it }
        selectedPlace = if (isPlace) id else null
        selectedHint = if (isPlace) null else id
        stop = if (isPlace) PlacesPanelStop.selectedPlace else PlacesPanelStop.selectedHint
    }

    /** The cross on the chosen pin's row: back to where the panel stood. */
    fun closeSelection() {
        clearSelection()
        stop = stopBeforeSelection
    }

    /** Focus in the field IS the entry into the search (state 10): the panel goes to the list. */
    fun beginSearch() {
        stopBeforeSearch = stop?.takeIf { it.isDraggable } ?: PlacesPanelStop.list
        clearSelection()
        stop = PlacesPanelStop.list
    }

    /** «Отмена» and the scrim: back to where the search came from. */
    fun endSearch() {
        stop = stopBeforeSearch
    }
}
