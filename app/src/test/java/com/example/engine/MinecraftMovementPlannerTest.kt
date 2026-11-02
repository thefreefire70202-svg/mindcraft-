package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftMovementPlannerTest {
    @Test
    fun createsShortWaypointsAndEndsAtTarget() {
        val points = MinecraftMovementPlanner.plan(
            start = Triple(0.0, 64.0, 0.0),
            target = Triple(1.0, 64.0, 0.0),
            stepLength = 0.25
        )
        assertEquals(4, points.size)
        assertEquals(0.25, points.first().first, 0.000001)
        assertEquals(1.0, points.last().first, 0.000001)
        assertEquals(64.0, points.last().second, 0.000001)
    }

    @Test
    fun returnsNoWaypointsWhenAlreadyAtTarget() {
        assertTrue(
            MinecraftMovementPlanner.plan(
                Triple(2.0, 70.0, -3.0),
                Triple(2.0, 70.0, -3.0)
            ).isEmpty()
        )
    }

    @Test
    fun rejectsDestinationsBeyondSafetyLimit() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            MinecraftMovementPlanner.plan(
                Triple(0.0, 64.0, 0.0),
                Triple(65.0, 64.0, 0.0)
            )
        }
    }

    @Test
    fun rejectsNonFiniteCoordinates() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            MinecraftMovementPlanner.plan(
                Triple(0.0, 64.0, 0.0),
                Triple(Double.NaN, 64.0, 0.0)
            )
        }
    }
}
