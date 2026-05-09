package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import omc.boundbyfate.component.components.EntityAppearanceData
import omc.boundbyfate.entity.BbfNpcEntity
import omc.boundbyfate.entity.NpcEntity
import omc.boundbyfate.network.packet.s2c.OpenCharacterEditScreenPacket
import omc.boundbyfate.registry.NpcEntityRegistry

/**
 * Регистрация команд BoundByFate.
 */
object BbfCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerBbfCommand(dispatcher)
        }
    }

    private fun registerBbfCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("bbf")
                .then(
                    CommandManager.literal("character")
                        .then(
                            CommandManager.literal("create")
                                .executes(::openCharacterEditScreen)
                        )
                )
                .then(
                    CommandManager.literal("npc")
                        .then(
                            CommandManager.literal("spawn")
                                .executes(::spawnNpc)
                        )
                        .then(
                            CommandManager.literal("spawn_proxy")
                                .executes(::spawnBbfNpc)
                        )
                )
        )
    }

    private fun openCharacterEditScreen(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        OpenCharacterEditScreenPacket.send(player)
        return 1
    }

    /**
     * Спавнит BbfNpcEntity (Proxy-based) перед игроком.
     */
    private fun spawnBbfNpc(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val world  = player.serverWorld

        val npc = BbfNpcEntity(NpcEntityRegistry.BBF_NPC, world)

        // Позиция — 2 блока перед игроком
        val yaw = Math.toRadians(player.yaw.toDouble())
        val spawnX = player.x - Math.sin(yaw) * 2
        val spawnY = player.y
        val spawnZ = player.z + Math.cos(yaw) * 2
        npc.setPosition(spawnX, spawnY, spawnZ)
        npc.yaw = player.yaw + 180f

        // Инициализируем компонент внешности
        val appearance = npc.getOrCreate(EntityAppearanceData.TYPE)
        appearance.modelType = "steve"
        // skinId пустой — будет дефолтный скин

        world.spawnEntity(npc)

        context.source.sendFeedback(
            { Text.literal("Spawned BBF Proxy NPC (uuid=${npc.uuid})") },
            false
        )
        return 1
    }

    /**
     * Спавнит NpcEntity с дефолтной моделью (classic.gltf) перед игроком.
     * Используется для тестирования рендера NPC модели в мире.
     */
    private fun spawnNpc(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val world  = player.serverWorld

        val npc = NpcEntity(NpcEntityRegistry.NPC, world)

        // Позиция — 2 блока перед игроком
        val yaw = Math.toRadians(player.yaw.toDouble())
        val spawnX = player.x - Math.sin(yaw) * 2
        val spawnY = player.y
        val spawnZ = player.z + Math.cos(yaw) * 2
        npc.setPosition(spawnX, spawnY, spawnZ)
        npc.yaw = player.yaw + 180f

        // Инициализируем компонент модели
        // val modelComp = npc.getOrCreate(NpcModelComponent.TYPE) // Disabled
        
        world.spawnEntity(npc)

        context.source.sendFeedback(
            { Text.literal("Spawned NPC (uuid=${npc.uuid})") },
            false
        )
        return 1
    }
}
