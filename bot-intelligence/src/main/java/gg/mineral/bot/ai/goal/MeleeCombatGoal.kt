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
import kotlin.math.PI

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
    private var lastJumpTime = 0
    private var lastCheckStapTime: Long = 0
    private var lastHurtEntity: UUID = clientInstance.fakePlayer.uuid
    private var lastTargetDamageTick = 0
    private var lastSelfDamageTick = 0
    private var inLiquid = false

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
        target?.let {
            if (fakePlayer.distance3DTo(it) <= 5) {
                pressButton(25, MouseButton.Type.LEFT_CLICK)
            }
        }
    }

    private var strafeDirection: Byte = 0
    private var canWtap = false
    private var canStap = false

    private fun strafe() {
        val target = this.target ?: return

        val config = clientInstance.configuration

        val fakePlayer = clientInstance.fakePlayer
        val distance = fakePlayer.distance3DTo(target)
        if (timeMillis() - lastJumpTime > 20 * 1000) {
            lastJumpTime = clientInstance.currentTick
            if (distance in 5.0..6.5 && fakePlayer.random.nextFloat() <= config.jumpProbability) {
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
        findTarget()

        val meleeWeaponSlot = getBestMeleeWeaponSlot()
        val fakePlayer = clientInstance.fakePlayer
        val inventory = fakePlayer.inventory

        if (target != null && clientInstance.currentTick - lastAstarFindPathTick > 19) {
            lastAstarFindPathTick = clientInstance.currentTick
            currentNodeIndex = 0
            currentPath = Astar(fakePlayer.world).compute(
                Pos(floor(fakePlayer.x), floor(fakePlayer.y), floor(fakePlayer.z)),
                Pos(floor(target!!.x), floor(target!!.y), floor(target!!.z))
            )
        }

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
            if (currentPath.size - currentNodeIndex <= 5) {
                aimAtTarget()
                pressKey(Key.Type.KEY_W, Key.Type.KEY_LCONTROL)
                return@execute
            }
            // 确保路径有效且还有节点要走
            if (currentPath.isNotEmpty() && currentNodeIndex < currentPath.size) {

                // ==========================================
                // 1. 动态节点剔除 (解决“走过头”原地打转的问题)
                // ==========================================
                while (currentNodeIndex < currentPath.size) {
                    val node = currentPath[currentNodeIndex]
                    // 【关键修改】目标点必须 +0.5，对齐方块中心！
                    val targetX = node.x + 0.5
                    val targetZ = node.z + 0.5

                    // 【关键修改】计算 2D 平面距离的平方 (忽略高度差Y)
                    val dx = targetX - fakePlayer.x
                    val dz = targetZ - fakePlayer.z
                    val distSq2D = dx * dx + dz * dz

                    // 如果水平距离小于 1.5 格 (1.5 * 1.5 = 2.25)，说明触达该节点，直接切换下一个！
                    if (distSq2D <= 2.25) {
                        currentNodeIndex++
                    } else {
                        // 还没走到当前节点，跳出循环，继续往这个节点走
                        break
                    }
                }

                // ==========================================
                // 2. 丝滑移动与朝向控制 (解决“摇头晃脑”的问题)
                // ==========================================
                if (currentNodeIndex < currentPath.size) {
                    // 前瞻机制 (Lookahead)：视角看向下一个节点，让走位更圆滑，而不是死板的折线
                    val lookAheadIndex = minOf(currentNodeIndex + 1, currentPath.size - 1)
                    val aimNode = currentPath[lookAheadIndex]
                    val aimX = aimNode.x + 0.5
                    val aimZ = aimNode.z + 0.5

                    // 纯手工计算 Yaw (偏航角)，基于前瞻节点
                    val aimDx = aimX - fakePlayer.x
                    val aimDz = aimZ - fakePlayer.z
                    // Minecraft 的 Yaw 计算公式：-atan2(x, z) 转换为角度
                    val optimalYaw = Math.toDegrees(-Math.atan2(aimDx, aimDz)).toFloat()

                    // 【平滑插值】计算需要转多少度
                    val yawDiff = angleDifference(fakePlayer.yaw, optimalYaw).toFloat()

                    // 核心魔法：只转动差值的 40% (系数可调 0.1~1.0)，实现人类视角的丝滑转动
                    val smoothYaw = fakePlayer.yaw + (yawDiff * 0.4f)
                    setMouseYaw(smoothYaw)

                    // 【独立 Pitch 控制】
                    // 寻路时千万不要把 Pitch 看向地板上的寻路节点，这会导致上下楼梯时疯狂点头！
                    if (target != null) {
                        // 如果有目标，准星高度看向敌人
                        val pitch = computeOptimalYawAndPitch(fakePlayer, NextPos(target!!.x, target!!.y + 1.0, target!!.z))[0]
                        setMousePitch(pitch)
                    } else {
                        // 没有目标时，平视前方
                        setMousePitch(0f)
                    }

                    // 【关键修改】无论视角差多少，只要在路上，就一直按住 W 和 Sprint，绝对不急刹车！
                    pressKey(Key.Type.KEY_W, Key.Type.KEY_LCONTROL)

                } else {
                    unpressKey(Key.Type.KEY_W, Key.Type.KEY_LCONTROL)
                }
            } else {
                unpressKey(Key.Type.KEY_W, Key.Type.KEY_LCONTROL)
            }
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

    private fun move() {
        val fakePlayer = clientInstance.fakePlayer
        val world = fakePlayer.world

        val dir = vectorForRotation(0f, fakePlayer.yaw)
        val nextX = fakePlayer.x + dir[0] * 0.75
        val nextZ = fakePlayer.z + dir[2] * 0.75

        val nextFeetBlock = world.getBlockAt(nextX, fakePlayer.y, nextZ).id
        val nextHeadBlock = world.getBlockAt(nextX, fakePlayer.y + 1.0, nextZ).id
        val groundAhead = world.getBlockAt(nextX, fakePlayer.y - 1.0, nextZ).id
        val feetBlock = world.getBlockAt(fakePlayer.x, fakePlayer.y, fakePlayer.z).id

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

        // Auto-jump on small ledges / one-block highs, but not on carpet/snow-layer micro-steps.
        if (!Block.Type.PASSABLE.isType(nextFeetBlock)  && !Block.Type.UNPASSABLE.isType(nextFeetBlock) && Block.Type.PASSABLE.isType(nextHeadBlock) && fakePlayer.isOnGround) {
            pressKey(100, Key.Type.KEY_SPACE)
        }
    }
}
