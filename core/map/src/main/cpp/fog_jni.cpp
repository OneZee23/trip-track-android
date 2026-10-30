// JNI bridge of com.onezee.triptrack.map.fog.NativeFog. A state handle is a heap-held
// shared_ptr<State>: Kotlin owns one reference, every host MapLibre owns another, so a mesh set after
// the layer died goes nowhere, safely.
#include <jni.h>

#include <memory>
#include <mutex>
#include <vector>

#include "fog_layer.hpp"
#include "fog_state.hpp"

using triptrack::fog::Chunk;
using triptrack::fog::FogHost;
using triptrack::fog::LodData;
using triptrack::fog::Rules;
using triptrack::fog::State;

namespace {

std::shared_ptr<State>& stateOf(jlong handle) { return *reinterpret_cast<std::shared_ptr<State>*>(handle); }

constexpr int kRuleCount = 9;
constexpr int kStatCount = 19;

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nCreate(JNIEnv* env, jclass, jdoubleArray rules) {
    if (env->GetArrayLength(rules) != kRuleCount) return 0;
    double r[kRuleCount];
    env->GetDoubleArrayRegion(rules, 0, kRuleCount, r);
    const Rules parsed{r[0], r[1], r[2], r[3], r[4], r[5], r[6], static_cast<int>(r[7]), r[8]};
    auto* holder = new std::shared_ptr<State>(std::make_shared<State>(parsed));
    return reinterpret_cast<jlong>(holder);
}

JNIEXPORT void JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nRelease(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<std::shared_ptr<State>*>(handle);
}

// Ownership of the host passes to MapLibre's CustomLayer, which deletes it with the layer.
JNIEXPORT jlong JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nCreateHost(JNIEnv*, jclass, jlong handle) {
    auto* host = new FogHost(stateOf(handle));
    return reinterpret_cast<jlong>(static_cast<mln::style::CustomLayerHost*>(host));
}

// One level of detail: segments as Float32 offsets from their chunk's origin, six doubles per chunk
// (origin x/y, min x/y, max x/y in map points) and two ints per chunk (first segment, count). A
// range outside the segments would make the GPU read past the buffer, so such a level is refused.
JNIEXPORT jboolean JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nStageLod(
    JNIEnv* env, jclass, jlong handle, jint lod, jfloatArray segments, jdoubleArray chunks, jintArray ranges) {
    if (lod < 0 || lod > 2) return JNI_FALSE;
    const jsize floats = env->GetArrayLength(segments);
    const jsize chunkCount = env->GetArrayLength(ranges) / 2;
    if (floats % 4 != 0 || env->GetArrayLength(chunks) != chunkCount * 6 || env->GetArrayLength(ranges) % 2 != 0) {
        return JNI_FALSE;
    }
    auto data = std::make_shared<LodData>();
    data->segments.resize(static_cast<size_t>(floats));
    env->GetFloatArrayRegion(segments, 0, floats, data->segments.data());
    std::vector<jdouble> box(static_cast<size_t>(chunkCount) * 6);
    std::vector<jint> range(static_cast<size_t>(chunkCount) * 2);
    env->GetDoubleArrayRegion(chunks, 0, chunkCount * 6, box.data());
    env->GetIntArrayRegion(ranges, 0, chunkCount * 2, range.data());
    const int64_t segmentCount = floats / 4;
    data->chunks.reserve(static_cast<size_t>(chunkCount));
    for (jsize i = 0; i < chunkCount; ++i) {
        const auto* bx = &box[static_cast<size_t>(i) * 6];
        const jint first = range[static_cast<size_t>(i) * 2];
        const jint count = range[static_cast<size_t>(i) * 2 + 1];
        if (first < 0 || count <= 0 || static_cast<int64_t>(first) + count > segmentCount) return JNI_FALSE;
        data->chunks.push_back({bx[0], bx[1], bx[2], bx[3], bx[4], bx[5], first, count});
    }
    auto& state = stateOf(handle);
    std::lock_guard<std::mutex> guard(state->lock);
    state->staged[static_cast<size_t>(lod)] = std::move(data);
    return JNI_TRUE;
}

// The three levels swap together: a frame must never mix yesterday's far level with today's fine.
JNIEXPORT void JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nCommit(JNIEnv*, jclass, jlong handle) {
    auto& state = stateOf(handle);
    std::lock_guard<std::mutex> guard(state->lock);
    state->committed = state->staged;
    state->staged = {};
    state->meshVersion += 1;
}

JNIEXPORT void JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nSetStyle(
    JNIEnv*, jclass, jlong handle, jfloat r, jfloat g, jfloat b, jfloat alpha, jboolean cells, jboolean halfResolution,
    jboolean visible) {
    auto& state = stateOf(handle);
    std::lock_guard<std::mutex> guard(state->lock);
    state->style = triptrack::fog::Style{r, g, b, alpha, cells == JNI_TRUE, visible == JNI_TRUE, halfResolution == JNI_TRUE};
}

JNIEXPORT void JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nSetReveal(
    JNIEnv*, jclass, jlong handle, jdouble latitude, jdouble longitude, jdouble radiusMetres) {
    auto& state = stateOf(handle);
    std::lock_guard<std::mutex> guard(state->lock);
    state->reveal = triptrack::fog::Reveal{latitude, longitude, radiusMetres > 0 ? radiusMetres : 0,
                                           state->rules.revealSolidFraction};
}

JNIEXPORT void JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nSetSuspended(
    JNIEnv*, jclass, jlong handle, jboolean suspended) {
    stateOf(handle)->suspended.store(suspended == JNI_TRUE, std::memory_order_release);
}

JNIEXPORT void JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nSetDiagnostics(
    JNIEnv*, jclass, jlong handle, jboolean enabled) {
    stateOf(handle)->diagnostics.store(enabled == JNI_TRUE, std::memory_order_relaxed);
}

// MapLibre's GL thread only (MapView.queueEvent). The lock is held through the whole release, so
// the host MapLibre owns cannot be destroyed on another thread in the middle of it.
JNIEXPORT jboolean JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nReleaseCoverage(JNIEnv*, jclass, jlong handle) {
    auto& state = stateOf(handle);
    std::lock_guard<std::mutex> guard(state->lock);
    FogHost* host = state->liveHost;
    return host != nullptr && host->releaseCoverageLocked() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdoubleArray JNICALL Java_com_onezee_triptrack_map_fog_NativeFog_nStats(JNIEnv* env, jclass, jlong handle) {
    double values[kStatCount];
    {
        auto& state = stateOf(handle);
        std::lock_guard<std::mutex> guard(state->lock);
        const auto& s = state->stats;
        values[0] = static_cast<double>(s.frames);
        values[1] = s.encodeMs;
        values[2] = s.lastEncodeMs;
        values[3] = s.lastLod;
        values[4] = s.lastChunks;
        values[5] = s.lastSegments;
        values[6] = s.lastHalfWidth;
        values[7] = s.gpuMs;
        values[8] = static_cast<double>(s.uploads);
        values[9] = s.lastUploadMs;
        values[10] = s.uploadBytes;
        values[11] = static_cast<double>(s.suspendedFrames);
        values[12] = static_cast<double>(s.coverageReleases);
        values[13] = static_cast<double>(s.contextLosses);
        values[14] = static_cast<double>(s.initializations);
        values[15] = s.shaderFailed ? 1 : 0;
        values[16] = static_cast<double>(state->meshVersion);
        values[17] = state->liveHost != nullptr ? 1 : 0;
        values[18] = state->suspended.load() ? 1 : 0;
    }
    jdoubleArray out = env->NewDoubleArray(kStatCount);
    env->SetDoubleArrayRegion(out, 0, kStatCount, values);
    return out;
}

}  // extern "C"
