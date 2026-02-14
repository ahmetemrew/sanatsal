package com.basitce.sanatsal

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.util.AttributeSet

class FluidGLSurfaceView(context: Context, val settings: FluidSettings = FluidSettings()) : GLSurfaceView(context) {

    val renderer: FluidRenderer

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        renderer = FluidRenderer(context, settings)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionMasked = event.actionMasked

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val x = event.getX(index) / width.toFloat()
                val y = 1f - event.getY(index) / height.toFloat()
                val p = event.getPressure(index)
                renderer.onTouchDown(id, x, y, p)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i) / width.toFloat()
                    val y = 1f - event.getY(i) / height.toFloat()
                    val p = event.getPressure(i)
                    renderer.onTouchMove(id, x, y, p)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                renderer.onTouchUp(id)
            }
        }

        performClick()
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
