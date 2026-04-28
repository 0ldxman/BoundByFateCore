package omc.boundbyfate.data.world.sections

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.core.SyncStrategy
import omc.boundbyfate.data.world.core.WorldDataSection

/**
 * Секция глобальных данных мира.
 *
 * Хранит мировые события, флаги и статистику сервера.
 * Синхронизируется со всеми игроками.
 *
 * Файл: `boundbyfate_world.dat`
 *
 * ## Использование
 *
 * ```kotlin
 * val section = BbfWorldData.get(server).getSection(WorldSection.TYPE)
 *
 * // Проверить флаг
 * val dragonDefeated = section.worldFlags["dragon_defeated"] ?: false
 *
 * // Запустить событие
 * section.activeEvents.add(WorldEvent(...))
 *
 * // Статистика
 * section.statistics.totalBossesKilled
 * ```
 */
class WorldSection : WorldDataSection() {

    /** Активные мировые события. */
    val activeEvents by syncedList(WorldEvent.CODEC)

    /**
     * Глобальные флаги состояния мира.
     * Примеры: "dragon_defeated", "gates_opened", "war_started"
     */
    val worldFlags by syncedMap(Codec.STRING, Codec.BOOL)

    /** Глобальная статистика сервера. */
    var statistics by synced(ServerStatistics(), ServerStatistics.CODEC)

    companion object {
        val TYPE = BbfWorldData.registerSection(
            id = "boundbyfate-core:world",
            file = "boundbyfate_world",
            syncStrategy = SyncStrategy.ToAll,
            factory = ::WorldSection
        )
    }
}

// ── Типы данных WorldSection ──────────────────────────────────────────────

/**
 * Мировое событие — временное состояние мира.
 *
 * @property id уникальный ID события
 * @property startedAt время начала (Unix ms)
 * @property endsAt время окончания (Unix ms), null = бессрочно
 * @property data произвольные данные события в NBT
 */
data class WorldEvent(
    val id: Identifier,
    val startedAt: Long,
    val endsAt: Long? = null
) {
    val isActive: Boolean
        get() = endsAt == null || System.currentTimeMillis() < endsAt

    companion object {
        val CODEC: Codec<WorldEvent> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("id").forGetter { it.id },
                Codec.LONG.fieldOf("startedAt").forGetter { it.startedAt },
                Codec.LONG.optionalFieldOf("endsAt").forGetter {
                    java.util.Optional.ofNullable(it.endsAt)
                }
            ).apply(instance) { id, startedAt, endsAt ->
                WorldEvent(id, startedAt, endsAt.orElse(null))
            }
        }
    }
}

/**
 * Глобальная статистика сервера.
 */
data class ServerStatistics(
    val totalPlayersJoined: Int = 0,
    val totalBossesKilled: Int = 0,
    val bossKills: Map<String, Int> = emptyMap(),
    val totalDungeonsCompleted: Int = 0
) {
    companion object {
        val CODEC: Codec<ServerStatistics> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("totalPlayersJoined").forGetter { it.totalPlayersJoined },
                Codec.INT.fieldOf("totalBossesKilled").forGetter { it.totalBossesKilled },
                Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .fieldOf("bossKills").forGetter { it.bossKills },
                Codec.INT.fieldOf("totalDungeonsCompleted").forGetter { it.totalDungeonsCompleted }
            ).apply(instance, ::ServerStatistics)
        }
    }
}
