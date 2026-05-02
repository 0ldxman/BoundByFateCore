package omc.boundbyfate.component.components

import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.SyncMode

/**
 * Визуальные данные entity для рендера.
 *
 * Хранит skinId и modelType прямо на entity — чтобы клиент мог читать их
 * без обращения к WorldData. Синхронизируется при изменении.
 *
 * ## Для игрока
 *
 * Заполняется из [omc.boundbyfate.data.world.character.CharacterAppearance]
 * при загрузке персонажа. Обновляется когда ГМ меняет скин через
 * [omc.boundbyfate.system.skin.SkinSystem].
 *
 * ## Для НПС
 *
 * Заполняется из [NpcModelComponent.skinId] — там уже есть skinId,
 * этот компонент нужен только для игроков.
 *
 * ## Клиентское использование
 *
 * ```kotlin
 * // В Mixin на PlayerEntityRenderer:
 * val appearance = player.getComponent(EntityAppearanceData.TYPE)
 * val texture = ClientSkinManager.getTexture(appearance?.skinId ?: "")
 *     ?: player.skinTextures.texture  // fallback на Mojang скин
 * ```
 */
class EntityAppearanceData : BbfComponent() {

    /**
     * ID скина из FileTransferSystem (FileCategory.SKIN).
     * Пустая строка — скин не назначен, используется дефолтный Minecraft скин.
     */
    var skinId by synced("")

    /**
     * Тип модели: "steve" (широкие руки) или "alex" (тонкие руки).
     * Влияет на UV-маппинг рук в ванильном рендере.
     */
    var modelType by synced("steve")

    companion object {
        @JvmField
        val TYPE = BbfComponents.register(
            id = "boundbyfate-core:appearance",
            syncMode = SyncMode.ON_CHANGE,
            factory = ::EntityAppearanceData
        )
    }
}
