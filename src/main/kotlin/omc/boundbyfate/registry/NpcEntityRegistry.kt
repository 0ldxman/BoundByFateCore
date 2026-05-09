package omc.boundbyfate.registry

import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier
import omc.boundbyfate.BoundByFateCore
import omc.boundbyfate.entity.BbfNpcEntity
import omc.boundbyfate.entity.NpcEntity
import org.slf4j.LoggerFactory

/**
 * Регистрация типов сущностей мода.
 */
object NpcEntityRegistry {

    private val logger = LoggerFactory.getLogger(NpcEntityRegistry::class.java)

    /**
     * Тип сущности НПС (старый, Kool).
     */
    val NPC: EntityType<NpcEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier(BoundByFateCore.MOD_ID, "npc"),
        FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, ::NpcEntity)
            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
            .build()
    )

    /**
     * Новый тип сущности НПС (чистый, Proxy-based).
     */
    val BBF_NPC: EntityType<BbfNpcEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier(BoundByFateCore.MOD_ID, "bbf_npc"),
        FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, ::BbfNpcEntity)
            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
            .trackRangeBlocks(128)
            .trackedUpdateRate(1)
            .build()
    )

    /**
     * Регистрирует атрибуты сущностей.
     * Вызывается при инициализации мода.
     */
    fun registerAttributes() {
        net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry.register(
            NPC,
            NpcEntity.createAttributes()
        )
        net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry.register(
            BBF_NPC,
            BbfNpcEntity.createAttributes()
        )
        logger.info("NPC entity attributes registered")
    }

    /**
     * Инициализирует регистр.
     * Обращение к объекту запускает регистрацию через static init.
     */
    fun register() {
        registerAttributes()
        logger.info("NPC entity type registered: ${NPC.translationKey}")
    }
}

