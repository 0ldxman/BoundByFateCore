package omc.boundbyfate.client.gui.screens

import net.minecraft.client.gui.DrawContext
import omc.boundbyfate.client.gui.core.*

/**
 * Экран создания/редактирования персонажа.
 *
 * Размеры рассчитаны под GUI Scale 3.
 *
 * Структура колонок:
 *   Левая  (L): 218x343, сегменты: L1=218x67, L2=218x135, L3=218x135
 *   Центр  (C): 194x343, сегменты: C1=194x67, C2=194x135, C3=194x135
 *   Правая (R): 194x343, сегменты: R1=194x67, R2=194x135, R3=194x135
 *
 * Отступы между колонками и между сегментами — 2px.
 */
class CharacterEditScreen : BbfScreen("screen.bbf.character_edit") {

    // ── Константы макета ──────────────────────────────────────────────────

    private val COL_GAP   = 2   // отступ между колонками
    private val SEG_GAP   = 2   // отступ между сегментами внутри колонки

    private val LEFT_W    = 218
    private val CENTER_W  = 194
    private val RIGHT_W   = 194

    private val TOTAL_W   = LEFT_W + COL_GAP + CENTER_W + COL_GAP + RIGHT_W  // 610
    private val TOTAL_H   = 343

    // Высоты сегментов (одинаковы для всех колонок)
    private val SEG1_H    = 67
    private val SEG2_H    = 135
    private val SEG3_H    = 135
    // SEG1_H + SEG_GAP + SEG2_H + SEG_GAP + SEG3_H = 67+2+135+2+135 = 341 ≠ 343
    // Добавляем 2px к последнему сегменту чтобы точно заполнить 343
    private val SEG3_H_ADJ = TOTAL_H - SEG1_H - SEG_GAP - SEG2_H - SEG_GAP  // 135

    // ── Цвета блоков (временные, для визуальной отладки) ──────────────────

    private val BG_PANEL   = 0xEE141420.toInt()
    private val BG_SEG     = 0xEE1e1e2e.toInt()
    private val BORDER_COL = 0xFF3a3a5a.toInt()
    private val TEXT_LABEL = 0xFF888899.toInt()

    // ── Рендер ────────────────────────────────────────────────────────────

    override fun renderContent(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Центрируем весь макет на экране
        val originX = (width  - TOTAL_W) / 2
        val originY = (height - TOTAL_H) / 2

        // X-позиции колонок
        val leftX   = originX
        val centerX = originX + LEFT_W + COL_GAP
        val rightX  = centerX + CENTER_W + COL_GAP

        // Фон всего окна
        ctx.fillRect(originX, originY, TOTAL_W, TOTAL_H, BG_PANEL)

        // ── Левая колонка ─────────────────────────────────────────────────
        drawSegment(ctx, "L1", leftX, originY,                                    LEFT_W, SEG1_H)
        drawSegment(ctx, "L2", leftX, originY + SEG1_H + SEG_GAP,                LEFT_W, SEG2_H)
        drawSegment(ctx, "L3", leftX, originY + SEG1_H + SEG_GAP + SEG2_H + SEG_GAP, LEFT_W, SEG3_H_ADJ)

        // ── Центральная колонка ───────────────────────────────────────────
        drawSegment(ctx, "C1", centerX, originY,                                    CENTER_W, SEG1_H)
        drawSegment(ctx, "C2", centerX, originY + SEG1_H + SEG_GAP,                CENTER_W, SEG2_H)
        drawSegment(ctx, "C3", centerX, originY + SEG1_H + SEG_GAP + SEG2_H + SEG_GAP, CENTER_W, SEG3_H_ADJ)

        // ── Правая колонка ────────────────────────────────────────────────
        drawSegment(ctx, "R1", rightX, originY,                                    RIGHT_W, SEG1_H)
        drawSegment(ctx, "R2", rightX, originY + SEG1_H + SEG_GAP,                RIGHT_W, SEG2_H)
        drawSegment(ctx, "R3", rightX, originY + SEG1_H + SEG_GAP + SEG2_H + SEG_GAP, RIGHT_W, SEG3_H_ADJ)
    }

    /**
     * Рисует один сегмент с фоном, рамкой и меткой-номером в углу.
     */
    private fun drawSegment(ctx: DrawContext, label: String, x: Int, y: Int, w: Int, h: Int) {
        ctx.fillRectWithBorder(x, y, w, h, bg = BG_SEG, border = BORDER_COL, thickness = 1)
        // Метка в левом верхнем углу для ориентации
        ctx.drawScaledText(label, x + 3, y + 3, color = TEXT_LABEL, shadow = false)
    }
}
