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

    // Пропорции колонок от ширины макета
    private val LEFT_RATIO   = 218f / 610f
    private val CENTER_RATIO = 194f / 610f

    // Пропорции сегментов от высоты макета
    private val SEG1_RATIO = 67f  / 341f
    private val SEG2_RATIO = 135f / 341f

    // Пропорции подколонок левой колонки (от ширины левой колонки)
    // LL = 70px, LR = 146px, зазор 2px → 70 + 2 + 146 = 218
    private val LEFT_LEFT_RATIO = 70f / 218f   // 32.11%

    // Цвета
    private val BG_PANEL   = 0xEE141420.toInt()
    private val BG_SEG     = 0xEE1e1e2e.toInt()
    private val BORDER_COL = 0xFF3a3a5a.toInt()
    private val TEXT_LABEL = 0xFFaaaacc.toInt()

    // ── Корневой layout ───────────────────────────────────────────────────

    private lateinit var rootLayout: HBoxLayout

    // Ссылки на скроллируемые блоки для проброса событий скролла
    private val scrollables = mutableListOf<ScrollableBlock>()

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
            add(buildLeftColumn(lw, s1h, s2h, s3h),   width = lw, height = mh)
            add(buildColumn("C", cw, s1h, s2h, s3h),  width = cw, height = mh)
            add(buildRightColumn(rw, s1h, s2h, s3h),  width = rw, height = mh)
        }
    }

    // ── Колонки ───────────────────────────────────────────────────────────

    /** Универсальная колонка из трёх заглушек (для C пока). */
    private fun buildColumn(
        prefix: String,
        colW: Int,
        s1h: Int, s2h: Int, s3h: Int
    ): VBoxLayout = vbox(gap = SEG_GAP) {
        add(PanelWidget("${prefix}1", BG_SEG, BORDER_COL, TEXT_LABEL), height = s1h, width = colW)
        add(PanelWidget("${prefix}2", BG_SEG, BORDER_COL, TEXT_LABEL), height = s2h, width = colW)
        add(PanelWidget("${prefix}3", BG_SEG, BORDER_COL, TEXT_LABEL), height = s3h, width = colW)
    }

    /**
     * Левая колонка.
     * L1 — цельный хедер.
     * L2, L3 — делятся горизонтально: LL (StatBox-ы) + LR (спасброски/навыки).
     */
    private fun buildLeftColumn(colW: Int, s1h: Int, s2h: Int, s3h: Int): VBoxLayout {
        val llW = (colW * LEFT_LEFT_RATIO).toInt()
        val lrW = colW - llW - COL_GAP

        val statH2 = (s2h - SEG_GAP * 2) / 3
        val statH3 = (s3h - SEG_GAP * 2) / 3

        // Стили чекбокса владения: пустой / акцентный / зелёный
        val profStyles = listOf(
            CheckboxAppearance(filled = false, fillColor = 0, borderColor = Theme.panel.border),
            CheckboxAppearance(filled = true,  fillColor = Theme.text.accent,  borderColor = Theme.text.accent),
            CheckboxAppearance(filled = true,  fillColor = 0xFF55AA55.toInt(), borderColor = 0xFF55AA55.toInt())
        )

        return vbox(gap = SEG_GAP) {
            add(PanelWidget("L1", BG_SEG, BORDER_COL, TEXT_LABEL), height = s1h, width = colW)

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
                    add(buildSavingThrowsBlock(lrW, s2h, profStyles), width = lrW, height = s2h)
                },
                height = s2h, width = colW
            )

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
                    add(buildSkillsBlock(lrW, s3h, profStyles), width = lrW, height = s3h)
                },
                height = s3h, width = colW
            )
        }
    }

    /** Регистрирует ScrollableBlock для получения событий скролла. */
    private fun <T : ScrollableBlock> T.register(): T {
        scrollables.add(this)
        return this
    }

    /**
     * LR2 — ScrollableBlock со списком спасбросков.
     * Строка: [Характеристика] [Бонус] [CycleCheckbox]
     * Пропорции: 50% / 30% / 20% от ширины строки.
     */
    private fun buildSavingThrowsBlock(
        w: Int, h: Int,
        profStyles: List<CheckboxAppearance>
    ): ScrollableBlock {
        val rowH = 12
        val gap  = 2

        val savingThrows = listOf("СИЛ", "ЛОВ", "ВЫН", "ИНТ", "МУД", "ХАР")

        val list = FlowList<HBoxLayout>(FlowConfig.Vertical(rowHeight = rowH, gap = gap))
        savingThrows.forEach { stat ->
            val nameW  = (w * 0.50f).toInt()
            val bonusW = (w * 0.30f).toInt()
            val cbW    = w - nameW - bonusW - gap * 2

            list.add(hbox(gap = gap) {
                add(TextWidget(stat,  align = TextAlign.LEFT,  color = Theme.text.secondary, scale = 0.8f), width = nameW,  height = rowH)
                add(TextWidget("+0", align = TextAlign.RIGHT, color = Theme.text.accent,    scale = 0.8f), width = bonusW, height = rowH)
                add(CycleCheckbox(profStyles), width = cbW, height = rowH)
            })
        }

        return ScrollableBlock(content = list, contentHeightProvider = { list.contentHeight }).register()
    }

    /**
     * LR3 — ScrollableBlock со списком навыков.
     * Строка: [Сокр. характеристика] [Навык] [Бонус] [CycleCheckbox]
     * Пропорции: 15% / 45% / 20% / 20% от ширины строки.
     */
    private fun buildSkillsBlock(
        w: Int, h: Int,
        profStyles: List<CheckboxAppearance>
    ): ScrollableBlock {
        val rowH = 12
        val gap  = 2

        // (сокращение характеристики, название навыка)
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

        val list = FlowList<HBoxLayout>(FlowConfig.Vertical(rowHeight = rowH, gap = gap))
        skills.forEach { (stat, skill) ->
            val statW  = (w * 0.15f).toInt()
            val skillW = (w * 0.45f).toInt()
            val bonusW = (w * 0.20f).toInt()
            val cbW    = w - statW - skillW - bonusW - gap * 3

            list.add(hbox(gap = gap) {
                add(TextWidget(stat,  align = TextAlign.LEFT,  color = Theme.text.disabled,  scale = 0.7f), width = statW,  height = rowH)
                add(TextWidget(skill, align = TextAlign.LEFT,  color = Theme.text.secondary, scale = 0.7f), width = skillW, height = rowH)
                add(TextWidget("+0", align = TextAlign.RIGHT, color = Theme.text.accent,    scale = 0.7f), width = bonusW, height = rowH)
                add(CycleCheckbox(profStyles), width = cbW, height = rowH)
            })
        }

        return ScrollableBlock(content = list, contentHeightProvider = { list.contentHeight }).register()
    }

    /**
     * Правая колонка.
     * R1 — заглушка.
     * R2 — 4 кнопки навигации.
     *   Пропорции: 4 кнопки + 3 зазора по 2px.
     *   Высота кнопки = (s2h - SEG_GAP * 3) / 4
     * R3 — заглушка.
     */
    private fun buildRightColumn(colW: Int, s1h: Int, s2h: Int, s3h: Int): VBoxLayout {
        val btnH = (s2h - SEG_GAP * 3) / 4

        val navLabels = listOf("Мировоззрение", "Внешность", "Способности", "Сохранённые")

        val navButtons = navLabels.map { label ->
            BbfButton(label).also { btn ->
                btn.onClick { /* TODO: навигация на соответствующий экран */ }
            }
        }

        return vbox(gap = SEG_GAP) {
            add(PanelWidget("R1", BG_SEG, BORDER_COL, TEXT_LABEL), height = s1h, width = colW)

            add(
                vbox(gap = SEG_GAP) {
                    navButtons.forEach { btn ->
                        add(btn, height = btnH, width = colW)
                    }
                },
                height = s2h, width = colW
            )

            add(PanelWidget("R3", BG_SEG, BORDER_COL, TEXT_LABEL), height = s3h, width = colW)
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
}

// ── Временный виджет-заглушка ─────────────────────────────────────────────

private class PanelWidget(
    val label: String,
    val bgColor: Int,
    val borderColor: Int,
    val textColor: Int
) : BbfWidget() {

    override fun tick(ctx: RenderContext) { tickAll(ctx.delta) }

    override fun render(ctx: RenderContext) {
        ctx.drawContext.fillRectWithBorder(
            ctx.x, ctx.y, ctx.width, ctx.height,
            bg = bgColor, border = borderColor, thickness = 1
        )
        ctx.drawContext.drawScaledText(
            label, ctx.x + 3, ctx.y + 3,
            color = textColor, shadow = false
        )
    }
}
