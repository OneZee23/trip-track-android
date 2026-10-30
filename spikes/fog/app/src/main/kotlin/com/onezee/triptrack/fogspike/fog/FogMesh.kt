package com.onezee.triptrack.fogspike.fog

import com.onezee.triptrack.fogspike.data.FloatList
import com.onezee.triptrack.fogspike.data.RevealedLayerLite
import com.onezee.triptrack.fogspike.data.Run
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Segments of one level of detail, grouped by chunk (iOS: FogMesh, FogChunk).
 *
 * `segments` holds p0.x, p0.y, p1.x, p1.y per segment as Float32 offsets from the chunk origin;
 * `bounds` holds originX, originY, minX, minY, maxX, maxY per chunk in map points (Double);
 * `ranges` holds the first segment and the count per chunk.
 */
class MeshLod(val segments: FloatArray, val bounds: DoubleArray, val ranges: IntArray) {
    val segmentCount: Int get() = segments.size / 4
    val chunkCount: Int get() = ranges.size / 2
    val byteSize: Int get() = segments.size * 4
}

class FogMesh(val lods: List<MeshLod>) {
    val byteSize: Int get() = lods.sumOf { it.byteSize }

    companion object {
        /** Built off the main thread: a mature library is hundreds of thousands of segments. */
        fun build(layer: RevealedLayerLite): FogMesh = FogMesh(List(3) { buildLod(layer.lod(it)) })

        private class Bucket(val originX: Double, val originY: Double) {
            val floats = FloatList(256)
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
        }

        private fun buildLod(runs: List<Run>): MeshLod {
            val buckets = LinkedHashMap<Long, Bucket>()
            val size = FogRules.CHUNK_SIZE
            for (run in runs) {
                val n = run.size / 2
                if (n < 2) continue
                var ax = FogRules.mapX(run[1])
                var ay = FogRules.mapY(run[0])
                for (i in 1 until n) {
                    val bx = FogRules.mapX(run[2 * i + 1])
                    val by = FogRules.mapY(run[2 * i])
                    // The chunk is picked by the segment's MIDPOINT, so a segment lying on a chunk
                    // border does not jump between neighbours with rounding.
                    val kx = floor((ax + bx) / 2 / size).toLong()
                    val ky = floor((ay + by) / 2 / size).toLong()
                    val bucket = buckets.getOrPut((kx shl 32) or (ky and 0xFFFFFFFFL)) {
                        Bucket(kx * size, ky * size)
                    }
                    bucket.floats.add((ax - bucket.originX).toFloat())
                    bucket.floats.add((ay - bucket.originY).toFloat())
                    bucket.floats.add((bx - bucket.originX).toFloat())
                    bucket.floats.add((by - bucket.originY).toFloat())
                    // A segment's box is at least one map point wide, as MKMapRect on iOS.
                    val loX = min(ax, bx)
                    val loY = min(ay, by)
                    bucket.minX = min(bucket.minX, loX)
                    bucket.minY = min(bucket.minY, loY)
                    bucket.maxX = max(bucket.maxX, loX + max(1.0, abs(ax - bx)))
                    bucket.maxY = max(bucket.maxY, loY + max(1.0, abs(ay - by)))
                    ax = bx
                    ay = by
                }
            }
            var total = 0
            buckets.values.forEach { total += it.floats.size }
            val segments = FloatArray(total)
            val bounds = DoubleArray(buckets.size * 6)
            val ranges = IntArray(buckets.size * 2)
            var offset = 0
            buckets.values.forEachIndexed { i, bucket ->
                val floats = bucket.floats.toArray()
                floats.copyInto(segments, offset)
                bounds[6 * i] = bucket.originX
                bounds[6 * i + 1] = bucket.originY
                bounds[6 * i + 2] = bucket.minX
                bounds[6 * i + 3] = bucket.minY
                bounds[6 * i + 4] = bucket.maxX
                bounds[6 * i + 5] = bucket.maxY
                ranges[2 * i] = offset / 4
                ranges[2 * i + 1] = floats.size / 4
                offset += floats.size
            }
            return MeshLod(segments, bounds, ranges)
        }
    }
}
