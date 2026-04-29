package omc.boundbyfate.util.sync

import com.mojang.serialization.Codec
import net.minecraft.nbt.AbstractNbtNumber
import net.minecraft.nbt.NbtByte
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtDouble
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtFloat
import net.minecraft.nbt.NbtInt
import net.minecraft.nbt.NbtLong
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtShort
import net.minecraft.nbt.NbtString
import net.minecraft.util.Identifier

/**
 * Общие утилиты NBT-сериализации для synced-делегатов.
 *
 * Используется в [omc.boundbyfate.component.core.BbfComponent]
 * и [omc.boundbyfate.data.world.core.WorldDataSection].
 */

@Suppress("UNCHECKED_CAST")
fun <T> encodeToNbt(value: T, codec: Codec<T>?): NbtElement? {
    if (codec != null) return codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null)
    return when (value) {
        is Int        -> NbtInt.of(value)
        is Long       -> NbtLong.of(value)
        is Float      -> NbtFloat.of(value)
        is Double     -> NbtDouble.of(value)
        is Boolean    -> NbtByte.of(value)
        is String     -> NbtString.of(value)
        is Byte       -> NbtByte.of(value)
        is Short      -> NbtShort.of(value)
        is Identifier -> NbtString.of(value.toString())
        else          -> null
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> decodeFromNbt(tag: NbtElement, codec: Codec<T>?, fallback: T): T? {
    if (codec != null) return codec.parse(NbtOps.INSTANCE, tag).result().orElse(null)
    return when (fallback) {
        is Int        -> (tag as? AbstractNbtNumber)?.intValue() as? T
        is Long       -> (tag as? AbstractNbtNumber)?.longValue() as? T
        is Float      -> (tag as? AbstractNbtNumber)?.floatValue() as? T
        is Double     -> (tag as? AbstractNbtNumber)?.doubleValue() as? T
        is Boolean    -> (tag as? AbstractNbtNumber)?.byteValue()?.let { it != 0.toByte() } as? T
        is String     -> (tag as? NbtString)?.asString() as? T
        is Byte       -> (tag as? AbstractNbtNumber)?.byteValue() as? T
        is Short      -> (tag as? AbstractNbtNumber)?.shortValue() as? T
        is Identifier -> (tag as? NbtString)?.asString()?.let {
            val parts = it.split(":")
            if (parts.size == 2) Identifier(parts[0], parts[1]) else null
        } as? T
        else          -> null
    }
}
