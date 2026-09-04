package com.quantumvault.wkqpx.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Panic Button Feature:
 * Uses Android Accelerometer sensor to detect when the device is flipped face-down.
 * When triggered, invokes `onDeviceFlipped` to immediately lock the app and exit to home.
 */
class PanicSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var onDeviceFlipped: (() -> Unit)? = null
    private var isListening = false
    private var lastTriggerTime = 0L

    fun startListening(onFlip: () -> Unit) {
        if (accelerometer == null || isListening) return
        this.onDeviceFlipped = onFlip
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isListening = true
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        onDeviceFlipped = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val z = event.values[2]
        val x = event.values[0]
        val y = event.values[1]

        // Check if device is flat face-down:
        // Z is strongly negative (~ -9.8 m/s^2), and X/Y are near 0.
        if (z < -8.0f && abs(x) < 4.0f && abs(y) < 4.0f) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime > 1500) { // Cooldown 1.5s
                lastTriggerTime = now
                onDeviceFlipped?.invoke()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
