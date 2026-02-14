package com.basitce.sanatsal

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FluidRenderer(private val context: Context, val settings: FluidSettings) : GLSurfaceView.Renderer {

    // Removed internal settings val since it's now passed in constructor
    // val settings = FluidSettings()

    companion object {
        private const val PARTICLE_RES = 64 // 64x64 = 4096 particles
    }

    // Dimensions
    private var screenWidth = 0
    private var screenHeight = 0
    private var simWidth = 0
    private var simHeight = 0
    private var dyeWidth = 0
    private var dyeHeight = 0

    // Shader programs
    private var advectionProgram = 0
    private var splatProgram = 0
    private var gravityProgram = 0
    private var curlProgram = 0
    private var vorticityProgram = 0
    private var divergenceProgram = 0
    private var pressureProgram = 0
    private var gradientSubtractProgram = 0
    private var displayProgram = 0
    private var particleUpdateProgram = 0
    private var particleRenderProgram = 0
    private var display3DProgram = 0
    private var worldProgram = 0

    // FBOs
    private var velocity: DoubleFBO? = null
    private var pressure: DoubleFBO? = null
    private var dye: DoubleFBO? = null
    private var divergenceFBO: FBO? = null
    private var curlFBO: FBO? = null
    private var particles: DoubleFBO? = null

    // Quad
    private var quadVAO = 0
    private var quadVBO = 0
    private var particleIndicesVBO = 0
    
    // World FBO
    private var worldFramebuffer = 0
    private var worldTexture = 0
    private var worldTextureWidth = 0
    private var worldTextureHeight = 0

    // Touch
    data class Pointer(
        var x: Float = 0f, var y: Float = 0f,
        var prevX: Float = 0f, var prevY: Float = 0f,
        var pressure: Float = 1f,
        var color: FloatArray = floatArrayOf(1f, 1f, 1f)
    )

    private val pointers = HashMap<Int, Pointer>()
    private val splatQueue = ArrayList<SplatData>()

    data class SplatData(
        val x: Float, val y: Float,
        val dx: Float, val dy: Float,
        val color: FloatArray,
        val radiusScale: Float = 1f
    )

    // Gravity
    @Volatile var gravityX = 0f
    @Volatile var gravityY = 0f

    // Auto mode
    private var lastAutoSplatTime = 0L

    // Intro animation
    private var introStartTime = -1L
    private var introStepIndex = 0
    private var introFinished = false

    data class IntroStep(val timeMs: Long, val x: Float, val y: Float, val angle: Float, val force: Float, val radius: Float, val colorIndex: Int)

    private val introSequence = listOf(
        IntroStep(0, 0.5f, 0.5f, 0f, 8000f, 0.008f, 0),
        IntroStep(50, 0.5f, 0.5f, 120f, 8000f, 0.008f, 1),
        IntroStep(100, 0.5f, 0.5f, 240f, 8000f, 0.008f, 2),
        IntroStep(400, 0.35f, 0.6f, 45f, 5000f, 0.006f, 3),
        IntroStep(600, 0.65f, 0.65f, 135f, 5000f, 0.006f, 4),
        IntroStep(800, 0.6f, 0.35f, 225f, 5000f, 0.006f, 5),
        IntroStep(1000, 0.35f, 0.35f, 315f, 5000f, 0.006f, 0),
        IntroStep(1500, 0.1f, 0.5f, 0f, 6000f, 0.005f, 1),
        IntroStep(1700, 0.9f, 0.5f, 180f, 6000f, 0.005f, 2),
        IntroStep(1900, 0.5f, 0.1f, 90f, 6000f, 0.005f, 3),
        IntroStep(2100, 0.5f, 0.9f, 270f, 6000f, 0.005f, 4),
        IntroStep(2800, 0.5f, 0.5f, 60f, 10000f, 0.01f, 5)
    )

    private var lastFrameTime = 0L

    // Audio
    @Volatile var currentAmplitude = 0f

    // ---- Touch interface ----

    fun onTouchDown(id: Int, x: Float, y: Float, p: Float) {
        synchronized(pointers) {
            val color = settings.palette.getColor(id)
            pointers[id] = Pointer(x, y, x, y, p, color)
        }
    }

    fun onTouchMove(id: Int, x: Float, y: Float, p: Float) {
        synchronized(pointers) {
            val pointer = pointers[id] ?: return
            pointer.prevX = pointer.x
            pointer.prevY = pointer.y
            pointer.x = x
            pointer.y = y
            pointer.pressure = p

            val forceScale = if (settings.pressureSensitivity) p * 2f else 1f
            val dx = (x - pointer.prevX) * settings.splatForce * forceScale
            val dy = (y - pointer.prevY) * settings.splatForce * forceScale

            if (dx * dx + dy * dy > 0.0001f) {
                synchronized(splatQueue) {
                    val radiusScale = if (settings.pressureSensitivity) 0.5f + p else 1f
                    splatQueue.add(SplatData(x, y, dx, dy, pointer.color, radiusScale))
                }
            }
        }
    }

    fun onTouchUp(id: Int) {
        synchronized(pointers) { pointers.remove(id) }
    }

    // ---- Renderer ----

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)

        val vertSrc = loadRawResource(R.raw.vertex_shader)
        advectionProgram = createProgram(vertSrc, loadRawResource(R.raw.advection))
        splatProgram = createProgram(vertSrc, loadRawResource(R.raw.splat))
        gravityProgram = createProgram(vertSrc, loadRawResource(R.raw.gravity))
        curlProgram = createProgram(vertSrc, loadRawResource(R.raw.curl))
        vorticityProgram = createProgram(vertSrc, loadRawResource(R.raw.vorticity))
        divergenceProgram = createProgram(vertSrc, loadRawResource(R.raw.divergence))
        pressureProgram = createProgram(vertSrc, loadRawResource(R.raw.pressure))
        gradientSubtractProgram = createProgram(vertSrc, loadRawResource(R.raw.gradient_subtract))
        displayProgram = createProgram(vertSrc, loadRawResource(R.raw.display))
        display3DProgram = createProgram(vertSrc, loadRawResource(R.raw.display_3d))
        worldProgram = createProgram(vertSrc, loadRawResource(R.raw.world_render))

        // Particles
        particleUpdateProgram = createProgram(vertSrc, loadRawResource(R.raw.particle_update))
        val pVert = loadRawResource(R.raw.particle_render_vertex)
        val pFrag = loadRawResource(R.raw.particle_render_fragment)
        particleRenderProgram = createProgram(pVert, pFrag)

        initQuad()
        initParticleIndices()
        lastFrameTime = System.nanoTime()
        lastAutoSplatTime = System.currentTimeMillis()
        introStartTime = System.currentTimeMillis() // Fix uTime precision
        introStepIndex = 0
        introFinished = true // Skip intro for user control
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        simWidth = (width * 0.5f).toInt().coerceAtLeast(128)
        simHeight = (height * 0.5f).toInt().coerceAtLeast(128)
        dyeWidth = width
        dyeHeight = height

        velocity?.delete(); pressure?.delete(); dye?.delete()
        divergenceFBO?.delete(); curlFBO?.delete()

        velocity = createDoubleFBO(simWidth, simHeight)
        pressure = createDoubleFBO(simWidth, simHeight)
        dye = createDoubleFBO(dyeWidth, dyeHeight)
        divergenceFBO = createSingleFBO(simWidth, simHeight)
        curlFBO = createSingleFBO(simWidth, simHeight)

        // Particles texture (RG=pos, B=life, A=age)
        particles = createDoubleFBO(PARTICLE_RES, PARTICLE_RES)
        initParticles()

        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val vel = velocity ?: return
        val pres = pressure ?: return
        val d = dye ?: return
        val divFBO = divergenceFBO ?: return
        val cFBO = curlFBO ?: return

        val now = System.nanoTime()
        val dt = ((now - lastFrameTime) / 1_000_000_000.0f).coerceIn(0.001f, 0.033f)
        lastFrameTime = now

        // Read current settings
        val splatR = settings.splatRadius
        val splatF = settings.splatForce
        val velDiss = settings.velocityDissipation
        val dyeDiss = settings.dyeDissipation
        val pressIter = settings.pressureIterations
        val vort = settings.vorticity
        val gravScale = settings.gravityScale
        val autoPlay = settings.autoPlay
        val autoInterval = settings.autoPlayInterval
        val pal = settings.palette

        // 0. Process Audio Wind
        if (settings.microphoneEnabled && currentAmplitude > 0.01f) {
            val windForce = currentAmplitude * settings.microphoneSensitivity * 2000f // Scale force
            // Apply wind from bottom center upwards
            val w = simWidth
            val h = simHeight
            val centerX = w / 2f / w
            val bottomY = 0.1f // Near bottom
            // Add upward velocity
            applySplat(vel, 0.5f, 0.1f, 0f, windForce, 0.2f, w, h)
            // Add some dye
            val c = pal.getColor(0)
            applySplat(d, 0.5f, 0.1f, c[0], c[1], c[2], 0.2f, dyeWidth, dyeHeight)
        }

        // 1. Process touch splats
        val splats: List<SplatData>
        synchronized(splatQueue) { splats = ArrayList(splatQueue); splatQueue.clear() }
        for (s in splats) {
            applySplat(vel, s.x, s.y, s.dx, s.dy, splatR * s.radiusScale, simWidth, simHeight)
            applySplat(d, s.x, s.y, s.color[0], s.color[1], s.color[2], splatR * 2f * s.radiusScale, dyeWidth, dyeHeight)
        }

        // 2. Intro animation
        if (!introFinished) {
            val nowMs = System.currentTimeMillis()
            if (introStartTime < 0) introStartTime = nowMs
            val elapsed = nowMs - introStartTime
            while (introStepIndex < introSequence.size && introSequence[introStepIndex].timeMs <= elapsed) {
                val step = introSequence[introStepIndex]
                val rad = Math.toRadians(step.angle.toDouble()).toFloat()
                val fdx = cos(rad) * step.force
                val fdy = sin(rad) * step.force
                val color = pal.getColor(step.colorIndex)
                applySplat(vel, step.x, step.y, fdx, fdy, step.radius, simWidth, simHeight)
                applySplat(d, step.x, step.y, color[0], color[1], color[2], step.radius * 2.5f, dyeWidth, dyeHeight)
                introStepIndex++
            }
            if (introStepIndex >= introSequence.size) introFinished = true
        }

        // 3. Auto mode
        if (autoPlay && introFinished) {
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastAutoSplatTime > autoInterval) {
                lastAutoSplatTime = nowMs
                val x = Random.nextFloat()
                val y = Random.nextFloat()
                val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
                val force = 3000f * (0.5f + Random.nextFloat())
                val dx = cos(angle) * force
                val dy = sin(angle) * force
                val color = pal.getColor(Random.nextInt(pal.colors.size))
                applySplat(vel, x, y, dx, dy, splatR * 1.5f, simWidth, simHeight)
                applySplat(d, x, y, color[0], color[1], color[2], splatR * 3f, dyeWidth, dyeHeight)
            }
        }

        // 4. Gravity
        if (gravityX != 0f || gravityY != 0f) {
            GLES30.glViewport(0, 0, simWidth, simHeight)
            GLES30.glUseProgram(gravityProgram)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, vel.read.texture)
            setUniform(gravityProgram, "uVelocity", 0)
            setUniform(gravityProgram, "uGravity", gravityX * gravScale, gravityY * gravScale)
            setUniform(gravityProgram, "uDt", dt)
            bindFBO(vel.write); drawQuad(); vel.swap()
        }

        // 3. Advection (Velocity)
        // If World Mode is on, we can skip all fluid physics!
        if (settings.worldMode) {
            // Just render the world and return
            renderWorld()
            return
        }

        GLES30.glViewport(0, 0, simWidth, simHeight)
        advect(vel.read.texture, vel, dt, velDiss, simWidth, simHeight)

        // 6. Advect dye
        GLES30.glViewport(0, 0, dyeWidth, dyeHeight)
        advect(vel.read.texture, d, dt, dyeDiss, dyeWidth, dyeHeight)

        // 7. Curl
        GLES30.glViewport(0, 0, simWidth, simHeight)
        GLES30.glUseProgram(curlProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, vel.read.texture)
        setUniform(curlProgram, "uVelocity", 0)
        setUniform(curlProgram, "uTexelSize", 1f / simWidth, 1f / simHeight)
        bindFBO(cFBO); drawQuad()

        // 8. Vorticity confinement
        if (vort > 0f) {
            GLES30.glUseProgram(vorticityProgram)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, vel.read.texture)
            setUniform(vorticityProgram, "uVelocity", 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cFBO.texture)
            setUniform(vorticityProgram, "uCurl", 1)
            setUniform(vorticityProgram, "uTexelSize", 1f / simWidth, 1f / simHeight)
            setUniform(vorticityProgram, "uVorticity", vort)
            setUniform(vorticityProgram, "uDt", dt)
            bindFBO(vel.write); drawQuad(); vel.swap()
        }

        // 9. Divergence
        GLES30.glUseProgram(divergenceProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, vel.read.texture)
        setUniform(divergenceProgram, "uVelocity", 0)
        setUniform(divergenceProgram, "uTexelSize", 1f / simWidth, 1f / simHeight)
        bindFBO(divFBO); drawQuad()

        // 10. Clear pressure
        clearFBO(pres.read); clearFBO(pres.write)

        // 11. Pressure solve
        GLES30.glUseProgram(pressureProgram)
        setUniform(pressureProgram, "uTexelSize", 1f / simWidth, 1f / simHeight)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, divFBO.texture)
        setUniform(pressureProgram, "uDivergence", 1)
        for (i in 0 until pressIter) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pres.read.texture)
            setUniform(pressureProgram, "uPressure", 0)
            bindFBO(pres.write); drawQuad(); pres.swap()
        }

        // 12. Gradient subtraction
        GLES30.glUseProgram(gradientSubtractProgram)
        setUniform(gradientSubtractProgram, "uTexelSize", 1f / simWidth, 1f / simHeight)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pres.read.texture)
        setUniform(gradientSubtractProgram, "uPressure", 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, vel.read.texture)
        setUniform(gradientSubtractProgram, "uVelocity", 1)
        bindFBO(vel.write); drawQuad(); vel.swap()

        // 13. Update Particles
        val p = particles
        if (p != null && settings.particlesEnabled) {
            GLES30.glViewport(0, 0, PARTICLE_RES, PARTICLE_RES)
            GLES30.glUseProgram(particleUpdateProgram)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, p.read.texture)
            setUniform(particleUpdateProgram, "uParticles", 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, vel.read.texture)
            setUniform(particleUpdateProgram, "uVelocity", 1)
            setUniform(particleUpdateProgram, "uDt", dt)
            setUniform(particleUpdateProgram, "uRandomSeed", Random.nextFloat())
            bindFBO(p.write); drawQuad(); p.swap()
        }

        // 14. Display
        GLES30.glViewport(0, 0, screenWidth, screenHeight)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        
        if (settings.renderMode3D) {
            GLES30.glUseProgram(display3DProgram)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, d.read.texture)
            setUniform(display3DProgram, "uDye", 0)
            setUniform(display3DProgram, "uResolution", screenWidth.toFloat(), screenHeight.toFloat())
            setUniform(display3DProgram, "uTime", (System.currentTimeMillis() - introStartTime) / 1000f) // approximate time
            setUniform(display3DProgram, "uDensityScale", settings.densityScale)
            setUniform(display3DProgram, "uRaySteps", settings.raySteps)
            setUniform(display3DProgram, "uLightIntensity", settings.lightIntensity)
            setUniform(display3DProgram, "uOpacity", settings.opacity)
        } else {
            GLES30.glUseProgram(displayProgram)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, d.read.texture)
            setUniform(displayProgram, "uDye", 0)
            setUniform(displayProgram, "uBloomIntensity", if (settings.bloomEnabled) settings.bloomIntensity else 0f)
            setUniform(displayProgram, "uBloomThreshold", settings.bloomThreshold)
            setUniform(displayProgram, "uNeonGlow", if (settings.neonGlow) 1f else 0f)
            setUniform(displayProgram, "uBackground", if (settings.amoledBlack) 0f else 0.015f)
            setUniform(displayProgram, "uChromaticAberration", settings.chromaticAberration)
        }
        drawQuad()

        // 15. Render Particles
        if (p != null && settings.particlesEnabled) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE) // Additive
            GLES30.glUseProgram(particleRenderProgram)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, p.read.texture)
            setUniform(particleRenderProgram, "uParticles", 0)
            setUniform(particleRenderProgram, "uParticlesRes", PARTICLE_RES)
            setUniform(particleRenderProgram, "uPointSize", 40f) // Larger, softer glow
            setUniform(particleRenderProgram, "uColor", 1f, 1f, 1f) // White sparkles
            
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, particleIndicesVBO)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 1, GLES30.GL_FLOAT, false, 0, 0)
            GLES30.glDrawArrays(GLES30.GL_POINTS, 0, PARTICLE_RES * PARTICLE_RES)
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
    }

    // ---- Helpers ----

    private fun applySplat(target: DoubleFBO, x: Float, y: Float, vx: Float, vy: Float, radius: Float, w: Int, h: Int) {
        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(splatProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.read.texture)
        setUniform(splatProgram, "uTarget", 0)
        setUniform(splatProgram, "uPoint", x, y)
        setUniform(splatProgram, "uValue", vx, vy, 0f)
        setUniform(splatProgram, "uRadius", radius)
        setUniform(splatProgram, "uAspectRatio", w.toFloat() / h.toFloat())
        bindFBO(target.write); drawQuad(); target.swap()
    }

    private fun applySplat(target: DoubleFBO, x: Float, y: Float, r: Float, g: Float, b: Float, radius: Float, w: Int, h: Int) {
        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(splatProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.read.texture)
        setUniform(splatProgram, "uTarget", 0)
        setUniform(splatProgram, "uPoint", x, y)
        setUniform(splatProgram, "uValue", r, g, b)
        setUniform(splatProgram, "uRadius", radius)
        setUniform(splatProgram, "uAspectRatio", w.toFloat() / h.toFloat())
        bindFBO(target.write); drawQuad(); target.swap()
    }

    private fun advect(velTex: Int, target: DoubleFBO, dt: Float, dissipation: Float, w: Int, h: Int) {
        GLES30.glUseProgram(advectionProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, velTex)
        setUniform(advectionProgram, "uVelocity", 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.read.texture)
        setUniform(advectionProgram, "uSource", 1)
        setUniform(advectionProgram, "uTexelSize", 1f / w, 1f / h)
        setUniform(advectionProgram, "uDt", dt)
        setUniform(advectionProgram, "uDissipation", dissipation)
        bindFBO(target.write); drawQuad(); target.swap()
    }

    // ---- FBO ----

    class FBO(val framebuffer: Int, val texture: Int, val width: Int, val height: Int) {
        fun delete() {
            GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        }
    }

    class DoubleFBO(var read: FBO, var write: FBO) {
        fun swap() { val t = read; read = write; write = t }
        fun delete() { read.delete(); write.delete() }
    }

    private fun createSingleFBO(w: Int, h: Int): FBO {
        val tex = IntArray(1); GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
        val fbo = IntArray(1); GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex[0], 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return FBO(fbo[0], tex[0], w, h)
    }
    
    private fun initWorldFBO(w: Int, h: Int) {
        if (worldFramebuffer != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(worldFramebuffer), 0)
            GLES30.glDeleteTextures(1, intArrayOf(worldTexture), 0)
        }
        val tex = IntArray(1); GLES30.glGenTextures(1, tex, 0)
        worldTexture = tex[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, worldTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        
        val fbo = IntArray(1); GLES30.glGenFramebuffers(1, fbo, 0)
        worldFramebuffer = fbo[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, worldFramebuffer)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, worldTexture, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        
        worldTextureWidth = w
        worldTextureHeight = h
    }

    private fun createDoubleFBO(w: Int, h: Int) = DoubleFBO(createSingleFBO(w, h), createSingleFBO(w, h))

    private fun bindFBO(fbo: FBO) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo.framebuffer)
        GLES30.glViewport(0, 0, fbo.width, fbo.height)
    }

    private fun clearFBO(fbo: FBO) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo.framebuffer)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    // ---- Quad ----

    private fun initQuad() {
        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        val vao = IntArray(1); GLES30.glGenVertexArrays(1, vao, 0); quadVAO = vao[0]
        GLES30.glBindVertexArray(quadVAO)
        val vbo = IntArray(1); GLES30.glGenBuffers(1, vbo, 0); quadVBO = vbo[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVBO)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun renderWorld() {
        // 0. FPS Limiter
        limitFPS()

        val time = (System.currentTimeMillis() - introStartTime) / 1000f

        // 1. Raymarch 3D World to FBO
        
        // Calculate Target Resolution
        val targetW = (screenWidth * settings.resolutionScale).toInt().coerceAtLeast(32)
        val targetH = (screenHeight * settings.resolutionScale).toInt().coerceAtLeast(32)
        
        // Resize FBO if resolution scale changed
        if (worldTextureWidth != targetW || worldTextureHeight != targetH) {
            initWorldFBO(targetW, targetH)
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, worldFramebuffer)
        GLES30.glViewport(0, 0, targetW, targetH)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        
        GLES30.glUseProgram(worldProgram)
        setUniform(worldProgram, "uResolution", targetW.toFloat(), targetH.toFloat())
        setUniform(worldProgram, "uTime", time)
        setUniform(worldProgram, "uSpeed", settings.travelSpeed)
        setUniform(worldProgram, "uDetail", settings.densityScale)
        setUniform(worldProgram, "uRaySteps", settings.raySteps)
        setUniform(worldProgram, "uColorShift", settings.gravityScale * 10.0f) // Recycling gravity as shift
        setUniform(worldProgram, "uLightIntensity", settings.lightIntensity)
        setUniform(worldProgram, "uOpacity", if (settings.fogEnabled) settings.opacity else 0f)
        setUniform(worldProgram, "uRimIntensity", settings.rimLighting)
        
        setUniform(worldProgram, "uWorldType", settings.worldType)
        setUniform(worldProgram, "uCameraMode", settings.cameraMode)
        setUniform(worldProgram, "uDistortion", settings.distortion)
        setUniform(worldProgram, "uPathWidth", settings.pathWidth)
        setUniform(worldProgram, "uWorldScale", settings.worldScale)
        
        // Pass Palette Colors
            val c1: FloatArray
            val c2: FloatArray
            val c3: FloatArray
            
            if (settings.palette == ColorPalette.CUSTOM) {
                 c1 = settings.customColors[0]
                 c2 = settings.customColors[1]
                 c3 = settings.customColors[2]
            } else {
                 c1 = settings.palette.colors[0]
                 c2 = settings.palette.colors[1]
                 c3 = settings.palette.colors[2]
            }

            setUniform(worldProgram, "uColor1", c1[0], c1[1], c1[2])
            setUniform(worldProgram, "uColor2", c2[0], c2[1], c2[2])
            setUniform(worldProgram, "uColor3", c3[0], c3[1], c3[2])
            
            setUniform(worldProgram, "uPartCount", if (settings.starDustEnabled) settings.bgParticleCount else 0f)
            setUniform(worldProgram, "uPartSize", settings.bgParticleSize)
            setUniform(worldProgram, "uPartSpeed", settings.bgParticleSpeed)
            
            setUniform(worldProgram, "uAmoled", if (settings.amoledTheme) 1f else 0f)
            setUniform(worldProgram, "uBlackThreshold", settings.blackThreshold)
            
            drawQuad()
            
            // 2. Upscale to Screen
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, screenWidth, screenHeight)
            
            // Use standard display program to copy texture to screen
            GLES30.glUseProgram(displayProgram)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, worldTexture) // Use WORLD TEXTURE
            setUniform(displayProgram, "uDye", 0)
            // Zero out other effects to just show texture
            setUniform(displayProgram, "uBloomIntensity", 0f)
            setUniform(displayProgram, "uBloomThreshold", 1.0f) // High threshold = no bloom
            setUniform(displayProgram, "uNeonGlow", 0f) 
            setUniform(displayProgram, "uBackground", 0f)
            setUniform(displayProgram, "uChromaticAberration", 0f)
            
            drawQuad()
    }

    private fun drawQuad() {
        GLES30.glBindVertexArray(quadVAO); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4); GLES30.glBindVertexArray(0)
    }

    private fun initParticles() {
        val p = particles ?: return
        val count = PARTICLE_RES * PARTICLE_RES
        val data = FloatArray(count * 4)
        for (i in 0 until count) {
            data[i * 4 + 0] = Random.nextFloat() // x
            data[i * 4 + 1] = Random.nextFloat() // y
            data[i * 4 + 2] = Random.nextFloat() // life
            data[i * 4 + 3] = Random.nextFloat() * 10f // age
        }
        val buf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data).position(0)
        
        // Init both read/write textures
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, p.read.texture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, PARTICLE_RES, PARTICLE_RES, 0, GLES30.GL_RGBA, GLES30.GL_FLOAT, buf)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, p.write.texture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, PARTICLE_RES, PARTICLE_RES, 0, GLES30.GL_RGBA, GLES30.GL_FLOAT, buf)
    }

    private fun initParticleIndices() {
        val count = PARTICLE_RES * PARTICLE_RES
        val indices = FloatArray(count) { it.toFloat() }
        val buf = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(indices).position(0)

        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        particleIndicesVBO = vbo[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, particleIndicesVBO)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, indices.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    // ---- Shader utils ----
    
    private var lastFpsTime = 0L
    
    private fun limitFPS() {
        val now = System.currentTimeMillis()
        val targetFrameTime = 1000L / settings.targetFPS.coerceAtLeast(1)
        val elapsed = now - lastFpsTime
        
        if (elapsed < targetFrameTime) {
            try {
                Thread.sleep(targetFrameTime - elapsed)
            } catch (e: InterruptedException) {
                // Ignore
            }
        }
        lastFpsTime = System.currentTimeMillis()
    }
    
    private fun setUniform(p: Int, n: String, v: Int) { GLES30.glUniform1i(GLES30.glGetUniformLocation(p, n), v) }
    private fun setUniform(p: Int, n: String, v: Float) { GLES30.glUniform1f(GLES30.glGetUniformLocation(p, n), v) }
    private fun setUniform(p: Int, n: String, x: Float, y: Float) { GLES30.glUniform2f(GLES30.glGetUniformLocation(p, n), x, y) }
    private fun setUniform(p: Int, n: String, x: Float, y: Float, z: Float) { GLES30.glUniform3f(GLES30.glGetUniformLocation(p, n), x, y, z) }

    private fun loadRawResource(resId: Int): String {
        val r = BufferedReader(InputStreamReader(context.resources.openRawResource(resId)))
        val sb = StringBuilder(); var line: String?
        while (r.readLine().also { line = it } != null) sb.appendLine(line)
        r.close(); return sb.toString()
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type); GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
        val st = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) { val log = GLES30.glGetShaderInfoLog(s); GLES30.glDeleteShader(s); throw RuntimeException("Shader error: $log") }
        return s
    }

    private fun createProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES30.GL_VERTEX_SHADER, vs); val f = compileShader(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram(); GLES30.glAttachShader(p, v); GLES30.glAttachShader(p, f)
        GLES30.glBindAttribLocation(p, 0, "aPosition"); GLES30.glLinkProgram(p)
        val st = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, st, 0)
        if (st[0] == 0) { val log = GLES30.glGetProgramInfoLog(p); GLES30.glDeleteProgram(p); throw RuntimeException("Link error: $log") }
        GLES30.glDeleteShader(v); GLES30.glDeleteShader(f); return p
    }
}
