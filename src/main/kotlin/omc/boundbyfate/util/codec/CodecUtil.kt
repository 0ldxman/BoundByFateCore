package omc.boundbyfate.util.codec

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Утилиты для работы с Codec (сериализация/десериализация).
 * 
 * Предоставляет helper методы для создания Codec'ов
 * для часто используемых типов данных.
 */
object CodecUtil {
    /**
     * Codec для Identifier.
     * Сериализует как строку "namespace:path".
     */
    val IDENTIFIER: Codec<Identifier> = Codec.STRING.xmap(
        { Identifier(it) },
        { it.toString() }
    )
    
    /**
     * Создаёт Codec для Map<Identifier, T>.
     * 
     * @param valueCodec Codec для значений
     * @return Codec для Map
     */
    fun <T> identifierMap(valueCodec: Codec<T>): Codec<Map<Identifier, T>> =
        Codec.unboundedMap(IDENTIFIER, valueCodec)
    
    /**
     * Создаёт Codec для List<Identifier>.
     */
    val IDENTIFIER_LIST: Codec<List<Identifier>> = IDENTIFIER.listOf()
    
    /**
     * Создаёт Codec для Set<Identifier>.
     */
    fun identifierSet(): Codec<Set<Identifier>> =
        IDENTIFIER_LIST.xmap({ it.toSet() }, { it.toList() })
}
