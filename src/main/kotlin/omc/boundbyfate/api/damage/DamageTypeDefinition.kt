package omc.boundbyfate.api.damage

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition

/**
 * Определение типа урона в D&D 5e.
 * 
 * В D&D 5e существует 13 типов урона:
 * 
 * **Физический урон:**
 * - Slashing (рубящий) — мечи, топоры
 * - Piercing (колющий) — стрелы, копья, рапиры
 * - Bludgeoning (дробящий) — молоты, дубины, кулаки
 * 
 * **Стихийный урон:**
 * - Fire (огонь)
 * - Cold (холод)
 * - Lightning (электричество)
 * - Thunder (звук/гром)
 * - Acid (кислота)
 * 
 * **Специальный урон:**
 * - Poison (яд)
 * - Necrotic (некротический)
 * - Radiant (излучение/свет)
 * - Force (силовое поле)
 * - Psychic (психический)
 * 
 * **ВАЖНО:** Тип урона описывает ЧТО за урон, но не КАК он был нанесён.
 * 
 * Источник урона (магический/немагический) определяется отдельно при нанесении:
 * - Обычный меч: slashing (немагический)
 * - Магический меч: slashing (магический)
 * - Flame Tongue: slashing (магический) + fire (магический)
 * - Горящая бочка: fire (немагический)
 * - Fireball: fire (магический)
 * 
 * Типы урона используются для:
 * 1. Определения сопротивлений/иммунитетов/уязвимостей
 * 2. Взаимодействия с особенностями (Features)
 * 3. Описания оружия и заклинаний
 * 
 * Примеры:
 * ```kotlin
 * // Огненный урон (может быть магическим или немагическим)
 * DamageTypeDefinition(
 *     id = Identifier("dnd", "fire"),
 *     tags = listOf("elemental")
 * )
 * 
 * // Рубящий урон (может быть магическим или немагическим)
 * DamageTypeDefinition(
 *     id = Identifier("dnd", "slashing"),
 *     tags = listOf("physical")
 * )
 * ```
 */
data class DamageTypeDefinition(
    /**
     * Уникальный идентификатор типа урона.
     * Примеры: "dnd:fire", "dnd:slashing", "dnd:necrotic"
     */
    override val id: Identifier,
    
    /**
     * Теги для группировки типов урона.
     * 
     * Стандартные теги:
     * - "physical" — физический урон (slashing, piercing, bludgeoning)
     * - "elemental" — стихийный урон (fire, cold, lightning, thunder, acid)
     * - "mental" — ментальный урон (psychic)
     * - "holy" — святой урон (radiant)
     * - "dark" — тёмный урон (necrotic)
     * - "pure" — чистая магия (force)
     * 
     * **ВАЖНО:** Теги описывают природу ТИПА урона, а не источник.
     * 
     * Источник урона (магический/немагический) определяется при нанесении:
     * - Обычный меч: slashing (немагический источник)
     * - Магический меч: slashing (магический источник)
     * - Горящая бочка: fire (немагический источник)
     * - Fireball: fire (магический источник)
     * 
     * Теги позволяют создавать гибкие правила:
     * - "Сопротивление ко всему физическому урону"
     * - "Иммунитет к стихийному урону"
     * - "Уязвимость к огню независимо от источника"
     */
    val tags: List<String> = emptyList()
) : Definition {
    
    override fun getTranslationKey(): String = "damage_type.${id.namespace}.${id.path}"
    
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<DamageTypeDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("id").forGetter { it.id },
                Codec.STRING.listOf().optionalFieldOf("tags", emptyList()).forGetter { it.tags }
            ).apply(instance, ::DamageTypeDefinition)
        }
    }
}
