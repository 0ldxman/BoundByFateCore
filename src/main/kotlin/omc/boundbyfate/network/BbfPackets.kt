package omc.boundbyfate.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.util.Identifier
import omc.boundbyfate.network.core.PacketRegistry
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
 * Регистрация всех пакетов мода.
 * 
 * Использует Fabric Networking API для регистрации пакетов.
 */
object BbfPackets {
    private val logger = LoggerFactory.getLogger(BbfPackets::class.java)
    
    // ========== Идентификаторы пакетов ==========
    
    // Server to Client
    val SYNC_COMPONENT_S2C = Identifier.of("boundbyfate-core", "sync_component")
    val SYNC_BATCH_S2C = Identifier.of("boundbyfate-core", "sync_batch")
    val SYNC_WORLD_DATA_S2C = Identifier.of("boundbyfate-core", "sync_world_data")
    val SYNC_ACTIVE_CHARACTER_S2C = Identifier.of("boundbyfate-core", "sync_active_character")
    val SYNC_SECTION_S2C = Identifier.of("boundbyfate-core", "sync_section")
    val SPAWN_PARTICLES_S2C = Identifier.of("boundbyfate-core", "spawn_particles")

    // File Transfer
    val FILE_UPLOAD_START_C2S = Identifier.of("boundbyfate-core", "file_upload_start")
    val FILE_UPLOAD_CHUNK_C2S = Identifier.of("boundbyfate-core", "file_upload_chunk")
    val FILE_UPLOAD_CANCEL_C2S = Identifier.of("boundbyfate-core", "file_upload_cancel")
    val FILE_UPLOAD_ACK_S2C = Identifier.of("boundbyfate-core", "file_upload_ack")
    val FILE_DISTRIBUTE_START_S2C = Identifier.of("boundbyfate-core", "file_distribute_start")
    val FILE_DISTRIBUTE_CHUNK_S2C = Identifier.of("boundbyfate-core", "file_distribute_chunk")
    val FILE_SYNC_LIST_S2C = Identifier.of("boundbyfate-core", "file_sync_list")

    // Sound & Music
    val PLAY_SOUND_S2C = Identifier.of("boundbyfate-core", "play_sound")
    val MUSIC_STATE_S2C = Identifier.of("boundbyfate-core", "music_state")
    val MUSIC_SLIDER_UPDATE_C2S = Identifier.of("boundbyfate-core", "music_slider_update")
    val MUSIC_TRACK_ASSIGN_C2S = Identifier.of("boundbyfate-core", "music_track_assign")
    
    // Client to Server
    val SWITCH_CHARACTER_C2S = Identifier.of("boundbyfate-core", "switch_character")
    val CREATE_CHARACTER_C2S = Identifier.of("boundbyfate-core", "create_character")
    
    /**
     * Регистрирует все пакеты.
     */
    fun register() {
        logger.info("Registering BoundByFate packets...")
        
        // Регистрация S2C пакетов
        PayloadTypeRegistry.playS2C().register(
            SyncComponentPacket.ID,
            SyncComponentPacket.CODEC
        )
        
        PayloadTypeRegistry.playS2C().register(
            SyncBatchPacket.ID,
            SyncBatchPacket.CODEC
        )
        
        PayloadTypeRegistry.playS2C().register(
            SyncWorldDataPacket.ID,
            SyncWorldDataPacket.CODEC
        )
        
        PayloadTypeRegistry.playS2C().register(
            SyncActiveCharacterPacket.ID,
            SyncActiveCharacterPacket.CODEC
        )
        
        PayloadTypeRegistry.playS2C().register(
            SyncSectionPacket.ID,
            SyncSectionPacket.CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            SpawnParticlesPacket.ID,
            SpawnParticlesPacket.CODEC
        )

        // File Transfer — S2C
        PayloadTypeRegistry.playS2C().register(FileUploadAckPacket.ID, FileUploadAckPacket.CODEC)
        PayloadTypeRegistry.playS2C().register(FileDistributeStartPacket.ID, FileDistributeStartPacket.CODEC)
        PayloadTypeRegistry.playS2C().register(FileDistributeChunkPacket.ID, FileDistributeChunkPacket.CODEC)
        PayloadTypeRegistry.playS2C().register(FileSyncListPacket.ID, FileSyncListPacket.CODEC)

        // Регистрация C2S пакетов
        PayloadTypeRegistry.playC2S().register(
            SwitchCharacterPacket.ID,
            SwitchCharacterPacket.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CreateCharacterPacket.ID,
            CreateCharacterPacket.CODEC
        )

        // Sound & Music — S2C
        PayloadTypeRegistry.playS2C().register(PlaySoundPacket.ID, PlaySoundPacket.CODEC)
        PayloadTypeRegistry.playS2C().register(MusicStatePacket.ID, MusicStatePacket.CODEC)

        // File Transfer — C2S
        PayloadTypeRegistry.playC2S().register(FileUploadStartPacket.ID, FileUploadStartPacket.CODEC)
        PayloadTypeRegistry.playC2S().register(FileUploadChunkPacket.ID, FileUploadChunkPacket.CODEC)
        PayloadTypeRegistry.playC2S().register(FileUploadCancelPacket.ID, FileUploadCancelPacket.CODEC)

        // Sound & Music — C2S
        PayloadTypeRegistry.playC2S().register(MusicSliderUpdatePacket.ID, MusicSliderUpdatePacket.CODEC)
        PayloadTypeRegistry.playC2S().register(MusicTrackAssignPacket.ID, MusicTrackAssignPacket.CODEC)

        logger.info("Registered 15 packets (9 S2C, 5 C2S + 1 visual)")
    }
}
