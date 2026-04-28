package omc.boundbyfate.component.components

import omc.boundbyfate.component.core.BbfComponent

/**
 * Компонент модели НПС.
 *
 * Хранит путь к GLTF модели и параметры отображения.
 * Рендеринг выполняется на клиенте через kool/GLTF пайплайн.
 *
 * Путь к модели — Minecraft Identifier в формате "namespace:path/to/model.gltf".
 * Например: "boundbyfate-core:models/entity/player_model.gltf"
 *
 * Компонент синхронизируется с клиентом при изменении.
 */
class NpcModelComponent : BbfComponent() {

    /**
     * Путь к GLTF модели.
     * Формат: "namespace:models/entity/name.gltf"
     */
    var modelPath by synced("boundbyfate-core:models/entity/player_model.gltf")

    /**
     * Масштаб модели (1.0 = стандартный).
     */
    var scale by synced(1.0f)

    /**
     * Включены ли анимации.
     */
    var animationsEnabled by synced(true)

    companion object {
        val TYPE = omc.boundbyfate.component.core.BbfComponents.register(
            id = "boundbyfate-core:npc_model",
            syncMode = omc.boundbyfate.component.core.SyncMode.ON_CHANGE,
            factory = ::NpcModelComponent
        )
    }
}
