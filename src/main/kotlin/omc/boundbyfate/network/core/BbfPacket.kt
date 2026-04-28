package omc.boundbyfate.network.core

import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.minecraft.util.Identifier

/**
 * Базовый интерфейс для всех пакетов мода.
 *
 * Расширяет Fabric FabricPacket для интеграции с сетевой системой (1.20.1).
 */
interface BbfPacket : FabricPacket

/**
 * Направление пакета.
 */
enum class PacketDirection {
    /**
     * Сервер → Клиент.
     */
    S2C,

    /**
     * Клиент → Сервер.
     */
    C2S,

    /**
     * Двунаправленный (редко используется).
     */
    BOTH
}

/**
 * Аннотация для автоматической регистрации пакета.
 *
 * Используется PacketRegistry для сканирования и регистрации пакетов.
 *
 * Пример:
 * ```kotlin
 * @RegisterPacket(
 *     id = "sync_component",
 *     direction = PacketDirection.S2C
 * )
 * data class SyncComponentPacket(...) : BbfPacket
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RegisterPacket(
    /**
     * ID пакета (без namespace, он добавится автоматически).
     */
    val id: String,

    /**
     * Направление пакета.
     */
    val direction: PacketDirection
)
