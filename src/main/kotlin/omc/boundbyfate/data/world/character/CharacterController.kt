package omc.boundbyfate.data.world.character

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Контроллер персонажа — кто им управляет прямо сейчас.
 *
 * Хранится в [CharacterSection.activeCharacters], не в [CharacterData].
 * Это runtime-состояние, а не постоянная характеристика персонажа.
 *
 * @property type тип контроллера
 * @property value UUID игрока (для PLAYER) или ID скрипта (для SCRIPT)
 */
data class CharacterController(
    val type: ControllerType,
    val value: String
) {
    companion object {
        val CODEC: Codec<CharacterController> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.xmap(
                    { ControllerType.valueOf(it) },
                    { it.name }
                ).fieldOf("type").forGetter { it.type },
                Codec.STRING.fieldOf("value").forGetter { it.value }
            ).apply(instance, ::CharacterController)
        }

        /** Создаёт контроллер для игрока/ГМа. */
        fun player(playerUuid: java.util.UUID): CharacterController =
            CharacterController(ControllerType.PLAYER, playerUuid.toString())

        /** Создаёт контроллер для скрипта/AI. */
        fun script(scriptId: net.minecraft.util.Identifier): CharacterController =
            CharacterController(ControllerType.SCRIPT, scriptId.toString())
    }

    /** UUID игрока если тип PLAYER, иначе null. */
    fun asPlayerUuid(): java.util.UUID? =
        if (type == ControllerType.PLAYER) java.util.UUID.fromString(value) else null

    /** ID скрипта если тип SCRIPT, иначе null. */
    fun asScriptId(): net.minecraft.util.Identifier? =
        if (type == ControllerType.SCRIPT) net.minecraft.util.Identifier.of(
            value.substringBefore(':'),
            value.substringAfter(':')
        ) else null
}

/**
 * Тип контроллера персонажа.
 */
enum class ControllerType {
    /** Персонаж под управлением игрока или ГМа. */
    PLAYER,
    /** Персонаж под управлением скрипта или AI. */
    SCRIPT
}
