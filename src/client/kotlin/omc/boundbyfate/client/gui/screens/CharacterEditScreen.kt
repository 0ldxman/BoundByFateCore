package omc.boundbyfate.client.gui.screens

import net.minecraft.client.gui.DrawContext
import omc.boundbyfate.client.gui.core.*
import omc.boundbyfate.client.gui.widgets.BbfButton
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

    override fun onInit() {
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
     * L2, L3 — делятся горизонтально: LL (StatBox-ы) + LR (заглушка).
     *   LL2: СИЛ, ЛОВ, ВЫН
     *   LL3: ИНТ, МУД, ХАР
     */
    private fun buildLeftColumn(colW: Int, s1h: Int, s2h: Int, s3h: Int): VBoxLayout {
        val llW = (colW * LEFT_LEFT_RATIO).toInt()
        val lrW = colW - llW - COL_GAP

        val statH2 = (s2h - SEG_GAP * 2) / 3
        val statH3 = (s3h - SEG_GAP * 2) / 3

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
                    add(PanelWidget("LR2", BG_SEG, BORDER_COL, TEXT_LABEL), width = lrW, height = s2h)
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
                    add(PanelWidget("LR3", BG_SEG, BORDER_COL, TEXT_LABEL), width = lrW, height = s3h)
                },
                height = s3h, width = colW
            )
        }
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

        return vbox(gap = SEG_GAP) {
            add(PanelWidget("R1", BG_SEG, BORDER_COL, TEXT_LABEL), height = s1h, width = colW)

            add(
                vbox(gap = SEG_GAP) {
                    navLabels.forEach { label ->
                        add(
                            BbfButton(label).also { btn ->
                                btn.onClick { /* TODO: навигация на соответствующий экран */ }
                            },
                            height = btnH, width = colW
                        )
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
