package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint

// iOS: GeohashEncoder.swift
object GeohashEncoder {
    private const val base32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(latitude: Double, longitude: Double, precision: Int = 6): String {
        var minLat = -90.0
        var maxLat = 90.0
        var minLon = -180.0
        var maxLon = 180.0
        var bits = 0
        var current = 0
        var isLon = true
        val hash = StringBuilder()
        while (hash.length < precision) {
            if (isLon) {
                val mid = (minLon + maxLon) / 2
                current = current shl 1
                if (longitude >= mid) {
                    current = current or 1
                    minLon = mid
                } else maxLon = mid
            } else {
                val mid = (minLat + maxLat) / 2
                current = current shl 1
                if (latitude >= mid) {
                    current = current or 1
                    minLat = mid
                } else maxLat = mid
            }
            isLon = !isLon
            bits++
            if (bits == 5) {
                hash.append(base32[current])
                bits = 0
                current = 0
            }
        }
        return hash.toString()
    }

    fun decode(hash: String): GeoBounds {
        var minLat = -90.0
        var maxLat = 90.0
        var minLon = -180.0
        var maxLon = 180.0
        var isLon = true
        for (char in hash.lowercase()) {
            val value = base32.indexOf(char)
            if (value < 0) continue
            for (i in 4 downTo 0) {
                val bit = (value shr i) and 1
                if (isLon) {
                    val mid = (minLon + maxLon) / 2
                    if (bit == 1) minLon = mid else maxLon = mid
                } else {
                    val mid = (minLat + maxLat) / 2
                    if (bit == 1) minLat = mid else maxLat = mid
                }
                isLon = !isLon
            }
        }
        return GeoBounds(minLat, maxLat, minLon, maxLon)
    }

    fun centerCoordinate(hash: String): GeoPoint {
        val box = decode(hash)
        return GeoPoint((box.minLat + box.maxLat) / 2, (box.minLon + box.maxLon) / 2)
    }

    fun neighbors(hash: String): List<String> {
        val center = centerCoordinate(hash)
        val box = decode(hash)
        val latDelta = box.maxLat - box.minLat
        val lonDelta = box.maxLon - box.minLon
        return listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1).map { (lat, lon) ->
            encode(center.latitude + lat * latDelta, center.longitude + lon * lonDelta, hash.length)
        }
    }
}
