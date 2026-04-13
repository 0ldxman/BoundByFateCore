package omc.boundbyfate.client.gui

import net.minecraft.client.gui.DrawContext

/**
 * Рендерит окна и панели используя 9-slice технику из атласа.
 * 
 * 9-slice разбивает изображение на 9 частей:
 * - 4 угла (не растягиваются)
 * - 4 стены (растягиваются в одном направлении)
 * - 1 фон (тайлится)
 */
object NineSliceRenderer {
    
    /**
     * Рисует окно с использованием 9-slice.
     * 
     * @param context Контекст рисования
     * @param x X позиция окна
     * @param y Y позиция окна
     * @param width Ширина окна
     * @param height Высота окна
     * @param cornerSize Размер углов (по умолчанию 5)
     */
    fun drawWindow(
        context: DrawContext,
        x: Int, y: Int,
        width: Int, height: Int,
        cornerSize: Int = 5
    ) {
        // 1. Углы (не растягиваются)
        GuiAtlas.WINDOW_CORNER_TL.draw(context, x, y)
        GuiAtlas.WINDOW_CORNER_TR.draw(context, x + width - cornerSize, y)
        GuiAtlas.WINDOW_CORNER_BL.draw(context, x, y + height - cornerSize)
        GuiAtlas.WINDOW_CORNER_BR.draw(context, x + width - cornerSize, y + height - cornerSize)
        
        // 2. Стены (растягиваются)
        // Верхняя стена
        drawTiledHorizontal(
            context,
            GuiAtlas.WINDOW_EDGE_TOP,
            x + cornerSize,
            y,
            width - cornerSize * 2
        )
        
        // Нижняя стена
        drawTiledHorizontal(
            context,
            GuiAtlas.WINDOW_EDGE_BOTTOM,
            x + cornerSize,
            y + height - cornerSize,
            width - cornerSize * 2
        )
        
        // Левая стена
        drawTiledVertical(
            context,
            GuiAtlas.WINDOW_EDGE_LEFT,
            x,
            y + cornerSize,
            height - cornerSize * 2
        )
        
        // Правая стена
        drawTiledVertical(
            context,
            GuiAtlas.WINDOW_EDGE_RIGHT,
            x + width - cornerSize,
            y + cornerSize,
            height - cornerSize * 2
        )
        
        // 3. Фон (тайлится)
        drawTiled(
            context,
            GuiAtlas.WINDOW_BACKGROUND,
            x + cornerSize,
            y + cornerSize,
            width - cornerSize * 2,
            height - cornerSize * 2
        )
    }
    
    /**
     * Рисует баннер/хедер.
     * 
     * @param context Контекст рисования
     * @param x X позиция
     * @param y Y позиция
     * @param width Ширина баннера
     * @param withHighlight Рисовать ли хайлайт
     */
    fun drawHeader(
        context: DrawContext,
        x: Int, y: Int,
        width: Int,
        withHighlight: Boolean = false
    ) {
        val leftWidth = GuiAtlas.HEADER_LEFT.width
        val rightWidth = GuiAtlas.HEADER_RIGHT.width
        val tileWidth = GuiAtlas.HEADER_TILE.width
        
        // Левый конец
        GuiAtlas.HEADER_LEFT.draw(context, x, y)
        
        // Тайлы в центре
        val centerWidth = width - leftWidth - rightWidth
        var currentX = x + leftWidth
        
        while (currentX < x + leftWidth + centerWidth) {
            val remainingWidth = (x + leftWidth + centerWidth) - currentX
            val drawWidth = minOf(tileWidth, remainingWidth)
            
            GuiAtlas.HEADER_TILE.draw(context, currentX, y, drawWidth, GuiAtlas.HEADER_TILE.height)
            currentX += tileWidth
        }
        
        // Правый конец
        GuiAtlas.HEADER_RIGHT.draw(context, x + width - rightWidth, y)
        
        // Хайлайт (опционально)
        if (withHighlight) {
            val highlightX = x + (width - GuiAtlas.HEADER_HIGHLIGHT.width) / 2
            val highlightY = y + (GuiAtlas.HEADER_LEFT.height - GuiAtlas.HEADER_HIGHLIGHT.height) / 2
            GuiAtlas.HEADER_HIGHLIGHT.draw(context, highlightX, highlightY)
        }
    }
    
    // ═══ HELPER METHODS ═══
    
    /**
     * Тайлит текстуру горизонтально.
     */
    private fun drawTiledHorizontal(
        context: DrawContext,
        region: GuiAtlas.AtlasRegion,
        x: Int, y: Int,
        width: Int
    ) {
        var currentX = x
        val tileWidth = region.width
        
        while (currentX < x + width) {
            val remainingWidth = (x + width) - currentX
            val drawWidth = minOf(tileWidth, remainingWidth)
            
            region.drawPartial(context, currentX, y, 0, 0, drawWidth, region.height)
            currentX += tileWidth
        }
    }
    
    /**
     * Тайлит текстуру вертикально.
     */
    private fun drawTiledVertical(
        context: DrawContext,
        region: GuiAtlas.AtlasRegion,
        x: Int, y: Int,
        height: Int
    ) {
        var currentY = y
        val tileHeight = region.height
        
        while (currentY < y + height) {
            val remainingHeight = (y + height) - currentY
            val drawHeight = minOf(tileHeight, remainingHeight)
            
            region.drawPartial(context, x, currentY, 0, 0, region.width, drawHeight)
            currentY += tileHeight
        }
    }
    
    /**
     * Тайлит текстуру в обоих направлениях.
     */
    private fun drawTiled(
        context: DrawContext,
        region: GuiAtlas.AtlasRegion,
        x: Int, y: Int,
        width: Int, height: Int
    ) {
        var currentY = y
        val tileWidth = region.width
        val tileHeight = region.height
        
        while (currentY < y + height) {
            var currentX = x
            val remainingHeight = (y + height) - currentY
            val drawHeight = minOf(tileHeight, remainingHeight)
            
            while (currentX < x + width) {
                val remainingWidth = (x + width) - currentX
                val drawWidth = minOf(tileWidth, remainingWidth)
                
                region.drawPartial(context, currentX, currentY, 0, 0, drawWidth, drawHeight)
                currentX += tileWidth
            }
            
            currentY += tileHeight
        }
    }
}
