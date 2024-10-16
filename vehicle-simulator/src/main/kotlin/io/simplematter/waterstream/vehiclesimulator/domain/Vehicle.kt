package io.simplematter.waterstream.vehiclesimulator.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@JsonIgnoreProperties(ignoreUnknown = true)
data class Vehicle(
    val plate: String,
    val modelId: Int,
    val color: String,
    val current: Point,
    val waypoint: Point,
    val updateTimestamp: Long,
    val speed: Double,
    val visible: Boolean,
    val id: String
) {

    fun metersToWaypoint(): Double {
        return current.distance(waypoint) * 1000
    }

    fun milliSecondsToWaypoint(): Double = metersToWaypoint() / (speed / 3.6) * 1000

    fun updatePosition(elapsed: Long): Vehicle {
        val msToWaypoint = milliSecondsToWaypoint()
        val fractionDone = if (msToWaypoint > 0.0) elapsed / msToWaypoint else 0.0
        val newPosition = waypoint.multiplyBy(fractionDone).add(current.multiplyBy(1 - fractionDone))
        return copy(current = newPosition, updateTimestamp = Instant.now().toEpochMilli())
    }

    fun updateSpeed(): Vehicle {
        val meters = metersToWaypoint()
        val actualSpeed = if (speed == 0.0) MIN_SPEED else speed
        return if (meters > 50) {
            copy(speed = min(actualSpeed + (actualSpeed / 100 * Random.nextDouble(0.0, 5.0)), randomMaxSpeed()))
        } else if (meters > 25) {
            copy(speed = min(actualSpeed + (actualSpeed / 100 * Random.nextDouble(-0.5, 1.5)), randomMaxSpeed()))
        } else {
            copy(speed = max(actualSpeed + (actualSpeed / 100 * Random.nextDouble(-15.0, -5.0)), randomMinSpeed()))
        }
    }

    private fun randomMaxSpeed(): Double {
        return MAX_SPEED + Random.nextDouble( -1.0, 2.0)
    }

    private fun randomMinSpeed(): Double {
        return MIN_SPEED + Random.nextDouble( -1.0, 2.0)
    }

    fun isArrived(): Boolean {
        return metersToWaypoint() < 10.0
    }

    fun setWaypoint(newWaypoint: Point): Vehicle {
        return copy(current = waypoint, waypoint = newWaypoint, updateTimestamp = Instant.now().toEpochMilli())
    }

    fun setSpeed(speed: Double): Vehicle {
        return copy(speed = speed)
    }

    companion object {
        const val MAX_SPEED = 95.0
        const val MIN_SPEED = 35.0

        val log = LoggerFactory.getLogger(Vehicle::class.java)
    }
}

/**
 * Custom serialization which adds `distance` field
 */
fun Vehicle.toJson(): JsonObject {
    val json = JsonObject()
    json.put("plate", plate)
    json.put("modelId", modelId)
    json.put("color", color)
    json.put("current", current.toJson())
    json.put("waypoint", waypoint.toJson())
    json.put("speed", speed)
    json.put("visible", visible)
    json.put("distance", metersToWaypoint())
    json.put("updateTimestamp", updateTimestamp)
    json.put("id", id)
    return json
}


