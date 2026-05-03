package omc.boundbyfate.network.packet.s2c

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.BoundByFateCore

/**
 * Пакет для открытия экрана редактирования персонажа на клиенте.
 */
object OpenCharacterEditScreenPacket {

    val ID = Identifier(BoundByFateCore.MOD_ID, "open_character_edit_screen")

    fun send(player: ServerPlayerEntity) {
        ServerPlayNetworking.send(player, ID, PacketByteBuf(io.netty.buffer.Unpooled.buffer()))
    }
}
