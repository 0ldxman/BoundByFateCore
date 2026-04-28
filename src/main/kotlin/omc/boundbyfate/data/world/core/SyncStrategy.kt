package omc.boundbyfate.data.world.core

import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * Стратегия синхронизации секции с клиентами.
 *
 * Определяет кому отправлять пакет при изменении данных секции.
 * Синхронизация адресная — не рассылаем всё всем.
 */
sealed class SyncStrategy {

    /**
     * Не синхронизировать с клиентами.
     * Для серверных данных которые клиенту не нужны (статистика, флаги и т.д.).
     */
    object None : SyncStrategy()

    /**
     * Синхронизировать со всеми онлайн игроками.
     * Для данных которые видят все (организации, мировые события).
     */
    object ToAll : SyncStrategy()

    /**
     * Синхронизировать только с владельцем данных.
     * Для данных персонажа — только его игроку.
     *
     * @param ownerExtractor функция которая извлекает UUID владельца из ключа
     */
    class ToOwner(val ownerExtractor: (server: MinecraftServer) -> List<ServerPlayerEntity>) : SyncStrategy()

    /**
     * Синхронизировать при входе игрока.
     * Для данных которые нужны клиенту один раз при подключении.
     */
    object OnJoin : SyncStrategy()

    /**
     * Кастомная стратегия — полный контроль над получателями.
     */
    class Custom(val recipients: (server: MinecraftServer) -> List<ServerPlayerEntity>) : SyncStrategy()
}

/**
 * Вспомогательные фабрики для удобного создания стратегий.
 */
object SyncStrategies {

    /**
     * Синхронизировать только с конкретным игроком по UUID.
     */
    fun toPlayer(playerId: UUID): SyncStrategy = SyncStrategy.Custom { server ->
        listOfNotNull(server.playerManager.getPlayer(playerId))
    }

    /**
     * Синхронизировать с несколькими конкретными игроками.
     */
    fun toPlayers(playerIds: Collection<UUID>): SyncStrategy = SyncStrategy.Custom { server ->
        playerIds.mapNotNull { server.playerManager.getPlayer(it) }
    }
}
