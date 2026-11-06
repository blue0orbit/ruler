package com.blueorbit.ruler.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A reading plus the accuracy status the sensor itself reports. `SensorEvent.accuracy` is a status code
 * (0 unreliable, 1 low, 2 medium, 3 high), not an angle — treating it as radians produced a nonsense
 * ±171.9° on the test device. The status is what the sensor actually reports, so it is what the UI
 * shows (rule 5).
 */
data class SensorReading(val gravity: Gravity, val accuracyStatus: Int)

/**
 * The seam that keeps the sensor out of the maths: the level and protractor take a `Gravity` and are
 * tested on the JVM with fixed vectors, so no device or Robolectric is needed.
 */
interface GravitySource {
    fun readings(): Flow<SensorReading>
}

/**
 * Rotation vector only — no camera permission, and no assumption that a magnetometer exists. The
 * third row of the rotation matrix is the earth's z axis expressed in the device frame, which is the
 * gravity direction. If the device has no rotation-vector sensor the flow closes instead of guessing.
 */
class SensorManagerGravitySource(context: Context) : GravitySource {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override fun readings(): Flow<SensorReading> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val smoothing = LowPass()
        // One matrix per subscription, not one per event: at the game rate an allocation per event is
        // 50 short-lived arrays a second for nothing.
        val matrix = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                trySend(
                    SensorReading(
                        gravity = smoothing.filter(
                            Gravity(matrix[6].toDouble(), matrix[7].toDouble(), matrix[8].toDouble())
                        ),
                        accuracyStatus = event.accuracy
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // Accuracy arrives with every event, so there is nothing to add here.
            }
        }

        // The game rate (~50 Hz) rather than the UI rate (~16 Hz): the bubble and the protractor's live ray
        // are drawn every frame, and at 16 Hz they visibly step. The low-pass above keeps the faster
        // stream from turning into faster jitter.
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
