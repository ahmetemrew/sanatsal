package com.basitce.sanatsal

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class FluidActivity : Activity(), SensorEventListener {

    private lateinit var glView: FluidGLSurfaceView
    private lateinit var sensorManager: SensorManager
    private lateinit var settingsPanel: SettingsPanel
    private lateinit var toolbar: LinearLayout
    private var gravitySensor: Sensor? = null
    
    private lateinit var settingsStorage: SettingsStorage
    private val audioInput = AudioInput()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val audioPollRunnable = object : Runnable {
        override fun run() {
            if (glView.renderer.settings.microphoneEnabled) {
                glView.renderer.currentAmplitude = audioInput.amplitude
            }
            handler.postDelayed(this, 16) // ~60fps poll
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Root layout
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        // GL Surface
        settingsStorage = SettingsStorage(this)
        val savedSettings = settingsStorage.load()
        
        glView = FluidGLSurfaceView(this, savedSettings)
        root.addView(glView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Floating toolbar
        toolbar = createToolbar()
        root.addView(toolbar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(32).toInt()
        })

        // Settings panel
        settingsPanel = SettingsPanel(this, glView.renderer.settings, onClose = {
            toolbar.visibility = View.VISIBLE
        }) {
            // Sync palette
            glView.renderer.settings.palette = glView.renderer.settings.palette
            
            // Check mic permission if enabled
            if (glView.renderer.settings.microphoneEnabled) {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
                } else {
                    audioInput.start()
                }
            } else {
                audioInput.stop()
            }
        }
        root.addView(settingsPanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        // Fullscreen immersive
        applyImmersive()

        // Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

        // Auto-mode on
        // Auto-mode on
        // glView.renderer.settings.autoPlay = true

        // Check/Request permission locally if needed, but for now just start if we have it
        // Removed startup request per user request

        handler.post(audioPollRunnable)
    }

    private fun createToolbar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4).toInt(), dp(4).toInt(), dp(4).toInt(), dp(4).toInt())
            background = GradientDrawable().apply {
                setColor(0xCC1A1A1A.toInt())
                cornerRadius = dp(28)
            }
            elevation = dp(8)

            // Settings button
            addView(createToolbarButton("⚙") {
                settingsPanel.toggle()
                toolbar.visibility = View.GONE
            })

            // Palette cycle button
            addView(createToolbarButton("🎨") {
                val s = glView.renderer.settings
                s.palette = s.palette.next()
            })

            // Clear button -> Re-purposed as AMOLED / AI Mode Toggle
            addView(createToolbarButton("✦") {
                val s = glView.renderer.settings
                s.amoledTheme = !s.amoledTheme
                
                if (s.amoledTheme) {
                    // Switch to Void Mode (Neon)
                    s.fogEnabled = false 
                    s.starDustEnabled = true
                    android.widget.Toast.makeText(context, "Neon Modu (AMOLED)", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    // Restore Standard Mode (Atmosphere)
                    s.fogEnabled = true 
                    s.starDustEnabled = true
                    android.widget.Toast.makeText(context, "Standart Mod", android.widget.Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun createToolbarButton(icon: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = icon
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            val size = dp(48).toInt()
            minimumWidth = size
            minimumHeight = size
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(24)
            }
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
        }
    }

    private fun applyImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { c ->
                c.hide(WindowInsets.Type.systemBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        if (glView.renderer.settings.microphoneEnabled && 
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            audioInput.start()
        }
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        applyImmersive()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        audioInput.stop()
        sensorManager.unregisterListener(this)
        settingsStorage.save(glView.renderer.settings)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (settingsPanel.isOpen()) {
            settingsPanel.hide()
            toolbar.visibility = View.VISIBLE
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GRAVITY) {
            val gx = event.values[0] / SensorManager.GRAVITY_EARTH
            val gy = event.values[1] / SensorManager.GRAVITY_EARTH
            glView.renderer.gravityX = -gx
            glView.renderer.gravityY = gy
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun dp(v: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics)
}
