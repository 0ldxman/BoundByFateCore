package omc.boundbyfate.client.gui.widgets

import omc.boundbyfate.client.gui.components.Hoverable
import omc.boundbyfate.client.gui.core.*

/**
 * Абстрактный базовый виджет для отображения 3D объектов в GUI.
 *
 * Содержит общую логику:
 * - Углы поворота и масштаб с анимацией
 * - Прозрачность
 * - Hover-анимация масштаба
 * - Drag-вращение мышью
 *
 * Дочерние классы реализуют только [render3D] — сам рендер объекта.
 *
 * ## Реализации
 * - [EntityViewWidget] — рендер LivingEntity / NpcEntity
 * - [ItemViewWidget]   — рендер ItemStack
 */
abstract class View3DWidget(
    initialScale: Float = 1f,
    initialRotationY: Float = 0f,
    var rotationX: Float = 0f,
    var rotationZ: Float = 0f,
    var draggable: Boolean = true
) : BbfWidget() {

    // ── Параметры ─────────────────────────────────────────────────────────

    var scale: Float = initialScale
        set(v) { field = v; scaleAnim.target = v }

    var rotationY: Float = initialRotationY
        set(v) { field = v; if (!isDragging) rotYAnim.target = v }

    var alpha: Float = 1f

    // ── Анимации ──────────────────────────────────────────────────────────

    protected val scaleAnim  = animFloat(initialScale,    speed = 0.15f)
    protected val rotYAnim   = animFloat(initialRotationY, speed = 0.15f)
    protected val hoverScale = animFloat(1f,               speed = 0.12f)
    val alphaAnim            = animFloat(1f,               speed = 0.12f)

    // ── Поведение ─────────────────────────────────────────────────────────

    protected val hover = Hoverable(playSoundOnEnter = false)

    // ── Drag ──────────────────────────────────────────────────────────────

    private var isDragging    = false
    private var dragStartX    = 0f
    private var dragStartRotY = initialRotationY

    // ── Tick ──────────────────────────────────────────────────────────────

    override fun tick(ctx: RenderContext) {
        hover.update(ctx)
        hoverScale.target = if (hover.isHovered) 1.05f else 1f
        alphaAnim.target  = alpha
        tickAll(ctx.delta)
    }

    // ── Render ────────────────────────────────────────────────────────────

    final override fun render(ctx: RenderContext) {
        if (alphaAnim.current < 0.005f) return
        render3D(ctx, scaleAnim.current * hoverScale.current, rotYAnim.current, alphaAnim.current)
    }

    /**
     * Рисует 3D объект.
     *
     * @param ctx контекст рендера
     * @param effectiveScale итоговый масштаб (scale * hoverScale)
     * @param effectiveRotY итоговый угол поворота по Y
     * @param effectiveAlpha итоговая прозрачность
     */
    protected abstract fun render3D(
        ctx: RenderContext,
        effectiveScale: Float,
        effectiveRotY: Float,
        effectiveAlpha: Float
    )

    // ── Drag API ──────────────────────────────────────────────────────────

    /** Начинает drag-вращение. Вызывается из экрана при mousePressed. */
    fun startDrag(mouseX: Float) {
        if (!draggable) return
        isDragging    = true
        dragStartX    = mouseX
        dragStartRotY = rotYAnim.current
    }

    /** Обновляет вращение во время drag. Вызывается из экрана при mouseDragged. */
    fun updateDrag(mouseX: Float) {
        if (!isDragging || !draggable) return
        val delta = mouseX - dragStartX
        rotYAnim.snap(dragStartRotY + delta * 0.5f)
    }

    /** Завершает drag. Вызывается из экрана при mouseReleased. */
    fun endDrag() { isDragging = false }

    /** Сбрасывает поворот к начальному значению с анимацией. */
    fun resetRotation() { rotYAnim.target = rotationY }

    val isHovered get() = hover.isHovered
    val currentRotY get() = rotYAnim.current
}
