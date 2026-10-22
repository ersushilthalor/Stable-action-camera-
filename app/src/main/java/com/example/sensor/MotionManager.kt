package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.model.MotionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2

/**
 * MotionManager matches the iOS Stable Action MotionManager:
 * - 120Hz sensor sampling interval
 * - Continuous 360° roll tracking with unwrapping across the ±180° boundary
 * - Damped translation correction (user acceleration -> velocity decay 0.82 -> position decay 0.992)
 * - Thread-safe snapshot() method
 */
class MotionManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val linearAccSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val _snapshotFlow = MutableStateFlow(MotionSnapshot())
    val snapshotFlow: StateFlow<MotionSnapshot> = _snapshotFlow.asStateFlow()

    // Thread-safe lock for atomic snapshot reads
    private val lock = Any()
    @Volatile private var snapRoll: Double = 0.0
    @Volatile private var snapOffsetX: Double = 0.0
    @Volatile private var snapOffsetY: Double = 0.0

    // Smoothed values for rendering (25% roll, 10% translation)
    @Volatile var smoothedRoll: Double = 0.0
        private set
    @Volatile var smoothedNormX: Double = 0.0
        private set
    @Volatile var smoothedNormY: Double = 0.0
        private set

    // Velocity accumulators
    private var velX: Double = 0.0
    private var velY: Double = 0.0

    // Continuous roll tracking
    private var previousRawRoll: Double = 0.0
    private var rollUnwrapped: Double = 0.0
    private var isFirstRollSample = true

    // Tuning constants matching iOS Stable Action
    private val dt: Double = 1.0 / 120.0
    private val velocityDecay: Double = 0.82
    private val positionDecay: Double = 0.992
    private val sensitivity: Double = 0.035
    private val accelDeadZone: Double = 0.02
    private val rollSmoothingAlpha: Double = 0.25
    private val translationSmoothingAlpha: Double = 0.10

    // Manual simulation fallback for emulator
    private var manualSimActive = false
    private var manualSimRoll = 0.0

    fun start() {
        if (sensorManager != null) {
            val samplingPeriodUs = 8333 // ~120 Hz (1,000,000 / 120)
            gravitySensor?.let {
                sensorManager.registerListener(this, it, samplingPeriodUs)
            }
            linearAccSensor?.let {
                sensorManager.registerListener(this, it, samplingPeriodUs)
            }
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        synchronized(lock) {
            snapRoll = 0.0
            snapOffsetX = 0.0
            snapOffsetY = 0.0
            smoothedRoll = 0.0
            smoothedNormX = 0.0
            smoothedNormY = 0.0
        }
        velX = 0.0
        velY = 0.0
        previousRawRoll = 0.0
        rollUnwrapped = 0.0
        isFirstRollSample = true
        _snapshotFlow.value = MotionSnapshot()
    }

    fun snapshot(): MotionSnapshot {
        synchronized(lock) {
            return MotionSnapshot(
                roll = snapRoll,
                offsetX = snapOffsetX,
                offsetY = snapOffsetY
            )
        }
    }

    /**
     * Update smoothed state on each frame render step (e.g. 60-120fps).
     */
    fun updateSmoothing(target: MotionSnapshot) {
        smoothedRoll += rollSmoothingAlpha * (target.roll - smoothedRoll)
        smoothedNormX += translationSmoothingAlpha * (target.offsetX - smoothedNormX)
        smoothedNormY += translationSmoothingAlpha * (target.offsetY - smoothedNormY)
    }

    /**
     * Set manual simulation roll angle in radians for testing in emulator.
     */
    fun setManualSimAngleDegrees(degrees: Float) {
        manualSimActive = true
        manualSimRoll = Math.toRadians(degrees.toDouble())
        synchronized(lock) {
            snapRoll = manualSimRoll
        }
        _snapshotFlow.value = snapshot()
    }

    fun disableManualSim() {
        manualSimActive = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || manualSimActive) return

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                // In Android portrait:
                // x is right, y is up, z is towards user
                // When phone rolls clockwise, x increases, y is negative gravity
                val gx = event.values[0].toDouble()
                val gy = event.values[1].toDouble()

                // Calculate angle in (-pi, pi]
                val rawRoll = atan2(gx, gy)

                if (isFirstRollSample) {
                    previousRawRoll = rawRoll
                    rollUnwrapped = rawRoll
                    isFirstRollSample = false
                }

                var delta = rawRoll - previousRawRoll
                if (delta > Math.PI) delta -= 2.0 * Math.PI
                if (delta < -Math.PI) delta += 2.0 * Math.PI

                previousRawRoll = rawRoll
                rollUnwrapped += delta
                val newRoll = rollUnwrapped

                synchronized(lock) {
                    snapRoll = newRoll
                }
                _snapshotFlow.value = snapshot()
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                var ax = event.values[0].toDouble()
                var ay = event.values[1].toDouble()

                if (abs(ax) < accelDeadZone) ax = 0.0
                if (abs(ay) < accelDeadZone) ay = 0.0

                velX = (velX + ax * dt) * velocityDecay
                velY = (velY + ay * dt) * velocityDecay

                var newOffX: Double
                var newOffY: Double
                synchronized(lock) {
                    newOffX = (snapOffsetX - velX * sensitivity) * positionDecay
                    newOffY = (snapOffsetY - velY * sensitivity) * positionDecay
                    newOffX = newOffX.coerceIn(-1.0, 1.0)
                    newOffY = newOffY.coerceIn(-1.0, 1.0)
                    snapOffsetX = newOffX
                    snapOffsetY = newOffY
                }
                _snapshotFlow.value = snapshot()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
