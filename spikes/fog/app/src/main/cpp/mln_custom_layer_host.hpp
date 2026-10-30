// ABI mirror of MapLibre Native's custom layer interface at tag android-v13.7.1:
// include/mln/style/layers/custom_layer_host.hpp, custom_layer_init_parameters.hpp and
// custom_layer_render_parameters.hpp. MapLibre Native is BSD 2-Clause licensed:
// Copyright (c) 2021 MapLibre contributors, (c) 2018-2021 MapTiler.com, (c) 2014-2020 Mapbox.
//
// The AAR ships no headers. MapLibre receives the host as a raw pointer from
// `CustomLayer(id, long)` and calls it only through the vtable, so what must match is the order
// of the virtual functions and the layout of the render parameters. Compare this file with the
// tag on every MapLibre upgrade: a reordered vtable calls the wrong method and crashes.
#pragma once

#include <array>

namespace mln {
namespace gfx {
class Context;
}  // namespace gfx

namespace style {

struct CustomLayerInitParameters {
    virtual ~CustomLayerInitParameters() = default;
};

struct CustomLayerRenderParameters {
    double width;   // logical pixels (dp)
    double height;
    double latitude;
    double longitude;
    double zoom;
    double bearing;
    double pitch;
    double fieldOfView;
    // World coordinates in logical pixels at the current zoom (world size 512 * 2^zoom) to clip.
    std::array<double, 16> projectionMatrix;
    std::array<double, 16> nearClippedProjectionMatrix;
};

class CustomLayerHost {
public:
    virtual ~CustomLayerHost() = default;
    virtual void initialize(const CustomLayerInitParameters&) = 0;
    virtual void preRender(const mln::gfx::Context&, const CustomLayerRenderParameters&) {}
    virtual void render(const CustomLayerRenderParameters&) = 0;
    virtual void contextLost() = 0;
    virtual void deinitialize() = 0;
};

}  // namespace style
}  // namespace mln
