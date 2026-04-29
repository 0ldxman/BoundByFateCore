package omc.boundbyfate.client.hud

import net.minecraft.client.option.KeyBinding
import net.minecraft.util.Identifier

/**
 * Одноразовые события для HUD системы.
 *
 * Постятся из обработчиков пакетов на клиенте через [HudSystem.notify].
 * HUD элементы подписываются на конкретные типы через [HudContext.on].
 *
 * ## Пример
 *
 * ```kotlin
 * // В обработчике пакета:
 * HudSystem.notify(HudEvent.DamageTaken(amount = 15f, isCritical = false))
 *
 * // В HUD элементе:
 * on<HudEvent.DamageTaken> { event ->
 *     damageFlash.snap(1f)
 *     damageFlash.target = 0f
 * }
 * ```
 */
sealed class HudEvent {

    // ── Боевые ───────────────────────────────────────────────────────────

    /** Персонаж получил урон. */
    data class DamageTaken(
        val amount: Float,
        val isCritical: Boolean = false,
        val damageTypeId: Identifier? = null
    ) : HudEvent()

    /** Персонаж исцелился. */
    data class Healed(val amount: Float) : HudEvent()

    /** Персонаж упал до 0 HP (крит состояние). */
    object Downed : HudEvent()

    /** Персонаж умер (потеря очка Жизненной Силы). */
    object Died : HudEvent()

    /** Применился статус. */
    data class StatusApplied(val statusId: Identifier) : HudEvent()

    /** Статус снят. */
    data class StatusRemoved(val statusId: Identifier) : HudEvent()

    // ── Способности и ресурсы ─────────────────────────────────────────────

    /** Использована способность. */
    data class AbilityUsed(val abilityId: Identifier) : HudEvent()

    /** Способность восстановилась. */
    data class AbilityReady(val abilityId: Identifier) : HudEvent()

    /** Ресурс изменился (Ki, Rage, ячейки заклинаний...). */
    data class ResourceChanged(
        val resourceId: Identifier,
        val oldValue: Int,
        val newValue: Int,
        val maxValue: Int
    ) : HudEvent()

    // ── Прогрессия ────────────────────────────────────────────────────────

    /** Персонаж повысил уровень. */
    data class LevelUp(val newLevel: Int) : HudEvent()

    /** Получена новая особенность. */
    data class FeatureGranted(val featureId: Identifier) : HudEvent()

    // ── Персонаж ──────────────────────────────────────────────────────────

    /** Персонаж загружен — игрок вошёл в персонажа. */
    object CharacterLoaded : HudEvent()

    /** Персонаж выгружен — игрок покинул персонажа. */
    object CharacterUnloaded : HudEvent()

    // ── Нарратив ──────────────────────────────────────────────────────────

    /** Начался диалог. */
    data class DialogueStarted(val speakerName: String) : HudEvent()

    /** Диалог завершён. */
    object DialogueEnded : HudEvent()

    /** Квест обновился. */
    data class QuestUpdated(
        val questId: Identifier,
        val message: String
    ) : HudEvent()

    /** Общее уведомление. */
    data class Notification(
        val text: String,
        val type: NotificationType = NotificationType.INFO
    ) : HudEvent()

    // ── Клавиши ───────────────────────────────────────────────────────────

    /** Нажата клавиша (для toggle элементов). */
    data class KeyPressed(val key: KeyBinding) : HudEvent()
}

enum class NotificationType { INFO, SUCCESS, WARNING, ERROR }
