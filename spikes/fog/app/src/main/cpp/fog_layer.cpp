// The Atlas fog of war drawn inside MapLibre's own frame, as a custom layer.
//
// iOS: FogShaders.metal (both passes), FogFrameEncoder.swift (uniforms, chunk translation in
// double), FogMetalVeil.swift (frame parameters), FogCellGrid.swift (the "cells" style).
//
// Why a custom layer: MapLibre calls render() between its own layers, with the camera of the
// frame it is drawing, and presents tiles and fog together. The fog cannot lag behind the
// fingers by construction. On iOS the same guarantee took presentsWithTransaction plus a draw
// on every camera callback, because the Metal view and MapKit present separately.

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <jni.h>
#include <time.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>

#include "mln_custom_layer_host.hpp"

namespace {

constexpr const char* kTag = "FogSpike";
constexpr double kPi = 3.14159265358979323846;
// Map points as in MapKit (MKMapSize.world): Web Mercator scaled to 2^28 across the world. The
// chunk size and the LOD thresholds are numbers in these units on iOS, so they stay the same.
constexpr double kWorld = 268435456.0;
constexpr double kEarthCircumference = 40075016.68557849;
// GL_EXT_disjoint_timer_query. Defined here so the GLES 2 extension header is not mixed in.
constexpr GLenum kTimeElapsed = 0x88BF;
constexpr GLenum kGpuDisjoint = 0x8FBB;

double nowMs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<double>(ts.tv_sec) * 1e3 + static_cast<double>(ts.tv_nsec) / 1e6;
}

struct MapPoint {
    double x;
    double y;
};

MapPoint mapPoint(double lat, double lon) {
    const double clamped = std::clamp(lat, -85.0511, 85.0511);
    const double s = std::sin(clamped * kPi / 180.0);
    return {(lon + 180.0) / 360.0 * kWorld,
            (0.5 - std::log((1.0 + s) / (1.0 - s)) / (4.0 * kPi)) * kWorld};
}

double latitudeOf(double y) {
    const double n = kPi - 2.0 * kPi * y / kWorld;
    return 180.0 / kPi * std::atan(0.5 * (std::exp(n) - std::exp(-n)));
}

double metresPerMapPoint(double lat) {
    return std::cos(lat * kPi / 180.0) * kEarthCircumference / kWorld;
}

// Rules of the picture. Kotlin passes them once (FogRules.kt), so the spike has one source of
// these numbers, like FogVeilRenderer on iOS.
struct Rules {
    double streetHalfWidthMetres;
    double haloHalfWidthPoints;
    double featherRatio;
    double lodFineAbove;  // points per map point
    double lodMidAbove;
};

// A chunk of the revealed world: a 32768 map point square with its own origin, so segment
// coordinates fit Float32 (iOS: FogChunk).
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

struct StyleState {
    float r = 0xEE / 255.0f;
    float g = 0xEE / 255.0f;
    float b = 0xEC / 255.0f;
    float alpha = 0.87f;
    bool cells = false;
    bool visible = true;
};

struct Sample {
    double time;      // ms, CLOCK_MONOTONIC, at the start of render()
    double cpuMs;     // time spent in render() on the GL thread
    double gpuMs;     // GPU time of both passes from a timer query, -1 if unknown
    double instances; // segments submitted
    double lod;       // 0 fine, 1 mid, 2 far, -1 probe or not drawn
};

struct FogState {
    explicit FogState(const Rules& r) : rules(r) {}

    const Rules rules;
    std::mutex lock;
    std::array<std::shared_ptr<const LodData>, 3> staged;
    std::array<std::shared_ptr<const LodData>, 3> committed;
    uint64_t meshVersion = 0;
    StyleState style;
    std::vector<Sample> samples;
    double uploadMs = 0;
    double uploadBytes = 0;
    double centreErrorDp = -1;
    bool gpuTimer = false;
    bool shaderFailed = false;
};

GLuint compile(GLenum type, const char* source) {
    const GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (ok != GL_TRUE) {
        char log[1024] = {};
        glGetShaderInfoLog(shader, sizeof(log) - 1, nullptr, log);
        __android_log_print(ANDROID_LOG_ERROR, kTag, "shader: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint link(const char* vertex, const char* fragment) {
    const GLuint vs = compile(GL_VERTEX_SHADER, vertex);
    const GLuint fs = compile(GL_FRAGMENT_SHADER, fragment);
    if (vs == 0 || fs == 0) return 0;
    const GLuint program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint ok = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &ok);
    if (ok != GL_TRUE) {
        char log[1024] = {};
        glGetProgramInfoLog(program, sizeof(log) - 1, nullptr, log);
        __android_log_print(ANDROID_LOG_ERROR, kTag, "program: %s", log);
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

// One instance per segment, four vertices as a strip. The rectangle is built in SCREEN points:
// its width then does not depend on the map scale, and the fragment measures the capsule in the
// same numbers (iOS: fog_coverage_vertex).
constexpr const char* kCoverageVertex = R"(#version 300 es
layout(location = 0) in vec4 a_segment;
uniform vec4 u_m;
uniform vec2 u_translation;
uniform vec2 u_viewport;
uniform float u_halfWidth;
uniform float u_feather;
flat out vec2 v_s0;
flat out vec2 v_s1;
out vec2 v_screen;
void main() {
    mat2 m = mat2(u_m.xy, u_m.zw);
    vec2 s0 = u_translation + m * a_segment.xy;
    vec2 s1 = u_translation + m * a_segment.zw;
    vec2 delta = s1 - s0;
    float len = length(delta);
    vec2 dir = len > 1e-5 ? delta / len : vec2(1.0, 0.0);
    vec2 normal = vec2(-dir.y, dir.x);
    float extend = u_halfWidth + u_feather;
    vec2 base = gl_VertexID < 2 ? s0 - dir * extend : s1 + dir * extend;
    float side = (gl_VertexID & 1) == 0 ? -1.0 : 1.0;
    vec2 screen = base + normal * side * extend;
    gl_Position = vec4(screen.x / u_viewport.x * 2.0 - 1.0, 1.0 - screen.y / u_viewport.y * 2.0, 0.0, 1.0);
    v_s0 = s0;
    v_s1 = s1;
    v_screen = screen;
}
)";

// Distance to the segment (capsule SDF) and the feather cut inward. Blending is MAX, so two
// neighbouring segments leave neither a bead nor a ring at their joint (iOS: fog_coverage_fragment).
constexpr const char* kCoverageFragment = R"(#version 300 es
precision highp float;
flat in vec2 v_s0;
flat in vec2 v_s1;
in vec2 v_screen;
uniform float u_halfWidth;
uniform float u_feather;
out vec4 o_colour;
void main() {
    vec2 pa = v_screen - v_s0;
    vec2 ba = v_s1 - v_s0;
    float bb = dot(ba, ba);
    float h = bb > 1e-6 ? clamp(dot(pa, ba) / bb, 0.0, 1.0) : 0.0;
    float d = length(pa - ba * h);
    float coverage = 1.0 - smoothstep(u_halfWidth - u_feather, u_halfWidth, d);
    o_colour = vec4(coverage);
}
)";

constexpr const char* kCompositeVertex = R"(#version 300 es
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
)";

// Flat fog with premultiplied alpha; the revealed coverage is subtracted from it. The cells
// style quantises coverage per world-anchored cell in this pass only, so the coverage pass and
// the reference "fog" picture stay byte for byte the same (iOS: fog_composite_fragment).
constexpr const char* kCompositeFragment = R"(#version 300 es
precision highp float;
uniform sampler2D u_coverage;
uniform vec3 u_colour;
uniform float u_alpha;
uniform vec2 u_pxPerDp;
uniform float u_fbHeight;
uniform float u_side;
uniform vec4 u_gm;
uniform vec4 u_ginv;
uniform vec2 u_gorigin;
uniform vec2 u_cellOrigin;
out vec4 o_colour;

float sampleDp(vec2 p) {
    ivec2 size = textureSize(u_coverage, 0);
    ivec2 texel = ivec2(clamp(vec2(p.x * u_pxPerDp.x, u_fbHeight - p.y * u_pxPerDp.y),
                              vec2(0.0), vec2(size - 1)));
    return texelFetch(u_coverage, texel, 0).r;
}

float cellCoverage(vec2 p) {
    mat2 fwd = mat2(u_gm.xy, u_gm.zw);
    mat2 inv = mat2(u_ginv.xy, u_ginv.zw);
    vec2 mp = inv * (p - u_gorigin);
    vec2 fromGrid = mp - u_cellOrigin;
    vec2 centreMp = (floor(fromGrid / u_side) + 0.5) * u_side + u_cellOrigin;
    vec2 centre = u_gorigin + fwd * centreMp;
    vec2 ex = (fwd * vec2(u_side, 0.0)) * 0.3;
    vec2 ey = (fwd * vec2(0.0, u_side)) * 0.3;
    float hit = sampleDp(centre);
    hit = max(hit, sampleDp(centre + ex));
    hit = max(hit, sampleDp(centre - ex));
    hit = max(hit, sampleDp(centre + ey));
    hit = max(hit, sampleDp(centre - ey));
    return hit > 0.35 ? 1.0 : 0.0;
}

void main() {
    float coverage;
    if (u_side > 0.0) {
        vec2 p = vec2(gl_FragCoord.x / u_pxPerDp.x, (u_fbHeight - gl_FragCoord.y) / u_pxPerDp.y);
        coverage = cellCoverage(p);
    } else {
        coverage = texelFetch(u_coverage, ivec2(gl_FragCoord.xy), 0).r;
    }
    float a = u_alpha * (1.0 - coverage);
    o_colour = vec4(u_colour * a, a);
}
)";

using GenQueries = void (*)(GLsizei, GLuint*);
using DeleteQueries = void (*)(GLsizei, const GLuint*);
using BeginQuery = void (*)(GLenum, GLuint);
using EndQuery = void (*)(GLenum);
using GetQueryObjectuiv = void (*)(GLuint, GLenum, GLuint*);
using GetQueryObjectui64v = void (*)(GLuint, GLenum, GLuint64*);

class FogHost final : public mln::style::CustomLayerHost {
public:
    FogHost(std::shared_ptr<FogState> state, bool probe) : state_(std::move(state)), probe_(probe) {}

    void initialize(const mln::style::CustomLayerInitParameters&) override {
        if (probe_) return;
        coverage_ = link(kCoverageVertex, kCoverageFragment);
        composite_ = link(kCompositeVertex, kCompositeFragment);
        if (coverage_ == 0 || composite_ == 0) {
            std::lock_guard<std::mutex> guard(state_->lock);
            state_->shaderFailed = true;
            return;
        }
        uM_ = glGetUniformLocation(coverage_, "u_m");
        uTranslation_ = glGetUniformLocation(coverage_, "u_translation");
        uViewport_ = glGetUniformLocation(coverage_, "u_viewport");
        uHalfWidth_ = glGetUniformLocation(coverage_, "u_halfWidth");
        uFeather_ = glGetUniformLocation(coverage_, "u_feather");
        uCoverage_ = glGetUniformLocation(composite_, "u_coverage");
        uColour_ = glGetUniformLocation(composite_, "u_colour");
        uAlpha_ = glGetUniformLocation(composite_, "u_alpha");
        uPxPerDp_ = glGetUniformLocation(composite_, "u_pxPerDp");
        uFbHeight_ = glGetUniformLocation(composite_, "u_fbHeight");
        uSide_ = glGetUniformLocation(composite_, "u_side");
        uGm_ = glGetUniformLocation(composite_, "u_gm");
        uGinv_ = glGetUniformLocation(composite_, "u_ginv");
        uGorigin_ = glGetUniformLocation(composite_, "u_gorigin");
        uCellOrigin_ = glGetUniformLocation(composite_, "u_cellOrigin");
        glGenVertexArrays(1, &segmentVao_);
        glGenVertexArrays(1, &emptyVao_);
        glGenFramebuffers(1, &fbo_);
        glGenBuffers(3, vbo_.data());
        uploadedVersion_ = 0;
        setUpTimer();
    }

    void render(const mln::style::CustomLayerRenderParameters& p) override {
        const double started = nowMs();
        if (probe_ || coverage_ == 0) {
            record({started, nowMs() - started, -1, 0, -1});
            return;
        }
        syncMesh();
        StyleState style;
        {
            std::lock_guard<std::mutex> guard(state_->lock);
            style = state_->style;
        }

        GLint previousFbo = 0;
        GLint previousVao = 0;
        GLint viewport[4] = {0, 0, 0, 0};
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &previousFbo);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &previousVao);
        glGetIntegerv(GL_VIEWPORT, viewport);
        const int fbWidth = viewport[2];
        const int fbHeight = viewport[3];
        if (!style.visible || fbWidth <= 1 || fbHeight <= 1 || p.width <= 1 || p.height <= 1) {
            record({started, nowMs() - started, -1, 0, -1});
            return;
        }

        // Map point -> screen point (dp) as an affine frame. Without pitch the projection of the
        // ground plane is affine, so three projected points define it exactly (iOS: VeilFrame).
        const double scale = 512.0 * std::pow(2.0, p.zoom) / kWorld;
        const MapPoint ref = mapPoint(p.latitude, p.longitude);
        const double step = 256.0 / scale;
        double s0x = 0, s0y = 0, s1x = 0, s1y = 0, s2x = 0, s2y = 0;
        if (!project(p, scale, ref.x, ref.y, s0x, s0y) ||
            !project(p, scale, ref.x + step, ref.y, s1x, s1y) ||
            !project(p, scale, ref.x, ref.y + step, s2x, s2y)) {
            record({started, nowMs() - started, -1, 0, -1});
            return;
        }
        const double a = (s1x - s0x) / step;
        const double b = (s1y - s0y) / step;
        const double c = (s2x - s0x) / step;
        const double d = (s2y - s0y) / step;
        const double det = a * d - c * b;
        if (!(std::abs(det) > 0) || !std::isfinite(det)) {
            record({started, nowMs() - started, -1, 0, -1});
            return;
        }
        if (!centreChecked_) {
            centreChecked_ = true;
            std::lock_guard<std::mutex> guard(state_->lock);
            state_->centreErrorDp = std::hypot(s0x - p.width / 2, s0y - p.height / 2);
        }

        // Visible map rectangle: the bounding box of the four viewport corners.
        double minX = INFINITY, minY = INFINITY, maxX = -INFINITY, maxY = -INFINITY;
        const double corners[4][2] = {{0, 0}, {p.width, 0}, {0, p.height}, {p.width, p.height}};
        for (const auto& corner : corners) {
            const double dx = corner[0] - s0x;
            const double dy = corner[1] - s0y;
            const double x = ref.x + (d * dx - c * dy) / det;
            const double y = ref.y + (-b * dx + a * dy) / det;
            minX = std::min(minX, x);
            minY = std::min(minY, y);
            maxX = std::max(maxX, x);
            maxY = std::max(maxY, y);
        }

        // Corridor width: the same rule as FogVeilRenderer.haloHalfWidth, in screen points.
        const double pointsPerMapPoint = std::hypot(a, b);
        const double centreLat = latitudeOf((minY + maxY) / 2);
        const double metresPerPoint = metresPerMapPoint(centreLat) / pointsPerMapPoint;
        const Rules& rules = state_->rules;
        const double halfWidthMetres =
            std::max(rules.streetHalfWidthMetres, rules.haloHalfWidthPoints * metresPerPoint);
        const double halfWidth = halfWidthMetres / metresPerPoint;
        const double feather = halfWidth * rules.featherRatio;
        const int lod = pointsPerMapPoint > rules.lodFineAbove ? 0
                        : pointsPerMapPoint > rules.lodMidAbove ? 1
                                                                 : 2;
        const double pad = (halfWidth + feather) / pointsPerMapPoint;

        beginTimer();
        ensureCoverageTexture(fbWidth, fbHeight);

        // Pass 1: corridors into the R8 coverage texture.
        glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
        glViewport(0, 0, fbWidth, fbHeight);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        glDepthMask(GL_FALSE);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendEquation(GL_MAX);
        glBlendFunc(GL_ONE, GL_ONE);
        glUseProgram(coverage_);
        glUniform4f(uM_, static_cast<float>(a), static_cast<float>(b), static_cast<float>(c),
                    static_cast<float>(d));
        glUniform2f(uViewport_, static_cast<float>(p.width), static_cast<float>(p.height));
        glUniform1f(uHalfWidth_, static_cast<float>(halfWidth));
        glUniform1f(uFeather_, static_cast<float>(feather));
        glBindVertexArray(segmentVao_);
        glBindBuffer(GL_ARRAY_BUFFER, vbo_[lod]);
        glEnableVertexAttribArray(0);
        glVertexAttribDivisor(0, 1);
        int instances = 0;
        if (const auto& data = gpuData_[lod]) {
            for (const Chunk& chunk : data->chunks) {
                if (chunk.maxX < minX - pad || chunk.minX > maxX + pad || chunk.maxY < minY - pad ||
                    chunk.minY > maxY + pad) {
                    continue;
                }
                // The chunk offset goes to the screen in DOUBLE and only then to float: that is
                // the whole point of a chunk having its own origin.
                const double ox = chunk.ox - ref.x;
                const double oy = chunk.oy - ref.y;
                glUniform2f(uTranslation_, static_cast<float>(s0x + a * ox + c * oy),
                            static_cast<float>(s0y + b * ox + d * oy));
                glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE, 16,
                                      reinterpret_cast<const void*>(static_cast<intptr_t>(chunk.first) * 16));
                glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, chunk.count);
                instances += chunk.count;
            }
        }
        glVertexAttribDivisor(0, 0);
        glDisableVertexAttribArray(0);

        // Pass 2: flat fog over the map, premultiplied, into MapLibre's own framebuffer.
        glBindFramebuffer(GL_FRAMEBUFFER, static_cast<GLuint>(previousFbo));
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(composite_);
        glBindVertexArray(emptyVao_);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, coverageTexture_);
        glUniform1i(uCoverage_, 0);
        glUniform3f(uColour_, style.r, style.g, style.b);
        glUniform1f(uAlpha_, style.alpha);
        glUniform2f(uPxPerDp_, static_cast<float>(fbWidth / p.width),
                    static_cast<float>(fbHeight / p.height));
        glUniform1f(uFbHeight_, static_cast<float>(fbHeight));
        setCells(style.cells, a, b, c, d, det, pointsPerMapPoint, ref, s0x, s0y, minX, minY);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        endTimer();

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(static_cast<GLuint>(previousVao));
        glUseProgram(0);
        record({started, nowMs() - started, takeGpuMs(), static_cast<double>(instances),
                static_cast<double>(lod)});
    }

    void contextLost() override {
        // The context took every GL object with it; initialize() comes next.
        coverage_ = composite_ = 0;
        segmentVao_ = emptyVao_ = fbo_ = coverageTexture_ = 0;
        vbo_ = {0, 0, 0};
        gpuData_ = {};
        textureWidth_ = textureHeight_ = 0;
        uploadedVersion_ = 0;
        queries_ = {0, 0, 0, 0};
        timerReady_ = false;
    }

    void deinitialize() override {
        if (probe_) return;
        if (coverage_) glDeleteProgram(coverage_);
        if (composite_) glDeleteProgram(composite_);
        if (segmentVao_) glDeleteVertexArrays(1, &segmentVao_);
        if (emptyVao_) glDeleteVertexArrays(1, &emptyVao_);
        if (fbo_) glDeleteFramebuffers(1, &fbo_);
        if (coverageTexture_) glDeleteTextures(1, &coverageTexture_);
        glDeleteBuffers(3, vbo_.data());
        if (timerReady_) deleteQueries_(static_cast<GLsizei>(queries_.size()), queries_.data());
        contextLost();
    }

private:
    static bool project(const mln::style::CustomLayerRenderParameters& p, double scale, double x,
                        double y, double& sx, double& sy) {
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

    void setCells(bool enabled, double a, double b, double c, double d, double det,
                  double pointsPerMapPoint, MapPoint ref, double s0x, double s0y, double minX,
                  double minY) {
        // FogCellGrid.make: a world-anchored grid of map points that doubles until a cell is at
        // least 14 points on screen, at most twelve times.
        double side = 512.0;
        double screen = side * pointsPerMapPoint;
        int doublings = 0;
        while (screen < 14.0 && doublings < 12) {
            side *= 2;
            screen *= 2;
            doublings += 1;
        }
        if (!enabled || screen < 14.0) {
            glUniform1f(uSide_, 0.0f);
            return;
        }
        const double originX = std::floor(minX / side) * side - minX;
        const double originY = std::floor(minY / side) * side - minY;
        const double gx = s0x + a * (minX - ref.x) + c * (minY - ref.y);
        const double gy = s0y + b * (minX - ref.x) + d * (minY - ref.y);
        glUniform1f(uSide_, static_cast<float>(side));
        glUniform4f(uGm_, static_cast<float>(a), static_cast<float>(b), static_cast<float>(c),
                    static_cast<float>(d));
        glUniform4f(uGinv_, static_cast<float>(d / det), static_cast<float>(-b / det),
                    static_cast<float>(-c / det), static_cast<float>(a / det));
        glUniform2f(uGorigin_, static_cast<float>(gx), static_cast<float>(gy));
        glUniform2f(uCellOrigin_, static_cast<float>(originX), static_cast<float>(originY));
    }

    void ensureCoverageTexture(int width, int height) {
        if (coverageTexture_ != 0 && textureWidth_ == width && textureHeight_ == height) return;
        if (coverageTexture_ == 0) glGenTextures(1, &coverageTexture_);
        glBindTexture(GL_TEXTURE_2D, coverageTexture_);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, height, 0, GL_RED, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, coverageTexture_, 0);
        const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            __android_log_print(ANDROID_LOG_ERROR, kTag, "coverage framebuffer incomplete: 0x%x", status);
        }
        textureWidth_ = width;
        textureHeight_ = height;
    }

    // The newest committed mesh goes to the GPU whole; a frame in flight keeps the old one.
    void syncMesh() {
        std::array<std::shared_ptr<const LodData>, 3> lods;
        uint64_t version = 0;
        {
            std::lock_guard<std::mutex> guard(state_->lock);
            version = state_->meshVersion;
            if (version == uploadedVersion_) return;
            lods = state_->committed;
        }
        const double started = nowMs();
        double bytes = 0;
        for (size_t i = 0; i < lods.size(); ++i) {
            glBindBuffer(GL_ARRAY_BUFFER, vbo_[i]);
            const auto size = lods[i] ? lods[i]->segments.size() * sizeof(float) : 0;
            glBufferData(GL_ARRAY_BUFFER, static_cast<GLsizeiptr>(size),
                         size ? lods[i]->segments.data() : nullptr, GL_STATIC_DRAW);
            bytes += static_cast<double>(size);
            gpuData_[i] = lods[i];
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        uploadedVersion_ = version;
        std::lock_guard<std::mutex> guard(state_->lock);
        state_->uploadMs = nowMs() - started;
        state_->uploadBytes = bytes;
    }

    void record(const Sample& sample) {
        std::lock_guard<std::mutex> guard(state_->lock);
        if (state_->samples.size() < 100000) state_->samples.push_back(sample);
    }

    void setUpTimer() {
        const auto* extensions = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
        if (extensions == nullptr || std::strstr(extensions, "GL_EXT_disjoint_timer_query") == nullptr) return;
        genQueries_ = reinterpret_cast<GenQueries>(eglGetProcAddress("glGenQueriesEXT"));
        deleteQueries_ = reinterpret_cast<DeleteQueries>(eglGetProcAddress("glDeleteQueriesEXT"));
        beginQuery_ = reinterpret_cast<BeginQuery>(eglGetProcAddress("glBeginQueryEXT"));
        endQuery_ = reinterpret_cast<EndQuery>(eglGetProcAddress("glEndQueryEXT"));
        getQueryuiv_ = reinterpret_cast<GetQueryObjectuiv>(eglGetProcAddress("glGetQueryObjectuivEXT"));
        getQueryui64v_ = reinterpret_cast<GetQueryObjectui64v>(eglGetProcAddress("glGetQueryObjectui64vEXT"));
        if (!genQueries_ || !deleteQueries_ || !beginQuery_ || !endQuery_ || !getQueryuiv_ || !getQueryui64v_) return;
        genQueries_(static_cast<GLsizei>(queries_.size()), queries_.data());
        timerReady_ = true;
        std::lock_guard<std::mutex> guard(state_->lock);
        state_->gpuTimer = true;
    }

    void beginTimer() {
        if (!timerReady_) return;
        // A ring of queries: the result of a frame is read a few frames later, never waited for.
        const GLuint query = queries_[queryHead_ % queries_.size()];
        if (pending_[queryHead_ % queries_.size()]) return;
        beginQuery_(kTimeElapsed, query);
        timing_ = true;
    }

    void endTimer() {
        if (!timing_) return;
        endQuery_(kTimeElapsed);
        pending_[queryHead_ % queries_.size()] = true;
        queryHead_ += 1;
        timing_ = false;
    }

    double takeGpuMs() {
        if (!timerReady_) return -1;
        GLint disjoint = 0;
        glGetIntegerv(kGpuDisjoint, &disjoint);
        double result = -1;
        for (size_t i = 0; i < queries_.size(); ++i) {
            if (!pending_[i]) continue;
            GLuint available = 0;
            getQueryuiv_(queries_[i], GL_QUERY_RESULT_AVAILABLE, &available);
            if (!available) continue;
            GLuint64 nanos = 0;
            getQueryui64v_(queries_[i], GL_QUERY_RESULT, &nanos);
            pending_[i] = false;
            if (!disjoint) result = static_cast<double>(nanos) / 1e6;
        }
        return result;
    }

    std::shared_ptr<FogState> state_;
    const bool probe_;
    GLuint coverage_ = 0;
    GLuint composite_ = 0;
    GLuint segmentVao_ = 0;
    GLuint emptyVao_ = 0;
    GLuint fbo_ = 0;
    GLuint coverageTexture_ = 0;
    int textureWidth_ = 0;
    int textureHeight_ = 0;
    std::array<GLuint, 3> vbo_{0, 0, 0};
    std::array<std::shared_ptr<const LodData>, 3> gpuData_{};
    uint64_t uploadedVersion_ = 0;
    bool centreChecked_ = false;
    GLint uM_ = -1, uTranslation_ = -1, uViewport_ = -1, uHalfWidth_ = -1, uFeather_ = -1;
    GLint uCoverage_ = -1, uColour_ = -1, uAlpha_ = -1, uPxPerDp_ = -1, uFbHeight_ = -1;
    GLint uSide_ = -1, uGm_ = -1, uGinv_ = -1, uGorigin_ = -1, uCellOrigin_ = -1;
    GenQueries genQueries_ = nullptr;
    DeleteQueries deleteQueries_ = nullptr;
    BeginQuery beginQuery_ = nullptr;
    EndQuery endQuery_ = nullptr;
    GetQueryObjectuiv getQueryuiv_ = nullptr;
    GetQueryObjectui64v getQueryui64v_ = nullptr;
    std::array<GLuint, 4> queries_{0, 0, 0, 0};
    std::array<bool, 4> pending_{false, false, false, false};
    size_t queryHead_ = 0;
    bool timerReady_ = false;
    bool timing_ = false;
};

std::shared_ptr<FogState>& stateOf(jlong handle) {
    return *reinterpret_cast<std::shared_ptr<FogState>*>(handle);
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_onezee_triptrack_fogspike_fog_NativeFog_createState(
    JNIEnv*, jclass, jdouble street, jdouble halo, jdouble feather, jdouble lodFine, jdouble lodMid) {
    auto* holder = new std::shared_ptr<FogState>(
        std::make_shared<FogState>(Rules{street, halo, feather, lodFine, lodMid}));
    return reinterpret_cast<jlong>(holder);
}

JNIEXPORT void JNICALL Java_com_onezee_triptrack_fogspike_fog_NativeFog_releaseState(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<std::shared_ptr<FogState>*>(handle);
}

// Ownership of the host passes to MapLibre's CustomLayer, which deletes it with the layer. The
// host shares the state with Kotlin, so a mesh set after the layer died goes nowhere, safely.
JNIEXPORT jlong JNICALL Java_com_onezee_triptrack_fogspike_fog_NativeFog_createHost(
    JNIEnv*, jclass, jlong handle, jboolean probe) {
    auto* host = new FogHost(stateOf(handle), probe == JNI_TRUE);
    return reinterpret_cast<jlong>(static_cast<mln::style::CustomLayerHost*>(host));
}

JNIEXPORT void JNICALL Java_com_onezee_triptrack_fogspike_fog_NativeFog_stageLod(
    JNIEnv* env, jclass, jlong handle, jint lod, jfloatArray segments, jdoubleArray bounds, jintArray ranges) {
    if (lod < 0 || lod > 2) return;
    auto data = std::make_shared<LodData>();
    const jsize floats = env->GetArrayLength(segments);
    data->segments.resize(static_cast<size_t>(floats));
    env->GetFloatArrayRegion(segments, 0, floats, data->segments.data());
    const jsize chunkCount = env->GetArrayLength(ranges) / 2;
    std::vector<jdouble> box(static_cast<size_t>(chunkCount) * 6);
    std::vector<jint> range(static_cast<size_t>(chunkCount) * 2);
    env->GetDoubleArrayRegion(bounds, 0, chunkCount * 6, box.data());
    env->GetIntArrayRegion(ranges, 0, chunkCount * 2, range.data());
    data->chunks.reserve(static_cast<size_t>(chunkCount));
    for (jsize i = 0; i < chunkCount; ++i) {
        const auto* bx = &box[static_cast<size_t>(i) * 6];
        data->chunks.push_back({bx[0], bx[1], bx[2], bx[3], bx[4], bx[5], range[static_cast<size_t>(i) * 2],
                                range[static_cast<size_t>(i) * 2 + 1]});
    }
    auto& state = stateOf(handle);
    std::lock_guard<std::mutex> guard(state->lock);
    state->staged[static_cast<size_t>(lod)] = std::move(data);
}

// The three levels swap together: a frame must never mix yesterday's far level with today's fine.
JNIEXPORT void JNICALL Java_com_onezee_triptrack_fogspike_fog_NativeFog_commitMesh(JNIEnv*, jclass, jlong handle) {
    auto& state = stateOf(handle);
    std::lock_guard<std::mutex> guard(state->lock);
    state->committed = state->staged;
    state->meshVersion += 1;
}

JNIEXPORT void JNICALL Java_com_onezee_triptrack_fogspike_fog_NativeFog_setStyle(
    JNIEnv*, jclass, jlong handle, jfloat r, jfloat g, jfloat b, jfloat alpha, jboolean cells, jboolean visible) {
    auto& state = stateOf(handle);
    std::lock_guard<std::mutex> guard(state->lock);
    state->style = StyleState{r, g, b, alpha, cells == JNI_TRUE, visible == JNI_TRUE};
}

JNIEXPORT jdoubleArray JNICALL Java_com_onezee_triptrack_fogspike_fog_NativeFog_takeSamples(
    JNIEnv* env, jclass, jlong handle) {
    std::vector<Sample> samples;
    {
        auto& state = stateOf(handle);
        std::lock_guard<std::mutex> guard(state->lock);
        samples.swap(state->samples);
    }
    const auto count = static_cast<jsize>(samples.size() * 5);
    jdoubleArray out = env->NewDoubleArray(count);
    if (count > 0) {
        env->SetDoubleArrayRegion(out, 0, count, reinterpret_cast<const jdouble*>(samples.data()));
    }
    return out;
}

// Diagnostics: GPU timer available, last mesh upload ms and bytes, how far the camera centre
// projected from the viewport centre (a wrong projection shows up here first), shader failure.
JNIEXPORT jdoubleArray JNICALL Java_com_onezee_triptrack_fogspike_fog_NativeFog_info(JNIEnv* env, jclass, jlong handle) {
    double values[5];
    {
        auto& state = stateOf(handle);
        std::lock_guard<std::mutex> guard(state->lock);
        values[0] = state->gpuTimer ? 1 : 0;
        values[1] = state->uploadMs;
        values[2] = state->uploadBytes;
        values[3] = state->centreErrorDp;
        values[4] = state->shaderFailed ? 1 : 0;
    }
    jdoubleArray out = env->NewDoubleArray(5);
    env->SetDoubleArrayRegion(out, 0, 5, values);
    return out;
}

}  // extern "C"
