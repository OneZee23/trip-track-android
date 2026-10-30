// The frame of one fog picture: where map points land on the screen, which level of detail is
// drawn, how wide a corridor is and where the cell grid sits.
//
// iOS: FogFrameEncoder.swift (FogFrameParams), FogMetalVeil.draw (the frame's numbers),
// FogVeilRenderer.haloHalfWidth / lod, FogCellGrid.make. Pure arithmetic, no GL: everything here is
// computed in double, and only offsets that already fit go down to float.
#pragma once

#include <algorithm>
#include <array>
#include <cmath>

#include "fog_state.hpp"
#include "mln/style/layers/custom_layer_render_parameters.hpp"

namespace triptrack::fog {

constexpr double kPi = 3.14159265358979323846;
// Map points as in MapKit (MKMapSize.world): Web Mercator scaled to 2^28 across the world. Chunk
// sizes and LOD thresholds are numbers in these units on iOS, so they stay the same here.
constexpr double kWorld = 268435456.0;
// MapLibre's spherical Web Mercator: the scale its own map and scale bar use.
constexpr double kEarthCircumference = 40075016.68557849;

struct MapPoint {
    double x;
    double y;
};

inline MapPoint mapPoint(double latitude, double longitude) {
    const double clamped = std::clamp(latitude, -85.0511, 85.0511);
    const double s = std::sin(clamped * kPi / 180.0);
    return {(longitude + 180.0) / 360.0 * kWorld, (0.5 - std::log((1.0 + s) / (1.0 - s)) / (4.0 * kPi)) * kWorld};
}

inline double latitudeOf(double y) {
    const double n = kPi - 2.0 * kPi * y / kWorld;
    return 180.0 / kPi * std::atan(0.5 * (std::exp(n) - std::exp(-n)));
}

inline double metresPerMapPoint(double latitude) {
    return std::cos(latitude * kPi / 180.0) * kEarthCircumference / kWorld;
}

// Map point -> screen point (dp) of this frame. Without pitch the ground plane projects affinely,
// so three projected points define it exactly, and rotation and map padding need no extra code
// (iOS: VeilFrame from three corners).
struct Frame {
    MapPoint ref{};         // the camera centre in map points
    double s0x = 0;         // where `ref` lands on the screen
    double s0y = 0;
    double a = 0, b = 0;    // first column: screen offset of one map point along x
    double c = 0, d = 0;    // second column: along y
    double det = 0;
    double width = 0;       // viewport in dp
    double height = 0;
    // Bounding box of the viewport in map points, for culling and the cell grid.
    double minX = 0, minY = 0, maxX = 0, maxY = 0;
    // Screen points per map point: the length of the first column. iOS divides the view width by
    // the visible rect's width, which shrinks when the map is rotated; this does not.
    double pointsPerMapPoint = 0;

    // Screen position of a map point given as an offset from `ref`, in double.
    void project(double dx, double dy, double& sx, double& sy) const {
        sx = s0x + a * dx + c * dy;
        sy = s0y + b * dx + d * dy;
    }
};

inline bool projectThrough(const mln::style::CustomLayerRenderParameters& p, double scale, double x, double y,
                           double& sx, double& sy) {
    // World coordinates in logical pixels at the current zoom (512 * 2^zoom across the world).
    const auto& m = p.projectionMatrix;  // column-major
    const double wx = x * scale;
    const double wy = y * scale;
    const double cx = m[0] * wx + m[4] * wy + m[12];
    const double cy = m[1] * wx + m[5] * wy + m[13];
    const double cw = m[3] * wx + m[7] * wy + m[15];
    if (!(std::abs(cw) > 1e-12)) return false;
    sx = (cx / cw + 1.0) * 0.5 * p.width;
    sy = (1.0 - cy / cw) * 0.5 * p.height;
    return std::isfinite(sx) && std::isfinite(sy);
}

inline bool makeFrame(const mln::style::CustomLayerRenderParameters& p, Frame& f) {
    if (!(p.width > 1 && p.height > 1)) return false;
    const double scale = 512.0 * std::pow(2.0, p.zoom) / kWorld;
    if (!(scale > 0) || !std::isfinite(scale)) return false;
    f.ref = mapPoint(p.latitude, p.longitude);
    f.width = p.width;
    f.height = p.height;
    const double step = 256.0 / scale;
    double s1x = 0, s1y = 0, s2x = 0, s2y = 0;
    if (!projectThrough(p, scale, f.ref.x, f.ref.y, f.s0x, f.s0y) ||
        !projectThrough(p, scale, f.ref.x + step, f.ref.y, s1x, s1y) ||
        !projectThrough(p, scale, f.ref.x, f.ref.y + step, s2x, s2y)) {
        return false;
    }
    f.a = (s1x - f.s0x) / step;
    f.b = (s1y - f.s0y) / step;
    f.c = (s2x - f.s0x) / step;
    f.d = (s2y - f.s0y) / step;
    f.det = f.a * f.d - f.c * f.b;
    if (!(std::abs(f.det) > 0) || !std::isfinite(f.det)) return false;
    f.pointsPerMapPoint = std::hypot(f.a, f.b);

    f.minX = f.minY = INFINITY;
    f.maxX = f.maxY = -INFINITY;
    const double corners[4][2] = {{0, 0}, {p.width, 0}, {0, p.height}, {p.width, p.height}};
    for (const auto& corner : corners) {
        const double dx = corner[0] - f.s0x;
        const double dy = corner[1] - f.s0y;
        const double x = f.ref.x + (f.d * dx - f.c * dy) / f.det;
        const double y = f.ref.y + (-f.b * dx + f.a * dy) / f.det;
        f.minX = std::min(f.minX, x);
        f.minY = std::min(f.minY, y);
        f.maxX = std::max(f.maxX, x);
        f.maxY = std::max(f.maxY, y);
    }
    return true;
}

// How wide the opened corridor is in this frame and which level of detail draws it.
struct Corridor {
    double halfWidth = 0;  // screen points
    double feather = 0;    // screen points, cut INWARD: half alpha lies at halfWidth - feather / 2
    int lod = 0;           // 0 fine, 1 mid, 2 far
    double pad = 0;        // map points a chunk may lie outside the frame and still paint into it
};

inline Corridor corridorOf(const Frame& f, const Rules& rules) {
    Corridor corridor;
    // The same rule as FogVeilRenderer.haloHalfWidth: a 50 m floor keeps fog between two streets,
    // further out 18 screen points per side turn a town into a patch and a road into a band.
    const double centreLat = latitudeOf((f.minY + f.maxY) / 2);
    const double metresPerPoint = metresPerMapPoint(centreLat) / f.pointsPerMapPoint;
    const double halfWidthMetres =
        std::max(rules.streetHalfWidthMetres, rules.haloHalfWidthPoints * metresPerPoint);
    corridor.halfWidth = halfWidthMetres / metresPerPoint;
    corridor.feather = corridor.halfWidth * rules.featherRatio;
    corridor.lod = f.pointsPerMapPoint > rules.lodFineAbove ? 0 : f.pointsPerMapPoint > rules.lodMidAbove ? 1 : 2;
    corridor.pad = (corridor.halfWidth + corridor.feather) / f.pointsPerMapPoint;
    return corridor;
}

// iOS: FogCellGrid.make. A world-anchored grid of map points that doubles until a cell is at least
// minScreenPoints on screen, at most maxDoublings times; side 0 means no grid in this frame.
struct CellGrid {
    double side = 0;     // map points
    double originX = 0;  // nearest grid line from the frame's corner, in map points (computed in double)
    double originY = 0;
};

inline CellGrid cellGridOf(const Frame& f, const Rules& rules) {
    CellGrid grid;
    double side = rules.cellBaseMapPoints;
    double screen = side * f.pointsPerMapPoint;
    int doublings = 0;
    while (screen < rules.cellMinScreenPoints && doublings < rules.cellMaxDoublings) {
        side *= 2;
        screen *= 2;
        doublings += 1;
    }
    if (!(screen >= rules.cellMinScreenPoints) || !std::isfinite(screen)) return grid;
    grid.side = side;
    grid.originX = std::floor(f.minX / side) * side - f.minX;
    grid.originY = std::floor(f.minY / side) * side - f.minY;
    return grid;
}

}  // namespace triptrack::fog
