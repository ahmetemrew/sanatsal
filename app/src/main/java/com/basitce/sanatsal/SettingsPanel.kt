package com.basitce.sanatsal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.view.MotionEvent
import java.util.ArrayDeque

@Suppress("UseSwitchCompatOrMaterialCode")
class SettingsPanel(
    context: Context,
    private val settings: FluidSettings,
    private val onClose: () -> Unit = {},
    private val onChanged: () -> Unit
) : FrameLayout(context) {

    companion object {
        private const val ACCENT = 0xFF8B5CF6.toInt() // Violet
        private const val BG_PANEL = 0xF0111111.toInt() // Dark
        private const val BG_DIM = 0x60000000 
        private const val TEXT_PRIMARY = 0xFFE5E7EB.toInt()
        private const val TEXT_SECONDARY = 0xFF9CA3AF.toInt()
        private const val DIVIDER = 0xFF2A2A2A.toInt()
    }

    private var isOpen = false
    private val container: LinearLayout
    private val content: LinearLayout
    private val scrollContainer: ScrollView
    
    private var currentSectionContainer: LinearLayout? = null
    private val expandedSections = mutableSetOf<String>()
    
    // Instead of a complex stack, we'll just track if we are in a section
    // Simplification for robustness.

    init {
        setBackgroundColor(BG_DIM)
        visibility = View.GONE
        setOnClickListener { toggle() } // Tap outside

        // Main Container (Sheet)
        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(BG_PANEL)
                cornerRadii = floatArrayOf(dp(24), dp(24), dp(24), dp(24), 0f, 0f, 0f, 0f)
            }
            isClickable = true // Prevent tap-outside
            setPadding(0, dp(16).toInt(), 0, 0)
        }

        // 1. Header (Drag Handle)
        // 1. Header (Drag Handle + Close Button)
        val header = FrameLayout(context).apply {
            setPadding(0, dp(12).toInt(), 0, dp(12).toInt())
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            // Allow clicking header to close as inconsistent 'drag' substitute
            setOnClickListener { hide() }
        }
        
        // Handle Bar
        val handleBar = View(context).apply {
            background = GradientDrawable().apply {
                setColor(0xFF444444.toInt())
                cornerRadius = dp(4)
            }
        }
        header.addView(handleBar, LayoutParams(dp(48).toInt(), dp(6).toInt()).apply {
            gravity = Gravity.CENTER
        })

        // Close Button (X)
        val closeBtn = TextView(context).apply {
            text = "✕" // or "Kapat"
            setTextColor(TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(16).toInt(), dp(8).toInt(), dp(16).toInt(), dp(8).toInt())
            setOnClickListener { hide() }
        }
        header.addView(closeBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = dp(16).toInt()
        })
        
        container.addView(header)

        // 2. Scrollable Content
        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24).toInt(), 0, dp(24).toInt(), dp(48).toInt())
        }
        scrollContainer = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }
        container.addView(scrollContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Add to Root
        addView(container, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })

        buildUI()
    }

    fun toggle() {
        if (isOpen) hide() else show()
    }

    fun show() {
        if (isOpen) return
        isOpen = true
        visibility = View.VISIBLE
        container.translationY = 1500f
        container.animate().translationY(0f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
    }

    fun hide() {
        if (!isOpen) return
        isOpen = false
        container.animate().translationY(1500f).setDuration(300).setInterpolator(DecelerateInterpolator())
            .withEndAction { visibility = View.GONE; onClose() }
            .start()
    }

    fun isOpen() = isOpen

    private fun buildUI() {
        content.removeAllViews()
        currentSectionContainer = null

        // ===================================
        // 1. MAIN MODES
        // ===================================
        addHeader("MODLAR")
        addToggle("3D Sonsuz Dünya", settings.worldMode) {
            settings.worldMode = it; onChanged(); rebuildUI()
        }

        if (settings.worldMode) {
            // 3D SETTINGS
            addHeader("ORTAM & KAMERA")
            addSelector("Ortam", FluidSettings.WORLD_TYPES, settings.worldType) {
                settings.worldType = it; onChanged(); rebuildUI()
            }
            addSelector("Kamera", FluidSettings.CAMERA_MODES, settings.cameraMode) {
                settings.cameraMode = it; onChanged(); rebuildUI()
            }
            addToggle("AMOLED (Karanlık Mod)", settings.amoledTheme) {
                settings.amoledTheme = it
                // Logic sync
                if (it) { settings.fogEnabled = false; settings.starDustEnabled = true } 
                else { settings.fogEnabled = true }
                onChanged(); rebuildUI()
            }
            if (settings.amoledTheme) {
                 addSlider("Siyah Eşiği", 0, 20, (settings.blackThreshold * 100).toInt()) {
                     settings.blackThreshold = it / 100f; onChanged()
                 }
            }

            addHeader("GÖRÜNÜM")
            // Palette Logic
            addColorPaletteSelector()
            if (settings.palette == ColorPalette.CUSTOM) {
                addCustomColorSliders()
            }

            addToggle("Sis (Fog)", settings.fogEnabled) {
                settings.fogEnabled = it; onChanged(); rebuildUI()
            }
            if (settings.fogEnabled) {
                addSlider("Sis Yoğunluğu", 0, 100, (settings.opacity * 100).toInt()) {
                    settings.opacity = it / 100f; onChanged()
                }
            }
            
            addHeader("DÜNYA AYARLARI")
            addSlider("Renk Hızı", 0, 100, (settings.gravityScale * 100).toInt()) {
                settings.gravityScale = it / 100f; onChanged()
            }
            addSlider("Seyahat Hızı", 0, 50, (settings.travelSpeed * 10).toInt()) {
                 settings.travelSpeed = it / 10f; onChanged()
            }
            // CRITICAL: DENSITY
            addSlider("Obje Sıklığı", 0, 100, (settings.densityScale * 100).toInt()) {
                settings.densityScale = it / 100f; onChanged()
            }
             addSlider("Dünya Ölçeği", 1, 30, (settings.worldScale * 10).toInt()) {
                settings.worldScale = it / 10f; onChanged()
            }
            addSlider("Tünel Genişliği", 5, 50, (settings.pathWidth * 10).toInt()) {
                settings.pathWidth = it / 10f; onChanged()
            }
            addSlider("Bükülme", 0, 100, (settings.distortion * 100).toInt()) {
                settings.distortion = it / 100f; onChanged()
            }

            addHeader("EFEKTLER")
            addToggle("Yıldızlar", settings.starDustEnabled) {
                settings.starDustEnabled = it; onChanged(); rebuildUI()
            }
            if (settings.starDustEnabled) {
                addSlider("Yıldız Sayısı", 0, 100, (settings.bgParticleCount * 100).toInt()) {
                     settings.bgParticleCount = it / 100f; onChanged()
                }
                addSlider("Yıldız Boyutu", 1, 100, (settings.bgParticleSize * 100).toInt()) {
                     settings.bgParticleSize = it / 100f; onChanged()
                }
                addSlider("Yıldız Hızı", 0, 100, (settings.bgParticleSpeed * 50).toInt()) {
                     settings.bgParticleSpeed = it / 50f; onChanged()
                }
            }

            addHeader("KALİTE & PERFORMANS")
            addSlider("Çözünürlük %", 25, 100, (settings.resolutionScale * 100).toInt()) {
                settings.resolutionScale = it / 100f; onChanged()
            }
            addSlider("Ray Steps (Kalite)", 10, 120, settings.raySteps) {
                settings.raySteps = it; onChanged()
            }
            addSlider("Hedef FPS", 15, 120, settings.targetFPS) {
                settings.targetFPS = it; onChanged()
            }

        } else {
            // 2D FLUID MODE
            addHeader("SIVI AYARLARI")
            addSlider("Dağılma", 900, 1000, (settings.velocityDissipation * 1000).toInt()) {
                 settings.velocityDissipation = it / 1000f; onChanged()
            }
            addSlider("Girdap", 0, 100, (settings.vorticity * 10).toInt()) {
                settings.vorticity = it / 10f; onChanged()
            }
            addSlider("Bloom", 0, 100, (settings.bloomIntensity * 100).toInt()) {
                settings.bloomIntensity = it / 100f; onChanged()
            }
             addHeader("RENK")
             addColorPaletteSelector()
        }
        
        // Final spacing
        val spacer = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(48).toInt()) }
        content.addView(spacer)
    }

    private fun rebuildUI() {
        val y = scrollContainer.scrollY
        buildUI()
        scrollContainer.post { scrollContainer.scrollY = y }
    }

    // ==========================================
    // UI BUILDER HELPERS
    // ==========================================

    private fun addHeader(title: String) {
        val tv = TextView(context).apply {
            text = title
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            setPadding(0, dp(24).toInt(), 0, dp(8).toInt())
        }
        content.addView(tv)
    }

    private fun addToggle(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8).toInt(), 0, dp(8).toInt())
        }
         val tv = TextView(context).apply {
            text = label
            setTextColor(TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val sw = Switch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, b -> onCheck(b) }
            // Styling can be added here
        }
        row.addView(tv)
        row.addView(sw)
        content.addView(row)
    }

    private fun addSlider(label: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12).toInt(), 0, dp(4).toInt())
        }
        
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val title = TextView(context).apply {
             text = label
             setTextColor(TEXT_PRIMARY)
             setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
             layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val valueTv = TextView(context).apply {
             text = formatValue(label, value)
             setTextColor(ACCENT)
             setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
             typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(title)
        header.addView(valueTv)
        row.addView(header)

        val sb = SeekBar(context).apply {
            this.max = max - min
            this.progress = value - min
            // Basic styling
            thumb.setTint(ACCENT)
            progressDrawable.setTint(ACCENT)
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if(fromUser) {
                    val realVal = p + min
                    valueTv.text = formatValue(label, realVal)
                    onChange(realVal)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        
        row.addView(sb)
        content.addView(row)
    }

    private fun addSelector(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val row = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL 
            setPadding(0, dp(12).toInt(), 0, dp(8).toInt())
        }
        val title = TextView(context).apply {
            text = label
            setTextColor(TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0,0,0,dp(8).toInt())
        }
        row.addView(title)

        val hsv = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        
        options.forEachIndexed { i, opt ->
            val isSelected = i == selected
            val chip = TextView(context).apply {
                text = opt
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if(isSelected) Color.WHITE else TEXT_SECONDARY)
                background = GradientDrawable().apply {
                    setColor(if(isSelected) ACCENT else 0xFF333333.toInt())
                    cornerRadius = dp(16)
                }
                setPadding(dp(16).toInt(), dp(8).toInt(), dp(16).toInt(), dp(8).toInt())
                setOnClickListener { onSelect(i) }
            }
            chips.addView(chip, LinearLayout.LayoutParams(-2,-2).apply { marginEnd = dp(8).toInt() })
        }
        hsv.addView(chips)
        row.addView(hsv)
        content.addView(row)
    }

    private fun addColorPaletteSelector() {
        val row = LinearLayout(context).apply { 
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12).toInt(), 0, dp(12).toInt())
        }
        
        for (palette in ColorPalette.entries) {
            val isSelect = (settings.palette == palette)
            val dot = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if(isSelect) ACCENT else 0xFF333333.toInt())
                }
                setPadding(dp(2).toInt(), dp(2).toInt(), dp(2).toInt(), dp(2).toInt())
            }
            
            // Inner color preview
            val preview = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                     shape = GradientDrawable.OVAL
                     setColor(Color.BLACK) // Mask
                }
            }
            
            // Draw 3 dots inside
            val c = palette.colors
            for(k in 0..2) {
                 val d = View(context).apply {
                     val rgb = Color.rgb((c[k][0]*255).toInt(), (c[k][1]*255).toInt(), (c[k][2]*255).toInt())
                     background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(rgb) }
                     layoutParams = LinearLayout.LayoutParams(dp(10).toInt(), dp(10).toInt())
                 }
                 preview.addView(d)
            }
            
            dot.addView(preview, FrameLayout.LayoutParams(dp(36).toInt(), dp(36).toInt()))
            dot.setOnClickListener {
                 if (palette == ColorPalette.CUSTOM && settings.palette != ColorPalette.CUSTOM) {
                     for(z in 0..2) settings.customColors[z] = settings.palette.colors[z].clone()
                 }
                 settings.palette = palette
                 onChanged(); rebuildUI()
            }
            
            row.addView(dot, LinearLayout.LayoutParams(-2,-2).apply { marginEnd = dp(12).toInt() })
        }
        content.addView(row)
    }
    
    private fun addCustomColorSliders() {
        val labels = listOf("Zemin", "Atmosfer", "Vurgu")
        for(i in 0..2) {
             val c = settings.customColors[i]
             // Simple RGB sliders for each
             val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12).toInt(),0,0,0) }
             val t = TextView(context).apply { text = labels[i]; setTextColor(TEXT_SECONDARY); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f) }
             row.addView(t)
             
             // R
             addCompactSlider(row, "R", c[0]) { c[0] = it; onChanged() }
             addCompactSlider(row, "G", c[1]) { c[1] = it; onChanged() }
             addCompactSlider(row, "B", c[2]) { c[2] = it; onChanged() }
             
             content.addView(row)
        }
    }

    private fun addCompactSlider(parent: LinearLayout, label: String, value: Float, onCh: (Float)->Unit) {
        val ln = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tv = TextView(context).apply { text = label; setTextColor(TEXT_SECONDARY); width = dp(20).toInt() }
        val sb = SeekBar(context).apply { 
            max = 255
            progress = (value*255).toInt()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            thumb.setTint(ACCENT)
            progressDrawable.setTint(ACCENT)
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
             override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                 if(u) onCh(p/255f)
             }
             override fun onStartTrackingTouch(s: SeekBar?) {}
             override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        ln.addView(tv); ln.addView(sb)
        parent.addView(ln)
    }

    private fun formatValue(label: String, v: Int): String {
        return when {
            label.contains("Sıklık") -> "$v%"
            label.contains("Hız") -> "${v/10f}x"
            label.contains("Ölçek") -> "${v/10f}x"
            label.contains("Çözünürlük") -> "$v%"
            label.contains("FPS") -> "$v"
            else -> v.toString()
        }
    }

    private fun dp(v: Int): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics)
}
