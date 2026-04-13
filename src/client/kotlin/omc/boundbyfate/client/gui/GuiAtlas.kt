package omc.boundbyfate.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier

/**
 * Атлас GUI элементов для BoundByFate.
 * 
 * Содержит все элементы интерфейса в одной текстуре.
 */
object GuiAtlas {
    val TEXTURE = Identifier("boundbyfate-core", "textures/gui/atlas.png")
    
    // Размер атласа
    const val ATLAS_WIDTH = 512
    const val ATLAS_HEIGHT = 512
    
    // ═══ ОКНА (9-SLICE) - 5x5 пикселей ═══
    
    val WINDOW_CORNER_TL = AtlasRegion(0, 0, 5, 5)      // Верхний левый угол
    val WINDOW_CORNER_TR = AtlasRegion(6, 0, 5, 5)      // Верхний правый угол
    val WINDOW_CORNER_BL = AtlasRegion(0, 6, 5, 5)      // Нижний левый угол
    val WINDOW_CORNER_BR = AtlasRegion(6, 6, 5, 5)      // Нижний правый угол
    
    val WINDOW_EDGE_TOP = AtlasRegion(12, 0, 5, 5)      // Верхняя стенка
    val WINDOW_EDGE_BOTTOM = AtlasRegion(18, 0, 5, 5)   // Нижняя стенка
    val WINDOW_EDGE_LEFT = AtlasRegion(12, 6, 5, 5)     // Левая стенка
    val WINDOW_EDGE_RIGHT = AtlasRegion(18, 6, 5, 5)    // Правая стенка
    
    val WINDOW_BACKGROUND = AtlasRegion(24, 0, 5, 5)    // Фон окна
    
    // ═══ ХЕДЕРЫ (БАННЕРЫ) ═══
    
    val HEADER_LEFT = AtlasRegion(0, 415, 66, 97)       // Левый конец баннера
    val HEADER_RIGHT = AtlasRegion(67, 415, 66, 97)     // Правый конец баннера
    val HEADER_HIGHLIGHT = AtlasRegion(138, 459, 152, 53) // Хайлайт
    val HEADER_TILE = AtlasRegion(291, 459, 53, 53)     // Тайл (повторяемый)
    
    // ═══ ИКОНКИ ═══
    
    val ICON_STAT_BG = AtlasRegion(403, 340, 109, 172)  // Фон характеристик
    val ICON_HP_BG = AtlasRegion(388, 197, 124, 136)    // Фон ХП
    val ICON_SKILL_BG = AtlasRegion(488, 0, 24, 24)     // Фон навыков
    val ICON_SAVE_BG = AtlasRegion(445, 0, 27, 27)      // Фон спасбросков
    
    val ICON_PROFICIENCY = AtlasRegion(473, 0, 14, 14)  // Иконка владения
    val ICON_SAVE_SUCCESS = AtlasRegion(426, 0, 15, 15) // Сердечко (успех)
    val ICON_SAVE_FAILURE = AtlasRegion(410, 0, 15, 16) // Черепок (провал)
    
    /**
     * Регион в атласе.
     */
    data class AtlasRegion(
        val u: Int,      // X координата в атласе
        val v: Int,      // Y координата в атласе
        val width: Int,  // Ширина региона
        val height: Int  // Высота региона
    ) {
        /**
         * Рисует регион на экране.
         * 
         * @param context Контекст рисования
         * @param x X позиция на экране
         * @param y Y позиция на экране
         * @param drawWidth Ширина на экране (по умолчанию = width)
         * @param drawHeight Высота на экране (по умолчанию = height)
         */
        fun draw(
            context: DrawContext,
            x: Int, y: Int,
            drawWidth: Int = width,
            drawHeight: Int = height
        ) {
            context.drawTexture(
                TEXTURE,
                x, y,                    // Позиция на экране
                u, v,                    // Позиция в атласе
                drawWidth, drawHeight,   // Размер на экране
                ATLAS_WIDTH, ATLAS_HEIGHT // Размер атласа
            )
        }
        
        /**
         * Рисует регион с UV координатами (для частичного рендера).
         * 
         * @param context Контекст рисования
         * @param x X позиция на экране
         * @param y Y позиция на экране
         * @param uOffset Смещение U в регионе
         * @param vOffset Смещение V в регионе
         * @param drawWidth Ширина на экране
         * @param drawHeight Высота на экране
         */
        fun drawPartial(
            context: DrawContext,
            x: Int, y: Int,
            uOffset: Int, vOffset: Int,
            drawWidth: Int, drawHeight: Int
        ) {
            context.drawTexture(
                TEXTURE,
                x, y,
                u + uOffset, v + vOffset,
                drawWidth, drawHeight,
                ATLAS_WIDTH, ATLAS_HEIGHT
            )
        }
        
        /**
         * Рисует регион с альфа-каналом.
         */
        fun drawWithAlpha(
            context: DrawContext,
            x: Int, y: Int,
            alpha: Float,
            drawWidth: Int = width,
            drawHeight: Int = height
        ) {
            // TODO: Implement alpha rendering if needed
            draw(context, x, y, drawWidth, drawHeight)
        }
    }
}
