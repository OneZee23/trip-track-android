package com.onezee.triptrack.fogspike.fog

/** The native fog layer (`fog_layer.cpp`). Handles are pointers owned by the native side. */
object NativeFog {
    init {
        System.loadLibrary("fogspike")
    }

    @JvmStatic external fun createState(street: Double, halo: Double, feather: Double, lodFine: Double, lodMid: Double): Long

    @JvmStatic external fun releaseState(state: Long)

    /** A host for `CustomLayer(id, host)`. MapLibre owns it from then on. */
    @JvmStatic external fun createHost(state: Long, probe: Boolean): Long

    @JvmStatic external fun stageLod(state: Long, lod: Int, segments: FloatArray, bounds: DoubleArray, ranges: IntArray)

    @JvmStatic external fun commitMesh(state: Long)

    @JvmStatic external fun setStyle(state: Long, r: Float, g: Float, b: Float, alpha: Float, cells: Boolean, visible: Boolean)

    /** Five numbers per rendered frame: time ms, CPU ms, GPU ms (-1 unknown), segments, LOD. */
    @JvmStatic external fun takeSamples(state: Long): DoubleArray

    /** GPU timer available, last upload ms, last upload bytes, centre error dp, shader failed. */
    @JvmStatic external fun info(state: Long): DoubleArray

    fun newState(): Long = createState(
        FogRules.STREET_HALF_WIDTH_METRES,
        FogRules.HALO_HALF_WIDTH_POINTS,
        FogRules.FEATHER_RATIO,
        FogRules.LOD_FINE_ABOVE,
        FogRules.LOD_MID_ABOVE,
    )

    fun upload(state: Long, mesh: FogMesh) {
        mesh.lods.forEachIndexed { lod, data -> stageLod(state, lod, data.segments, data.bounds, data.ranges) }
        commitMesh(state)
    }

    fun setStyle(state: Long, style: FogStyle, visible: Boolean = true) {
        val p = style.palette
        setStyle(state, p.red / 255f, p.green / 255f, p.blue / 255f, p.alpha, style.cells, visible)
    }
}
