// iOS: TripRevealedBlock.swift, TripDiscoveries.swift, DiscoveriesAvailability.swift.
package com.onezee.triptrack.recording

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.TripDiscoveries
import kotlin.math.abs
import kotlin.math.cos

object DiscoveriesAvailability { const val isEnabled = false }

object TripRevealedLine {
    const val separator = "\u00A0· "
    fun compose(strings: AppStrings, km: String?, secrets: Int, riddles: Int, milestones: Int): String = buildList {
        if (!km.isNullOrEmpty()) add(strings.revealedNewPath(km))
        if (secrets > 0) add("$secrets ${strings.nounSecrets(secrets)}")
        if (riddles > 0) add("$riddles ${strings.nounRiddles(riddles)}")
        if (milestones > 0) add("$milestones ${strings.nounMilestones(milestones)}")
    }.joinToString(separator)
}

fun TripDiscoveries.visible(enabled: Boolean = DiscoveriesAvailability.isEnabled): TripDiscoveries =
    if (enabled) this else copy(secrets = emptyList(), riddles = emptyList(), milestones = emptyList())

val TripDiscoveries.isEmpty: Boolean get() = newKm < .5 && newRegionIds.isEmpty() && secrets.isEmpty() && riddles.isEmpty() && milestones.isEmpty()

/** iOS SealPainter.ring(for: .milestone): #F0A070. */
private val MILESTONE_RING = Color(0xFFF0A070)

@Composable
fun TripRevealedBlock(discoveries: TripDiscoveries, modifier: Modifier = Modifier) {
    val visible = discoveries.visible()
    if (visible.isEmpty) return
    val strings = appStrings
    val km = visible.newKm.takeIf { it >= .5 }?.let {
        Measure.distance(it * 1000, LocalDistanceUnit.current, strings.language.code, strings, Measure.Style.adaptive)
    }
    Column(modifier.fillMaxWidth().summaryCard(16.dp).padding(14.dp).testTag("summary_revealed"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The seal in the colour of the find's kind; with no finds (they are hidden) the milestone's warm one.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RecordingGlyphIcon(RecordingGlyph.sealFill, RecordingInk.sealFillSemibold14, MILESTONE_RING, Modifier.testTag("summary_revealed_seal"))
            Text(strings.revealedTitle(), style = inter(15f, InterWeight.heavy), color = AppTheme.colors.text)
        }
        val line = TripRevealedLine.compose(strings, km, visible.secrets.size, visible.riddles.size, visible.milestones.size)
        if (line.isNotEmpty()) Text(line, style = inter(13f, InterWeight.semibold), color = AppTheme.colors.textSecondary)
    }
}

// iOS: RevealSweep. Its clock is explicit; resuming an interrupted sweep never rewinds it.
class RevealSweep {
    var progress = 0.0
        private set
    private var startedAt: Double? = null
    fun begin(reduceMotion: Boolean, now: Double): Boolean {
        if (progress >= 1 || startedAt != null) return false
        if (reduceMotion) { progress = 1.0; return false }
        startedAt = now
        return true
    }
    fun advance(now: Double) {
        val start = startedAt ?: return
        progress = maxOf(progress, value(now - start))
        if (progress >= 1) cancel()
    }
    fun cancel() { startedAt = null }
    companion object {
        const val duration = .7
        const val crossfade = .3
        fun value(elapsed: Double, duration: Double = this.duration): Double = if (duration <= 0) 1.0 else (elapsed / duration).coerceIn(0.0, 1.0)
        fun axis(coordinates: List<GeoPoint>): Pair<Offset, Offset> {
            val first = coordinates.firstOrNull() ?: return Offset(0f, .5f) to Offset(1f, .5f)
            val last = coordinates.last()
            val dx = (last.longitude - first.longitude) * cos(Math.toRadians(first.latitude))
            val dy = first.latitude - last.latitude
            val scale = maxOf(abs(dx), abs(dy))
            if (scale <= 1e-9) return Offset(0f, .5f) to Offset(1f, .5f)
            return Offset((.5 - dx / scale / 2).toFloat(), (.5 - dy / scale / 2).toFloat()) to
                Offset((.5 + dx / scale / 2).toFloat(), (.5 + dy / scale / 2).toFloat())
        }
        fun stops(progress: Double): Pair<Float, Float> {
            val head = (progress.coerceAtLeast(0.0) * 1.15).coerceAtMost(1.15)
            val clear = (head - .15).coerceIn(0.0, 1.0)
            return clear.toFloat() to head.coerceIn(clear, 1.0).toFloat()
        }
    }
}
