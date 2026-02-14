package com.basitce.sanatsal

enum class ColorPalette(val displayName: String, val colors: Array<FloatArray>) {
    NEON("Neon", arrayOf(
        floatArrayOf(1.0f, 0.0f, 0.8f),
        floatArrayOf(0.0f, 1.0f, 0.8f),
        floatArrayOf(1.0f, 1.0f, 0.0f),
        floatArrayOf(0.4f, 0.0f, 1.0f),
        floatArrayOf(0.0f, 1.0f, 0.2f),
        floatArrayOf(1.0f, 0.4f, 0.0f)
    )),
    OCEAN("Okyanus", arrayOf(
        floatArrayOf(0.0f, 0.3f, 0.8f),
        floatArrayOf(0.0f, 0.7f, 0.9f),
        floatArrayOf(0.0f, 0.9f, 0.7f),
        floatArrayOf(0.2f, 0.5f, 0.8f),
        floatArrayOf(0.0f, 0.8f, 0.5f),
        floatArrayOf(0.1f, 0.2f, 0.5f)
    )),
    LAVA("Lav", arrayOf(
        floatArrayOf(1.0f, 0.2f, 0.0f),
        floatArrayOf(1.0f, 0.5f, 0.0f),
        floatArrayOf(1.0f, 0.8f, 0.0f),
        floatArrayOf(0.8f, 0.1f, 0.0f),
        floatArrayOf(1.0f, 0.0f, 0.0f),
        floatArrayOf(0.6f, 0.0f, 0.0f)
    )),
    AURORA("Aurora", arrayOf(
        floatArrayOf(0.0f, 0.8f, 0.4f),
        floatArrayOf(0.2f, 0.6f, 0.9f),
        floatArrayOf(0.5f, 0.0f, 0.8f),
        floatArrayOf(0.0f, 1.0f, 0.6f),
        floatArrayOf(0.8f, 0.2f, 0.6f),
        floatArrayOf(0.1f, 0.9f, 0.9f)
    )),
    NEBULA("Nebula", arrayOf(
        floatArrayOf(0.6f, 0.0f, 0.9f),
        floatArrayOf(0.9f, 0.0f, 0.5f),
        floatArrayOf(0.2f, 0.0f, 0.6f),
        floatArrayOf(0.0f, 0.2f, 0.8f),
        floatArrayOf(1.0f, 0.3f, 0.6f),
        floatArrayOf(0.4f, 0.1f, 0.7f)
    )),
    CUSTOM("Özel", arrayOf(
        floatArrayOf(1.0f, 1.0f, 1.0f), // Will be overridden by settings
        floatArrayOf(0.5f, 0.5f, 0.5f),
        floatArrayOf(0.0f, 0.0f, 0.0f),
        floatArrayOf(0.0f, 0.0f, 0.0f),
        floatArrayOf(0.0f, 0.0f, 0.0f),
        floatArrayOf(0.0f, 0.0f, 0.0f)
    ));

    fun getColor(index: Int): FloatArray {
        return colors[index % colors.size]
    }

    fun next(): ColorPalette {
        val vals = entries.toTypedArray()
        return vals[(ordinal + 1) % vals.size]
    }
}
