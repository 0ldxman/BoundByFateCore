package omc.boundbyfate.client.gui.screens

import omc.boundbyfate.client.gui.core.*
import omc.boundbyfate.client.gui.widgets.*
import omc.boundbyfate.client.gui.widgets.character.*

/**
 * Экран создания/редактирования персонажа.
 *
 * Layout: 30% / 40% / 30%
 *
 * ## Использование
 *
 * ```kotlin
 * val screen = CharacterEditScreen()
 * screen.open()
 * ```
 */
class CharacterEditScreen : BbfScreen("screen.bbf.character_edit") {

    // ── Левая колонка (30%) ───────────────────────────────────────────────

    private val raceButton = BbfButton("Выбрать расу", width = 120, height = 24)
    private val genderButton = BbfButton("Выбрать пол", width = 120, height = 24)
    private val birthButton = BbfButton("Возраст: 25", width = 120, height = 24)

    private val savingThrows = ScrollableList(
        items = listOf(
            "СИЛ" to 2,
            "ЛОВ" to 5,
            "ВЫН" to 3,
            "ИНТ" to 1,
            "МУД" to 4,
            "ХАР" to 0
        ),
        itemHeight = 18,
        createWidget = { (name, bonus) ->
            SkillRowWidget(name, bonus, ProficiencyState.PROFICIENT)
        }
    )

    private val abilityScores = GridWidget(
        widgets = listOf(
            AbilityScoreWidget("СИЛ", 10, 2),
            AbilityScoreWidget("ЛОВ", 14, 2),
            AbilityScoreWidget("ВЫН", 12, 2),
            AbilityScoreWidget("ИНТ", 8, 2),
            AbilityScoreWidget("МУД", 13, 2),
            AbilityScoreWidget("ХАР", 10, 2)
        ),
        columns = 2,
        gap = 8
    )

    private val skills = ScrollableList(
        items = listOf(
            "Акробатика" to 5,
            "Анализ" to 1,
            "Атлетика" to 2,
            "Восприятие" to 4,
            "Выживание" to 4,
            "Выступление" to 0,
            "Запугивание" to 0,
            "История" to 1,
            "Ловкость рук" to 5,
            "Магия" to 1,
            "Медицина" to 4,
            "Обман" to 0,
            "Обращение с животными" to 4,
            "Природа" to 1,
            "Проницательность" to 4,
            "Религия" to 1,
            "Скрытность" to 5,
            "Убеждение" to 0
        ),
        itemHeight = 18,
        createWidget = { (name, bonus) ->
            SkillRowWidget(name, bonus, ProficiencyState.NONE)
        }
    )

    // ── Центральная колонка (40%) ─────────────────────────────────────────

    private val nameField = BbfTextField(
        text = "",
        placeholder = "Имя персонажа",
        width = 200,
        height = 24
    )

    private val levelBar = BarWidget(
        current = 750f,
        max = 1000f,
        width = 200,
        height = 8,
        fillColor = 0xFF55FF55.toInt(),
        showText = false
    )

    private val ownersButton = BbfButton("👥", width = 24, height = 24)

    private val appearanceField = BbfTextField(
        text = "",
        placeholder = "Описание внешности",
        width = 250,
        height = 60,
        maxLength = 256
    )

    private val lifeForce = CheckboxScaleWidget(
        total = 5,
        checked = 5,
        checkboxSize = 16,
        gap = 4
    )

    private val scarsButton = BbfButton("Шрамы (0)", width = 120, height = 24)

    private val features = ScrollableList(
        items = listOf(
            "Тёмное зрение",
            "Ярость",
            "Безрассудная атака",
            "Опасное чутьё"
        ),
        itemHeight = 20,
        createWidget = { feature ->
            // TODO: создать FeatureRowWidget
            BbfButton(feature, width = 200, height = 18)
        }
    )

    private val addFeatureButton = BbfButton("+", width = 24, height = 24)

    // ── Правая колонка (30%) ──────────────────────────────────────────────

    private val classButton = BbfButton("Выбрать класс", width = 120, height = 24)
    private val subclassButton = BbfButton("Подкласс", width = 120, height = 24, enabled = false)

    private val alignmentButton = BbfButton("Мировоззрение", width = 140, height = 24)
    private val appearanceButton = BbfButton("Внешность", width = 140, height = 24)
    private val abilitiesButton = BbfButton("Способности и механики", width = 140, height = 24)
    private val savedStatsButton = BbfButton("Saved Stats", width = 140, height = 24)

    private val proficiencies = ScrollableList(
        items = listOf(
            "Простое оружие",
            "Боевое оружие",
            "Лёгкая броня",
            "Средняя броня",
            "Щиты",
            "Общий язык",
            "Драконий язык"
        ),
        itemHeight = 18,
        createWidget = { prof ->
            BbfButton(prof, width = 130, height = 16)
        }
    )

    private val addProficiencyButton = BbfButton("+", width = 24, height = 24)

    // ── Layout ────────────────────────────────────────────────────────────

    private lateinit var mainLayout: HBoxLayout

    override fun onInit() {
        val screenWidth = width
        val screenHeight = height

        val leftWidth = (screenWidth * 0.3f).toInt()
        val centerWidth = (screenWidth * 0.4f).toInt()
        val rightWidth = (screenWidth * 0.3f).toInt()

        // Левая колонка
        val leftColumn = vbox(gap = 8, padding = 10) {
            add(raceButton, height = 24)
            add(genderButton, height = 24)
            add(birthButton, height = 24)
            add(BbfButton("Спасброски", width = leftWidth - 20, height = 20, enabled = false), height = 20)
            add(savingThrows, height = 120)
            add(BbfButton("Характеристики", width = leftWidth - 20, height = 20, enabled = false), height = 20)
            add(abilityScores, height = abilityScores.totalHeight)
            add(BbfButton("Навыки", width = leftWidth - 20, height = 20, enabled = false), height = 20)
            add(skills, height = 300)
        }

        // Центральная колонка
        val centerColumn = vbox(gap = 8, padding = 10) {
            add(nameField, height = 24)
            add(levelBar, height = 8)
            add(BbfButton("Ур. 5", width = centerWidth - 20, height = 16, enabled = false), height = 16)
            add(appearanceField, height = 60)
            add(BbfButton("Жизненная сила", width = centerWidth - 20, height = 20, enabled = false), height = 20)
            add(lifeForce, height = 20)
            add(scarsButton, height = 24)
            add(BbfButton("Особенности", width = centerWidth - 20, height = 20, enabled = false), height = 20)
            add(features, height = 300)
        }

        // Правая колонка
        val rightColumn = vbox(gap = 8, padding = 10) {
            add(classButton, height = 24)
            add(subclassButton, height = 24)
            add(alignmentButton, height = 24)
            add(appearanceButton, height = 24)
            add(abilitiesButton, height = 24)
            add(savedStatsButton, height = 24)
            add(BbfButton("Владения", width = rightWidth - 20, height = 20, enabled = false), height = 20)
            add(proficiencies, height = 300)
        }

        mainLayout = hbox(gap = 0, padding = 0) {
            add(leftColumn, width = leftWidth)
            add(centerColumn, width = centerWidth)
            add(rightColumn, width = rightWidth)
        }
    }

    override fun renderContent(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val rctx = RenderContext(ctx, 0, 0, width, height, mouseX, mouseY, delta)
        mainLayout.tick(rctx)
        mainLayout.render(rctx)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        // TODO: передать scroll в нужный список
        return super.mouseScrolled(mouseX, mouseY, amount)
    }
}
