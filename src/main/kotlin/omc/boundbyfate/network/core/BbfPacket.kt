package omc.boundbyfate.network.core

import net.minecraft.network.packet.CustomPayload

/**
 * Базовый интерфейс для всех пакетов мода.
 * 
 * Расширяет Fabric CustomPayload для интеграции с сетевой системой.
 */
interface BbfPacket : CustomPayload {
    /**
     * Уникальный ID пакета (из getId()).
     */
    val id: CustomPayload.Id<out CustomPayload>
        get() = getId()
}

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
