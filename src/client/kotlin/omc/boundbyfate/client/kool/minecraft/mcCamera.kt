package omc.boundbyfate.client.kool.minecraft

import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.scene.PerspectiveCamera
import de.fabmax.kool.scene.Scene
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper
import kotlin.math.abs
import kotlin.math.min

class MinecraftCamera : PerspectiveCamera() {
    override fun updateProjectionMatrix(updateEvent: RenderPass.UpdateEvent) {
        super.updateProjectionMatrix(updateEvent)

        val mc = MinecraftClient.getInstance()
        if (mc.player == null || mc.world == null) return

        val partialTick = mc.renderTickCounter.getTickDelta(true)
        bobHurt(proj, partialTick)
        if (mc.options.bobView) {
            bobView(proj, partialTick)
        }
    }
}

fun Scene.mcCamera() {
    val camera = MinecraftCamera()
    mainRenderPass.defaultView.camera = camera

    onUpdate {
        camera.syncFromMinecraft()
    }
}

fun MinecraftCamera.syncFromMinecraft() {
    val mc = MinecraftClient.getInstance()
    if (mc.player == null || mc.world == null) return

    val gameRenderer = mc.gameRenderer
    val partialTick = mc.renderTickCounter.getTickDelta(true)

    clipNear = 0.05f
    clipFar = gameRenderer.viewDistance.toFloat()
    fovY = gameRenderer.getFov(gameRenderer.camera, partialTick, true).toFloat().deg

    val pos = gameRenderer.camera.pos
    position.set(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())

    val look = gameRenderer.camera.horizontalPlane
    lookAt.set(
        (pos.x + look.x).toFloat(),
        (pos.y + look.y).toFloat(),
        (pos.z + look.z).toFloat()
    )

    val upVec = gameRenderer.camera.verticalPlane
    up.set(upVec.x, upVec.y, upVec.z)
}

private fun bobHurt(mat4f: MutableMat4f, partialTicks: Float) {
    val mc = MinecraftClient.getInstance()
    val cameraEntity = mc.cameraEntity
    if (cameraEntity is LivingEntity) {
        var f = cameraEntity.hurtTime.toFloat() - partialTicks
        var g: Float
        if (cameraEntity.isDead) {
            g = min((cameraEntity.deathTime.toFloat() + partialTicks).toDouble(), 20.0).toFloat()
            mat4f.rotate(QuatF.rotation((40.0f - 8000.0f / (g + 200.0f)).deg, Vec3f.Z_AXIS))
        }

        if (f < 0.0f) return

        f /= 10.0f // hurtDuration equivalent
        f = MathHelper.sin(f * f * f * f * MathHelper.PI)
        g = cameraEntity.lastDamageTakenBeforeAbsorption
        mat4f.rotate(QuatF((-g).deg, Vec3f.Y_AXIS))
        val h = ((-f).toDouble() * 14.0 * mc.options.damageTiltStrength.value).toFloat()
        mat4f.rotate(QuatF(h.deg, Vec3f.Z_AXIS))
        mat4f.rotate(QuatF(g.deg, Vec3f.Y_AXIS))
    }
}

private fun bobView(mat4f: MutableMat4f, partialTicks: Float) {
    val mc = MinecraftClient.getInstance()
    val cameraEntity = mc.cameraEntity
    if (cameraEntity is PlayerEntity) {
        val f = cameraEntity.distanceTraveled - cameraEntity.prevDistanceTraveled
        val g = -(cameraEntity.distanceTraveled + f * partialTicks)
        val h = MathHelper.lerp(partialTicks, cameraEntity.prevStrideDistance, cameraEntity.strideDistance)
        mat4f.translate(
            (MathHelper.sin(g * MathHelper.PI) * h * 0.5f),
            -abs((MathHelper.cos(g * MathHelper.PI) * h)),
            0.0f
        )
        mat4f.rotate(QuatF((MathHelper.sin(g * MathHelper.PI) * h * 3.0f).deg, Vec3f.Z_AXIS))
        mat4f.rotate(QuatF((abs(MathHelper.cos(g * MathHelper.PI - 0.2f) * h) * 5.0f).deg, Vec3f.X_AXIS))
    }

    val f = mc.options.distortionEffectScale.value.toFloat()
    val player = mc.player ?: return
    val g = MathHelper.lerp(
        partialTicks,
        player.prevNauseaIntensity,
        player.nauseaIntensity
    ) * f * f
    if (g > 0.0f) {
        val i = if (player.hasStatusEffect(StatusEffects.NAUSEA)) 7 else 20
        var h = 5.0f / (g * g + 5.0f) - g * 0.04f
        h *= h
        val axis = Vec3f(0.0f, MathHelper.SQUARE_ROOT_OF_TWO / 2.0f, MathHelper.SQUARE_ROOT_OF_TWO / 2.0f)
        mat4f.rotate(((player.age + partialTicks) * i.toFloat()).deg, axis)
        mat4f.scale(Vec3f(1.0f / h, 1.0f, 1.0f))
        val j: Float = -(player.age + partialTicks) * i.toFloat()
        mat4f.rotate(j.deg, axis)
    }
}
