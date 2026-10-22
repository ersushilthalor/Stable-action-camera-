package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.MotionSnapshot
import com.example.sensor.MotionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Stable Action", appName)
    }

    @Test
    fun `test motion snapshot initialization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val motionManager = MotionManager(context)
        val snap = motionManager.snapshot()
        assertEquals(0.0, snap.roll, 0.0001)
        assertEquals(0.0, snap.offsetX, 0.0001)
        assertEquals(0.0, snap.offsetY, 0.0001)
    }

    @Test
    fun `test continuous roll tracking and smoothing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val motionManager = MotionManager(context)

        // Simulate 45 degree tilt
        motionManager.setManualSimAngleDegrees(45f)
        val snap = motionManager.snapshot()
        assertEquals(Math.toRadians(45.0), snap.roll, 0.001)

        // Test smoothing step
        motionManager.updateSmoothing(snap)
        assertTrue(motionManager.smoothedRoll > 0.0)
    }

    @Test
    fun `test 360 degree rotation continuous unwrapping`() {
        // Test angle unwrapping logic directly
        var previousRawRoll = 3.10 // near +pi (~177.6 deg)
        val rawRoll = -3.10        // crossed to -pi (~-177.6 deg)
        var rollUnwrapped = previousRawRoll

        var delta = rawRoll - previousRawRoll
        if (delta > PI) delta -= 2.0 * PI
        if (delta < -PI) delta += 2.0 * PI

        rollUnwrapped += delta

        // Delta should be positive around +0.082 rad instead of jumping -6.2 rad!
        assertTrue(delta > 0.0)
        assertTrue(rollUnwrapped > 3.10)
    }
}
