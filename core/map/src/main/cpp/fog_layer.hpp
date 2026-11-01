// The Atlas fog of war drawn inside MapLibre's own frame, as a custom layer.
//
// iOS: FogShaders.metal (both passes), FogFrameEncoder.swift (uniforms, chunk translation in
// double), FogMetalVeil.swift (frame parameters, background and memory rules), FogCellGrid.swift.
//
// Why a custom layer: MapLibre calls render() between its own layers, with the camera of the frame
// it is drawing, and presents tiles and fog together. The fog cannot float behind the fingers by
// construction; on iOS the same guarantee took presentsWithTransaction plus a draw on every camera
// callback, because the Metal view and MapKit present separately.
#pragma once

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <array>
#include <cstdint>
#include <memory>

#include "fog_state.hpp"
#include "mln/style/layers/custom_layer_host.hpp"

namespace triptrack::fog {

class FogHost final : public mln::style::CustomLayerHost {
public:
    explicit FogHost(std::shared_ptr<State> state);
    ~FogHost() override;

    void initialize(const mln::style::CustomLayerInitParameters&) override;
    void render(const mln::style::CustomLayerRenderParameters& parameters) override;
    void contextLost() override;
    void deinitialize() override;

    // GL thread only, called between frames WITH the state lock held (it must not lock, and the
    // host cannot be destroyed while the caller holds it): gives back the coverage texture, the one
    // thing held besides the mesh (iOS: FogMetalVeil.handleMemoryWarning). The next frame makes a
    // new one. False when this host's context is not current (the renderer is paused): nothing was
    // touched.
    bool releaseCoverageLocked();

private:
    void syncMesh();
    void ensureCoverageTexture(int width, int height, bool linear);
    void forgetGlObjects();
    void setUpTimer();
    void beginTimer();
    void endTimer();
    double takeGpuMs();

    std::shared_ptr<State> state_;
    EGLContext context_ = EGL_NO_CONTEXT;
    GLuint coverageProgram_ = 0;
    GLuint compositeProgram_ = 0;
    GLuint segmentVao_ = 0;
    GLuint emptyVao_ = 0;
    GLuint fbo_ = 0;
    GLuint coverageTexture_ = 0;
    int textureWidth_ = 0;
    int textureHeight_ = 0;
    bool textureLinear_ = false;
    std::array<GLuint, 3> vbo_{0, 0, 0};
    std::array<std::shared_ptr<const LodData>, 3> gpuData_{};
    uint64_t uploadedVersion_ = 0;

    GLint uM_ = -1, uTranslation_ = -1, uViewport_ = -1, uHalfWidth_ = -1, uFeather_ = -1;
    GLint uCoverage_ = -1, uColour_ = -1, uAlpha_ = -1, uPxPerDp_ = -1, uFbOrigin_ = -1, uFbSize_ = -1;
    GLint uSide_ = -1, uGm_ = -1, uGinv_ = -1, uGorigin_ = -1, uCellOrigin_ = -1, uReveal_ = -1, uWorld_ = -1;

    // GL_EXT_disjoint_timer_query, for the GPU time per frame the emulator cannot show.
    using GenQueries = void (*)(GLsizei, GLuint*);
    using DeleteQueries = void (*)(GLsizei, const GLuint*);
    using BeginQuery = void (*)(GLenum, GLuint);
    using EndQuery = void (*)(GLenum);
    using GetQueryObjectuiv = void (*)(GLuint, GLenum, GLuint*);
    using GetQueryObjectui64v = void (*)(GLuint, GLenum, GLuint64*);
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

}  // namespace triptrack::fog
