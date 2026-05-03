package omc.boundbyfate.client.gui.screens

import net.minecraft.client.gui.DrawContext
import omc.boundbyfate.client.gui.core.*

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

    // Цвета
    private val BG_PANEL   = 0xEE141420.toInt()
    private val BG_SEG     = 0xEE1e1e2e.toInt()
    private val BORDER_COL = 0xFF3a3a5a.toInt()
    private val TEXT_LABEL = 0xFFaaaacc.toInt()

    // ── Корневой layout ───────────────────────────────────────────────────

    /** Горизонтальный layout — три колонки. */
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
            add(buildColumn("L", lw, s1h, s2h, s3h), width = lw, height = mh)
            add(buildColumn("C", cw, s1h, s2h, s3h), width = cw, height = mh)
            add(buildColumn("R", rw, s1h, s2h, s3h), width = rw, height = mh)
        }
    }

    /** Строит вертикальную колонку из трёх сегментов. */
    private fun buildColumn(
        prefix: String,
        colW: Int,
        s1h: Int, s2h: Int, s3h: Int
    ): VBoxLayout = vbox(gap = SEG_GAP) {
        add(PanelWidget("${prefix}1", BG_SEG, BORDER_COL, TEXT_LABEL), height = s1h, width = colW)
        add(PanelWidget("${prefix}2", BG_SEG, BORDER_COL, TEXT_LABEL), height = s2h, width = colW)
        add(PanelWidget("${prefix}3", BG_SEG, BORDER_COL, TEXT_LABEL), height = s3h, width = colW)
    }

    // ── Рендер ────────────────────────────────────────────────────────────

    override fun renderContent(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val ox = SCREEN_PAD
        val oy = SCREEN_PAD
        val mw = width  - SCREEN_PAD * 2
        val mh = height - SCREEN_PAD * 2

        // Фон всего макета
        ctx.fillRect(ox, oy, mw, mh, BG_PANEL)

        // Рендер через layout систему
        val rctx = RenderContext(ctx, ox, oy, mw, mh, mouseX, mouseY, delta)
        rootLayout.tick(rctx)
        rootLayout.render(rctx)
    }
}

// ── Временный виджет-заглушка для сегмента ────────────────────────────────

/**
 * Простая панель с фоном, рамкой и меткой.
 * Временная заглушка — будет заменена реальным содержимым.
 */
private class PanelWidget(
    val label: String,
    val bgColor: Int,
    val borderColor: Int,
    val textColor: Int
) : BbfWidget() {

    override fun tick(ctx: RenderContext) {
        tickAll(ctx.delta)
    }

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
