// iOS: AtlasSlot.swift, AtlasSheetDetent.swift, AtlasSheet.swift
package com.onezee.triptrack.feature.atlas

// iOS: AtlasSheetDetent.swift
enum class AtlasSheetDetent { peek, collapsed, expanded }

internal fun atlasOwnsBack(tripStackCanPop: Boolean, hasSelection: Boolean, hasHome: Boolean, detent: AtlasSheetDetent): Boolean =
    !tripStackCanPop && (hasSelection || hasHome || detent == AtlasSheetDetent.expanded)

// iOS: AtlasSheetDetent.swift
enum class AtlasSummaryVariant(val contentHeight: Double) {
    summary(220.0), shortCard(212.0), emptyAtlas(230.0), withNoticeRow(276.0), region(298.0)
}

// iOS: AtlasSlot.swift. Coordinates start at the screen edge, not its safe area.
data class AtlasSlot(val height: Double, val safeTop: Double, val safeBottom: Double) {
    val tabBarTop get() = height - 22 - 68
    fun collapsedTop(variant: AtlasSummaryVariant = AtlasSummaryVariant.summary) = tabBarTop - variant.contentHeight
    val peekTop get() = tabBarTop - 100
    val expandedTop get() = safeTop + 56
    fun top(detent: AtlasSheetDetent, variant: AtlasSummaryVariant = AtlasSummaryVariant.summary) = when (detent) {
        AtlasSheetDetent.peek -> peekTop
        AtlasSheetDetent.collapsed -> collapsedTop(variant)
        AtlasSheetDetent.expanded -> expandedTop
    }
    val showsPeekRow get() = height >= 700
    val searchTop get() = safeTop + 8
    val modalBottomPadding get() = if (safeBottom > 0) safeBottom else 16.0
    fun attributionAnchor(detent: AtlasSheetDetent, variant: AtlasSummaryVariant = AtlasSummaryVariant.summary) =
        if (detent == AtlasSheetDetent.peek) peekTop else collapsedTop(variant)
    fun attributionInset(detent: AtlasSheetDetent, variant: AtlasSummaryVariant = AtlasSummaryVariant.summary) =
        height - attributionAnchor(detent, variant) + attributionGap
    fun settleBoundary(variant: AtlasSummaryVariant = AtlasSummaryVariant.summary) =
        collapsedTop(variant).let { it - (it - expandedTop) * 0.45 }
    fun clamped(top: Double, lowerBound: AtlasSheetDetent = AtlasSheetDetent.collapsed,
                variant: AtlasSummaryVariant = AtlasSummaryVariant.summary): Double {
        val lower = top(lowerBound, variant)
        return when {
            top < expandedTop -> expandedTop - (expandedTop - top) * rubber
            top > lower -> lower + (top - lower) * rubber
            else -> top
        }
    }
    fun substateTop(top: Double) = if (top < expandedTop) expandedTop - (expandedTop - top) * rubber else top
    val substateCeiling get() = tabBarTop - 334
    companion object {
        const val flickVelocity = 300.0
        const val rubber = 0.3
        const val dismissDrop = 72.0
        const val controlsGap = 12.0
        const val attributionGap = 8.0
        fun settle(top: Double, velocity: Double, slot: AtlasSlot,
                   variant: AtlasSummaryVariant = AtlasSummaryVariant.summary) = when {
            velocity < -flickVelocity -> AtlasSheetDetent.expanded
            velocity > flickVelocity -> AtlasSheetDetent.collapsed
            top <= slot.settleBoundary(variant) -> AtlasSheetDetent.expanded
            else -> AtlasSheetDetent.collapsed
        }
        fun substateDismisses(top: Double, velocity: Double, slot: AtlasSlot, variant: AtlasSummaryVariant): Boolean {
            val rest = slot.collapsedTop(variant)
            if (top <= rest) return false
            if (velocity > flickVelocity) return true
            if (velocity < -flickVelocity) return false
            return top - rest > dismissDrop
        }
        fun controlsOpacity(slotTop: Double) = ((slotTop - 240) / 60).coerceIn(0.0, 1.0)
    }
}

// iOS: AtlasSheet.swift (containerTop). A UI must offset this inside its safe-area container.
object AtlasSheet {
    fun containerTop(screenTop: Double, safeTop: Double) = screenTop - safeTop
    fun height(screenTop: Double, screenHeight: Double) = (screenHeight - screenTop).coerceAtLeast(0.0)
}
