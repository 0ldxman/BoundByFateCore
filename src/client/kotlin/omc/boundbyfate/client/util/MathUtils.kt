package omc.boundbyfate.client.util

import de.fabmax.kool.math.Mat3f
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import org.joml.Matrix3f
import org.joml.Matrix4f
import kotlin.math.sqrt

fun Mat4f.asMatrix4f(): Matrix4f = Matrix4f(
    m00, m01, m02, m03,
    m10, m11, m12, m13,
    m20, m21, m22, m23,
    m30, m31, m32, m33
).transpose()

fun Mat3f.asMatrix3f(): Matrix3f = Matrix3f(
    m00, m10, m20,
    m01, m11, m21,
    m02, m12, m22,
).transpose()

fun QuatF.conjugate() = QuatF(-x, -y, -z, w)

fun Float.lerp(other: Float, alpha: Float) = this + (other - this) * alpha

/**
 * Simple Interpolation enum for use in Bedrock model animations.
 */
enum class Interpolation {
    LINEAR, CATMULLROM, STEP;

    companion object {
        fun catmullRom(t: Float, p0: Vec3f, p1: Vec3f, p2: Vec3f, p3: Vec3f): Vec3f {
            val t2 = t * t
            val t3 = t2 * t
            return Vec3f(
                0.5f * ((2f * p1.x) + (-p0.x + p2.x) * t + (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 + (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3),
                0.5f * ((2f * p1.y) + (-p0.y + p2.y) * t + (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 + (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3),
                0.5f * ((2f * p1.z) + (-p0.z + p2.z) * t + (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t2 + (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t3)
            )
        }
    }
}

