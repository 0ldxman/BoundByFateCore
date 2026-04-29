package omc.boundbyfate.api.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.component.components.EntityCharacterData
import omc.boundbyfate.component.core.getComponent
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.sections.CharacterSection

/**
 * Контекст конкретного использования способности.
 *
 * Передаётся во все методы [AbilityHandler] и extension-функции.
 * Содержит всё необходимое для выполнения логики способности.
 *
 * ## Жизненный цикл
 *
 * Создаётся [omc.boundbyfate.system.ability.AbilityExecutor] при попытке
 * использовать способность. Живёт на протяжении всего выполнения.
 *
 * ## Кэширование
 *
 * [casterLevel] вычисляется один раз при первом обращении (`lazy`) и кэшируется
 * на всё время жизни контекста. Инвалидация не нужна — контекст создаётся
 * заново при каждом использовании способности.
 *
 * ## Передача данных между хуками
 *
 * Используй [stash] для передачи данных между [AbilityHandler.onPreparationStart],
 * [AbilityHandler.onPreparationTick] и [AbilityHandler.execute]:
 *
 * ```kotlin
 * override fun onPreparationTick(ctx: AbilityContext, ticksElapsed: Int) {
 *     ctx.stash["charge_level"] = ticksElapsed.toFloat() / 60f
 * }
 *
 * override fun execute(ctx: AbilityContext) {
 *     val charge = ctx.stash["charge_level"] as? Float ?: 1f
 *     val damage = (ctx.data.getInt("base_damage") * charge).toInt()
 * }
 * ```
 *
 * ## Dry-run
 *
 * При [isDryRun] = true extension-функции не применяют реальных изменений,
 * но пишут в [results]. Используется для UI превью.
 *
 * @property caster кастер способности
 * @property definition данные способности из JSON
 * @property world серверный мир
 * @property currentTick текущий игровой тик
 * @property targets список целей (заполняется в execute через extension-функции)
 * @property targetPos позиция цели для AoE (опционально)
 * @property isDryRun режим превью — без реальных изменений
 * @property stash хранилище данных между хуками
 * @property results накопленные результаты выполнения
 */
class AbilityContext(
    val caster: LivingEntity,
    val definition: AbilityDefinition,
    val world: ServerWorld,
    val currentTick: Long,
    val targets: MutableList<LivingEntity> = mutableListOf(),
    val targetPos: Vec3d? = null,
    val isDryRun: Boolean = false,
    val stash: MutableMap<String, Any> = mutableMapOf(),
    val results: MutableList<AbilityExecutionResult> = mutableListOf()
) {
    // ── Удобные геттеры ───────────────────────────────────────────────────

    /**
     * Данные способности из JSON блока `data`.
     */
    val data: AbilityData get() = definition.abilityData

    /**
     * Данные масштабирования из JSON блока `scaling`.
     */
    val scaling: AbilityData get() = definition.abilityScaling

    /**
     * Является ли способность заклинанием.
     */
    val isSpell: Boolean get() = definition.isSpell

    /**
     * Первая цель из списка или null.
     */
    val primaryTarget: LivingEntity? get() = targets.firstOrNull()

    /**
     * Текущая обрабатываемая цель.
     * Устанавливается в extension-функциях при итерации по целям.
     * Используется модификаторами для проверки конкретной цели.
     */
    var currentTarget: LivingEntity? = null
        internal set

    // ── Stash helpers ─────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    fun <T> getStash(key: String): T? = stash[key] as? T

    fun <T : Any> putStash(key: String, value: T) { stash[key] = value }

    // ── Уровень кастера ───────────────────────────────────────────────────

    /**
     * Уровень персонажа кастера.
     *
     * Вычисляется один раз при первом обращении и кэшируется на всё время
     * жизни контекста (одно выполнение способности).
     *
     * Инвалидация не нужна — контекст создаётся заново при каждом использовании
     * способности, поэтому кэш всегда актуален.
     *
     * Читается из [CharacterSection] через [EntityCharacterData] компонент.
     * Возвращает 1 если персонаж не привязан к entity (НПС без CharacterData).
     */
    val casterLevel: Int by lazy {
        val characterId = caster.getComponent(EntityCharacterData.TYPE)?.characterId
            ?: return@lazy 1
        try {
            BbfWorldData.get(world.server)
                .getSection(CharacterSection.TYPE)
                .characters[characterId]
                ?.progression?.level
                ?: 1
        } catch (_: Exception) {
            1
        }
    }
}
