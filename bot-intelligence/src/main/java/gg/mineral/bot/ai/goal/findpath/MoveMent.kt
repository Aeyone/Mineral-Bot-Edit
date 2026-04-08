package gg.mineral.bot.ai.goal.findpath

import gg.mineral.bot.api.util.MathUtil
import gg.mineral.bot.api.world.ClientWorld
import net.minecraft.client.multiplayer.WorldClient
import gg.mineral.bot.api.world.block.Block
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.AxisAlignedBB


class MoveMent(
    val world : ClientWorld
) : MathUtil {
    data class Pos(
        val x: Int,
        val y: Int,
        val z: Int
    )

    data class BlockInfo(
        val air: Boolean = true,
        val solid: Boolean = false,
        val liquid: Boolean = false,
        val climbable: Boolean = false,
        val dangerous: Boolean = false
    )

    class WorldQuery(val world : ClientWorld) {

        fun getBlock(pos: Pos): BlockInfo? {
            var block = world.getBlockAt(pos.x, pos.y, pos.z).id

            if (Block.Type.LIQUID.isType(block)) {
                return BlockInfo(liquid = true)
            }
            if (Block.Type.CLIMBABLE.isType(block)) {
                return BlockInfo(climbable = true)
            }
            if (Block.Type.DANGEROUS.isType(block)) {
                return BlockInfo(dangerous = true)
            }
            if (Block.Type.UNPASSABLE.isType(block)) {
                return BlockInfo(air = false)
            }
            if (Block.Type.PASSABLE.isType(block)) {
                return BlockInfo()
            }
            return BlockInfo(air = false, solid = true)
        }

        fun isLoaded(pos: Pos): Boolean {
            return (world as WorldClient).clientChunkProvider?.chunkExists(pos.x shr 4, pos.z shr 4) ?: false
        }

        fun getEntityPenalty(pos: Pos): Boolean {
            val entities = (world as WorldClient).getEntitiesWithinAABB(
            EntityLivingBase::class.java,
            AxisAlignedBB.getBoundingBox(
                pos.x.toDouble(),
                pos.y.toDouble(),
                pos.z.toDouble(),
                pos.x + 1.0,
                pos.y + 1.0,
                pos.z + 1.0
                )
            )
            return entities.isNotEmpty()
        }
    }

    private val walkCost = 1.0
    private val jumpCost = 1.5
    private val dropCost = 1.20
    private val liquidCost = 5.0
    private val dangerousCost = 20.0
    private val entityCost = 1.10
    private val maxDropDown = 4
    private val worldQuery = WorldQuery(world)


    private fun block(pos: Pos): BlockInfo? {
        if (!worldQuery.isLoaded(pos)) return null
        return worldQuery.getBlock(pos)
    }

    private fun isWalkable(pos: Pos): Boolean {
        val feet = block(pos) ?: return false
        val head = block(Pos(pos.x, pos.y + 1, pos.z)) ?: return false
        val ground = block(Pos(pos.x, pos.y - 1, pos.z)) ?: return false

        // 脚和头要空，脚下要能站
        return feet.air && head.air && (ground.solid || ground.climbable)
    }

    private fun getLandingPos(x: Int, startY: Int, z: Int): Pos? {
        for (dy in 1..maxDropDown) {
            val candidate = Pos(x, startY - dy, z)
            if (isWalkable(candidate)) return candidate
        }
        return null
    }

    fun heuristic(a: Pos, b: Pos): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return distanceXZ(dx, dz) + abs(a.y - b.y)
    }

    fun movementCost(from: Pos, to: Pos): Double {
        var cost = walkCost

        val dy = to.y - from.y
        if (dy > 0) cost += jumpCost
        if (dy < 0) cost += dropCost * abs(dy)

        val dest = block(to)
        if (dest?.liquid == true) cost += liquidCost
        if (dest?.dangerous == true) cost += dangerousCost
        if (worldQuery.getEntityPenalty(to)) cost +=  entityCost

        return cost
    }

    fun neighbors(pos: Pos): List<Pos> {
        val result = ArrayList<Pos>()

        val cardinals = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(-1, 0),
            intArrayOf(0, 1),
            intArrayOf(0, -1),
        )

        val diagonals = arrayOf(
            intArrayOf(1, 1),
            intArrayOf(1, -1),
            intArrayOf(-1, 1),
            intArrayOf(-1, -1),
        )

        // 4方向
        for (dir in cardinals) {
            addNeighbor(pos, dir[0], dir[1], result)
        }

        // 斜方向
        for (dir in diagonals) {
            val dx = dir[0]
            val dz = dir[1]

            // 防止“擦角”穿过去：两个边都得能过
            val side1 = Pos(pos.x + dx, pos.y, pos.z)
            val side2 = Pos(pos.x, pos.y, pos.z + dz)
            if (!isWalkable(side1) || !isWalkable(side2)) {
                continue
            }

            addNeighbor(pos, dx, dz, result)
        }

        return result
    }

    private fun addNeighbor(from: Pos, dx: Int, dz: Int, result: MutableList<Pos>) {
        val nx = from.x + dx
        val nz = from.z + dz

        // 1) 平地
        val same = Pos(nx, from.y, nz)
        if (isWalkable(same)) {
            result.add(same)
            return
        }

        // 2) 上一格
        val up = Pos(nx, from.y + 1, nz)
        if (isWalkable(up) && block(Pos(nx, from.y + 2, nz))?.air == true) {
            result.add(up)
            return
        }

        // 3) 下落
        val down = getLandingPos(nx, from.y, nz)
        if (down != null && block(Pos(nx, from.y + 1, nz))?.air == true) {
            result.add(down)
            return
        }
    }

}