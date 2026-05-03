package omc.boundbyfate.client.gui.core

/**
 * Система тем для GUI.
 *
 * Централизованное управление цветами, размерами и визуальными параметрами.
 * Позволяет менять внешний вид всего UI одной строкой.
 *
 * ## Использование
 *
 * ```kotlin
 * // В коде виджета:
 * ctx.fillRect(x, y, w, h, Theme.panel.background)
 * ctx.drawScaledText("Title", x, y, color = Theme.text.primary)
 *
 * // Смена темы:
 * Theme.current = Theme.MYSTICAL
 * ```
 */
object Theme {

    /** Колбек на смену темы. */
    var onThemeChanged: ((BbfTheme) -> Unit)? = null

    // ── Предустановленные темы ────────────────────────────────────────────

    /** Темная тема (по умолчанию). */
    val DARK = BbfTheme(
        name = "Dark",
        
        panel = PanelColors(
            background = 0xEE1a1a2e.toInt(),
            backgroundLight = 0xEE2a2a3e.toInt(),
            border = 0xFF6B4C9A.toInt(),
            borderLight = 0xFF8B6CBA.toInt(),
            shadow = 0x88000000.toInt()
        ),
        
        text = TextColors(
            primary = 0xFFFFFFFF.toInt(),
            secondary = 0xFFCCCCCC.toInt(),
            disabled = 0xFF666666.toInt(),
            accent = 0xFF6B4C9A.toInt(),
            error = 0xFFFF5555.toInt(),
            success = 0xFF55FF55.toInt(),
            warning = 0xFFFFAA00.toInt()
        ),
        
        button = ButtonColors(
            normal = 0xFF3a3a4e.toInt(),
            hovered = 0xFF4a4a5e.toInt(),
            pressed = 0xFF2a2a3e.toInt(),
            disabled = 0xFF1a1a2e.toInt(),
            text = 0xFFFFFFFF.toInt(),
            textDisabled = 0xFF666666.toInt()
        ),
        
        input = InputColors(
            background = 0xFF2a2a3e.toInt(),
            backgroundFocused = 0xFF3a3a4e.toInt(),
            border = 0xFF4a4a5e.toInt(),
            borderFocused = 0xFF6B4C9A.toInt(),
            text = 0xFFFFFFFF.toInt(),
            placeholder = 0xFF888888.toInt(),
            cursor = 0xFFFFFFFF.toInt(),
            selection = 0x886B4C9A.toInt()
        ),
        
        stat = StatColors(
            strength = 0xFFFF5555.toInt(),
            dexterity = 0xFF55FF55.toInt(),
            constitution = 0xFFFFAA00.toInt(),
            intelligence = 0xFF5555FF.toInt(),
            wisdom = 0xFFAA55FF.toInt(),
            charisma = 0xFFFF55AA.toInt()
        ),
        
        health = HealthColors(
            full = 0xFFFF5555.toInt(),
            medium = 0xFFFFAA00.toInt(),
            low = 0xFFFF0000.toInt(),
            background = 0xFF2a2a3e.toInt(),
            border = 0xFF4a4a5e.toInt()
        ),
        
        spacing = Spacing(
            tiny = 2,
            small = 4,
            medium = 8,
            large = 12,
            huge = 16
        ),
        
        sizing = Sizing(
            buttonHeight = 20,
            inputHeight = 24,
            iconSmall = 16,
            iconMedium = 24,
            iconLarge = 32,
            panelPadding = 8,
            borderWidth = 1,
            scrollbarWidth = 8
        )
    )

    /** Мистическая тема — фиолетовые тона. */
    val MYSTICAL = DARK.copy(
        name = "Mystical",
        panel = DARK.panel.copy(
            background = 0xEE1a1a2e.toInt(),
            border = 0xFFAA55FF.toInt(),
            borderLight = 0xFFCC77FF.toInt()
        ),
        text = DARK.text.copy(
            accent = 0xFFAA55FF.toInt()
        ),
        button = DARK.button.copy(
            normal = 0xFF3a2a4e.toInt(),
            hovered = 0xFF4a3a5e.toInt(),
            pressed = 0xFF2a1a3e.toInt()
        )
    )

    /** Светлая тема. */
    val LIGHT = BbfTheme(
        name = "Light",
        
        panel = PanelColors(
            background = 0xFFEEEEEE.toInt(),
            backgroundLight = 0xFFFFFFFF.toInt(),
            border = 0xFF6B4C9A.toInt(),
            borderLight = 0xFF8B6CBA.toInt(),
            shadow = 0x44000000.toInt()
        ),
        
        text = TextColors(
            primary = 0xFF000000.toInt(),
            secondary = 0xFF333333.toInt(),
            disabled = 0xFF999999.toInt(),
            accent = 0xFF6B4C9A.toInt(),
            error = 0xFFCC0000.toInt(),
            success = 0xFF00AA00.toInt(),
            warning = 0xFFFF8800.toInt()
        ),
        
        button = ButtonColors(
            normal = 0xFFDDDDDD.toInt(),
            hovered = 0xFFCCCCCC.toInt(),
            pressed = 0xFFBBBBBB.toInt(),
            disabled = 0xFFEEEEEE.toInt(),
            text = 0xFF000000.toInt(),
            textDisabled = 0xFF999999.toInt()
        ),
        
        input = InputColors(
            background = 0xFFFFFFFF.toInt(),
            backgroundFocused = 0xFFFAFAFA.toInt(),
            border = 0xFFCCCCCC.toInt(),
            borderFocused = 0xFF6B4C9A.toInt(),
            text = 0xFF000000.toInt(),
            placeholder = 0xFF888888.toInt(),
            cursor = 0xFF000000.toInt(),
            selection = 0x886B4C9A.toInt()
        ),
        
        stat = DARK.stat,
        health = DARK.health,
        spacing = DARK.spacing,
        sizing = DARK.sizing
    )

    // ── Быстрый доступ к текущей теме ─────────────────────────────────────

    val panel get() = current.panel
    val text get() = current.text
    val button get() = current.button
    val input get() = current.input
    val stat get() = current.stat
    val health get() = current.health
    val spacing get() = current.spacing
    val sizing get() = current.sizing

    /** Активная тема. */
    var current: BbfTheme = DARK
        set(value) {
            field = value
            onThemeChanged?.invoke(value)
        }
}

// ── Тема ──────────────────────────────────────────────────────────────────

/**
 * Полная тема UI.
 */
data class BbfTheme(
    val name: String,
    val panel: PanelColors,
    val text: TextColors,
    val button: ButtonColors,
    val input: InputColors,
    val stat: StatColors,
    val health: HealthColors,
    val spacing: Spacing,
    val sizing: Sizing
)

// ── Цветовые палитры ──────────────────────────────────────────────────────

/**
 * Цвета панелей и контейнеров.
 */
data class PanelColors(
    val background: Int,
    val backgroundLight: Int,
    val border: Int,
    val borderLight: Int,
    val shadow: Int
)

/**
 * Цвета текста.
 */
data class TextColors(
    val primary: Int,
    val secondary: Int,
    val disabled: Int,
    val accent: Int,
    val error: Int,
    val success: Int,
    val warning: Int
)

/**
 * Цвета кнопок.
 */
data class ButtonColors(
    val normal: Int,
    val hovered: Int,
    val pressed: Int,
    val disabled: Int,
    val text: Int,
    val textDisabled: Int
)

/**
 * Цвета полей ввода.
 */
data class InputColors(
    val background: Int,
    val backgroundFocused: Int,
    val border: Int,
    val borderFocused: Int,
    val text: Int,
    val placeholder: Int,
    val cursor: Int,
    val selection: Int
)

/**
 * Цвета характеристик.
 */
data class StatColors(
    val strength: Int,
    val dexterity: Int,
    val constitution: Int,
    val intelligence: Int,
    val wisdom: Int,
    val charisma: Int
)

/**
 * Цвета здоровья.
 */
data class HealthColors(
    val full: Int,
    val medium: Int,
    val low: Int,
    val background: Int,
    val border: Int
)

// ── Размеры и отступы ─────────────────────────────────────────────────────

/**
 * Стандартные отступы.
 */
data class Spacing(
    val tiny: Int,
    val small: Int,
    val medium: Int,
    val large: Int,
    val huge: Int
)

/**
 * Стандартные размеры элементов.
 */
data class Sizing(
    val buttonHeight: Int,
    val inputHeight: Int,
    val iconSmall: Int,
    val iconMedium: Int,
    val iconLarge: Int,
    val panelPadding: Int,
    val borderWidth: Int,
    val scrollbarWidth: Int
)
