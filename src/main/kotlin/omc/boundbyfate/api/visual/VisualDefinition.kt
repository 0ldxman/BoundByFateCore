package omc.boundbyfate.api.visual

import omc.boundbyfate.api.core.Registrable

/**
 * Базовый интерфейс для всех определений визуальной системы.
 *
 * Каждая подсистема (партиклы, звуки, лучи и т.д.) имеет свой
 * тип Definition, который расширяет этот интерфейс.
 *
 * Definition хранится в Registry на сервере и синхронизируется
 * с клиентом при подключении игрока.
 */
interface VisualDefinition : Registrable
