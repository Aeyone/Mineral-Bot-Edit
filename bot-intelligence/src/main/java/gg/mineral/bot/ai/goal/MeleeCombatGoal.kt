package gg.mineral.bot.ai.goal

import gg.mineral.bot.ai.goal.MeleeCombatGoal.ResetType.*
import gg.mineral.bot.ai.goal.findpath.Astar
import gg.mineral.bot.ai.goal.findpath.MoveMent.Pos
import gg.mineral.bot.ai.goal.type.InventoryGoal
import gg.mineral.bot.api.controls.Key
import gg.mineral.bot.api.controls.MouseButton
import gg.mineral.bot.api.entity.ClientEntity
import gg.mineral.bot.api.entity.living.ClientLivingEntity
import gg.mineral.bot.api.entity.living.player.ClientPlayer
import gg.mineral.bot.api.event.Event
import gg.mineral.bot.api.event.entity.EntityHurtEvent
import gg.mineral.bot.api.instance.ClientInstance
import gg.mineral.bot.api.inv.item.Item
import gg.mineral.bot.api.screen.type.ContainerScreen
import gg.mineral.bot.api.world.block.Block
import gg.mineral.bot.api.math.Positionable
import java.util.UUID

class MeleeCombatGoal(clientInstance: ClientInstance) : InventoryGoal(clientInstance) {
    data class NextPos(
        override val x : Double,
        override val y : Double,
        override val z : Double
    ) : Positionable

    private var target: ClientPlayer? = null

    private val meanDelay = (1000 / clientInstance.configuration.averageCps).toLong()
    private val deviation =
        kotlin.math.abs(((1000 / (clientInstance.configuration.averageCps + 1)).toLong() - meanDelay).toDouble())
            .toLong()
    private var lastBounceTime: Long = 0
    private var lastTargetSwitchTick = 0
    private var lastSprintResetTick = 0
    private var currentHorizontalAimAcceleration = 1.0f
    private var currentVerticalAimAcceleration = 1.0f
    private var lastHorizontalTurnSignum = 1
    private var lastVerticalTurnSignum = 1
    private var currentPath : List<Pos> = ArrayList()
    private var currentNodeIndex = 0
    private var lastAstarFindPathTick = 0
    private var lastJumpTime: Long = 0
    private var lastCheckStapTime: Long = 0
    private var lastHurtEntity: UUID = clientInstance.fakePlayer.uuid
    private var lastTargetDamageTick = 0
    private var lastSelfDamageTick = 0
    private var inLiquid = false
    private var pathLookAheadNodes = 3
    private var lastArrivedNodeTime : Long = 0
    private var lastSwitchAheadTime : Long = 0
    private var lastTargetPos : Pos = Pos(0, 0, 0)
    private val pathTurnSpeed = 18.0f

    override fun shouldExecute() = true

    private fun findTarget() {
        val targetSearchRange = clientInstance.configuration.targetSearchRange

        val fakePlayer = clientInstance.fakePlayer
        val world = fakePlayer.world

        val entities = world.entities

        if (clientInstance.currentTick - lastTargetSwitchTick < 20 && target?.let {
                entities.contains(it) && isTargetValid(
                    it,
                    targetSearchRange.toFloat()
                )
            } == true
        ) return

        var closestTarget: ClientPlayer? = null
        var closestDistance = Double.MAX_VALUE

        for (entity in entities) {
            if (entity is ClientPlayer) {
                if (isTargetValid(entity, targetSearchRange.toFloat())) {
                    val distance = fakePlayer.distance3DTo(entity)
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closestTarget = entity
                    }
                }
            }
        }

        if (closestTarget !== this.target) {
            lastTargetSwitchTick = clientInstance.currentTick
            this.target = closestTarget
        }
    }

    private fun isTargetValid(entity: ClientLivingEntity, range: Float): Boolean {
        val fakePlayer = clientInstance.fakePlayer
        return !clientInstance.configuration.friendlyUUIDs.contains(entity.uuid) && fakePlayer.distance3DTo(entity) <= range && entity is ClientPlayer
    }

    private fun getRotationTarget(
        current: Float,
        target: Float,
        turnSpeed: Float,
        accuracy: Float,
        erraticness: Float
    ): Float {
        val difference = angleDifference(current, target)

        if (abs(difference.toDouble()) > turnSpeed) return current + signum(difference) * turnSpeed

        if (accuracy >= 1) return target

        val deviation = 3f / max(0.01f, accuracy)
        val fakePlayer = clientInstance.fakePlayer
        val newTarget = fakePlayer.random.nextGaussian(target.toDouble(), deviation.toDouble()).toFloat()
        val newDifference = angleDifference(current, newTarget)

        val erraticnessFactor = min(180 * erraticness, turnSpeed)

        if (abs(newDifference.toDouble()) > erraticnessFactor) return current + signum(newDifference) * erraticnessFactor
        return newTarget
    }

    private fun aimAtTarget() {
        val target = this.target ?: return

        val fakePlayer = clientInstance.fakePlayer
        val optimalAngles = computeOptimalYawAndPitch(fakePlayer, target)

        if (fakePlayer.distance3DTo(target) > 6.0f) {
            setMouseYaw(optimalAngles[1])
            setMousePitch(optimalAngles[0])
            return
        }

        val yawDiff = abs(
            angleDifference(
                fakePlayer.yaw,
                optimalAngles[1]
            ).toDouble()
        )
        val pitchDiff = abs(
            angleDifference(
                fakePlayer.pitch,
                optimalAngles[0]
            ).toDouble()
        )

        val distX = abs(fakePlayer.x - target.x)
        val distZ = abs(fakePlayer.z - target.z)

        val yawSpeed =
            calculateHorizontalAimSpeed(sqrt(distX * distX + distZ * distZ), yawDiff) * currentHorizontalAimAcceleration
        val pitchSpeed = calculateVerticalAimSpeed(pitchDiff) * currentVerticalAimAcceleration

        val config = clientInstance.configuration

        val currentHorizontalTurnSignum = signum(
            angleDifference(fakePlayer.yaw, optimalAngles[1])
        ).toInt()

        if (currentHorizontalTurnSignum != lastHorizontalTurnSignum || currentHorizontalTurnSignum == 0) currentHorizontalAimAcceleration =
            1.0f
        else currentHorizontalAimAcceleration *= config.horizontalAimAcceleration

        setMouseYaw(
            getRotationTarget(
                fakePlayer.yaw, optimalAngles[1],
                abs(
                    (yawSpeed
                            * config.horizontalAimSpeed * 2.0)
                ).toFloat(),
                config.horizontalAimAccuracy,
                config.horizontalErraticness
            )
        )

        lastHorizontalTurnSignum = currentHorizontalTurnSignum

        // Give it a higher chance of aiming down
        val verAccuracy = max(0.01f, config.verticalAimAccuracy)
        val deviation = if (verAccuracy >= 1) 0f else 3f / verAccuracy

        val currentVerticalTurnSignum = signum(
            angleDifference(fakePlayer.pitch, optimalAngles[0])
        ).toInt()

        if (currentVerticalTurnSignum != lastVerticalTurnSignum || currentVerticalTurnSignum == 0) currentVerticalAimAcceleration =
            1.0f
        else currentVerticalAimAcceleration *= config.verticalAimAcceleration

        setMousePitch(
            getRotationTarget(
                fakePlayer.pitch,
                optimalAngles[0] + deviation,
                abs(
                    (pitchSpeed
                            * config.verticalAimSpeed * 2.0)
                ).toFloat(),
                verAccuracy,
                config.verticalErraticness
            )
        )

        lastVerticalTurnSignum = currentVerticalTurnSignum
    }

    private fun calculateHorizontalAimSpeed(distHorizontal: Double, yawDiff: Double): Double {
        val yawDiffFactor = 1
        val distHorizontalFactor = 1

        val distanceFactorEquation = (10 / ((5 * distHorizontal.coerceAtLeast(0.2)) - 1)) + 2
        val diffFactorEquation = yawDiff / 5
        return ((distHorizontalFactor * distanceFactorEquation)
                + (yawDiffFactor * diffFactorEquation))
    }

    private val isCollidingWithWall: Boolean
        get() {
            val fakePlayer = clientInstance.fakePlayer
            val world = fakePlayer.world

            val posX = fakePlayer.x
            val posY = fakePlayer.y + fakePlayer.eyeHeight
            val posZ = fakePlayer.z
            val yaw = fakePlayer.yaw
            val pitch = 0f

            val checkDistance = 1.0

            // Check for collision in the direction the bot is facing
            val dir = vectorForRotation(pitch, yaw)
            val checkX = posX + dir[0] * checkDistance
            val checkY = posY + dir[1] * checkDistance
            val checkZ = posZ + dir[2] * checkDistance

            val block = world.getBlockAt(checkX, checkY, checkZ)
            return block.id != Block.AIR
        }

    private val collisionNormal: DoubleArray?
        get() {
            val fakePlayer = clientInstance.fakePlayer
            val world = fakePlayer.world

            val posX = fakePlayer.x
            val posY = fakePlayer.y + fakePlayer.eyeHeight

            // Sampling multiple points around the bot
            val sampleCount = 8
            val checkDistance = 0.5
            val normal = doubleArrayOf(0.0, 0.0, 0.0)

            for (i in 0..<sampleCount) {
                val angle = fakePlayer.yaw + (360.0 / sampleCount * i).toFloat()
                val dir = vectorForRotation(0f, angle)

                val checkX = posX + dir[0] * checkDistance
                val checkZ = fakePlayer.z + dir[2] * checkDistance

                val block = world.getBlockAt(checkX, posY, checkZ)
                if (block.id != Block.AIR) {
                    // Add the opposite of the direction to the normal
                    normal[0] += -dir[0]
                    normal[1] += 0.0 // We ignore Y for horizontal normal
                    normal[2] += -dir[2]
                }
            }

            val length = sqrt(normal[0] * normal[0] + normal[2] * normal[2])
            if (length == 0.0) return null

            // Normalize the normal vector
            normal[0] /= length
            normal[2] /= length

            return normal
        }

    private fun reflectOffWall() {
        val fakePlayer = clientInstance.fakePlayer
        val normal = collisionNormal ?: return

        // Normalize the normal vector (should already be normalized)
        val normX = normal[0]
        val normZ = normal[2]

        // Get direction vector
        val yaw = fakePlayer.yaw

        // Convert yaw to radians
        val yawRad = Math.toRadians(yaw.toDouble())

        // Direction vector in x and z
        val dirX = -kotlin.math.sin(yawRad)
        val dirZ = kotlin.math.cos(yawRad)

        // Perform reflection R = V - 2(V ⋅ N)N
        val dot = dirX * normX + dirZ * normZ // Only x and z components
        val reflectedX = dirX - 2 * dot * normX
        val reflectedZ = dirZ - 2 * dot * normZ

        val newYaw = toDegrees(fastArcTan2(-reflectedX, reflectedZ)).toFloat()

        setMouseYaw(newYaw)

        this.lastBounceTime = timeMillis()
    }

    private fun calculateVerticalAimSpeed(pitchDiff: Double): Double {
        val pitchDiffFactor = 1
        val diffFactorEquation = 2 * pitchDiff / 5
        return pitchDiffFactor * diffFactorEquation
    }

    private var nextClick: Long = 0

    private fun attackTarget() {
        val fakePlayer = clientInstance.fakePlayer
        nextClick = (timeMillis() + fakePlayer.random.nextGaussian(meanDelay.toDouble(), deviation.toDouble())).toLong()
        if (target != null && fakePlayer.distance3DTo(target!!) <= 6) {
            pressButton(25, MouseButton.Type.LEFT_CLICK)
        }
    }

    private var strafeDirection: Byte = 0
    private var canWtap = false
    private var canStap = false

    private fun strafe() {
        if (isPathFollowingActive()) {
            unpressKey(Key.Type.KEY_A, Key.Type.KEY_D, Key.Type.KEY_S)
            return
        }

        val target = this.target ?: return

        val config = clientInstance.configuration

        val fakePlayer = clientInstance.fakePlayer
        val distance = fakePlayer.distance3DTo(target)
        if (distance in 5.0..6.5 && timeMillis() - lastJumpTime > 4 * 1000) {
            lastJumpTime = timeMillis()
            if ( fakePlayer.random.nextFloat() <= config.jumpProbability) {
                pressKey(100, Key.Type.KEY_SPACE)
            } else {
                unpressKey( Key.Type.KEY_SPACE)
            }
        }

        if (timeMillis() - lastCheckStapTime > 3 * 20 * 1000) {
            lastCheckStapTime = timeMillis()
            canWtap = fakePlayer.random.nextFloat() <= config.wtapProbability
            canStap = fakePlayer.random.nextFloat() <= 0.5
        }

        if (!fakePlayer.isOnGround || distance > 2.95 /*
                                                         * || timeMillis() -
                                                         * fakePlayer.
                                                         * getLastHitSelected
                                                         * ()
                                                         * < 1000
                                                         */) {
            unpressKey(Key.Type.KEY_D, Key.Type.KEY_A)
            return
        }

        strafeDirection = strafeDirection(target)

        when (strafeDirection.toInt()) {
            1 -> {
                unpressKey(Key.Type.KEY_D)
                pressKey(Key.Type.KEY_A)
            }

            2 -> {
                unpressKey(Key.Type.KEY_A)
                pressKey(Key.Type.KEY_D)
            }
            3 -> {
                unpressKey(Key.Type.KEY_A,Key.Type.KEY_D)
                if (canWtap && fakePlayer.isOnGround && clientInstance.currentTick - lastTargetDamageTick > 35)  {
                    unpressKey(150,Key.Type.KEY_W)
                    if (canStap) pressKey(150,Key.Type.KEY_S)
                }
            }
        }
    }

    private fun strafeDirection(target: ClientEntity): Byte {
        val fakePlayer = clientInstance.fakePlayer
        val toPlayer = doubleArrayOf(
            fakePlayer.x - target.x,
            fakePlayer.y - target.y,
            fakePlayer.z - target.z
        )
        val aimVector = vectorForRotation(
            target.pitch,
            target.yaw
        )

        val crossProduct = crossProduct2D(toPlayer, aimVector)

        // If cross product is positive, player is to the right of the aim direction
        // If cross product is negative, player is to the left of the aim direction

        val epsilon = 0.6

        return when {
            crossProduct < -epsilon -> 1 // left
            crossProduct > epsilon  -> 2 // right
            else -> 3
        }
    }

    private fun crossProduct2D(vec: DoubleArray, other: DoubleArray): Float {
        return (vec[0] * other[2] - vec[2] * other[0]).toFloat()
    }

    private fun sprintReset() {
        val target = this.target ?: return

        val fakePlayer = clientInstance.fakePlayer

        val distance = fakePlayer.distance3DTo(target)
        val targetDiffTick = clientInstance.currentTick - lastTargetDamageTick

        val inventory = fakePlayer.inventory
        val itemStack = inventory.heldItemStack
        val config = clientInstance.configuration

        val resetType =
            if (fakePlayer.isOnGround && targetDiffTick > 35) { // combo
                if (distance <= 2.5) EXTRA_OFFENSIVE
                else OFFENSIVE
            } else if (itemStack != null && Item.Type.SWORD.isType(itemStack.item.id)) {
                if (fakePlayer.random.nextFloat() <= config.sprintResetAccuracy) DEFENSIVE
                else EXTRA_DEFENSIVE
            } else DEFENSIVE

        when (resetType) {
            EXTRA_OFFENSIVE -> {
                if (config.sprintResetAccuracy >= 1
                    || fakePlayer.random.nextFloat() < config
                        .sprintResetAccuracy
                ) {
                    unpressKey(200, Key.Type.KEY_W)
                    pressKey(200, Key.Type.KEY_S)
                }
            }

            OFFENSIVE -> if (config.sprintResetAccuracy >= 1
                || fakePlayer.random.nextFloat() < config
                    .sprintResetAccuracy
            ) {
                unpressKey(300, Key.Type.KEY_W)
            }

            DEFENSIVE -> if (config.sprintResetAccuracy >= 1
                || fakePlayer.random.nextFloat() < config
                    .sprintResetAccuracy
            ) {
                unpressKey(150, Key.Type.KEY_W)
            }

            EXTRA_DEFENSIVE -> if (config.sprintResetAccuracy >= 1
                || fakePlayer.random.nextFloat() < config
                    .sprintResetAccuracy
            ) {
                pressButton(150, MouseButton.Type.RIGHT_CLICK)
            }

        }
    }

    internal enum class ResetType {
        EXTRA_OFFENSIVE, OFFENSIVE, DEFENSIVE, EXTRA_DEFENSIVE
    }

    private fun getBestMeleeWeaponSlot(): Int {
        var bestMeleeWeaponSlot = 0
        var damage = 0.0
        val fakePlayer = clientInstance.fakePlayer
        val inventory = fakePlayer.inventory

        // Look for a non-splash potion in one of the 36 slots
        invLoop@ for (i in 0..35) {
            val itemStack = inventory.getItemStackAt(i) ?: continue
            val attackDamage = itemStack.attackDamage
            if (attackDamage > damage) {
                bestMeleeWeaponSlot = i
                damage = attackDamage
            }
        }

        return bestMeleeWeaponSlot
    }

    override fun onTick(tick: Tick) {
        val meleeWeaponSlot = getBestMeleeWeaponSlot()
        val inventory = clientInstance.fakePlayer.inventory

        tick.prerequisite("In Hotbar", meleeWeaponSlot <= 8) {
            moveItemToHotbar(meleeWeaponSlot, inventory)
        }

        tick.prerequisite("Inventory Closed", clientInstance.currentScreen !is ContainerScreen) {
            pressKey(
                10,
                Key.Type.KEY_E
            )
        }

        tick.prerequisite("Correct Hotbar Slot Selected", inventory.heldSlot == meleeWeaponSlot) {
            pressKey(10, Key.Type.valueOf("KEY_" + (meleeWeaponSlot + 1)))
        }


        tick.execute {
            findTarget()
            updatePath()
            if (isPathFollowingActive()) {
                return@execute
            }

            if (target == null) {
                unpressKey(Key.Type.KEY_W, Key.Type.KEY_A, Key.Type.KEY_D, Key.Type.KEY_S, Key.Type.KEY_LCONTROL)
                return@execute
            }

            aimAtTarget()
            unpressKey(Key.Type.KEY_S)
            pressKey(Key.Type.KEY_W, Key.Type.KEY_LCONTROL)
        }

        if (this.target == null && timeMillis() - lastBounceTime > 1000) if (isCollidingWithWall) reflectOffWall()
    }

    override fun onEvent(event: Event): Boolean {
        if (event is EntityHurtEvent) return onEntityHurt(event)
        return false
    }

    fun onEntityHurt(event: EntityHurtEvent): Boolean {
        val entity = event.attackedEntity
        val fakePlayer = clientInstance.fakePlayer
        val target = this.target

        lastHurtEntity = entity.uuid
        if (entity.uuid == fakePlayer.uuid) lastTargetDamageTick = clientInstance.currentTick
        if (target != null && entity.uuid == target.uuid) lastSelfDamageTick = clientInstance.currentTick

        if (clientInstance.currentTick - lastSprintResetTick < 9) return false

        if (target != null && entity.uuid == target.uuid) {
            sprintReset()
            lastSprintResetTick = clientInstance.currentTick
        }

        return false
    }

    public override fun onGameLoop() {
        strafe()
        move()
        if (timeMillis() >= nextClick) attackTarget()
    }

    private fun isPathFollowingActive(): Boolean {
        if (currentPath.isEmpty()) {
            return false
        }
        if (currentPath.size - currentNodeIndex > 8) {
            return true
        }
        val lastDamageTick = maxOf(lastSelfDamageTick, lastTargetDamageTick)
        return clientInstance.currentTick - lastDamageTick > 20 * 3 && timeMillis() - lastArrivedNodeTime > 1000L * 3
    }

    private fun getPathAimTarget(): NextPos? {
        if (currentNodeIndex >= currentPath.size) return null

        updatePathLookAheadNodes()
        val lookAheadIndex = min(currentNodeIndex + pathLookAheadNodes, currentPath.lastIndex)

        val node = currentPath[lookAheadIndex]
        return NextPos(node.x + 0.5, node.y.toDouble(), node.z + 0.5)
    }

    private fun updatePathLookAheadNodes() {
        if (timeMillis() - lastSwitchAheadTime <= 500L) {
            return
        }
        if (timeMillis() - lastArrivedNodeTime >= 1000L * 1 && pathLookAheadNodes > 0) {
            lastSwitchAheadTime = timeMillis()
            pathLookAheadNodes --
        }
        if (timeMillis() - lastArrivedNodeTime < 1000L * 1 && pathLookAheadNodes < 5) {
            lastSwitchAheadTime = timeMillis()
            pathLookAheadNodes ++
        }
    }

    private fun lookAtPath() {
        val fakePlayer = clientInstance.fakePlayer
        val pathTarget = getPathAimTarget()
        if (pathTarget != null) {
            val targetYaw = computeOptimalYawAndPitch(fakePlayer, pathTarget)[1]
            setMouseYaw(getRotationTarget(fakePlayer.yaw, targetYaw, pathTurnSpeed, 1.0f, 0.0f))
            setMousePitch(0.0f)

            pressKey(Key.Type.KEY_W, Key.Type.KEY_LCONTROL)
        }
    }

    private fun move() {
        val fakePlayer = clientInstance.fakePlayer
        val world = fakePlayer.world

        val dir = vectorForRotation(0f, fakePlayer.yaw)
        val nextX = fakePlayer.x + dir[0] * 0.75
        val nextZ = fakePlayer.z + dir[2] * 0.75

        val groundAhead = world.getBlockAt(nextX, fakePlayer.y - 1.0, nextZ).id
        val feetBlock = world.getBlockAt(fakePlayer.x, fakePlayer.y, fakePlayer.z).id

        val box = world.getBlockAt(nextX, fakePlayer.y, nextZ).getCollisionBoundingBox(world, floor(nextX), floor(fakePlayer.y), floor(nextZ))
        val dy = if (box != null )box.maxY - box.minY else 0.0

        if (Block.Type.LIQUID.isType(feetBlock)) {
            inLiquid = true
            pressKey(Key.Type.KEY_SPACE)
        } else if (inLiquid){
            inLiquid = false
            unpressKey(Key.Type.KEY_SPACE)
        }
        if (Block.Type.DANGEROUS.isType(groundAhead)) {
            keyboardStopAll()
        }

        if (dy >= 1.0 && fakePlayer.isOnGround) {
            pressKey(100, Key.Type.KEY_SPACE)
        }

        if (currentNodeIndex < currentPath.size) {
            val node = currentPath[currentNodeIndex]
            val targetX = node.x + 0.5
            val targetY = node.y.toDouble()
            val targetZ = node.z + 0.5
            val dx = targetX - fakePlayer.x
            val dy = targetY - fakePlayer.y
            val dz = targetZ - fakePlayer.z

            if (abs(dx) <= 0.35 && abs(dz) <= 0.35 && abs(dy) < 1.0) {
                if (currentNodeIndex != 0) {
                    lastArrivedNodeTime = timeMillis()
                }
                currentNodeIndex++
            }
        }
        if (isPathFollowingActive()) {
            lookAtPath()
        }

    }

    private fun updatePath() {
        val currentTarget = target
        if (currentTarget == null) {
            currentPath = emptyList()
            currentNodeIndex = 0
            return
        }

        val fakePlayer = clientInstance.fakePlayer

        if (clientInstance.currentTick - lastAstarFindPathTick < 10) return
        if (!fakePlayer.isOnGround) return

        val startPos = Pos(floor(fakePlayer.x), floor(fakePlayer.y), floor(fakePlayer.z))
        val goalPos = Pos(floor(currentTarget.x), floor(currentTarget.y), floor(currentTarget.z))

        val dx = goalPos.x - lastTargetPos.x
        val dy = goalPos.y - lastTargetPos.y
        val dz = goalPos.z - lastTargetPos.z

        if (distanceXZ(dx, dz) < 1 && dy < 1 && currentPath.size - currentNodeIndex > 6) return

        lastTargetPos = goalPos
        lastAstarFindPathTick = clientInstance.currentTick

        currentNodeIndex = 0
        currentPath = Astar(fakePlayer.world).compute(
            startPos,
            goalPos
        ).dropWhile { it == startPos }
    }
}
