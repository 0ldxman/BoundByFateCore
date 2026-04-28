package omc.boundbyfate.data.world.character

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Классовые данные персонажа.
 *
 * @property classId ID класса из реестра классов
 * @property subclassId ID подкласса (null пока не выбран)
 * @property levelUpHistory полная история прогрессии по классу
 */
data class CharacterClass(
    val classId: Identifier,
    val subclassId: Identifier? = null,
    val levelUpHistory: List<LevelUpRecord> = emptyList()
) {
    companion object {
        val CODEC: Codec<CharacterClass> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("classId").forGetter { it.classId },
                Identifier.CODEC.optionalFieldOf("subclassId").forGetter {
                    java.util.Optional.ofNullable(it.subclassId)
                },
                LevelUpRecord.CODEC.listOf().fieldOf("levelUpHistory").forGetter { it.levelUpHistory }
            ).apply(instance) { classId, subclassId, history ->
                CharacterClass(classId, subclassId.orElse(null), history)
            }
        }
    }
}

/**
 * Запись об одном уровне в истории прогрессии.
 *
 * Хранит всё что было выбрано и получено на конкретном уровне.
 *
 * @property level номер уровня (1–20)
 * @property timestamp игровое время когда был получен уровень
 * @property chosenFeatures особенности выбранные на этом уровне (фиты, ASI и т.д.)
 * @property statImprovements улучшения характеристик (ASI): stat ID → бонус
 */
data class LevelUpRecord(
    val level: Int,
    val timestamp: Long,
    val chosenFeatures: List<Identifier> = emptyList(),
    val statImprovements: Map<String, Int> = emptyMap()
) {
    companion object {
        val CODEC: Codec<LevelUpRecord> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("level").forGetter { it.level },
                Codec.LONG.fieldOf("timestamp").forGetter { it.timestamp },
                Identifier.CODEC.listOf().fieldOf("chosenFeatures").forGetter { it.chosenFeatures },
                Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .fieldOf("statImprovements").forGetter { it.statImprovements }
            ).apply(instance, ::LevelUpRecord)
        }
    }
}
