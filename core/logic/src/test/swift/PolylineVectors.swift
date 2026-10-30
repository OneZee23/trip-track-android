// iOS: Trip.swift, a155859d. The encoder body is unchanged; run on Apple Swift to freeze wire bytes.
import Foundation
import CoreLocation

enum Trip {
    static func encodePolyline(_ coords: [CLLocationCoordinate2D]) -> Data {
        var data = Data(capacity: coords.count * 8)
        for coord in coords {
            var lat = Float32(coord.latitude)
            var lon = Float32(coord.longitude)
            data.append(Data(bytes: &lat, count: 4))
            data.append(Data(bytes: &lon, count: 4))
        }
        return data
    }
}

let vectors: [[(Double, Double)]] = [
    [(45.035, 38.975), (45.036, 38.976), (45.037, 38.977)],
    [(55.7558, 37.6173)],
    [(55.755826, 37.617299)],
    [(0, 0), (-0.0, -0.0), (-33.9249, -18.4241), (90, 180), (-90, -180)],
]
for vector in vectors {
    let coordinates = vector.map { CLLocationCoordinate2D(latitude: $0.0, longitude: $0.1) }
    print(Trip.encodePolyline(coordinates).map { String(format: "%02x", $0) }.joined())
}
