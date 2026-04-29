package omc.boundbyfate.client.gui.core

import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

/**
 * Звуковая тема UI.
 *
 * Компоненты ([Hoverable], [Clickable]) автоматически играют звуки из активной темы.
 * Экран может переопределить тему через [BbfScreen.soundTheme].
 *
 * ## Создание кастомной темы
 *
 * ```kotlin
 * val MYSTICAL = UiSoundTheme(
 *     hover = bbfSound("ui.mystical.hover"),
 *     click = bbfSound("ui.mystical.click"),
 *     open  = bbfSound("ui.mystical.open")
 * )
 * ```
 */
open class UiSoundTheme(
    val hover:        SoundEvent? = bbfSound("ui.hover"),
    val hoverEnd:     SoundEvent? = null,
    val click:        SoundEvent? = bbfSound("ui.click"),
    val open:         SoundEvent? = bbfSound("ui.screen.open"),
    val close:        SoundEvent? = bbfSound("ui.screen.close"),
    val overlayOpen:  SoundEvent? = bbfSound("ui.overlay.open"),
    val overlayClose: SoundEvent? = bbfSound("ui.overlay.close"),
    val error:        SoundEvent? = bbfSound("ui.error"),
    val success:      SoundEvent? = bbfSound("ui.success"),
    val notify:       SoundEvent? = bbfSound("ui.notification"),
    val typeChar:     SoundEvent? = null,
    val scroll:       SoundEvent? = null
)

/**
 * Центральный объект управления звуком UI.
 */
object UiSounds {
    /** Активная тема — устанавливается экраном при открытии. */
    var current: UiSoundTheme = DEFAULT

    val DEFAULT = UiSoundTheme()

    val MYSTICAL = UiSoundTheme(
        hover        = bbfSound("ui.mystical.hover"),
        click        = bbfSound("ui.mystical.click"),
        open         = bbfSound("ui.mystical.open"),
        close        = bbfSound("ui.mystical.close"),
        overlayOpen  = bbfSound("ui.mystical.overlay_open"),
        overlayClose = bbfSound("ui.mystical.overlay_close")
    )

    val SILENT = UiSoundTheme(
        hover = null, click = null, open = null, close = null,
        overlayOpen = null, overlayClose = null
    )
}

/**
 * Играет звук UI у клиента (без позиции в мире).
 */
fun SoundEvent.playUi(volume: Float = 1f, pitch: Float = 1f) {
    MinecraftClient.getInstance().soundManager.play(
        PositionedSoundInstance.master(this, pitch, volume)
    )
}

/** Создаёт SoundEvent с namespace boundbyfate-core. */
fun bbfSound(path: String): SoundEvent =
    SoundEvent.of(Identifier("boundbyfate-core", path))
