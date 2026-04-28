package omc.boundbyfate.client.util.molang

/**
 * Minimal stub for MolangContext - the full scripting system is not included.
 * Animation controllers that use Molang expressions will use simplified logic.
 */
interface Variables {
    fun getOrNull(name: String): Variable?
    fun getOrPut(name: String, initialValue: Float = 0f): Variable

    class Variable(var value: Float = 0f)
}

class VariablesMap : Variables {
    private val map = mutableMapOf<String, Variables.Variable>()

    override fun getOrNull(name: String): Variables.Variable? = map[name]
    override fun getOrPut(name: String, initialValue: Float): Variables.Variable =
        map.getOrPut(name) { Variables.Variable(initialValue) }
}

interface Query {
    val ground_speed: Float get() = 0f
    val is_moving: Boolean get() = false
    val is_on_ground: Boolean get() = true
    val health: Float get() = 20f
    val max_health: Float get() = 20f
    val is_alive: Boolean get() = true

    companion object {
        val EMPTY = object : Query {}
    }
}

data class MolangContext(val query: Query, val variables: Variables = VariablesMap()) {
    companion object {
        val EMPTY = MolangContext(Query.EMPTY)
    }
}

/**
 * Stub for BoolExpr - always returns false by default.
 */
fun interface BoolExpr {
    fun getBoolean(query: Query, variables: Variables): Boolean
}

/**
 * Stub for FloatExpr - always returns 0f by default.
 */
fun interface FloatExpr {
    fun getFloat(query: Query, variables: Variables): Float
}

/**
 * Stub for FloatVec3Expr.
 */
fun interface FloatVec3Expr {
    fun getVec3(query: Query, variables: Variables): Triple<Float, Float, Float>
}

/**
 * Stub MolangCompiler - compiles expressions to simple stubs.
 */
object MolangCompiler {
    fun compileBoolean(expression: String): BoolExpr {
        return when (expression.trim()) {
            "true", "1" -> BoolExpr { _, _ -> true }
            "false", "0" -> BoolExpr { _, _ -> false }
            else -> BoolExpr { _, _ -> false } // TODO: implement full Molang
        }
    }

    fun compileFloat(expression: String): FloatExpr {
        return try {
            val value = expression.trim().trimEnd('f').toFloat()
            FloatExpr { _, _ -> value }
        } catch (e: NumberFormatException) {
            FloatExpr { _, _ -> 1f } // TODO: implement full Molang
        }
    }
}

fun parseMolangExpression(expression: String): FloatExpr = MolangCompiler.compileFloat(expression)

fun FloatExpr.eval(ctx: MolangContext): Float = getFloat(ctx.query, ctx.variables)

fun FloatVec3Expr.eval(ctx: MolangContext): de.fabmax.kool.math.Vec3f {
    val (x, y, z) = getVec3(ctx.query, ctx.variables)
    return de.fabmax.kool.math.Vec3f(x, y, z)
}


/**
 * Extension function to parse a JsonPrimitive as a Molang expression.
 */
fun kotlinx.serialization.json.JsonPrimitive.parseMolangExpression(): FloatExpr =
    parseMolangExpression(content)
