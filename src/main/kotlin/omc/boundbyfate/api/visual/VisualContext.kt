package omc.boundbyfate.api.visual

import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import net.minecraft.util.Identifier

/**
 * Контекст выполнения визуального эффекта.
 *
 * Передаётся в подсистемы при вызове через [VisualSystem].
 * Содержит всё необходимое для выполнения визуального эффекта:
 * где, в каком мире и от чьего имени.
 *
 * @param world серверный мир в котором выполняется эффект
 * @param pos позиция центра эффекта
 * @param sourceId ID источника эффекта (способность, предмет и т.д.) — для отладки и логов
 */
data class VisualContext(
    val world: ServerWorld,
    val pos: Vec3d,
    val sourceId: Identifier? = null
)
