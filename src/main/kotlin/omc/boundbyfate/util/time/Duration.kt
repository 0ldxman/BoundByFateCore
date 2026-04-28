package omc.boundbyfate.util.time

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Длительность — описывает как долго что-то действует.
 *
 * Универсальный тип, используется везде где есть временные эффекты:
 * - Состояния (отравлен, парализован)
 * - Эффекты заклинаний (Haste, Bless)
 * - Кулдауны способностей (Second Wind до короткого отдыха)
 * - Ресурсы (Ki Points восстанавливаются на длинном отдыхе)
 * - Баффы от зелий (зелье силы на 1 час)
 *
 * ## Варианты длительности
 *
 * ```kotlin
 * Duration.Permanent                          // пока явно не снято
 * Duration.Ticks(200)                         // 10 секунд (200 тиков)
 * Duration.seconds(10)                        // то же самое, удобнее
 * Duration.minutes(1)                         // 1 минута
 * Duration.UntilEvent(BbfEvents.Rest.SHORT)   // до короткого отдыха
 * Duration.UntilEvent(BbfEvents.Rest.LONG)    // до длинного отдыха
 * Duration.UntilSave(dc=15, stat=CON, checkOn=SaveTrigger.END_OF_TURN)
 * ```
 *
 * ## JSON
 *
 * ```json
 * {"type": "permanent"}
 * {"type": "ticks", "ticks": 200}
 * {"type": "until_event", "event": "boundbyfate-core:rest/short"}
 * {"type": "until_save", "dc": 15, "stat": "boundbyfate-core:constitution", "check_on": "end_of_turn"}
 * ```
 */
sealed class Duration {

    /**
     * Постоянное — действует пока явно не снято.
     *
     * Используется для:
     * - Проклятий (снимается только заклинанием Remove Curse)
     * - Постоянных эффектов предметов
     * - GM-наложенных состояний
     */
    data object Permanent : Duration()

    /**
     * Фиксированное количество тиков.
     *
     * Используй фабричные методы для удобства:
     * - [Duration.seconds]
     * - [Duration.minutes]
     * - [Duration.hours]
     *
     * Проверка истечения через [TimeUtil.isExpired].
     */
    data class Ticks(val ticks: Int) : Duration() {
        companion object {
            val CODEC: Codec<Ticks> = RecordCodecBuilder.create { i ->
                i.group(Codec.INT.fieldOf("ticks").forGetter { it.ticks })
                 .apply(i, ::Ticks)
            }
        }
    }

    /**
     * До наступления именованного события.
     *
     * Событие публикуется через EventBus — система Duration
     * не знает что именно его вызывает. Это позволяет гибко
     * настраивать что является "коротким отдыхом" или другим событием.
     *
     * Стандартные события отдыха:
     * - `RestEvents.SHORT` — короткий отдых
     * - `RestEvents.LONG`  — длинный отдых (сон)
     *
     * Кастомные события:
     * - `"mymod:meditation_complete"` — завершение медитации монаха
     * - `"mymod:sunrise"` — рассвет
     */
    data class UntilEvent(val eventId: Identifier) : Duration() {
        companion object {
            val CODEC: Codec<UntilEvent> = RecordCodecBuilder.create { i ->
                i.group(CodecUtil.IDENTIFIER.fieldOf("event").forGetter { it.eventId })
                 .apply(i, ::UntilEvent)
            }
        }
    }

    /**
     * До успешного спасброска.
     *
     * Существо периодически бросает спасбросок против DC.
     * При успехе — состояние снимается.
     *
     * Примеры:
     * - Яд: CON спасбросок DC 14 в конце каждого хода
     * - Страх: WIS спасбросок DC 16 в начале каждого хода
     *
     * @param dc сложность спасброска
     * @param stat характеристика для спасброска
     * @param checkOn когда проверяется спасбросок
     */
    data class UntilSave(
        val dc: Int,
        val stat: Identifier,
        val checkOn: SaveTrigger
    ) : Duration() {
        companion object {
            val CODEC: Codec<UntilSave> = RecordCodecBuilder.create { i ->
                i.group(
                    Codec.INT.fieldOf("dc").forGetter { it.dc },
                    CodecUtil.IDENTIFIER.fieldOf("stat").forGetter { it.stat },
                    SaveTrigger.CODEC.fieldOf("check_on").forGetter { it.checkOn }
                ).apply(i, ::UntilSave)
            }
        }
    }

    // ── Фабричные методы ──────────────────────────────────────────────────

    companion object {

        /** Постоянное действие. */
        val PERMANENT: Duration = Permanent

        /** N секунд. */
        fun seconds(n: Int): Duration = Ticks(TimeUtil.secondsToTicks(n))

        /** N минут. */
        fun minutes(n: Int): Duration = Ticks(TimeUtil.minutesToTicks(n))

        /** N часов. */
        fun hours(n: Int): Duration = Ticks(TimeUtil.hoursToTicks(n))

        /** До наступления события по строковому ID. */
        fun untilEvent(id: String): Duration = UntilEvent(
            Identifier.of(id.substringBefore(':'), id.substringAfter(':'))
        )

        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<Duration> = Codec.STRING.dispatch(
            "type",
            { duration ->
                when (duration) {
                    is Permanent   -> "permanent"
                    is Ticks       -> "ticks"
                    is UntilEvent  -> "until_event"
                    is UntilSave   -> "until_save"
                }
            },
            { type ->
                when (type) {
                    "permanent"   -> Codec.unit(Permanent)
                    "ticks"       -> Ticks.CODEC
                    "until_event" -> UntilEvent.CODEC
                    "until_save"  -> UntilSave.CODEC
                    else -> throw IllegalArgumentException("Unknown duration type: $type")
                }
            }
        )
    }
}

/**
 * Когда проверяется спасбросок для [Duration.UntilSave].
 *
 * Адаптировано под реальное время Minecraft (не пошаговую систему).
 */
sealed class SaveTrigger {

    /**
     * Каждые N тиков.
     *
     * Примеры:
     * - Яд: спасбросок каждые 20 тиков (раз в секунду)
     * - Страх: спасбросок каждые 100 тиков (раз в 5 секунд)
     *
     * @param intervalTicks интервал проверки в тиках
     */
    data class EveryTicks(val intervalTicks: Int) : SaveTrigger() {
        companion object {
            val CODEC: Codec<EveryTicks> = RecordCodecBuilder.create { i ->
                i.group(Codec.INT.fieldOf("interval_ticks").forGetter { it.intervalTicks })
                 .apply(i, ::EveryTicks)
            }
        }
    }

    /**
     * При получении урона.
     *
     * Примеры:
     * - Страх: спасбросок когда существо получает урон
     * - Оглушение: спасбросок при каждом попадании
     */
    data object OnDamage : SaveTrigger() {
        val CODEC: Codec<OnDamage> = Codec.unit(OnDamage)
    }

    /**
     * При совершении атаки.
     *
     * Примеры:
     * - Дрожь рук: спасбросок при попытке атаковать
     */
    data object OnAttack : SaveTrigger() {
        val CODEC: Codec<OnAttack> = Codec.unit(OnAttack)
    }

    /**
     * При использовании способности или заклинания.
     */
    data object OnAbilityUse : SaveTrigger() {
        val CODEC: Codec<OnAbilityUse> = Codec.unit(OnAbilityUse)
    }

    companion object {
        val CODEC: Codec<SaveTrigger> = Codec.STRING.dispatch(
            "type",
            { trigger ->
                when (trigger) {
                    is EveryTicks  -> "every_ticks"
                    is OnDamage    -> "on_damage"
                    is OnAttack    -> "on_attack"
                    is OnAbilityUse -> "on_ability_use"
                }
            },
            { type ->
                when (type) {
                    "every_ticks"    -> EveryTicks.CODEC
                    "on_damage"      -> OnDamage.CODEC
                    "on_attack"      -> OnAttack.CODEC
                    "on_ability_use" -> OnAbilityUse.CODEC
                    else -> throw IllegalArgumentException("Unknown save trigger: $type")
                }
            }
        )

        /** Удобный метод: каждую секунду. */
        fun everySecond(): SaveTrigger = EveryTicks(TimeUtil.TICKS_PER_SECOND)

        /** Удобный метод: каждые N секунд. */
        fun everySeconds(n: Int): SaveTrigger = EveryTicks(TimeUtil.secondsToTicks(n))
    }
}
