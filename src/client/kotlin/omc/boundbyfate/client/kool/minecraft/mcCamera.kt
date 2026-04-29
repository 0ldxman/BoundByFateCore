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
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper
import omc.boundbyfate.client.mixin.accessor.GameRendererAccessor
import omc.boundbyfate.client.mixin.accessor.LivingEntityAccessor
import omc.boundbyfate.client.mixin.accessor.MinecraftClientAccessor
import kotlin.math.abs
import kotlin.math.min

class MinecraftCamera : PerspectiveCamera() {
    override fun updateProjectionMatrix(updateEvent: RenderPass.UpdateEvent) {
        super.updateProjectionMatrix(updateEvent)

        val mc = MinecraftClient.getInstance()
        if (mc.player == null || mc.world == null) return

        val partialTick = (mc as MinecraftClientAccessor).bbf_getRenderTickCounter().tickDelta
        bobHurt(proj, partialTick)
        if (mc.options.bobView.value) {
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
    val partialTick = (mc as MinecraftClientAccessor).bbf_getRenderTickCounter().tickDelta

    clipNear = 0.05f
    clipFar = gameRenderer.viewDistance.toFloat()
    fovY = (gameRenderer as GameRendererAccessor).bbf_getFov(gameRenderer.camera, partialTick, true).toFloat().deg

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
        g = (cameraEntity as LivingEntityAccessor).bbf_getLastDamageTaken()
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
        val entityAccessor = cameraEntity as LivingEntityAccessor
        val f = cameraEntity.distanceTraveled - cameraEntity.horizontalSpeed
        val g = -(cameraEntity.distanceTraveled + f * partialTicks)
        // yarn 1.20.1: prevStepBobbingAmount, stepBobbingAmount
        val h = MathHelper.lerp(partialTicks, entityAccessor.bbf_getLastStrideDistance(), entityAccessor.bbf_getStrideDistance())
        mat4f.translate(
            (MathHelper.sin(g * MathHelper.PI) * h * 0.5f),
            -abs((MathHelper.cos(g * MathHelper.PI) * h)),
            0.0f
        )
        mat4f.rotate(QuatF((MathHelper.sin(g * MathHelper.PI) * h * 3.0f).deg, Vec3f.Z_AXIS))
        mat4f.rotate(QuatF((abs(MathHelper.cos(g * MathHelper.PI - 0.2f) * h) * 5.0f).deg, Vec3f.X_AXIS))
    }

    // nauseaIntensity not available in 1.20.1 yarn — skip nausea bobbing
}
