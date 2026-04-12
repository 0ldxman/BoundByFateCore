package omc.boundbyfate.api.ability.component

/**
 * Компонент, определяющий механику активации способности.
 * 
 * Определяет:
 * - Как игрок активирует способность
 * - Время подготовки до срабатывания
 * - Может ли быть прервана
 * - Специфические параметры для каждого типа
 */
sealed class ActivationComponent {
    /** Время подготовки в тиках (20 тиков = 1 секунда) */
    abstract val preparationTime: Int
    
    /** Может ли активация быть прервана */
    abstract val canBeInterrupted: Boolean
    
    /**
     * Мгновенная активация.
     * 
     * Используется для:
     * - Реакций (Shield, Counterspell)
     * - Быстрых способностей (Misty Step, Second Wind)
     * - Мгновенных заклинаний
     * 
     * @property preparationTime Время до срабатывания (обычно 0-10 тиков)
     * @property canBeInterrupted Может ли быть прервана (обычно false)
     */
    data class Instant(
        override val preparationTime: Int = 0,
        override val canBeInterrupted: Boolean = false
    ) : ActivationComponent()
    
    /**
     * Активация с удержанием кнопки.
     * 
     * Используется для:
     * - Заклинаний-лучей (Eldritch Blast)
     * - Способностей, требующих прицеливания
     * 
     * @property preparationTime Время до начала канала
     * @property maxChannelDuration Максимальное время удержания
     * @property canBeInterrupted Может ли быть прервана
     * @property interruptOnMove Прерывается ли при движении
     * @property interruptOnDamage Прерывается ли при получении урона
     */
    data class Channeled(
        override val preparationTime: Int = 0,
        val maxChannelDuration: Int = 60,
        override val canBeInterrupted: Boolean = true,
        val interruptOnMove: Boolean = true,
        val interruptOnDamage: Boolean = true
    ) : ActivationComponent()
    
    /**
     * Активация с накоплением силы.
     * 
     * Используется для:
     * - Заклинаний с апкастом (Fireball)
     * - Способностей, усиливающихся при зарядке
     * 
     * Чем дольше удерживается кнопка, тем сильнее эффект.
     * 
     * @property preparationTime Время до начала зарядки
     * @property minChargeTicks Минимальное время зарядки
     * @property maxChargeTicks Максимальное время зарядки
     * @property canBeInterrupted Может ли быть прервана
     */
    data class Charged(
        override val preparationTime: Int = 0,
        val minChargeTicks: Int = 20,
        val maxChargeTicks: Int = 60,
        override val canBeInterrupted: Boolean = true
    ) : ActivationComponent()
    
    /**
     * Ритуальная активация с длительным кастом.
     * 
     * Используется для:
     * - Ритуальных заклинаний (Identify, Detect Magic)
     * - Длительных способностей
     * 
     * Показывает прогресс-бар игроку.
     * 
     * @property preparationTime Время каста в тиках (обычно 600+ = 30+ секунд)
     * @property canBeInterrupted Может ли быть прервана (обычно true)
     * @property requiresStanding Требует ли стоять на месте
     */
    data class Ritual(
        override val preparationTime: Int = 600,
        override val canBeInterrupted: Boolean = true,
        val requiresStanding: Boolean = true
    ) : ActivationComponent()
}
