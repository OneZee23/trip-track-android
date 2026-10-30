package com.onezee.triptrack.map.fog

import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The segments of one level of detail as the GPU sees them, grouped by chunk (iOS: FogChunk and the
 * per-LOD arrays of FogMesh).
 *
 * Points are OFFSETS from their chunk's corner, not map points: a map point reaches 2.7e8 and Float32
 * carries about seven digits, so absolute coordinates would make a corridor jitter by metres at street
 * zoom. Inside a chunk the offset is millimetre precise.
 *
 * - [segments]: p0.x, p0.y, p1.x, p1.y per segment, Float32 offsets from the chunk origin;
 * - [chunks]: origin x, origin y, min x, min y, max x, max y per chunk, in map points (Double);
 * - [ranges]: first segment and segment count per chunk.
 */
class FogMeshLod internal constructor(
    val segments: FloatArray,
    val chunks: DoubleArray,
    val ranges: IntArray,
    val chunkSize: Double,
) {
    val segmentCount: Int get() = segments.size / 4
    val chunkCount: Int get() = ranges.size / 2
    val byteSize: Int get() = segments.size * Float.SIZE_BYTES

    fun originX(chunk: Int): Double = chunks[6 * chunk]
    fun originY(chunk: Int): Double = chunks[6 * chunk + 1]
    fun minX(chunk: Int): Double = chunks[6 * chunk + 2]
    fun minY(chunk: Int): Double = chunks[6 * chunk + 3]
    fun maxX(chunk: Int): Double = chunks[6 * chunk + 4]
    fun maxY(chunk: Int): Double = chunks[6 * chunk + 5]
    fun first(chunk: Int): Int = ranges[2 * chunk]
    fun count(chunk: Int): Int = ranges[2 * chunk + 1]

    companion object {
        val empty = FogMeshLod(FloatArray(0), DoubleArray(0), IntArray(0), FogRules.CHUNK_SIZE)
    }
}

/**
 * The revealed world on the GPU: a set of chunks per level of detail (iOS: FogMesh).
 *
 * Immutable: built off the main thread and replaced whole. Nothing is ever appended to a live buffer,
 * because a frame may be drawing it right now.
 */
class FogMesh(val fine: FogMeshLod, val mid: FogMeshLod, val far: FogMeshLod) {
    fun lod(lod: RevealedLayer.LOD): FogMeshLod = when (lod) {
        RevealedLayer.LOD.fine -> fine
        RevealedLayer.LOD.mid -> mid
        RevealedLayer.LOD.far -> far
    }

    val isEmpty: Boolean get() = RevealedLayer.LOD.entries.all { lod(it).segmentCount == 0 }

    val segmentCount: Int get() = RevealedLayer.LOD.entries.sumOf { lod(it).segmentCount }

    /**
     * Bytes of segment data across the three levels. Only the budget guard asks (iOS spec §7: 64 MB):
     * the number grows with the library, and its ceiling is what we agree to occupy.
     */
    val byteSize: Int get() = RevealedLayer.LOD.entries.sumOf { lod(it).byteSize }

    /** Chunks on a level: the frame's culling is a linear pass over them. */
    fun chunkCount(lod: RevealedLayer.LOD): Int = lod(lod).chunkCount

    companion object {
        val empty = FogMesh(FogMeshLod.empty, FogMeshLod.empty, FogMeshLod.empty)

        /**
         * Builds the buffers. Called OFF the main thread: a mature library is hundreds of thousands of
         * segments. [chunkSize] defaults to the Android sizes; the iOS size is a parameter so tests
         * can reproduce iOS chunk counts.
         */
        fun build(
            layer: RevealedLayer,
            chunkSize: (RevealedLayer.LOD) -> Double = FogRules::chunkSize,
        ): FogMesh = FogMesh(
            buildLod(layer.polylines(RevealedLayer.LOD.fine), chunkSize(RevealedLayer.LOD.fine)),
            buildLod(layer.polylines(RevealedLayer.LOD.mid), chunkSize(RevealedLayer.LOD.mid)),
            buildLod(layer.polylines(RevealedLayer.LOD.far), chunkSize(RevealedLayer.LOD.far)),
        )

        private class Bucket(val originX: Double, val originY: Double) {
            var floats = FloatArray(64)
            var size = 0
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY

            fun add(ax: Double, ay: Double, bx: Double, by: Double) {
                if (size + 4 > floats.size) floats = floats.copyOf(floats.size * 2)
                floats[size] = (ax - originX).toFloat()
                floats[size + 1] = (ay - originY).toFloat()
                floats[size + 2] = (bx - originX).toFloat()
                floats[size + 3] = (by - originY).toFloat()
                size += 4
                // A segment's box is at least one map point wide, as an MKMapRect on iOS.
                val loX = min(ax, bx)
                val loY = min(ay, by)
                minX = min(minX, loX)
                minY = min(minY, loY)
                maxX = max(maxX, loX + max(abs(ax - bx), 1.0))
                maxY = max(maxY, loY + max(abs(ay - by), 1.0))
            }
        }

        private fun buildLod(polylines: List<List<GeoPoint>>, size: Double): FogMeshLod {
            val buckets = LinkedHashMap<Long, Bucket>()
            for (line in polylines) {
                if (line.size < 2) continue
                var ax = MapPoints.x(line[0].longitude)
                var ay = MapPoints.y(line[0].latitude)
                for (i in 1 until line.size) {
                    val bx = MapPoints.x(line[i].longitude)
                    val by = MapPoints.y(line[i].latitude)
                    // The chunk is picked by the segment's MIDPOINT, so a segment lying on a chunk
                    // border does not jump between neighbours with the rounding of its start.
                    val kx = floor((ax + bx) / 2 / size).toInt()
                    val ky = floor((ay + by) / 2 / size).toInt()
                    val key = (kx.toLong() shl 32) or (ky.toLong() and 0xFFFF_FFFFL)
                    buckets.getOrPut(key) { Bucket(kx * size, ky * size) }.add(ax, ay, bx, by)
                    ax = bx
                    ay = by
                }
            }
            val total = buckets.values.sumOf { it.size }
            val segments = FloatArray(total)
            val chunks = DoubleArray(buckets.size * 6)
            val ranges = IntArray(buckets.size * 2)
            var offset = 0
            buckets.values.forEachIndexed { i, bucket ->
                bucket.floats.copyInto(segments, offset, 0, bucket.size)
                chunks[6 * i] = bucket.originX
                chunks[6 * i + 1] = bucket.originY
                chunks[6 * i + 2] = bucket.minX
                chunks[6 * i + 3] = bucket.minY
                chunks[6 * i + 4] = bucket.maxX
                chunks[6 * i + 5] = bucket.maxY
                ranges[2 * i] = offset / 4
                ranges[2 * i + 1] = bucket.size / 4
                offset += bucket.size
            }
            return FogMeshLod(segments, chunks, ranges, size)
        }
    }
}

/**
 * What tells an already built world from one to build (iOS: FogMesh.signature): the identities of the
 * polylines of ALL three levels. A layer whose `mid` and `far` changed while `fine` was reused would
 * otherwise never reach the GPU, and the country zoom would show yesterday's fog. An empty `fine`
 * with a non-empty `far` is a legal layer too, and two such layers must not share one empty signature.
 */
class FogMeshSignature private constructor(private val identities: List<Any>) {
    val count: Int get() = identities.size

    override fun equals(other: Any?): Boolean =
        other is FogMeshSignature && other.identities.size == identities.size &&
            identities.indices.all { identities[it] === other.identities[it] }

    override fun hashCode(): Int = identities.fold(1) { hash, item -> 31 * hash + System.identityHashCode(item) }

    companion object {
        fun of(layer: RevealedLayer): FogMeshSignature =
            FogMeshSignature(RevealedLayer.LOD.entries.flatMap { layer.polylines(it) })
    }
}
