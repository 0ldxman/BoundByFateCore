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
    LINEAR, CATMULLROM, STEP
}

