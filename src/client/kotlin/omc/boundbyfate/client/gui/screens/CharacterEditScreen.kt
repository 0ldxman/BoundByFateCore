package omc.boundbyfate.client.gui.screens

import net.minecraft.client.gui.DrawContext
import omc.boundbyfate.client.gui.core.*
import omc.boundbyfate.client.gui.widgets.*
import omc.boundbyfate.client.gui.widgets.character.StatBox

/**
 * Экран создания/редактирования персонажа.
 *
 * Макет строится через Layout систему (HBoxLayout + VBoxLayout).
 * Пропорции колонок выведены из эталонных размеров при GUI Scale 3:
 *   Левая  = 218/610 ≈ 35.74%  от ширины макета
 *   Центр  = 194/610 ≈ 31.80%  от ширины макета
 *   Правая = остаток
 *
 * Пропорции сегментов по высоте:
 *   S1 = 67/341  ≈ 19.65%
 *   S2 = 135/341 ≈ 39.59%
 *   S3 = остаток
 */
class CharacterEditScreen : BbfScreen("screen.bbf.character_edit") {

    private val SCREEN_PAD = 2
    private val COL_GAP    = 2
    private val SEG_GAP    = 2

    private val LEFT_RATIO   = 218f / 610f
    private val CENTER_RATIO = 194f / 610f
    private val SEG1_RATIO   = 67f  / 341f
    private val SEG2_RATIO   = 135f / 341f
    private val LEFT_LEFT_RATIO = 70f / 218f

    private val BG_PANEL   = 0xEE141420.toInt()
    private val BG_SEG     = 0xEE1e1e2e.toInt()
    private val BORDER_COL = 0xFF3a3a5a.toInt()
    private val TEXT_LABEL = 0xFFaaaacc.toInt()

    // ── Корневой layout ───────────────────────────────────────────────────

    private lateinit var rootLayout: HBoxLayout
    private val scrollables = mutableListOf<ScrollableBlock>()

    /** Лейбл имени персонажа в C1 — нужен для проброса событий. */
    private lateinit var nameLabel: EditableLabel

    override fun onInit() {
        scrollables.clear()
        buildLayout()
    }

    private fun buildLayout() {
        val mw = width  - SCREEN_PAD * 2
        val mh = height - SCREEN_PAD * 2

        val lw = (mw * LEFT_RATIO).toInt()
        val cw = (mw * CENTER_RATIO).toInt()
        val rw = mw - lw - cw - COL_GAP * 2

        val s1h = (mh * SEG1_RATIO).toInt()
        val s2h = (mh * SEG2_RATIO).toInt()
        val s3h = mh - s1h - s2h - SEG_GAP * 2

        rootLayout = hbox(gap = COL_GAP) {
            add(buildLeftColumn(lw, s1h, s2h, s3h),  width = lw, height = mh)
            add(buildCenterColumn(cw, s1h, s2h, s3h), width = cw, height = mh)
            add(buildRightColumn(rw, s1h, s2h, s3h),  width = rw, height = mh)
        }
    }

    // ── Колонки ───────────────────────────────────────────────────────────

    private fun buildColumn(
        prefix: String,
        colW: Int,
        s1h: Int, s2h: Int, s3h: Int
    ): VBoxLayout = vbox(gap = SEG_GAP) {
        add(panel(colW, s1h, "${prefix}1"), height = s1h, width = colW)
        add(panel(colW, s2h, "${prefix}2"), height = s2h, width = colW)
        add(panel(colW, s3h, "${prefix}3"), height = s3h, width = colW)
    }

    /**
     * Центральная колонка.
     * C1 — имя персонажа (EditableLabel).
     * C2 — заглушка.
     * C3 — список владений (оружие, броня, инструменты, языки) с кнопкой "+".
     */
    private fun buildCenterColumn(colW: Int, s1h: Int, s2h: Int, s3h: Int): VBoxLayout =
        vbox(gap = SEG_GAP) {
            add(buildNamePanel(colW, s1h), height = s1h, width = colW)
            add(panel(colW, s2h, "C2"),   height = s2h, width = colW)
            add(buildProficienciesPanel(colW, s3h), height = s3h, width = colW)
        }

    /**
     * C1 — панель с именем персонажа.
     * EditableLabel центрируется вертикально внутри панели.
     */
    private fun buildNamePanel(w: Int, h: Int): PanelWidget {
        val pad = 4
        nameLabel = EditableLabel(
            text = "Имя персонажа",
            textScale = 1f,
            onConfirm = { newName -> /* TODO: сохранить имя персонажа */ }
        )
        return PanelWidget(
            bgColor = BG_SEG, borderColor = BORDER_COL,
            padding = pad, content = nameLabel
        )
    }

    /**
     * Левая колонка.
     * L1 — цельная панель-заглушка.
     * L2, L3 — LL (StatBox-ы) + LR (PanelWidget → ScrollableBlock → FlowList).
     */
    private fun buildLeftColumn(colW: Int, s1h: Int, s2h: Int, s3h: Int): VBoxLayout {
        val llW = (colW * LEFT_LEFT_RATIO).toInt()
        val lrW = colW - llW - COL_GAP

        val statH2 = (s2h - SEG_GAP * 2) / 3
        val statH3 = (s3h - SEG_GAP * 2) / 3

        val profStyles = listOf(
            CheckboxAppearance(filled = false, fillColor = 0,                  borderColor = Theme.panel.border),
            CheckboxAppearance(filled = true,  fillColor = Theme.text.accent,  borderColor = Theme.text.accent),
            CheckboxAppearance(filled = true,  fillColor = 0xFF55AA55.toInt(), borderColor = 0xFF55AA55.toInt())
        )

        return vbox(gap = SEG_GAP) {
            add(panel(colW, s1h, "L1"), height = s1h, width = colW)

            // L2: StatBox-ы + спасброски
            add(
                hbox(gap = COL_GAP) {
                    add(
                        vbox(gap = SEG_GAP) {
                            add(StatBox("СИЛ", baseValue = 10), height = statH2, width = llW)
                            add(StatBox("ЛОВ", baseValue = 10), height = statH2, width = llW)
                            add(StatBox("ВЫН", baseValue = 10), height = statH2, width = llW)
                        },
                        width = llW, height = s2h
                    )
                    add(buildSavingThrowsPanel(lrW, s2h, profStyles), width = lrW, height = s2h)
                },
                height = s2h, width = colW
            )

            // L3: StatBox-ы + навыки
            add(
                hbox(gap = COL_GAP) {
                    add(
                        vbox(gap = SEG_GAP) {
                            add(StatBox("ИНТ", baseValue = 10), height = statH3, width = llW)
                            add(StatBox("МУД", baseValue = 10), height = statH3, width = llW)
                            add(StatBox("ХАР", baseValue = 10), height = statH3, width = llW)
                        },
                        width = llW, height = s3h
                    )
                    add(buildSkillsPanel(lrW, s3h, profStyles), width = lrW, height = s3h)
                },
                height = s3h, width = colW
            )
        }
    }

    /**
     * LR2 — PanelWidget → ScrollableBlock → FlowList<HBoxLayout> (спасброски).
     * Строка: [Название] [Бонус 16px] [Чекбокс rowH×rowH]
     * Ширина названия = остаток.
     */
    private fun buildSavingThrowsPanel(
        w: Int, h: Int,
        profStyles: List<CheckboxAppearance>
    ): PanelWidget {
        val rowH  = 11
        val gap   = 1
        val pad   = 3
        val innerW = w - pad * 2

        val savingThrows = listOf(
            "Сила", "Ловкость", "Выносливость",
            "Интеллект", "Мудрость", "Харизма"
        )

        val cbW    = rowH
        val bonusW = 16
        val nameW  = innerW - bonusW - cbW - gap * 2

        val list = FlowList<HBoxLayout>(FlowConfig.Vertical(rowHeight = rowH, gap = gap))
        savingThrows.forEach { stat ->
            list.add(hbox(gap = gap) {
                add(TextWidget(stat,  align = TextAlign.LEFT,  color = Theme.text.secondary, scale = 0.7f), width = nameW,  height = rowH)
                add(TextWidget("+0", align = TextAlign.RIGHT, color = Theme.text.accent,    scale = 0.7f), width = bonusW, height = rowH)
                add(CycleCheckbox(profStyles), width = cbW, height = rowH)
            })
        }

        val scrollable = ScrollableBlock(
            content = list,
            contentHeightProvider = { list.contentHeight }
        ).also { scrollables.add(it) }

        return PanelWidget(
            bgColor = BG_SEG,
            borderColor = BORDER_COL,
            padding = pad,
            content = scrollable
        )
    }

    /**
     * LR3 — PanelWidget → ScrollableBlock → FlowList<HBoxLayout> (навыки).
     * Строка: [Сокр. хар-ка 14px] [Навык] [Бонус 16px] [Чекбокс rowH×rowH]
     * Ширина навыка = остаток.
     */
    private fun buildSkillsPanel(
        w: Int, h: Int,
        profStyles: List<CheckboxAppearance>
    ): PanelWidget {
        val rowH  = 11
        val gap   = 1
        val pad   = 3
        val innerW = w - pad * 2

        val skills = listOf(
            "СИЛ" to "Атлетика",
            "ЛОВ" to "Акробатика",
            "ЛОВ" to "Ловкость рук",
            "ЛОВ" to "Скрытность",
            "ИНТ" to "Анализ",
            "ИНТ" to "История",
            "ИНТ" to "Магия",
            "ИНТ" to "Природа",
            "ИНТ" to "Религия",
            "МУД" to "Внимание",
            "МУД" to "Выживание",
            "МУД" to "Медицина",
            "МУД" to "Уход за животными",
            "МУД" to "Проницательность",
            "ХАР" to "Выступление",
            "ХАР" to "Запугивание",
            "ХАР" to "Обман",
            "ХАР" to "Убеждение"
        )

        val cbW    = rowH
        val bonusW = 16
        val statW  = 14
        val skillW = innerW - statW - bonusW - cbW - gap * 3

        val list = FlowList<HBoxLayout>(FlowConfig.Vertical(rowHeight = rowH, gap = gap))
        skills.forEach { (stat, skill) ->
            list.add(hbox(gap = gap) {
                add(TextWidget(stat,  align = TextAlign.LEFT,  color = Theme.text.disabled,  scale = 0.65f), width = statW,  height = rowH)
                add(TextWidget(skill, align = TextAlign.LEFT,  color = Theme.text.secondary, scale = 0.65f), width = skillW, height = rowH)
                add(TextWidget("+0", align = TextAlign.RIGHT, color = Theme.text.accent,    scale = 0.65f), width = bonusW, height = rowH)
                add(CycleCheckbox(profStyles), width = cbW, height = rowH)
            })
        }

        val scrollable = ScrollableBlock(
            content = list,
            contentHeightProvider = { list.contentHeight }
        ).also { scrollables.add(it) }

        return PanelWidget(
            bgColor = BG_SEG,
            borderColor = BORDER_COL,
            padding = pad,
            content = scrollable
        )
    }

    /**
     * C3 — список владений персонажа (оружие, броня, инструменты, языки).
     * Строка: [BbfButton с названием] [кнопка "×" rowH×rowH]
     * Кнопка "+" в правом верхнем углу панели.
     */
    private fun buildProficienciesPanel(w: Int, h: Int): PanelWidget {
        val rowH = 11
        val gap  = 1
        val pad  = 3

        // Тестовые данные — заменить на реальные данные персонажа
        val proficiencies = listOf(
            "Простое оружие", "Воинское оружие",
            "Лёгкие доспехи", "Средние доспехи",
            "Общий язык", "Эльфийский"
        )

        val list = buildItemList(w, h, pad, rowH, gap, proficiencies)
        val scrollable = ScrollableBlock(
            content = list,
            contentHeightProvider = { list.contentHeight }
        ).also { scrollables.add(it) }

        return PanelWidget(
            bgColor = BG_SEG, borderColor = BORDER_COL,
            padding = pad, title = "Владения",
            content = scrollable,
            onAdd = { /* TODO: открыть диалог добавления владения */ }
        )
    }

    /**
     * R3 — список особенностей и черт персонажа.
     * Строка: [BbfButton с названием] [кнопка "×" rowH×rowH]
     * Кнопка "+" в правом верхнем углу панели.
     */
    private fun buildFeaturesPanel(w: Int, h: Int): PanelWidget {
        val rowH = 11
        val gap  = 1
        val pad  = 3

        // Тестовые данные — заменить на реальные данные персонажа
        val features = listOf(
            "Дарохранитель", "Тёмное зрение",
            "Стойкость дварфов", "Боевой стиль"
        )

        val list = buildItemList(w, h, pad, rowH, gap, features)
        val scrollable = ScrollableBlock(
            content = list,
            contentHeightProvider = { list.contentHeight }
        ).also { scrollables.add(it) }

        return PanelWidget(
            bgColor = BG_SEG, borderColor = BORDER_COL,
            padding = pad, title = "Особенности",
            content = scrollable,
            onAdd = { /* TODO: открыть диалог добавления особенности */ }
        )
    }

    /**
     * Строит FlowList со строками формата [кнопка-название] [кнопка-×].
     * Переиспользуется для C3 и R3.
     */
    private fun buildItemList(
        w: Int, h: Int, pad: Int, rowH: Int, gap: Int,
        items: List<String>
    ): FlowList<HBoxLayout> {
        val innerW = w - pad * 2
        val delW   = rowH  // квадратная кнопка удаления
        val nameW  = innerW - delW - gap

        val list = FlowList<HBoxLayout>(FlowConfig.Vertical(rowHeight = rowH, gap = gap))
        items.forEach { name ->
            list.add(hbox(gap = gap) {
                add(
                    BbfButton(name).also { btn -> btn.onClick { /* TODO: открыть детали */ } },
                    width = nameW, height = rowH
                )
                add(
                    BbfButton("×").also { btn ->
                        btn.onClick { /* TODO: удалить элемент */ }
                    },
                    width = delW, height = rowH
                )
            })
        }
        return list
    }

    /**
     * Правая колонка.
     * R2 — 4 кнопки навигации.
     */
    private fun buildRightColumn(colW: Int, s1h: Int, s2h: Int, s3h: Int): VBoxLayout {
        val btnH = (s2h - SEG_GAP * 3) / 4

        val navLabels = listOf("Мировоззрение", "Внешность", "Способности", "Сохранённые")
        val navButtons = navLabels.map { label ->
            BbfButton(label).also { btn -> btn.onClick { /* TODO */ } }
        }

        return vbox(gap = SEG_GAP) {
            add(panel(colW, s1h, "R1"), height = s1h, width = colW)

            add(
                vbox(gap = SEG_GAP) {
                    navButtons.forEach { btn -> add(btn, height = btnH, width = colW) }
                },
                height = s2h, width = colW
            )

            add(buildFeaturesPanel(colW, s3h), height = s3h, width = colW)
        }
    }

    // ── Рендер ────────────────────────────────────────────────────────────

    override fun renderContent(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val ox = SCREEN_PAD
        val oy = SCREEN_PAD
        val mw = width  - SCREEN_PAD * 2
        val mh = height - SCREEN_PAD * 2

        ctx.fillRect(ox, oy, mw, mh, BG_PANEL)

        val rctx = RenderContext(ctx, ox, oy, mw, mh, mouseX, mouseY, delta)
        rootLayout.tick(rctx)
        rootLayout.render(rctx)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        scrollables.forEach { it.handleScroll(mouseX, mouseY, amount) }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        if (::nameLabel.isInitialized && nameLabel.handleClick(mx, my, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (::nameLabel.isInitialized && nameLabel.handleKey(keyCode, modifiers)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (::nameLabel.isInitialized && nameLabel.handleChar(chr)) return true
        return super.charTyped(chr, modifiers)
    }

    // ── Хелперы ───────────────────────────────────────────────────────────

    /** Создаёт панель-заглушку с меткой для отладки. */
    private fun panel(w: Int, h: Int, label: String): PanelWidget =
        object : PanelWidget(bgColor = BG_SEG, borderColor = BORDER_COL) {
            override fun renderContent(ctx: RenderContext) {
                ctx.drawContext.drawScaledText(label, ctx.x + 3, ctx.y + 3, color = TEXT_LABEL, shadow = false)
            }
        }
}
