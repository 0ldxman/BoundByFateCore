package omc.boundbyfate.client.gui.core

/**
 * Базовый класс самостоятельного виджета.
 *
 * Самостоятельный виджет — тот что знает свои координаты и может жить в списке.
 * Наследует [AnimOwner] — все [AnimState] созданные через фабричные методы
 * тикаются автоматически через [tickAll].
 *
 * Компоненты поведения ([Hoverable], [Clickable], [Scrollable] и т.д.) добавляются
 * как поля — виджет берёт только то что нужно. Экран пробрасывает события
 * напрямую через эти компоненты.
 *
 * Анимации появления/исчезновения — ответственность экрана, не виджета.
 * Виджет отвечает только за постоянное интерактивное состояние.
 *
 * ## Создание виджета
 *
 * ```kotlin
 * class StatShieldWidget(val stat: StatDef) : BbfWidget() {
 *     val hover = Hoverable()
 *     val click = Clickable()
 *
 *     val scale = animFloat(1f, speed = 0.15f)
 *     val tiltX = animFloat(0f, speed = 0.15f)
 *     val tiltY = animFloat(0f, speed = 0.15f)
 *
 *     // Экран пишет сюда смещение въезда
 *     var introOffsetX = 0f
 *     var introOffsetY = 0f
 *
 *     override fun tick(ctx: RenderContext) {
 *         hover.update(ctx)
 *         scale.target = if (hover.isHovered) 1.25f else 1f
 *         tiltX.target = if (hover.isHovered) hover.normalizedX else 0f
 *         tiltY.target = if (hover.isHovered) hover.normalizedY else 0f
 *         tickAll(ctx.delta)  // всегда в конце
 *     }
 *
 *     override fun render(ctx: RenderContext) {
 *         ctx.drawContext.transform(
 *             pivotX = ctx.cx.toFloat(),
 *             pivotY = ctx.cy.toFloat(),
 *             scale = scale.current,
 *             offsetX = introOffsetX,
 *             offsetY = introOffsetY
 *         ) {
 *             // рисуем щит
 *         }
 *     }
 * }
 * ```
 *
 * ## Использование в экране
 *
 * ```kotlin
 * class CharacterScreen : BbfScreen() {
 *     private val shields = Stats.ALL.map { StatShieldWidget(it) }
 *
 *     // Анимация появления — на экране, не в виджете
 *     private val openAnim = AnimSequence(Easing.EASE_OUT)
 *         .then(0.5f) { p ->
 *             shields.forEachIndexed { i, shield ->
 *                 val shieldP = ((p - i * 0.07f) / (1f - i * 0.07f)).coerceIn(0f, 1f)
 *                 shield.introOffsetX = slideStartX[i] * (1f - Easing.EASE_OUT(shieldP))
 *                 shield.introOffsetY = slideStartY[i] * (1f - Easing.EASE_OUT(shieldP))
 *             }
 *         }
 *
 *     override fun onInit() {
 *         openAnim.reset()
 *         shields.forEachIndexed { i, shield ->
 *             shield.introOffsetX = slideStartX[i]
 *             shield.introOffsetY = slideStartY[i]
 *         }
 *     }
 *
 *     override fun renderContent(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
 *         openAnim.tick(delta)
 *         val rctx = RenderContext(ctx, 0, 0, width, height, mouseX, mouseY, delta)
 *         shields.forEach { shield ->
 *             val childCtx = rctx.child(offsetX = ..., offsetY = ..., w = shieldW, h = shieldH)
 *             shield.tick(childCtx)
 *             shield.render(childCtx)
 *         }
 *     }
 * }
 * ```
 */
abstract class BbfWidget : AnimOwner() {

    /**
     * Тикает виджет — обновляет компоненты поведения, целевые значения анимаций.
     * Вызывай [tickAll] в конце своей реализации.
     */
    abstract fun tick(ctx: RenderContext)

    /**
     * Рисует виджет. Вызывается после [tick] каждый кадр.
     * Читай только [AnimState.current] — не меняй состояние здесь.
     */
    abstract fun render(ctx: RenderContext)
}
