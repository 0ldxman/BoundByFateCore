package omc.boundbyfate.system.visual.sound

/**
 * Глобальное состояние музыкальной системы на сервере.
 *
 * Хранит три слота треков и текущую позицию ползунка.
 * Позиция ползунка — барицентрические координаты треугольника:
 * [u] + [v] + [w] = 1.0
 *
 * Визуально:
 * ```
 *       слот B (0,1,0)
 *          /\
 *         /  \
 *        / u,v,w\
 *       /________\
 * слот A         слот C
 * (1,0,0)       (0,0,1)
 * ```
 *
 * Громкость трека = его координата × громкость музыки в настройках Minecraft.
 *
 * @param trackA ID трека в слоте A (null = слот пуст)
 * @param trackB ID трека в слоте B (null = слот пуст)
 * @param trackC ID трека в слоте C (null = слот пуст)
 * @param u координата слота A (0.0 - 1.0)
 * @param v координата слота B (0.0 - 1.0)
 * @param w координата слота C (0.0 - 1.0), всегда = 1 - u - v
 * @param sliderSpeed максимальная скорость ползунка (единиц в секунду, 0.0-1.0 шкала)
 */
data class MusicState(
    val trackA: String? = null,
    val trackB: String? = null,
    val trackC: String? = null,
    val u: Float = 1f / 3f,
    val v: Float = 1f / 3f,
    val w: Float = 1f / 3f,
    val sliderSpeed: Float = DEFAULT_SLIDER_SPEED
) {
    companion object {
        /** Скорость ползунка по умолчанию — проходит весь треугольник за ~2 секунды. */
        const val DEFAULT_SLIDER_SPEED = 0.5f
    }

    /** Громкость трека A (0.0 - 1.0). */
    val volumeA: Float get() = if (trackA != null) u else 0f

    /** Громкость трека B (0.0 - 1.0). */
    val volumeB: Float get() = if (trackB != null) v else 0f

    /** Громкость трека C (0.0 - 1.0). */
    val volumeC: Float get() = if (trackC != null) w else 0f

    /** Есть ли хоть один трек. */
    val hasAnyTrack: Boolean get() = trackA != null || trackB != null || trackC != null

    /**
     * Возвращает новое состояние с обновлённой позицией ползунка.
     * Нормализует координаты чтобы u + v + w = 1.0.
     */
    fun withPosition(u: Float, v: Float): MusicState {
        val clampedU = u.coerceIn(0f, 1f)
        val clampedV = v.coerceIn(0f, 1f - clampedU)
        val clampedW = 1f - clampedU - clampedV
        return copy(u = clampedU, v = clampedV, w = clampedW)
    }

    /**
     * Возвращает новое состояние с треком в слоте A.
     */
    fun withTrackA(trackId: String?) = copy(trackA = trackId)

    /**
     * Возвращает новое состояние с треком в слоте B.
     */
    fun withTrackB(trackId: String?) = copy(trackB = trackId)

    /**
     * Возвращает новое состояние с треком в слоте C.
     */
    fun withTrackC(trackId: String?) = copy(trackC = trackId)
}
