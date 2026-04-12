package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import omc.boundbyfate.component.PlayerSkinData
import omc.boundbyfate.network.ServerPacketHandler
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.system.skin.SkinLoader

object SkinCommand {

    fun register(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        registryAccess: CommandRegistryAccess,
        environment: CommandManager.RegistrationEnvironment
    ) {
        dispatcher.register(
            CommandManager.literal("bbf")
                .then(
                    CommandManager.literal("skin")
                        .requires { it.hasPermissionLevel(2) }
                        .then(
                            CommandManager.literal("set")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("skinName", StringArgumentType.string())
                                                .then(
                                                    CommandManager.argument("model", StringArgumentType.word())
                                                        .executes(::setSkin)
                                                )
                                                .executes { ctx ->
                                                    setSkinWithModel(ctx, "default")
                                                }
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("clear")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(::clearSkin)
                                )
                        )
                        .then(
                            CommandManager.literal("list")
                                .executes(::listSkins)
                        )
                )
        )
    }

    private fun setSkin(context: CommandContext<ServerCommandSource>): Int {
        val model = StringArgumentType.getString(context, "model")
        return setSkinWithModel(context, model)
    }

    private fun setSkinWithModel(context: CommandContext<ServerCommandSource>, model: String): Int {
        val player = EntityArgumentType.getPlayer(context, "player")
        val skinName = StringArgumentType.getString(context, "skinName")
        val server = context.source.server

        val worldDir = getWorldDir(context) ?: run {
            context.source.sendError(Text.literal("§cНе удалось определить директорию мира"))
            return 0
        }

        val base64 = SkinLoader.loadAsBase64(worldDir, skinName) ?: run {
            context.source.sendError(Text.literal("§cСкин '$skinName' не найден в папке skins/"))
            return 0
        }

        val validModel = if (model == "slim") "slim" else "default"
        val skinData = PlayerSkinData(skinName, validModel)
        player.setAttached(BbfAttachments.PLAYER_SKIN, skinData)

        ServerPacketHandler.broadcastSkin(player.name.string, base64, validModel, server)

        context.source.sendFeedback(
            { Text.literal("§aСкин '$skinName' (модель: $validModel) установлен для ${player.name.string}") },
            true
        )
        return 1
    }

    private fun clearSkin(context: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(context, "player")
        val server = context.source.server

        player.removeAttached(BbfAttachments.PLAYER_SKIN)
        ServerPacketHandler.broadcastSkinClear(player.name.string, server)

        context.source.sendFeedback(
            { Text.literal("§aСкин сброшен для ${player.name.string}") },
            true
        )
        return 1
    }

    private fun listSkins(context: CommandContext<ServerCommandSource>): Int {
        val worldDir = getWorldDir(context) ?: run {
            context.source.sendError(Text.literal("§cНе удалось определить директорию мира"))
            return 0
        }

        val skins = SkinLoader.listAvailableSkins(worldDir)
        if (skins.isEmpty()) {
            context.source.sendFeedback({ Text.literal("§eНет доступных скинов в папке skins/") }, false)
        } else {
            context.source.sendFeedback({ Text.literal("§6Доступные скины:") }, false)
            skins.forEach { name ->
                context.source.sendFeedback({ Text.literal("§f  - $name") }, false)
            }
        }
        return 1
    }

    private fun getWorldDir(context: CommandContext<ServerCommandSource>): java.nio.file.Path? {
        return try {
            val server = context.source.server
            val world = server.overworld
            val sm = world.persistentStateManager
            val f = sm.javaClass.getDeclaredField("directory")
            f.isAccessible = true
            (f.get(sm) as java.io.File).toPath().parent
        } catch (e: Exception) {
            null
        }
    }
}
