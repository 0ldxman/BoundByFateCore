package omc.boundbyfate.client.util

/**
 * Определяет находимся ли мы в shadow rendering pass.
 *
 * Поддерживает Iris (опциональный мод) через reflection — без жёсткой зависимости.
 * Если Iris не установлен — всегда возвращает false (корректное поведение).
 *
 * При shadow pass анимации не должны тикать (dt = 0),
 * иначе они ускоряются вдвое (основной pass + shadow pass).
 */
object ShadowRenderingDetector {

    /**
     * Метод `isShadowPass()` из Iris, найденный через reflection при первом вызове.
     * null если Iris не установлен или API изменилось.
     */
    private val irisMethod: java.lang.reflect.Method? by lazy {
        try {
            val clazz = Class.forName("net.irisshaders.iris.shadows.ShadowRenderer")
            clazz.getMethod("isShadowPass")
        } catch (_: ClassNotFoundException) {
            null // Iris не установлен
        } catch (_: NoSuchMethodException) {
            null // API изменилось
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Возвращает true если сейчас выполняется shadow rendering pass.
     *
     * Без Iris всегда false.
     * С Iris — делегирует в `ShadowRenderer.isShadowPass()`.
     */
    fun isShadowRendering(): Boolean {
        return try {
            irisMethod?.invoke(null) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }
}
