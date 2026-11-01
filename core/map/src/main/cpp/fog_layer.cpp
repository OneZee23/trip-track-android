#include "fog_layer.hpp"

#include <android/log.h>
#include <time.h>

#include <cmath>
#include <cstring>
#include <mutex>
#include <utility>

#include "fog_frame.hpp"

namespace triptrack::fog {
namespace {

constexpr const char* kTag = "TripTrackFog";
// GL_EXT_disjoint_timer_query enums, spelled here so the GLES 2 extension header is not mixed in.
constexpr GLenum kTimeElapsed = 0x88BF;
constexpr GLenum kGpuDisjoint = 0x8FBB;
// iOS logs the encode cost every 120 frames, so the measurement itself stays cheap.
constexpr uint64_t kLogEvery = 120;

double nowMs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<double>(ts.tv_sec) * 1e3 + static_cast<double>(ts.tv_nsec) / 1e6;
}

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
    if (vs == 0 || fs == 0) {
        if (vs) glDeleteShader(vs);
        if (fs) glDeleteShader(fs);
        return 0;
    }
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

// One instance per segment, four vertices as a strip. The rectangle is built in SCREEN points: its
// width then does not depend on the map scale, and the fragment measures the capsule in the same
// numbers (iOS: fog_coverage_vertex).
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
    // A degenerate segment (both ends on one point after projection) takes any direction: the
    // capsule comes out a circle anyway.
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

// Flat fog with premultiplied alpha, the revealed coverage subtracted from it. One colour on
// purpose: the owner's decision of 22 September, as in Fog of World. The cells style quantises the
// coverage per world-anchored cell in this pass only, so with cells off the picture is the reference
// fog byte for byte. The live hole around the car fades LINEARLY from `solid` of its radius, as the
// raster brush and the mask on iOS do (iOS: fog_composite_fragment, fog_cell_coverage). The window
// under Apple's attribution is not ported: MapLibre's attribution is a view above the map.
constexpr const char* kCompositeFragment = R"(#version 300 es
precision highp float;
uniform sampler2D u_coverage;
uniform vec3 u_colour;
uniform float u_alpha;
uniform vec2 u_pxPerDp;
uniform vec2 u_fbOrigin;
uniform vec2 u_fbSize;
uniform float u_side;
uniform vec4 u_gm;
uniform vec4 u_ginv;
uniform vec2 u_gorigin;
uniform vec2 u_cellOrigin;
uniform vec4 u_reveal;
uniform vec2 u_world;
out vec4 o_colour;

// Coverage is addressed in normalised coordinates, so the same code reads the full-size texture
// (nearest: exactly the pixel the coverage pass wrote) and the half-size option.
float sampleDp(vec2 p) {
    vec2 uv = vec2(p.x * u_pxPerDp.x, u_fbSize.y - p.y * u_pxPerDp.y) / u_fbSize;
    return texture(u_coverage, clamp(uv, vec2(0.0), vec2(1.0))).r;
}

// A cell is open WHOLE or closed whole. The coverage is sampled at FIVE points (the middle and 0.3
// of a cell towards each side) and the largest wins, with a low threshold: a corridor one cell wide
// easily misses a cell's centre, and the revealed grid marks a cell the track merely PASSED.
float cellCoverage(vec2 p) {
    mat2 fwd = mat2(u_gm.xy, u_gm.zw);
    mat2 inv = mat2(u_ginv.xy, u_ginv.zw);
    // An offset from the frame's corner is small in Float32; the absolute map point near Krasnodar
    // is ~1.6e8, and indexing cells by it would bring back the jitter chunks exist to avoid.
    vec2 mp = inv * (p - u_gorigin);
    vec2 fromGrid = mp - u_cellOrigin;
    vec2 centreMp = (floor(fromGrid / u_side) + 0.5) * u_side + u_cellOrigin;
    vec2 centre = u_gorigin + fwd * centreMp;
    // A cell on screen is a parallelogram when the map is rotated; the steps come from the same
    // matrix, so the probes stay inside the cell at any heading.
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
    vec2 local = gl_FragCoord.xy - u_fbOrigin;
    vec2 p = vec2(local.x / u_pxPerDp.x, (u_fbSize.y - local.y) / u_pxPerDp.y);
    float coverage = u_side > 0.0 ? cellCoverage(p) : texture(u_coverage, local / u_fbSize).r;
    float a = u_alpha * (1.0 - coverage);
    if (u_reveal.z > 0.0) {
        vec2 delta = p - u_reveal.xy;
        // The nearest repeated circle, even with a rotated map or several worlds on screen.
        delta -= u_world * floor(dot(delta, u_world) / dot(u_world, u_world) + 0.5);
        float d = length(delta) / u_reveal.z;
        a *= clamp((d - u_reveal.w) / max(1.0 - u_reveal.w, 1e-3), 0.0, 1.0);
    }
    o_colour = vec4(u_colour * a, a);
}
)";

}  // namespace

FogHost::FogHost(std::shared_ptr<State> state) : state_(std::move(state)) {
    std::lock_guard<std::mutex> guard(state_->lock);
    state_->liveHost = this;
}

FogHost::~FogHost() {
    std::lock_guard<std::mutex> guard(state_->lock);
    if (state_->liveHost == this) state_->liveHost = nullptr;
}

void FogHost::initialize(const mln::style::CustomLayerInitParameters&) {
    // MapLibre calls this again after contextLost() and after deinitialize(), with a fresh context:
    // the mesh is uploaded anew from the copy the state keeps (uploadedVersion_ is reset).
    context_ = eglGetCurrentContext();
    coverageProgram_ = link(kCoverageVertex, kCoverageFragment);
    compositeProgram_ = link(kCompositeVertex, kCompositeFragment);
    {
        std::lock_guard<std::mutex> guard(state_->lock);
        state_->stats.initializations += 1;
        if (coverageProgram_ == 0 || compositeProgram_ == 0) state_->stats.shaderFailed = true;
    }
    if (coverageProgram_ == 0 || compositeProgram_ == 0) return;
    uM_ = glGetUniformLocation(coverageProgram_, "u_m");
    uTranslation_ = glGetUniformLocation(coverageProgram_, "u_translation");
    uViewport_ = glGetUniformLocation(coverageProgram_, "u_viewport");
    uHalfWidth_ = glGetUniformLocation(coverageProgram_, "u_halfWidth");
    uFeather_ = glGetUniformLocation(coverageProgram_, "u_feather");
    uCoverage_ = glGetUniformLocation(compositeProgram_, "u_coverage");
    uColour_ = glGetUniformLocation(compositeProgram_, "u_colour");
    uAlpha_ = glGetUniformLocation(compositeProgram_, "u_alpha");
    uPxPerDp_ = glGetUniformLocation(compositeProgram_, "u_pxPerDp");
    uFbOrigin_ = glGetUniformLocation(compositeProgram_, "u_fbOrigin");
    uFbSize_ = glGetUniformLocation(compositeProgram_, "u_fbSize");
    uSide_ = glGetUniformLocation(compositeProgram_, "u_side");
    uGm_ = glGetUniformLocation(compositeProgram_, "u_gm");
    uGinv_ = glGetUniformLocation(compositeProgram_, "u_ginv");
    uGorigin_ = glGetUniformLocation(compositeProgram_, "u_gorigin");
    uCellOrigin_ = glGetUniformLocation(compositeProgram_, "u_cellOrigin");
    uReveal_ = glGetUniformLocation(compositeProgram_, "u_reveal");
    uWorld_ = glGetUniformLocation(compositeProgram_, "u_world");
    glGenVertexArrays(1, &segmentVao_);
    glGenVertexArrays(1, &emptyVao_);
    glGenFramebuffers(1, &fbo_);
    glGenBuffers(3, vbo_.data());
    uploadedVersion_ = 0;
    setUpTimer();
}

void FogHost::render(const mln::style::CustomLayerRenderParameters& p) {
    const double started = nowMs();
    // IN THE BACKGROUND THE GPU IS NOT TOUCHED (iOS: FogMetalVeil.draw). MapLibre stops drawing when
    // the map stops, and this guards a frame that slips through: it is forgotten, not drawn, and the
    // host asks for a new one when the app comes back.
    if (state_->suspended.load(std::memory_order_acquire)) {
        std::lock_guard<std::mutex> guard(state_->lock);
        state_->stats.suspendedFrames += 1;
        return;
    }
    if (coverageProgram_ == 0 || compositeProgram_ == 0) return;
    syncMesh();
    Style style;
    Reveal reveal;
    {
        std::lock_guard<std::mutex> guard(state_->lock);
        style = state_->style;
        reveal = state_->reveal;
    }
    if (!style.visible) return;

    // The composite has to land in MapLibre's framebuffer, so the layer asks which one it is and
    // where. Every other piece of state MapLibre re-applies itself after render() returns.
    GLint target = 0;
    GLint viewport[4] = {0, 0, 0, 0};
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &target);
    glGetIntegerv(GL_VIEWPORT, viewport);
    const int fbWidth = viewport[2];
    const int fbHeight = viewport[3];
    if (fbWidth <= 1 || fbHeight <= 1) return;

    Frame frame;
    if (!makeFrame(p, frame)) return;
    const Rules& rules = state_->rules;
    const Corridor corridor = corridorOf(frame, rules);
    const bool diagnostics = state_->diagnostics.load(std::memory_order_relaxed);

    const int coverageWidth = style.halfResolution ? (fbWidth + 1) / 2 : fbWidth;
    const int coverageHeight = style.halfResolution ? (fbHeight + 1) / 2 : fbHeight;
    ensureCoverageTexture(coverageWidth, coverageHeight, style.halfResolution);
    if (coverageTexture_ == 0) return;
    if (diagnostics) beginTimer();

    // Pass 1: corridors into the R8 coverage texture.
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glViewport(0, 0, coverageWidth, coverageHeight);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_CULL_FACE);
    glDepthMask(GL_FALSE);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glClearColor(0, 0, 0, 0);
    glClear(GL_COLOR_BUFFER_BIT);
    glEnable(GL_BLEND);
    // Overlaps take the LARGER coverage, never the sum: a sum gives a bright bead at every joint of
    // two segments and a ring at every turn.
    glBlendEquation(GL_MAX);
    glBlendFunc(GL_ONE, GL_ONE);
    glUseProgram(coverageProgram_);
    glUniform4f(uM_, static_cast<float>(frame.a), static_cast<float>(frame.b), static_cast<float>(frame.c),
                static_cast<float>(frame.d));
    glUniform2f(uViewport_, static_cast<float>(frame.width), static_cast<float>(frame.height));
    glUniform1f(uHalfWidth_, static_cast<float>(corridor.halfWidth));
    glUniform1f(uFeather_, static_cast<float>(corridor.feather));
    glBindVertexArray(segmentVao_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_[static_cast<size_t>(corridor.lod)]);
    glEnableVertexAttribArray(0);
    glVertexAttribDivisor(0, 1);
    int chunks = 0;
    int segments = 0;
    if (const auto& data = gpuData_[static_cast<size_t>(corridor.lod)]) {
        const double pad = corridor.pad;
        for (const Chunk& chunk : data->chunks) {
            // Culling with room for the corridor: a chunk whose segments lie past the edge still
            // paints into the frame with its halo.
            if (chunk.maxY < frame.minY - pad ||
                chunk.minY > frame.maxY + pad) {
                continue;
            }
            const auto copies = visibleWorldCopies(chunk.minX, chunk.maxX, frame.minX, frame.maxX, pad);
            for (int copy = copies.first; copy <= copies.last; ++copy) {
                // Keep the shared VBO: each copy changes only the double-precision origin.
                double tx = 0, ty = 0;
                frame.project(chunk.ox + copy * kWorld - frame.ref.x, chunk.oy - frame.ref.y, tx, ty);
                glUniform2f(uTranslation_, static_cast<float>(tx), static_cast<float>(ty));
                glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE, 16,
                                      reinterpret_cast<const void*>(static_cast<intptr_t>(chunk.first) * 16));
                glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, chunk.count);
                chunks += 1;
                segments += chunk.count;
            }
        }
    }
    glVertexAttribDivisor(0, 0);
    glDisableVertexAttribArray(0);

    // Pass 2: flat fog over the map, premultiplied, into MapLibre's own framebuffer.
    glBindFramebuffer(GL_FRAMEBUFFER, static_cast<GLuint>(target));
    glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    glBlendEquation(GL_FUNC_ADD);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glUseProgram(compositeProgram_);
    glBindVertexArray(emptyVao_);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, coverageTexture_);
    glUniform1i(uCoverage_, 0);
    glUniform3f(uColour_, style.r, style.g, style.b);
    glUniform1f(uAlpha_, style.alpha);
    glUniform2f(uPxPerDp_, static_cast<float>(fbWidth / frame.width), static_cast<float>(fbHeight / frame.height));
    glUniform2f(uFbOrigin_, static_cast<float>(viewport[0]), static_cast<float>(viewport[1]));
    glUniform2f(uFbSize_, static_cast<float>(fbWidth), static_cast<float>(fbHeight));

    const CellGrid grid = style.cells ? cellGridOf(frame, rules) : CellGrid{};
    if (grid.side > 0) {
        // The forward matrix is THE SAME as the coverage pass's: diverging, the cells would drift
        // from the corridors they show.
        double gx = 0, gy = 0;
        frame.project(frame.minX - frame.ref.x, frame.minY - frame.ref.y, gx, gy);
        glUniform1f(uSide_, static_cast<float>(grid.side));
        glUniform4f(uGm_, static_cast<float>(frame.a), static_cast<float>(frame.b), static_cast<float>(frame.c),
                    static_cast<float>(frame.d));
        glUniform4f(uGinv_, static_cast<float>(frame.d / frame.det), static_cast<float>(-frame.b / frame.det),
                    static_cast<float>(-frame.c / frame.det), static_cast<float>(frame.a / frame.det));
        glUniform2f(uGorigin_, static_cast<float>(gx), static_cast<float>(gy));
        glUniform2f(uCellOrigin_, static_cast<float>(grid.originX), static_cast<float>(grid.originY));
    } else {
        glUniform1f(uSide_, 0.0f);
    }

    if (reveal.radiusMetres > 0) {
        const MapPoint at = mapPoint(reveal.latitude, reveal.longitude);
        double cx = 0, cy = 0;
        frame.project(nearestWorldX(at.x, frame.ref.x) - frame.ref.x, at.y - frame.ref.y, cx, cy);
        const double metresPerPoint = metresPerMapPoint(latitudeOf((frame.minY + frame.maxY) / 2)) / frame.pointsPerMapPoint;
        const double radius = std::max(reveal.radiusMetres / metresPerPoint, 1.0);
        glUniform4f(uReveal_, static_cast<float>(cx), static_cast<float>(cy), static_cast<float>(radius),
                    static_cast<float>(reveal.solid));
    } else {
        glUniform4f(uReveal_, 0.0f, 0.0f, 0.0f, 0.0f);
    }
    glUniform2f(uWorld_, static_cast<float>(frame.a * kWorld), static_cast<float>(frame.b * kWorld));
    glDrawArrays(GL_TRIANGLES, 0, 3);
    if (diagnostics) endTimer();

    glBindTexture(GL_TEXTURE_2D, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
    glUseProgram(0);

    const double gpuMs = diagnostics ? takeGpuMs() : -1;
    const double elapsed = nowMs() - started;
    uint64_t frames = 0;
    double encodeMs = 0;
    {
        std::lock_guard<std::mutex> guard(state_->lock);
        Stats& stats = state_->stats;
        stats.frames += 1;
        stats.encodeMs += elapsed;
        stats.lastEncodeMs = elapsed;
        stats.lastLod = corridor.lod;
        stats.lastChunks = chunks;
        stats.lastSegments = segments;
        stats.lastHalfWidth = corridor.halfWidth;
        if (gpuMs >= 0) stats.gpuMs = gpuMs;
        frames = stats.frames;
        encodeMs = stats.encodeMs;
    }
    if (frames % kLogEvery == 0) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "fog: %llu frames, encoding %.2f ms/frame",
                            static_cast<unsigned long long>(frames), encodeMs / static_cast<double>(frames));
    }
}

void FogHost::contextLost() {
    // The context took every GL object with it; deleting them now would act on a dead context.
    // initialize() comes next and builds everything anew.
    forgetGlObjects();
    std::lock_guard<std::mutex> guard(state_->lock);
    state_->stats.contextLosses += 1;
}

void FogHost::deinitialize() {
    // MapLibre calls this with the context current, and possibly without initialize() before it.
    if (coverageProgram_) glDeleteProgram(coverageProgram_);
    if (compositeProgram_) glDeleteProgram(compositeProgram_);
    if (segmentVao_) glDeleteVertexArrays(1, &segmentVao_);
    if (emptyVao_) glDeleteVertexArrays(1, &emptyVao_);
    if (fbo_) glDeleteFramebuffers(1, &fbo_);
    if (coverageTexture_) glDeleteTextures(1, &coverageTexture_);
    for (GLuint& buffer : vbo_) {
        if (buffer) glDeleteBuffers(1, &buffer);
    }
    if (timerReady_ && deleteQueries_) deleteQueries_(static_cast<GLsizei>(queries_.size()), queries_.data());
    forgetGlObjects();
}

bool FogHost::releaseCoverageLocked() {
    // Only on the context the texture was made in: a queued release that runs after MapLibre has
    // paused its thread finds no current context and leaves everything as it was.
    if (context_ == EGL_NO_CONTEXT || eglGetCurrentContext() != context_) return false;
    if (coverageTexture_ == 0) return false;
    glDeleteTextures(1, &coverageTexture_);
    coverageTexture_ = 0;
    textureWidth_ = 0;
    textureHeight_ = 0;
    state_->stats.coverageReleases += 1;
    return true;
}

void FogHost::forgetGlObjects() {
    context_ = EGL_NO_CONTEXT;
    coverageProgram_ = compositeProgram_ = 0;
    segmentVao_ = emptyVao_ = fbo_ = coverageTexture_ = 0;
    vbo_ = {0, 0, 0};
    gpuData_ = {};
    textureWidth_ = textureHeight_ = 0;
    uploadedVersion_ = 0;
    queries_ = {0, 0, 0, 0};
    pending_ = {false, false, false, false};
    timerReady_ = false;
    timing_ = false;
}

void FogHost::ensureCoverageTexture(int width, int height, bool linear) {
    if (coverageTexture_ != 0 && textureWidth_ == width && textureHeight_ == height && textureLinear_ == linear) return;
    if (coverageTexture_ == 0) glGenTextures(1, &coverageTexture_);
    glBindTexture(GL_TEXTURE_2D, coverageTexture_);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, height, 0, GL_RED, GL_UNSIGNED_BYTE, nullptr);
    // Nearest at full size, as on the iPhone; bilinear only for the half-size option.
    const GLint filter = linear ? GL_LINEAR : GL_NEAREST;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, coverageTexture_, 0);
    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "coverage framebuffer incomplete: 0x%x", status);
        glDeleteTextures(1, &coverageTexture_);
        coverageTexture_ = 0;
        textureWidth_ = textureHeight_ = 0;
        return;
    }
    textureWidth_ = width;
    textureHeight_ = height;
    textureLinear_ = linear;
}

// The newest committed mesh goes to the GPU whole; three levels swap together, so a frame never
// mixes yesterday's far level with today's fine one.
void FogHost::syncMesh() {
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
        glBufferData(GL_ARRAY_BUFFER, static_cast<GLsizeiptr>(size), size ? lods[i]->segments.data() : nullptr,
                     GL_STATIC_DRAW);
        bytes += static_cast<double>(size);
        gpuData_[i] = lods[i];
    }
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    uploadedVersion_ = version;
    std::lock_guard<std::mutex> guard(state_->lock);
    state_->stats.uploads += 1;
    state_->stats.lastUploadMs = nowMs() - started;
    state_->stats.uploadBytes = bytes;
}

void FogHost::setUpTimer() {
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
}

void FogHost::beginTimer() {
    if (!timerReady_) return;
    // A ring of queries: a frame's result is read a few frames later and never waited for.
    const size_t slot = queryHead_ % queries_.size();
    if (pending_[slot]) return;
    beginQuery_(kTimeElapsed, queries_[slot]);
    timing_ = true;
}

void FogHost::endTimer() {
    if (!timing_) return;
    endQuery_(kTimeElapsed);
    pending_[queryHead_ % queries_.size()] = true;
    queryHead_ += 1;
    timing_ = false;
}

double FogHost::takeGpuMs() {
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

}  // namespace triptrack::fog
