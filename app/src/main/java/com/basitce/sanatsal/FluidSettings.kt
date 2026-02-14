package com.basitce.sanatsal

class FluidSettings {
    // Simulation
    var velocityDissipation = 0.98f
    var dyeDissipation = 0.97f
    var pressureIterations = 20
    var splatRadius = 0.003f
    var splatForce = 6000f
    var vorticity = 2.0f
    var gravityScale = 0.0004f

    // Effects
    var bloomEnabled = true
    var bloomIntensity = 0.8f
    var bloomThreshold = 0.6f
    var neonGlow = false
    var chromaticAberration = 1.0f
    var particlesEnabled = false
    var amoledBlack = true
    var pressureSensitivity = true
    var microphoneEnabled = false
    var microphoneSensitivity = 1.0f
    
    // 3D & World
    var renderMode3D = false
    var worldMode = true // New Pivot Default
    var worldType = 1 // 1=Geometric (Cleaner than Web)
    var cameraMode = 1 // 0=Straight, 1=Wander
    var distortion = 0.2f // Less    
    // World / Raymarching
    var densityScale = 2.0f
    var raySteps = 40 // Optimized Standard (was 64) 
    var lightIntensity = 1.2f 
    var fogEnabled = true // Restore Fog default (Standard Mode)
    var opacity = 0.5f // Default fog density
    var travelSpeed = 0.2f 
    var worldScale = 1.0f
    var pathWidth = 2.0f 
    
    // Background Particles
    var starDustEnabled = false // Default OFF per user request
    var bgParticleCount = 0.5f  
    var bgParticleSize = 0.03f // Ultra-tiny stars (User requested 10x reduction)
    var bgParticleSpeed = 0.3f  
    var rimLighting = 5.0f 
    
    // Performance
    var resolutionScale = 0.7f  // Balanced High-Res (was 0.4)
    var targetFPS = 60         
    
    // New Focus/battery features
    var amoledTheme = false // Default: BEAUTIFUL MODE (False)
    var blackThreshold = 0.03f // Reduced from 0.1 to avoid banding (horror game look) // 30, 60, 120 (Max)

    // General
    var autoPlay = false
    var autoPlayInterval = 800L
    var palette = ColorPalette.NEON
    
    // Custom Palette Storage
    var customColors = arrayOf(
        floatArrayOf(1.0f, 0.0f, 0.5f), // Color 1 (Base)
        floatArrayOf(0.0f, 1.0f, 1.0f), // Color 2 (Atmosphere)
        floatArrayOf(1.0f, 0.8f, 0.0f)  // Color 3 (Highlight)
    )
    
    companion object {
        val WORLD_TYPES = listOf(
            "Örümcek Ağı", 
            "Geometrik", 
            "Neon Halkalar", 
            "Uzaylı Yüzey",
            "Siber Tünel", // Future placeholder
            "Kristal Mağara" // Future placeholder
        )
        
        val CAMERA_MODES = listOf(
            "Düz Uçuş", 
            "Serbest Gezi",
            "Sinematik", // Future
            "Rastgele"   // Future
        )
    }
}
