package omc.boundbyfate.util.extension

import net.minecraft.util.Identifier

/**
 * Extension функции для работы с Identifier.
 */

/**
 * Создаёт Identifier из строки с namespace по умолчанию.
 * 
 * Примеры:
 * - "fighter" -> "boundbyfate-core:fighter"
 * - "minecraft:stone" -> "minecraft:stone"
 */
fun String.toIdentifier(defaultNamespace: String = "boundbyfate-core"): Identifier {
    return if (this.contains(':')) {
        Identifier.of(this)
    } else {
        Identifier.of(defaultNamespace, this)
    }
}

/**
 * Проверяет, принадлежит ли Identifier к namespace BoundByFate.
 */
fun Identifier.isBbf(): Boolean = this.namespace == "boundbyfate-core"

/**
 * Создаёт дочерний Identifier с тем же namespace.
 * 
 * Пример:
 * ```kotlin
 * val base = Identifier("boundbyfate-core", "fighter")
 * val child = base.child("champion") // "boundbyfate-core:fighter/champion"
 * ```
 */
fun Identifier.child(path: String): Identifier =
    Identifier.of(this.namespace, "${this.path}/$path")
