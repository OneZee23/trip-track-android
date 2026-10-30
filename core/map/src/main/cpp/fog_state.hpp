// State shared by Kotlin (JNI, any thread) and the fog host (MapLibre's GL thread).
//
// iOS: FogMesh.swift (FogChunk), FogVeilPainter.Palette, FogRevealCircle.swift, the counters of
// FogMetalVeil.swift. Kotlin builds the mesh off the main thread and hands it over whole; the host
// takes the newest committed version on its next frame. Everything here is guarded by `lock`, except
// the flags that are read once per frame without blocking.
#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

namespace triptrack::fog {

class FogHost;

// The numbers of the picture. Kotlin passes them once (FogRules.kt), so every number has one
// source: the same constants that core:logic's FogVeilRenderer and FogCellGrid use.
struct Rules {
    double streetHalfWidthMetres;  // FogVeilRenderer.streetHalfWidthMetres
    double haloHalfWidthPoints;    // FogVeilRenderer.haloHalfWidthPoints
    double featherRatio;           // FogMetalVeil.featherRatio
    double lodFineAbove;           // FogVeilRenderer.lod, screen points per map point
    double lodMidAbove;
    double cellBaseMapPoints;      // FogCellGrid.baseMapPoints
    double cellMinScreenPoints;    // FogCellGrid.minScreenPoints
    int cellMaxDoublings;          // FogCellGrid.maxDoublings
    double revealSolidFraction;    // VeilRevealMask.solidFraction
};

// A square of the revealed world with its own origin, so segment coordinates fit Float32 (iOS:
// FogChunk). Bounds are the true bounds of its segments, used for culling.
struct Chunk {
    double ox;
    double oy;
    double minX;
    double minY;
    double maxX;
    double maxY;
    int32_t first;
    int32_t count;
};

struct LodData {
    std::vector<float> segments;  // p0.x, p0.y, p1.x, p1.y per segment, offsets from the chunk origin
    std::vector<Chunk> chunks;
};

// iOS: FogVeilPainter.Palette (.mist #EEEEEC × 0.87, .night #262B36 × 0.70) and the cells flag of
// AtlasMapAppearance.Style.
struct Style {
    float r = 0xEE / 255.0f;
    float g = 0xEE / 255.0f;
    float b = 0xEC / 255.0f;
    float alpha = 0.87f;
    bool cells = false;
    bool visible = true;
    // Coverage at half the framebuffer size, read back bilinearly: a quarter of the fragments of the
    // coverage pass. Android only, for phones whose GPU cannot fill the full-size pass.
    bool halfResolution = false;
};

// iOS: FogRevealCircle. The live hole around the car, tied to the GROUND: projected every frame.
struct Reveal {
    double latitude = 0;
    double longitude = 0;
    double radiusMetres = 0;  // 0: no hole, the composite leaves alpha alone
    double solid = 0;
};

// iOS: FogMetalVeil.frames / encodeSeconds and the budget numbers. Read by the demo screen.
struct Stats {
    uint64_t frames = 0;         // frames the layer drew
    double encodeMs = 0;         // GL-thread CPU time spent drawing them
    double lastEncodeMs = 0;
    int lastLod = -1;
    int lastChunks = 0;
    int lastSegments = 0;
    double lastHalfWidth = 0;    // screen points
    double gpuMs = -1;           // last GPU time of both passes; -1 without a timer query
    uint64_t uploads = 0;
    double lastUploadMs = 0;
    double uploadBytes = 0;
    uint64_t suspendedFrames = 0;   // frames MapLibre drew while the app was in the background
    uint64_t coverageReleases = 0;
    uint64_t contextLosses = 0;
    uint64_t initializations = 0;
    bool shaderFailed = false;
};

struct State {
    explicit State(const Rules& r) : rules(r) {}

    const Rules rules;
    std::mutex lock;
    std::array<std::shared_ptr<const LodData>, 3> staged;
    std::array<std::shared_ptr<const LodData>, 3> committed;
    uint64_t meshVersion = 0;
    Style style;
    Reveal reveal;
    Stats stats;
    // The host MapLibre currently owns for this state, for releases run on the GL thread.
    FogHost* liveHost = nullptr;
    std::atomic<bool> suspended{false};
    std::atomic<bool> diagnostics{false};
};

}  // namespace triptrack::fog
