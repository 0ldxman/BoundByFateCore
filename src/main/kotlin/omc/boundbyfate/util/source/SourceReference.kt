package omc.boundbyfate.util.source

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Ссылка на источник бонуса/особенности/модификатора.
 * 
 * Хранит информацию о том, откуда пришёл эффект:
 * - Тип источника (раса, класс, фит, предмет, заклинание)
 * - ID источника (конкретная раса/класс/фит)
 * - Дополнительные метаданные (опционально)
 * 
 * Философия:
 * - Используется для отслеживания происхождения эффектов
 * - Позволяет показывать игроку "откуда этот бонус?"
 * - Позволяет удалять эффекты от конкретного источника
 * 
 * Примеры:
 * ```kotlin
 * // Бонус от расы Dwarf
 * val raceBonus = SourceReference(
 *     type = SourceType.RACE,
 *     id = Identifier.of("boundbyfate-core", "dwarf")
 * )
 * 
 * // Бонус от заклинания Bull's Strength
 * val spellBonus = SourceReference(
 *     type = SourceType.SPELL,
 *     id = Identifier.of("boundbyfate-core", "bulls_strength"),
 *     metadata = mapOf("caster" to casterUuid.toString())
 * )
 * 
 * // Бонус от предмета Belt of Giant Strength
 * val itemBonus = SourceReference(
 *     type = SourceType.ITEM,
 *     id = Identifier.of("minecraft", "leather_chestplate"),
 *     metadata = mapOf("slot" to "chest")
 * )
 * ```
 * 
 * @property type тип источника (RACE, CLASS, FEAT, ITEM, SPELL, etc)
 * @property id идентификатор источника (ID расы/класса/фита/предмета)
 * @property metadata дополнительные метаданные (опционально)
 */
data class SourceReference(
    val type: SourceType,
    val id: Identifier,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<SourceReference> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.xmap(
                    { SourceType.valueOf(it.uppercase()) },
                    { it.name.lowercase() }
                ).fieldOf("type").forGetter { it.type },
                CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id },
                Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("metadata", emptyMap())
                    .forGetter { it.metadata }
            ).apply(instance, ::SourceReference)
        }
        
        /**
         * Создаёт ссылку на источник типа RACE.
         */
        fun race(raceId: Identifier) = SourceReference(SourceType.RACE, raceId)
        
        /**
         * Создаёт ссылку на источник типа CLASS.
         */
        fun charClass(classId: Identifier) = SourceReference(SourceType.CLASS, classId)
        
        /**
         * Создаёт ссылку на источник типа SUBCLASS.
         */
        fun subclass(subclassId: Identifier) = SourceReference(SourceType.SUBCLASS, subclassId)
        
        /**
         * Создаёт ссылку на источник типа FEAT.
         */
        fun feat(featId: Identifier) = SourceReference(SourceType.FEAT, featId)
        
        /**
         * Создаёт ссылку на источник типа FEATURE.
         */
        fun feature(featureId: Identifier) = SourceReference(SourceType.FEATURE, featureId)
        
        /**
         * Создаёт ссылку на источник типа ITEM.
         */
        fun item(itemId: Identifier, slot: String? = null): SourceReference {
            val metadata = if (slot != null) mapOf("slot" to slot) else emptyMap()
            return SourceReference(SourceType.ITEM, itemId, metadata)
        }
        
        /**
         * Создаёт ссылку на источник типа SPELL.
         */
        fun spell(spellId: Identifier, casterId: String? = null): SourceReference {
            val metadata = if (casterId != null) mapOf("caster" to casterId) else emptyMap()
            return SourceReference(SourceType.SPELL, spellId, metadata)
        }
        
        /**
         * Создаёт ссылку на источник типа POTION.
         */
        fun potion(potionId: Identifier) = SourceReference(SourceType.POTION, potionId)
        
        /**
         * Создаёт ссылку на источник типа ABILITY.
         */
        fun ability(abilityId: Identifier) = SourceReference(SourceType.ABILITY, abilityId)
        
        /**
         * Создаёт ссылку на источник типа CONDITION.
         */
        fun condition(conditionId: Identifier) = SourceReference(SourceType.CONDITION, conditionId)
        
        /**
         * Создаёт ссылку на источник типа ENVIRONMENTAL.
         */
        fun environmental(sourceId: Identifier) = SourceReference(SourceType.ENVIRONMENTAL, sourceId)
        
        /**
         * Создаёт ссылку на источник типа LEVEL.
         */
        fun level(level: Int, asiIndex: Int = 0): SourceReference {
            return SourceReference(
                SourceType.LEVEL,
                Identifier.of("boundbyfate-core", "level_$level"),
                mapOf("level" to level.toString(), "asi_index" to asiIndex.toString())
            )
        }
        
        /**
         * Создаёт ссылку на источник типа ADMIN.
         */
        fun admin(reason: String = "admin_command"): SourceReference {
            return SourceReference(
                SourceType.ADMIN,
                Identifier.of("boundbyfate-core", "admin"),
                mapOf("reason" to reason)
            )
        }
    }
    
    /**
     * Проверяет, является ли источник постоянным (из листа персонажа).
     * 
     * Постоянные источники: RACE, CLASS, SUBCLASS, FEAT, FEATURE, LEVEL
     * Временные источники: ITEM, SPELL, POTION, ABILITY, CONDITION, ENVIRONMENTAL
     */
    fun isPermanent(): Boolean = when (type) {
        SourceType.RACE,
        SourceType.CLASS,
        SourceType.SUBCLASS,
        SourceType.FEAT,
        SourceType.FEATURE,
        SourceType.LEVEL -> true
        else -> false
    }
    
    /**
     * Проверяет, является ли источник временным (runtime).
     */
    fun isTemporary(): Boolean = !isPermanent() && type != SourceType.ADMIN
    
    /**
     * Получает читаемое представление источника.
     */
    override fun toString(): String {
        val base = "${type.name.lowercase()}:${id.namespace}:${id.path}"
        return if (metadata.isNotEmpty()) {
            "$base (${metadata.entries.joinToString { "${it.key}=${it.value}" }})"
        } else {
            base
        }
    }
}
