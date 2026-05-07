package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import omc.boundbyfate.component.components.NpcModelComponent
import omc.boundbyfate.component.core.getOrCreate
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
                )
        )
    }

    private fun openCharacterEditScreen(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        OpenCharacterEditScreenPacket.send(player)
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
        val modelComp = npc.getOrCreate(NpcModelComponent.TYPE)
        // modelPath уже дефолтный: boundbyfate-core:models/entity/classic.gltf

        world.spawnEntity(npc)

        context.source.sendFeedback(
            { Text.literal("Spawned NPC (uuid=${npc.uuid}, model=${modelComp.modelPath})") },
            false
        )
        return 1
    }
}
