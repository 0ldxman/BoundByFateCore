package omc.boundbyfate.component.components

import com.mojang.serialization.Codec
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryWrapper
import net.minecraft.util.Identifier
import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.SyncMode
import omc.boundbyfate.util.codec.CodecUtil
import java.util.UUID

/**
 * Ссылка на персонажа в WorldData.
 *
 * Хранит UUID персонажа который сейчас "надет" на эту entity.
 * Через этот UUID все системы получают доступ к CharacterData.
 *
 * Не хранит копию данных — только ссылку.
 * При изменении CharacterData в WorldData компонент остаётся актуальным.
 */
class EntityCharacterData : BbfComponent() {

    private val UUID_CODEC: Codec<UUID> = Codec.STRING.xmap(
        { UUID.fromString(it) },
        { it.toString() }
    )

    /** UUID персонажа в CharacterSection. */
    var characterId by synced<UUID?>(null)

    /** Есть ли активный персонаж на этой entity. */
    val hasCharacter: Boolean get() = characterId != null

    companion object {
        val TYPE = BbfComponents.register(
            id = "boundbyfate-core:character",
            syncMode = SyncMode.ON_CHANGE,
            factory = ::EntityCharacterData
        )
    }
}
