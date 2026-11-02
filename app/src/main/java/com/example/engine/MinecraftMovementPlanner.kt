package com.example.engine

import kotlin.math.ceil
import kotlin.math.sqrt

/** Straight-line waypoint generation; it does not perform collision-aware pathfinding. */
internal object MinecraftMovementPlanner {
    fun plan(
        start: Triple<Double, Double, Double>,
        target: Triple<Double, Double, Double>,
        maxDistance: Double = 64.0,
        stepLength: Double = 0.25
    ): List<Triple<Double, Double, Double>> {
        require((start.toList() + target.toList()).all { it.isFinite() }) {
            "coordinates must be finite"
        }
        require(maxDistance.isFinite() && maxDistance > 0.0) { "maxDistance must be positive and finite" }
        require(stepLength.isFinite() && stepLength > 0.0) { "stepLength must be positive and finite" }

        val dx = target.first - start.first
        val dy = target.second - start.second
        val dz = target.third - start.third
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        require(distance <= maxDistance) { "target is $distance blocks away; limit is $maxDistance" }
        if (distance < 0.01) return emptyList()

        val count = ceil(distance / stepLength).toInt().coerceAtLeast(1)
        return (1..count).map { index ->
            val fraction = index.toDouble() / count
            Triple(
                start.first + dx * fraction,
                start.second + dy * fraction,
                start.third + dz * fraction
            )
        }
    }
}
