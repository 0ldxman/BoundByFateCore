package omc.boundbyfate.client.gui.components

/**
 * Компонент drag-and-drop — источник перетаскивания.
 *
 * ## Использование
 *
 * ```kotlin
 * class FeatureSlot(val featureId: Identifier) : AnimOwner() {
 *     val drag = Draggable { featureId }  // данные которые тащим
 *
 *     fun handleMouseDrag(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean =
 *         drag.handleDrag(mouseX.toInt(), mouseY.toInt(), button, hover.isHovered)
 * }
 * ```
 */
class Draggable(
    private val dataProvider: () -> Any?
) {
    var isDragging = false
        private set

    var dragStartX = 0
        private set
    var dragStartY = 0
        private set

    var currentDragX = 0
        private set
    var currentDragY = 0
        private set

    private val startCallbacks = mutableListOf<(data: Any?) -> Unit>()
    private val dragCallbacks  = mutableListOf<(x: Int, y: Int) -> Unit>()
    private val endCallbacks   = mutableListOf<(dropped: Boolean) -> Unit>()

    fun onDragStart(block: (data: Any?) -> Unit) { startCallbacks += block }
    fun onDrag(block: (x: Int, y: Int) -> Unit)  { dragCallbacks  += block }
    fun onDragEnd(block: (dropped: Boolean) -> Unit) { endCallbacks += block }

    fun handleDrag(mouseX: Int, mouseY: Int, button: Int, isHovered: Boolean): Boolean {
        if (button != 0) return false

        if (!isDragging && isHovered) {
            isDragging = true
            dragStartX = mouseX
            dragStartY = mouseY
            val data = dataProvider()
            DragDropManager.startDrag(data, this)
            startCallbacks.forEach { it(data) }
            return true
        }

        if (isDragging) {
            currentDragX = mouseX
            currentDragY = mouseY
            dragCallbacks.forEach { it(mouseX, mouseY) }
            return true
        }

        return false
    }

    fun handleRelease(dropped: Boolean) {
        if (!isDragging) return
        isDragging = false
        endCallbacks.forEach { it(dropped) }
        DragDropManager.endDrag()
    }
}

/**
 * Компонент drag-and-drop — цель сброса.
 */
class Droppable(
    private val accepts: (data: Any?) -> Boolean
) {
    var isDragOver = false
        private set

    private val dropCallbacks     = mutableListOf<(data: Any?) -> Unit>()
    private val dragOverCallbacks = mutableListOf<() -> Unit>()
    private val dragLeaveCallbacks = mutableListOf<() -> Unit>()

    fun onDrop(block: (data: Any?) -> Unit)  { dropCallbacks      += block }
    fun onDragOver(block: () -> Unit)        { dragOverCallbacks  += block }
    fun onDragLeave(block: () -> Unit)       { dragLeaveCallbacks += block }

    fun update(isHovered: Boolean) {
        val wasDragOver = isDragOver
        isDragOver = isHovered && DragDropManager.isDragging && accepts(DragDropManager.currentData)

        if (isDragOver && !wasDragOver) dragOverCallbacks.forEach { it() }
        if (!isDragOver && wasDragOver) dragLeaveCallbacks.forEach { it() }
    }

    fun handleDrop(): Boolean {
        if (!isDragOver) return false
        val data = DragDropManager.currentData
        if (!accepts(data)) return false
        dropCallbacks.forEach { it(data) }
        return true
    }
}

/**
 * Глобальный менеджер drag-and-drop состояния.
 */
object DragDropManager {
    var isDragging = false
        private set

    var currentData: Any? = null
        private set

    var currentSource: Draggable? = null
        private set

    /** Позиция курсора во время перетаскивания. */
    var dragX = 0
    var dragY = 0

    fun startDrag(data: Any?, source: Draggable) {
        isDragging = true
        currentData = data
        currentSource = source
    }

    fun endDrag() {
        isDragging = false
        currentData = null
        currentSource = null
    }
}
