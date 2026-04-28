package omc.boundbyfate.api.stat

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.source.SourceReference

/**
 * Модификатор характеристики.
 * 
 * Модификаторы изменяют базовое значение характеристики:
 * - FLAT — добавляет значение (+2 STR от расы)
 * - MULTIPLY — умножает значение (×1.5 от заклинания)
 * - OVERRIDE — заменяет значение (устанавливает STR = 19)
 * - SET_MIN — устанавливает минимум (минимум 13 для multiclass)
 * - SET_MAX — устанавливает максимум (максимум 20 без магических предметов)
 * 
 * Порядок применения:
 * 1. OVERRIDE (если есть, игнорирует base и другие модификаторы)
 * 2. FLAT (складываются)
 * 3. MULTIPLY (перемножаются)
 * 4. SET_MIN / SET_MAX (ограничения)
 * 
 * Примеры:
 * ```kotlin
 * // Расовый бонус: +2 STR от Горного дварфа
 * StatModifier(
 *     source = SourceReference.race(Identifier("dnd", "mountain_dwarf")),
 *     type = ModifierType.FLAT,
 *     value = 2
 * )
 * 
 * // Заклинание: Bull's Strength устанавливает STR = 19
 * StatModifier(
 *     source = SourceReference.spell(Identifier("dnd", "bulls_strength")),
 *     type = ModifierType.OVERRIDE,
 *     value = 19
 * )
 * 
 * // Состояние: Отравление даёт помеху на проверки STR
 * // (это не модификатор стата, а модификатор броска)
 * ```
 */
data class StatModifier(
    /**
     * Источник модификатора.
     * Используется для отслеживания откуда пришёл бонус.
     */
    val source: SourceReference,
    
    /**
     * Тип модификатора.
     */
    val type: ModifierType,
    
    /**
     * Значение модификатора.
     * 
     * Интерпретация зависит от типа:
     * - FLAT: добавляется к базе (+2, -1)
     * - MULTIPLY: множитель в процентах (150 = ×1.5, 50 = ×0.5)
     * - OVERRIDE: новое значение (19)
     * - SET_MIN: минимальное значение (13)
     * - SET_MAX: максимальное значение (20)
     */
    val value: Int,
    
    /**
     * Приоритет применения.
     * Модификаторы с большим приоритетом применяются позже.
     * 
     * Используется для контроля порядка применения модификаторов одного типа.
     * Например, если два OVERRIDE модификатора, применится тот у которого больше priority.
     */
    val priority: Int = 0
) {
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<StatModifier> = RecordCodecBuilder.create { instance ->
            instance.group(
                SourceReference.CODEC.fieldOf("source").forGetter { it.source },
                ModifierType.CODEC.fieldOf("type").forGetter { it.type },
                Codec.INT.fieldOf("value").forGetter { it.value },
                Codec.INT.optionalFieldOf("priority", 0).forGetter { it.priority }
            ).apply(instance, ::StatModifier)
        }
    }
}

/**
 * Тип модификатора характеристики.
 */
enum class ModifierType {
    /**
     * Плоский бонус/штраф.
     * Добавляется к базовому значению.
     * 
     * Примеры:
     * - +2 STR от расы Горный дварф
     * - +4 STR от заклинания Enhance Ability
     * - -2 STR от отравления
     */
    FLAT,
    
    /**
     * Процентный модификатор.
     * Умножает значение.
     * 
     * value = 150 означает ×1.5 (увеличение на 50%)
     * value = 50 означает ×0.5 (уменьшение на 50%)
     * 
     * Примеры:
     * - ×2 от Enlarge (редко используется в D&D 5e)
     */
    MULTIPLY,
    
    /**
     * Переопределение значения.
     * Заменяет базовое значение и игнорирует другие модификаторы.
     * 
     * Примеры:
     * - Bull's Strength устанавливает STR = 19
     * - Belt of Giant Strength устанавливает STR = 21/23/25/27/29
     */
    OVERRIDE,
    
    /**
     * Установка минимального значения.
     * Если итоговое значение меньше, устанавливается минимум.
     * 
     * Примеры:
     * - Multiclass требует минимум 13 в определённых статах
     */
    SET_MIN,
    
    /**
     * Установка максимального значения.
     * Если итоговое значение больше, устанавливается максимум.
     * 
     * Примеры:
     * - Обычный максимум стата = 20
     * - С магическими предметами может быть выше
     */
    SET_MAX;
    
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<ModifierType> = Codec.STRING.xmap(
            { str -> valueOf(str.uppercase()) },
            { type -> type.name.lowercase() }
        )
    }
}
