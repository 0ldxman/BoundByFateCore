package omc.boundbyfate.client.models.internal

import kotlinx.serialization.Serializable
import net.minecraft.util.MathHelper
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf

@Serializable
data class Transform(
    val tX: Float = 0f, val tY: Float = 0f, val tZ: Float = 0f,
    val rX: Float = 0f, val rY: Float = 0f, val rZ: Float = 0f,
    val sX: Float = 1.0f, val sY: Float = 1.0f, val sZ: Float = 1.0f,
) {
    val matrix: Matrix4f
        get() = Matrix4f()
            .translate(tX, tY, tZ)
            .rotate(Quaternionf().rotateX(rX * MathHelper.RADIANS_PER_DEGREE))
            .rotate(Quaternionf().rotateY(rY * MathHelper.RADIANS_PER_DEGREE))
            .rotate(Quaternionf().rotateZ(rZ * MathHelper.RADIANS_PER_DEGREE))
            .scale(sX, sY, sZ)
    val normalMatrix: Matrix3f
        get() = Matrix3f()
            .rotate(Quaternionf().rotateX(rX * MathHelper.RADIANS_PER_DEGREE))
            .rotate(Quaternionf().rotateY(rY * MathHelper.RADIANS_PER_DEGREE))
            .rotate(Quaternionf().rotateZ(rZ * MathHelper.RADIANS_PER_DEGREE))
            .scale(sX, sY, sZ)

    companion object {
        fun create(builder: Builder.() -> Unit) = Builder().apply(builder).build()

        class Builder {
            var tX: Float = 0f
            var tY: Float = 0f
            var tZ: Float = 0f
            var rX: Float = 0f
            var rY: Float = 0f
            var rZ: Float = 0f
            var sX: Float = 1.0f
            var sY: Float = 1.0f
            var sZ: Float = 1.0f

            fun translate(x: Float, y: Float, z: Float) {
                tX = x
                tY = y
                tZ = z
            }

            fun scale(x: Float, y: Float, z: Float) {
                sX = x
                sY = y
                sZ = z
            }

            fun rotate(x: Float, y: Float, z: Float) {
                rX = x
                rY = y
                rZ = z
            }

            fun build() = Transform(tX, tY, tZ, rX, rY, rZ, sX, sY, sZ)
        }
    }
}


