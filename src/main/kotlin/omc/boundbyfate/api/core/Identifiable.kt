package omc.boundbyfate.api.core

import net.minecraft.util.Identifier

/**
 * Интерфейс для объектов, имеющих уникальный идентификатор.
 * 
 * Используется для:
 * - Хранения ссылок в World Data (вместо хранения всего объекта)
 * - Поиска в Registry
 * - Сериализации/десериализации
 */
interface Identifiable {
    /**
     * Уникальный идентификатор объекта.
     * Формат: "namespace:path" (например, "boundbyfate-core:fighter")
     */
    val id: Identifier
}
