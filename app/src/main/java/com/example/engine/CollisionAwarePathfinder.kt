package com.example.engine

import java.util.PriorityQueue
import kotlin.math.abs

/** Integer block coordinate occupied by the bot's feet. */
data class BlockPos(val x: Int, val y: Int, val z: Int)

/**
 * Only authoritative, decoded block states belong in this map.
 * UNKNOWN is deliberately treated as an obstacle: the planner never walks
 * through chunks it has not received or decoded.
 */
enum class VoxelState { UNKNOWN, SOLID, PASSABLE, WATER, LAVA }

data class VoxelWorldSnapshot(
    val blocks: Map<BlockPos, VoxelState>,
    val minY: Int = -64,
    val maxY: Int = 320
) {
    fun stateAt(pos: BlockPos): VoxelState = blocks[pos] ?: VoxelState.UNKNOWN
}

data class PathPlan(
    val steps: List<BlockPos>,
    val expandedNodes: Int
)

/**
 * Bounded A* for a two-block-tall player. Supports level movement, one-block
 * steps up, and one-block drops. Diagonal moves are intentionally omitted to
 * prevent corner cutting. Unknown blocks are not assumed to be air.
 *
 * This is a pure planner. It becomes live navigation only after the protocol
 * layer supplies a trustworthy chunk/block snapshot and movement acknowledgments.
 */
object CollisionAwarePathfinder {
    private data class Node(val pos: BlockPos, val g: Int, val f: Int)

    private val directions = listOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1
    )

    fun findPath(
        world: VoxelWorldSnapshot,
        start: BlockPos,
        goal: BlockPos,
        maxExpandedNodes: Int = 20_000,
        maxDistance: Int = 128
    ): PathPlan? {
        require(maxExpandedNodes > 0) { "maxExpandedNodes must be positive" }
        require(maxDistance > 0) { "maxDistance must be positive" }
        if (start == goal) return PathPlan(emptyList(), 0)
        if (!isStandable(world, start) || !isStandable(world, goal)) return null
        if (manhattan(start, goal) > maxDistance) return null

        val open = PriorityQueue<Node>(compareBy<Node> { it.f }.thenBy { it.g })
        val cameFrom = HashMap<BlockPos, BlockPos>()
        val bestG = HashMap<BlockPos, Int>()
        bestG[start] = 0
        open += Node(start, 0, heuristic(start, goal))
        var expanded = 0

        while (open.isNotEmpty() && expanded < maxExpandedNodes) {
            val current = open.remove()
            if (current.g != bestG[current.pos]) continue
            if (current.pos == goal) {
                val reversed = mutableListOf<BlockPos>()
                var cursor = goal
                while (cursor != start) {
                    reversed += cursor
                    cursor = cameFrom[cursor] ?: return null
                }
                return PathPlan(reversed.asReversed(), expanded)
            }
            expanded++

            for ((dx, dz) in directions) {
                for (dy in intArrayOf(0, 1, -1)) {
                    val next = BlockPos(current.pos.x + dx, current.pos.y + dy, current.pos.z + dz)
                    if (abs(next.x - start.x) > maxDistance ||
                        abs(next.y - start.y) > maxDistance ||
                        abs(next.z - start.z) > maxDistance ||
                        manhattan(start, next) > maxDistance ||
                        !isStandable(world, next)
                    ) continue

                    val tentativeG = current.g + if (dy == 0) 1 else 2
                    if (tentativeG >= (bestG[next] ?: Int.MAX_VALUE)) continue
                    bestG[next] = tentativeG
                    cameFrom[next] = current.pos
                    open += Node(next, tentativeG, tentativeG + heuristic(next, goal))
                }
            }
        }
        return null
    }

    private fun isStandable(world: VoxelWorldSnapshot, feet: BlockPos): Boolean {
        if (feet.y !in world.minY..world.maxY) return false
        val feetState = world.stateAt(feet)
        val headState = world.stateAt(BlockPos(feet.x, feet.y + 1, feet.z))
        val floorState = world.stateAt(BlockPos(feet.x, feet.y - 1, feet.z))
        val clear = feetState == VoxelState.PASSABLE && headState == VoxelState.PASSABLE
        val supported = floorState == VoxelState.SOLID
        return clear && supported
    }

    private fun heuristic(a: BlockPos, b: BlockPos): Int =
        abs(a.x - b.x) + abs(a.z - b.z) + 2 * abs(a.y - b.y)

    private fun manhattan(a: BlockPos, b: BlockPos): Int =
        abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)
}
