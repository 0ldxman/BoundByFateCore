package omc.boundbyfate.api.level

import com.mojang.serialization.Codec
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Грант уровня класса — что персонаж получает на определённом уровне.
 *
 * ## Философия новой системы
 *
 * LevelGrant теперь максимально упрощён — это либо Feature, либо выбор подкласса.
 * Всё остальное (способности, ресурсы, механики, эффекты) даётся через Feature.
 *
 * ## Типы грантов
 *
 * - **Feature** — даёт особенность (которая в свою очередь даёт гранты)
 * - **SubclassChoice** — выбор подкласса
 *
 * ## Примеры JSON
 *
 * ### Fighter Level 1
 * ```json
 * {
 *   "1": [
 *     {"type": "feature", "id": "boundbyfate-core:fighting_style_choice"},
 *     {"type": "feature", "id": "boundbyfate-core:second_wind"}
 *   ]
 * }
 * ```
 *
 * ### Fighter Level 3 (Subclass Choice)
 * ```json
 * {
 *   "3": [
 *     {"type": "subclass_choice"}
 *   ]
 * }
 * ```
 *
 * ### Wizard Level 1 (Spellcasting)
 * ```json
 * {
 *   "1": [
 *     {"type": "feature", "id": "boundbyfate-core:wizard_spellcasting"}
 *   ]
 * }
 * ```
 *
 * Feature `wizard_spellcasting` в свою очередь даёт:
 * - Mechanic: spellcasting (с конфигом для Wizard)
 * - Mechanic: wizard_spellbook
 * - Ability: cast_spell
 */
sealed class LevelGrant {
    
    /**
     * Даёт особенность.
     * Feature сама определяет что даётся (способности, ресурсы, механики, эффекты).
     */
    data class Feature(val featureId: Identifier) : LevelGrant()
    
    /**
     * Выбор подкласса.
     * Список доступных подклассов берётся из ClassDefinition.subclasses.
     */
    data object SubclassChoice : LevelGrant()
    
    companion object {
        val CODEC: Codec<LevelGrant> = Codec.STRING.dispatch(
            "type",
            { grant ->
                when (grant) {
                    is Feature -> "feature"
                    is SubclassChoice -> "subclass_choice"
                }
            },
            { type ->
                when (type) {
                    "feature" -> CodecUtil.IDENTIFIER.fieldOf("id").xmap(
                        { Feature(it) },
                        { it.featureId }
                    ).codec()
                    
                    "subclass_choice" -> Codec.unit(SubclassChoice)
                    
                    else -> throw IllegalArgumentException("Unknown level grant type: $type")
                }
            }
        )
    }
}
