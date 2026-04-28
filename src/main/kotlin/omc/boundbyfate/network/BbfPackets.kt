package omc.boundbyfate.network

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.util.Identifier
import omc.boundbyfate.network.packet.c2s.CreateCharacterPacket
import omc.boundbyfate.network.packet.c2s.FileUploadCancelPacket
import omc.boundbyfate.network.packet.c2s.FileUploadChunkPacket
import omc.boundbyfate.network.packet.c2s.FileUploadStartPacket
import omc.boundbyfate.network.packet.c2s.MusicSliderUpdatePacket
import omc.boundbyfate.network.packet.c2s.MusicTrackAssignPacket
import omc.boundbyfate.network.packet.c2s.SwitchCharacterPacket
import omc.boundbyfate.network.packet.s2c.FileDistributeChunkPacket
import omc.boundbyfate.network.packet.s2c.FileDistributeStartPacket
import omc.boundbyfate.network.packet.s2c.FileUploadAckPacket
import omc.boundbyfate.network.packet.s2c.FileSyncListPacket
import omc.boundbyfate.network.packet.s2c.MusicStatePacket
import omc.boundbyfate.network.packet.s2c.PlaySoundPacket
import omc.boundbyfate.network.packet.s2c.SpawnParticlesPacket
import omc.boundbyfate.network.packet.s2c.SyncActiveCharacterPacket
import omc.boundbyfate.network.packet.s2c.SyncBatchPacket
import omc.boundbyfate.network.packet.s2c.SyncComponentPacket
import omc.boundbyfate.network.packet.s2c.SyncSectionPacket
import omc.boundbyfate.network.packet.s2c.SyncWorldDataPacket
import org.slf4j.LoggerFactory

/**
 * Регистрация всех пакетов мода (1.20.1 Fabric API).
 *
 * Использует FabricPacket + PacketType для регистрации пакетов.
 */
object BbfPackets {
    private val logger = LoggerFactory.getLogger(BbfPackets::class.java)

    // ========== Идентификаторы пакетов ==========

    // Server to Client
    val SYNC_COMPONENT_S2C = Identifier("boundbyfate-core", "sync_component")
    val SYNC_BATCH_S2C = Identifier("boundbyfate-core", "sync_batch")
    val SYNC_WORLD_DATA_S2C = Identifier("boundbyfate-core", "sync_world_data")
    val SYNC_ACTIVE_CHARACTER_S2C = Identifier("boundbyfate-core", "sync_active_character")
    val SYNC_SECTION_S2C = Identifier("boundbyfate-core", "sync_section")
    val SPAWN_PARTICLES_S2C = Identifier("boundbyfate-core", "spawn_particles")

    // File Transfer
    val FILE_UPLOAD_START_C2S = Identifier("boundbyfate-core", "file_upload_start")
    val FILE_UPLOAD_CHUNK_C2S = Identifier("boundbyfate-core", "file_upload_chunk")
    val FILE_UPLOAD_CANCEL_C2S = Identifier("boundbyfate-core", "file_upload_cancel")
    val FILE_UPLOAD_ACK_S2C = Identifier("boundbyfate-core", "file_upload_ack")
    val FILE_DISTRIBUTE_START_S2C = Identifier("boundbyfate-core", "file_distribute_start")
    val FILE_DISTRIBUTE_CHUNK_S2C = Identifier("boundbyfate-core", "file_distribute_chunk")
    val FILE_SYNC_LIST_S2C = Identifier("boundbyfate-core", "file_sync_list")

    // Sound & Music
    val PLAY_SOUND_S2C = Identifier("boundbyfate-core", "play_sound")
    val MUSIC_STATE_S2C = Identifier("boundbyfate-core", "music_state")
    val MUSIC_SLIDER_UPDATE_C2S = Identifier("boundbyfate-core", "music_slider_update")
    val MUSIC_TRACK_ASSIGN_C2S = Identifier("boundbyfate-core", "music_track_assign")

    // Client to Server
    val SWITCH_CHARACTER_C2S = Identifier("boundbyfate-core", "switch_character")
    val CREATE_CHARACTER_C2S = Identifier("boundbyfate-core", "create_character")

    /**
     * Регистрирует все C2S пакеты на сервере.
     * S2C пакеты регистрируются через PacketType.create() автоматически.
     */
    fun register() {
        logger.info("Registering BoundByFate packets...")

        // Регистрация C2S пакетов (серверная сторона)
        ServerPlayNetworking.registerGlobalReceiver(SwitchCharacterPacket.TYPE) { packet, player, _ ->
            // Обработка переключения персонажа
            logger.debug("Player ${player.name.string} switching character to ${packet.characterId}")
        }

        ServerPlayNetworking.registerGlobalReceiver(CreateCharacterPacket.TYPE) { packet, player, _ ->
            // Обработка создания персонажа
            logger.debug("Player ${player.name.string} creating character '${packet.name}'")
        }

        ServerPlayNetworking.registerGlobalReceiver(FileUploadStartPacket.TYPE) { packet, player, _ ->
            logger.debug("Player ${player.name.string} starting file upload: ${packet.fileId}")
        }

        ServerPlayNetworking.registerGlobalReceiver(FileUploadChunkPacket.TYPE) { packet, player, _ ->
            logger.debug("Player ${player.name.string} uploading chunk ${packet.chunkIndex}")
        }

        ServerPlayNetworking.registerGlobalReceiver(FileUploadCancelPacket.TYPE) { packet, player, _ ->
            logger.debug("Player ${player.name.string} cancelling upload ${packet.sessionId}")
        }

        ServerPlayNetworking.registerGlobalReceiver(MusicSliderUpdatePacket.TYPE) { packet, player, _ ->
            logger.debug("Player ${player.name.string} updating music slider: u=${packet.u}, v=${packet.v}")
        }

        ServerPlayNetworking.registerGlobalReceiver(MusicTrackAssignPacket.TYPE) { packet, player, _ ->
            logger.debug("Player ${player.name.string} assigning track ${packet.trackId} to slot ${packet.slot}")
        }

        logger.info("Registered BoundByFate packets")
    }
}
