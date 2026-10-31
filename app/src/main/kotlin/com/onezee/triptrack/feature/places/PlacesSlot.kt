// iOS: PlacesSlot.swift, PlacesScreenState.swift, SlotGesture.swift
package com.onezee.triptrack.feature.places

/**
 * iOS: PlacesPanelStop. The panel's positions, top to bottom. Three are reached by the finger, two by
 * a pin's choice, one by the empty tab, which does not drag at all.
 */
enum class PlacesPanelStop {
    /** The whole list, scrolling inside. The highest. */
    list,
    /** The empty tab: the example, «Мест пока нет», «Отметить в поездке». */
    empty,
    /** By default: the title, «Мои места», the suggestions. */
    half,
    /** The card of the chosen suggestion under the title. */
    selectedHint,
    /** The row of the chosen place under the title. */
    selectedPlace,
    /** The title only: the map at full height. */
    map;

    val isDraggable: Boolean get() = this in draggable

    companion object {
        /** What the finger reaches; the screen sets the rest. */
        val draggable = listOf(list, half, map)
    }
}

/**
 * iOS: SlotGesture. The panel's gesture rules, ONE set for Atlas and Places: the gesture is learnt
 * once and carried from tab to tab.
 */
object SlotGesture {
    /** A release faster than this goes to the NEIGHBOUR in the gesture's direction. */
    const val flickVelocity = 300.0

    /** Resistance past a bound: the panel moves at a third of the finger. */
    const val rubber = 0.3

    /** 45 %, not half: opening the list is easier than closing it. */
    const val settleThreshold = 0.45

    /** The boundary between two neighbours; [from] is the lower (larger number), [to] the upper. */
    fun boundary(from: Double, to: Double): Double = from - (from - to) * settleThreshold

    fun clamped(top: Double, lower: Double, upper: Double): Double = when {
        top < upper -> upper - (upper - top) * rubber
        top > lower -> lower + (top - lower) * rubber
        else -> top
    }
}

/**
 * iOS: PlacesSlot. The Places panel's geometry with no absolute coordinate: the low positions count
 * from the TOP of the tab bar's capsule, the high one from the safe area. The spec's table checks the
 * formulas, it is not their source. A twin of AtlasSlot on purpose: the tables differ, the gesture
 * rules are shared in [SlotGesture].
 */
data class PlacesSlot(val height: Double, val safeTop: Double, val safeBottom: Double) {
    /** iOS: `height − bottomGap − pillHeight`, 22 and 68 on every phone (decision of 0.8.1). */
    val tabBarTop: Double get() = height - 22 - 68

    fun top(stop: PlacesPanelStop): Double = if (stop == PlacesPanelStop.list) listTop else tabBarTop - contentHeight(stop)

    /** The list: safe area + 56. A strip of map stays above it. */
    val listTop: Double get() = safeTop + 56

    /** No HALF on an SE: between map and list there is no height that shows more than a row. */
    val showsHalf: Boolean get() = height >= 700

    /** The tab opens on the half, and on an SE on the list: on a small screen the list is more use. */
    val defaultStop: PlacesPanelStop get() = if (showsHalf) PlacesPanelStop.half else PlacesPanelStop.list

    /** The place screen's sheet at the start: lower on an SE (300 instead of 374). */
    val placeSheetTop: Double get() = tabBarTop - if (showsHalf) 374 else 300

    /** Scrolled, the place sheet goes under the blurred header up to the safe area. */
    val placeSheetScrolledTop: Double get() = safeTop

    val upperBound: Double get() = listTop
    val lowerBound: Double get() = top(PlacesPanelStop.map)

    fun clamped(top: Double): Double = SlotGesture.clamped(top, lowerBound, upperBound)

    /** The positions the finger reaches on THIS screen. */
    val reachableStops: List<PlacesPanelStop>
        get() = if (showsHalf) PlacesPanelStop.draggable else listOf(PlacesPanelStop.list, PlacesPanelStop.map)

    /**
     * Where a released panel stands. A flick faster than [SlotGesture.flickVelocity] goes to the
     * NEIGHBOUR in its direction, even against the nearest; otherwise the boundaries at 45 % decide.
     * The positions set by the screen take no part.
     */
    fun settle(top: Double, velocity: Double, from: PlacesPanelStop): PlacesPanelStop {
        val stops = reachableStops
        val current = stops.indexOf(from).takeIf { it >= 0 } ?: (stops.size / 2)
        if (velocity < -SlotGesture.flickVelocity) return stops[maxOf(0, current - 1)]
        if (velocity > SlotGesture.flickVelocity) return stops[minOf(stops.size - 1, current + 1)]
        // Boundaries between NEIGHBOURING reachable positions: on an SE there is one, between map
        // and list, not where the half would have been.
        for (index in stops.size - 1 downTo 1) {
            val lower = top(stops[index])
            val upper = top(stops[index - 1])
            if (top > SlotGesture.boundary(lower, upper)) return stops[index]
        }
        return stops[0]
    }

    /**
     * The map's bottom inset over the panel, NEVER over the list: the camera fits by the sum of
     * insets, and on the list the remainder would go negative, a frame of the whole world.
     */
    fun mapBottomInset(stop: PlacesPanelStop): Double {
        val anchor = if (stop == PlacesPanelStop.list) top(if (defaultStop == PlacesPanelStop.list) PlacesPanelStop.map else PlacesPanelStop.half) else top(stop)
        return height - anchor + 8
    }

    companion object {
        /** Each position's content height as a NUMBER: inside the panel a scroll answers «as much as you offer». */
        fun contentHeight(stop: PlacesPanelStop): Double = when (stop) {
            PlacesPanelStop.map -> 100.0
            PlacesPanelStop.selectedPlace -> 164.0
            PlacesPanelStop.selectedHint -> 232.0
            PlacesPanelStop.half -> 374.0
            PlacesPanelStop.empty -> 474.0
            PlacesPanelStop.list -> 0.0
        }
    }
}

/**
 * iOS: PlacesEmptyState. What the tab is empty of, and where it stands then. Suggestions do NOT take
 * part: they change what the panel holds, not its height.
 */
enum class PlacesEmptyState {
    notEmpty,
    /** No places, trips exist: the example, «Мест пока нет», «Отметить в поездке» and suggestions. */
    noPlaces,
    /** Not one trip: one sentence, no presses, at the default position. */
    noTrips;

    /** The empty states hold one position and do not drag. */
    val isPinned: Boolean get() = this != notEmpty

    fun stop(slot: PlacesSlot): PlacesPanelStop = when (this) {
        notEmpty -> slot.defaultStop
        noPlaces -> PlacesPanelStop.empty
        noTrips -> slot.defaultStop
    }

    companion object {
        fun resolve(places: Int, trips: Int): PlacesEmptyState = when {
            places != 0 -> notEmpty
            trips == 0 -> noTrips
            else -> noPlaces
        }
    }
}

/** iOS: PlacesSearchOutcome. What the panel shows while the person searches (states 10 and 11). */
enum class PlacesSearchOutcome {
    /** The field is open, nothing is searched yet: the list as it was. */
    idle,
    /** Matches, as rows right under the field, without a section heading. */
    matches,
    /** Nothing found: the magnifier, two lines, not one button. */
    nothingFound;

    companion object {
        /** Blanks are not a query: a field with one space is not a search yet. */
        fun resolve(query: String, matches: Int): PlacesSearchOutcome = when {
            query.isBlank() -> idle
            matches == 0 -> nothingFound
            else -> PlacesSearchOutcome.matches
        }
    }
}
