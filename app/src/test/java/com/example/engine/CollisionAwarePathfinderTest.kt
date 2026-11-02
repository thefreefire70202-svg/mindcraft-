package com.example.engine

import org.junit.Assert.*
import org.junit.Test

class CollisionAwarePathfinderTest {
    private fun floorWorld(xRange: IntRange, zRange: IntRange): VoxelWorldSnapshot {
        val blocks = HashMap<BlockPos, VoxelState>()
        for (x in xRange) for (z in zRange) {
            blocks[BlockPos(x, 0, z)] = VoxelState.SOLID
            blocks[BlockPos(x, 1, z)] = VoxelState.PASSABLE
            blocks[BlockPos(x, 2, z)] = VoxelState.PASSABLE
        }
        return VoxelWorldSnapshot(blocks, minY = -64, maxY = 320)
    }

    @Test fun findsLevelPathOnKnownFloor() {
        val world = floorWorld(0..4, 0..2)
        val path = CollisionAwarePathfinder.findPath(world, BlockPos(0, 1, 1), BlockPos(4, 1, 1))
        assertNotNull(path)
        assertEquals(4, path!!.steps.size)
        assertEquals(BlockPos(4, 1, 1), path.steps.last())
    }

    @Test fun routesAroundSolidObstacle() {
        val base = floorWorld(0..4, 0..2)
        val blocks = base.blocks.toMutableMap()
        blocks[BlockPos(2, 1, 1)] = VoxelState.SOLID
        val path = CollisionAwarePathfinder.findPath(
            VoxelWorldSnapshot(blocks), BlockPos(0, 1, 1), BlockPos(4, 1, 1)
        )
        assertNotNull(path)
        assertFalse(path!!.steps.contains(BlockPos(2, 1, 1)))
    }

    @Test fun unknownChunkIsNotTreatedAsAir() {
        val world = floorWorld(0..1, 0..0)
        val path = CollisionAwarePathfinder.findPath(world, BlockPos(0, 1, 0), BlockPos(3, 1, 0))
        assertNull(path)
    }

    @Test fun refusesLavaAndUnsupportedDestination() {
        val base = floorWorld(0..2, 0..0)
        val blocks = base.blocks.toMutableMap()
        blocks[BlockPos(2, 1, 0)] = VoxelState.LAVA
        assertNull(CollisionAwarePathfinder.findPath(
            VoxelWorldSnapshot(blocks), BlockPos(0, 1, 0), BlockPos(2, 1, 0)
        ))
    }

    @Test fun allowsOneBlockStepUpWhenHeadroomExists() {
        val base = floorWorld(0..2, 0..0)
        val blocks = base.blocks.toMutableMap()
        blocks[BlockPos(1, 0, 0)] = VoxelState.SOLID
        blocks[BlockPos(1, 1, 0)] = VoxelState.SOLID
        blocks[BlockPos(1, 2, 0)] = VoxelState.PASSABLE
        blocks[BlockPos(1, 3, 0)] = VoxelState.PASSABLE
        val path = CollisionAwarePathfinder.findPath(
            VoxelWorldSnapshot(blocks), BlockPos(0, 1, 0), BlockPos(1, 2, 0)
        )
        assertNotNull(path)
        assertEquals(BlockPos(1, 2, 0), path!!.steps.last())
    }
}
