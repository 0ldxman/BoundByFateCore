package omc.boundbyfate.api.item

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение предмета — привязывает свойства к Minecraft item ID.
 *
 * Не является [omc.boundbyfate.api.core.Definition] в полном смысле —
 * ID здесь это Minecraft item ID, а не наш внутренний ID.
 *
 * ## Ключевая идея
 *
 * `ItemDefinition` — это просто список свойств привязанных к предмету.
 * Никакой другой логики. Всё остальное (урон, AC, владение) — это свойства.
 *
 * ## Примеры JSON
 *
 * ### Оружие
 * ```json
 * {
 *   "item": "minecraft:iron_sword",
 *   "properties": [
 *     {"id": "boundbyfate-core:melee_damage", "data": {"dice": "1d8", "stat": "boundbyfate-core:strength"}},
 *     {"id": "boundbyfate-core:versatile",    "data": {"one_hand": "1d8", "two_hand": "1d10"}},
 *     {"id": "boundbyfate-core:martial_weapon"}
 *   ]
 * }
 * ```
 *
 * ### Броня
 * ```json
 * {
 *   "item": "minecraft:chainmail_chestplate",
 *   "properties": [
 *     {"id": "boundbyfate-core:armor_class", "data": {"formula": "13 + min(@dex_modifier, 2)"}},
 *     {"id": "boundbyfate-core:medium_armor"}
 *   ]
 * }
 * ```
 *
 * ### Предмет из другого мода
 * ```json
 * {
 *   "item": "somemod:magic_amulet",
 *   "properties": [
 *     {"id": "boundbyfate-core:stat_bonus",    "data": {"stat": "boundbyfate-core:charisma", "value": 2}},
 *     {"id": "boundbyfate-core:grants_ability", "data": {"ability": "boundbyfate-core:fireball", "uses": 1, "recovery": "long_rest"}}
 *   ]
 * }
 * ```
 *
 * @property item Minecraft item ID к которому привязаны свойства
 * @property properties список свойств предмета
 */
data class ItemDefinition(
    val item: Identifier,
    val properties: List<ItemPropertyDefinition> = emptyList()
) : Registrable {

    /**
     * ID для системы регистрации — используем item ID.
     */
    override val id: Identifier get() = item

    override fun getTranslationKey(): String =
        "item.${item.namespace}.${item.path}"

    override fun validate() {
        // Проверяем что нет дублирующихся свойств с одинаковым ID
        val ids = properties.map { it.id }
        val duplicates = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw IllegalStateException(
                "ItemDefinition for '$item' has duplicate properties: $duplicates"
            )
        }
    }

    companion object {
        val CODEC: Codec<ItemDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER
                    .fieldOf("item")
                    .forGetter { it.item },
                ItemPropertyDefinition.CODEC.listOf()
                    .optionalFieldOf("properties", emptyList())
                    .forGetter { it.properties }
            ).apply(instance, ::ItemDefinition)
        }
    }
}
