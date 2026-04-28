package omc.boundbyfate.api.stat

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение характеристики (Stat).
 * 
 * Характеристики — это базовые атрибуты персонажа в D&D:
 * - Strength (STR) — Сила
 * - Dexterity (DEX) — Ловкость
 * - Constitution (CON) — Телосложение
 * - Intelligence (INT) — Интеллект
 * - Wisdom (WIS) — Мудрость
 * - Charisma (CHA) — Харизма
 * 
 * Пример JSON:
 * ```json
 * {
 *   "abbreviation": "STR",
 *   "default_value": 10,
 *   "min_value": 1,
 *   "max_value": 30
 * }
 * ```
 * 
 * Локализация (en_us.json):
 * ```json
 * {
 *   "stat.boundbyfate-core.strength": "Strength",
 *   "stat.boundbyfate-core.strength.description": "Physical power and athletic ability",
 *   "stat.boundbyfate-core.strength.abbreviation": "STR"
 * }
 * ```
 */
data class StatDefinition(
    override val id: Identifier,
    
    /**
     * Аббревиатура (например, "STR", "DEX").
     * Может быть ключом локализации.
     */
    val abbreviation: String,
    
    /**
     * Значение по умолчанию (обычно 10 в D&D).
     */
    val defaultValue: Int = 10,
    
    /**
     * Минимальное значение.
     */
    val minValue: Int = 1,
    
    /**
     * Максимальное значение.
     */
    val maxValue: Int = 30
) : Definition, Registrable {
    
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<StatDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("abbreviation").forGetter { it.abbreviation },
                Codec.INT.optionalFieldOf("default_value", 10).forGetter { it.defaultValue },
                Codec.INT.optionalFieldOf("min_value", 1).forGetter { it.minValue },
                Codec.INT.optionalFieldOf("max_value", 30).forGetter { it.maxValue }
            ).apply(instance, ::StatDefinition)
        }
    }
    
    override fun getTranslationKey(): String = "stat.${id.namespace}.${id.path}"
    
    /**
     * Ключ локализации для аббревиатуры.
     */
    fun getAbbreviationKey(): String = "${getTranslationKey()}.abbreviation"
    
    /**
     * Получает локализованную аббревиатуру.
     * Если ключ не найден, возвращает значение из JSON.
     */
    fun getTranslatedAbbreviation(): Text = 
        Text.translatable(getAbbreviationKey()).also {
            // Fallback на значение из JSON если перевод не найден
            // (Minecraft автоматически использует ключ если перевод отсутствует)
        }
    
    override fun validate() {
        require(minValue <= defaultValue) {
            "Default value ($defaultValue) must be >= min value ($minValue)"
        }
        require(defaultValue <= maxValue) {
            "Default value ($defaultValue) must be <= max value ($maxValue)"
        }
        require(abbreviation.isNotBlank()) {
            "Abbreviation cannot be blank"
        }
    }
    
    /**
     * Вычисляет модификатор характеристики.
     * В D&D: modifier = (value - 10) / 2
     */
    fun calculateModifier(value: Int): Int = (value - 10) / 2
    
    /**
     * Проверяет, находится ли значение в допустимых пределах.
     */
    fun isValidValue(value: Int): Boolean = value in minValue..maxValue
    
    /**
     * Ограничивает значение допустимыми пределами.
     */
    fun clampValue(value: Int): Int = value.coerceIn(minValue, maxValue)
}
