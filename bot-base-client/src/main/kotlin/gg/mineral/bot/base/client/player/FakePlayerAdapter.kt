package gg.mineral.bot.base.client.player

import gg.mineral.bot.api.entity.living.player.FakePlayer
import gg.mineral.bot.api.instance.ClientInstance
import gg.mineral.bot.api.inv.Inventory
import gg.mineral.bot.api.math.BoundingBox
import gg.mineral.bot.api.math.simulation.PlayerMotionSimulator
import gg.mineral.bot.api.world.ClientWorld
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityClientPlayerMP
import java.util.*

class FakePlayerAdapter(
    private val instance: ClientInstance,
    private val player: EntityClientPlayerMP
) : FakePlayer {

    override val x get() = player.x
    override val y get() = player.y
    override val z get() = player.z

    override val lastX get() = player.lastX
    override val lastY get() = player.lastY
    override val lastZ get() = player.lastZ

    override val yaw get() = player.yaw
    override val pitch get() = player.pitch

    override val isOnGround get() = player.onGround

    override val eyeHeight get() = player.eyeHeight

    override val headY
        get() = player.posY + player.eyeHeight


    override var motionX
        get() = player.motionX
        set(value) { player.motionX = value }

    override var motionY
        get() = player.motionY
        set(value) { player.motionY = value }

    override var motionZ
        get() = player.motionZ
        set(value) { player.motionZ = value }

    override val username get() = player.username
    override val uuid: UUID get() = player.uniqueID
    override val entityId get() = player.entityId

    override val health get() = player.health
    override val hunger get() = player.foodStats.foodLevel.toFloat()

    override val isSprinting get() = player.isSprinting


    override val activePotionEffectIds
        get() = player.activePotionEffects.map { it.potionID }.toIntArray()

    override fun isPotionActive(potionId: Int) =
        player.isPotionActive(potionId)


    override val collidingBoundingBox = player.collidingBoundingBox

    override var boundingBox: BoundingBox =
        object : BoundingBox {
            override var minX: Double = player.boundingBox.minX
            override var minY: Double = player.boundingBox.minY
            override var minZ: Double = player.boundingBox.minZ
            override var maxX: Double = player.boundingBox.maxX
            override var maxY: Double = player.boundingBox.maxY
            override var maxZ: Double = player.boundingBox.maxZ
        }

    override val world: ClientWorld
        get() = player.world

    override val inventory: Inventory = player.inventory
    override val inventoryContainer = player.inventoryContainer

    override val lastReportedX get() = player.lastReportedX
    override val lastReportedY get() = player.lastReportedY
    override val lastReportedZ get() = player.lastReportedZ

    override val random: Random = player.rng

    override val clientInstance get() = instance

    override fun motionSimulator(world: ClientWorld): PlayerMotionSimulator {
        return gg.mineral.bot.base.client.math.simulation.PlayerMotionSimulator(
            instance as Minecraft,
                this,
                world
        )
    }
}