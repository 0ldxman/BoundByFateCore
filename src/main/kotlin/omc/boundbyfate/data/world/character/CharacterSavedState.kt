package omc.boundbyfate.data.world.character

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d

/**
 * Сохранённое состояние персонажа.
 *
 * Обновляется когда игрок выходит из игры или переключает персонажа.
 * При загрузке персонажа — восстанавливается в компоненты (Attachments).
 *
 * Хранит только то что нельзя вычислить из базового состояния:
 * текущее HP, использованные ячейки, позицию, инвентарь, снимки компонентов.
 */
data class CharacterSavedState(
    val worldPosition: WorldPosition = WorldPosition(),
    val equipment: CharacterEquipment = CharacterEquipment(),
    /**
     * Снимки компонентов которые нельзя вычислить из базового состояния.
     * Например: текущее HP, использованные ячейки, наложенные эффекты.
     * Ключ — ID компонента, значение — NBT данные.
     */
    val componentsSnapshot: Map<String, NbtCompound> = emptyMap()
) {
    companion object {
        val CODEC: Codec<CharacterSavedState> = RecordCodecBuilder.create { instance ->
            instance.group(
                WorldPosition.CODEC.fieldOf("worldPosition").forGetter { it.worldPosition },
                CharacterEquipment.CODEC.fieldOf("equipment").forGetter { it.equipment },
                // NbtCompound сериализуем через NbtOps напрямую
                Codec.unboundedMap(Codec.STRING, net.minecraft.nbt.NbtCompound.CODEC)
                    .fieldOf("componentsSnapshot").forGetter { it.componentsSnapshot }
            ).apply(instance, ::CharacterSavedState)
        }
    }
}

/**
 * Позиция персонажа в мире.
 *
 * @property dimension ID измерения (minecraft:overworld, minecraft:the_nether и т.д.)
 * @property x, y, z координаты
 * @property yaw, pitch поворот
 */
data class WorldPosition(
    val dimension: Identifier = Identifier.of("minecraft", "overworld"),
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    fun toVec3d(): Vec3d = Vec3d(x, y, z)

    companion object {
        val CODEC: Codec<WorldPosition> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("dimension").forGetter { it.dimension },
                Codec.DOUBLE.fieldOf("x").forGetter { it.x },
                Codec.DOUBLE.fieldOf("y").forGetter { it.y },
                Codec.DOUBLE.fieldOf("z").forGetter { it.z },
                Codec.FLOAT.fieldOf("yaw").forGetter { it.yaw },
                Codec.FLOAT.fieldOf("pitch").forGetter { it.pitch }
            ).apply(instance, ::WorldPosition)
        }
    }
}

/**
 * Снаряжение и инвентарь персонажа.
 *
 * Сохраняется как сырые NBT данные ItemStack'ов — это стандартный
 * подход Minecraft для сериализации предметов.
 *
 * @property equipmentSlots надетая броня и артефакты (ключ — название слота)
 * @property inventory основной инвентарь (36 слотов, ключ — индекс слота)
 * @property offhand предмет в левой руке
 * @property enderChest содержимое эндер-сундука (27 слотов)
 */
data class CharacterEquipment(
    val equipmentSlots: Map<String, NbtCompound> = emptyMap(),
    val inventory: Map<Int, NbtCompound> = emptyMap(),
    val offhand: NbtCompound? = null,
    val enderChest: Map<Int, NbtCompound> = emptyMap()
) {
    companion object {
        private val SLOT_MAP_CODEC: Codec<Map<String, NbtCompound>> =
            Codec.unboundedMap(Codec.STRING, NbtCompound.CODEC)

        private val INT_MAP_CODEC: Codec<Map<Int, NbtCompound>> =
            Codec.unboundedMap(
                Codec.STRING.xmap({ it.toInt() }, { it.toString() }),
                NbtCompound.CODEC
            )

        val CODEC: Codec<CharacterEquipment> = RecordCodecBuilder.create { instance ->
            instance.group(
                SLOT_MAP_CODEC.fieldOf("equipmentSlots").forGetter { it.equipmentSlots },
                INT_MAP_CODEC.fieldOf("inventory").forGetter { it.inventory },
                NbtCompound.CODEC.optionalFieldOf("offhand").forGetter {
                    java.util.Optional.ofNullable(it.offhand)
                },
                INT_MAP_CODEC.fieldOf("enderChest").forGetter { it.enderChest }
            ).apply(instance) { slots, inv, offhand, ender ->
                CharacterEquipment(slots, inv, offhand.orElse(null), ender)
            }
        }
    }
}
