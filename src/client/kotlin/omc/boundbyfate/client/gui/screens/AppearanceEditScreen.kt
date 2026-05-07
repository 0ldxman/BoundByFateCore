package omc.boundbyfate.client.gui.screens

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import omc.boundbyfate.client.character.CharacterDummy
import omc.boundbyfate.client.gui.core.*
import omc.boundbyfate.client.gui.screens.draft.CharacterEditDraft
import omc.boundbyfate.client.gui.widgets.*
import omc.boundbyfate.client.skin.ClientSkinManager
import omc.boundbyfate.component.components.NpcModelComponent
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.data.world.character.ModelType
import omc.boundbyfate.entity.NpcEntity
import omc.boundbyfate.network.packet.s2c.DummyAnimationType
import omc.boundbyfate.registry.NpcEntityRegistry
import java.util.UUID

/**
 * Экран редактирования внешности персонажа.
 *
 * Показывает две 3D модели:
 *   - Слева: NpcEntity с GLTF моделью (НПС-версия персонажа)
 *   - Справа: CharacterDummy с Minecraft скином (игровая версия)
 *
 * Кнопки управления:
 *   - Слева от NPC: Скин НПС, Модель НПС
 *   - Справа от CharacterDummy: Скин, Модель (Стив/Алекс)
 *   - Верхний левый угол: ← Назад
 *
 * Изменения пишутся в [draft] — не сохраняются до нажатия "Сохранить" на основном экране.
 *
 * @param parentScreen экран, на который возвращаемся при нажатии "← Назад"
 */
class AppearanceEditScreen(
    val draft: CharacterEditDraft,
    private val parentScreen: net.minecraft.client.gui.screen.Screen? = null
) : BbfScreen("screen.bbf.appearance_edit") {

    // ── Константы макета ──────────────────────────────────────────────────

    private val PAD        = 8
    private val BTN_H      = 16
    private val BTN_W_RATIO = 0.15f   // ширина кнопок = 15% ширины экрана
    private val MODEL_SCALE = 60f

    // ── Цвета ─────────────────────────────────────────────────────────────

    private val BG_COLOR = 0xEE141420.toInt()

    // ── Entity для превью ─────────────────────────────────────────────────

    /** CharacterDummy — Minecraft-скин персонажа. */
    private var characterDummy: CharacterDummy? = null

    /** NpcEntity — GLTF-модель НПС. */
    private var npcPreview: NpcEntity? = null

    // ── Виджеты ───────────────────────────────────────────────────────────

    private lateinit var dummyView: EntityViewWidget
    private lateinit var npcView: EntityViewWidget

    private lateinit var backBtn: BbfButton

    // Кнопки CharacterDummy (справа)
    private lateinit var skinBtn: BbfButton
    private lateinit var modelBtn: BbfButton

    // Кнопки NPC (слева)
    private lateinit var npcSkinBtn: BbfButton
    private lateinit var npcModelBtn: BbfButton

    // ── Инициализация ─────────────────────────────────────────────────────

    override fun onInit() {
        buildEntities()
        buildWidgets()
    }

    private fun buildEntities() {
        val client = MinecraftClient.getInstance()
        val world  = client.world ?: return

        // CharacterDummy — берём skinId и modelType из драфта
        if (draft.skinId.isNotEmpty()) {
            ClientSkinManager.ensureLoaded(
                draft.skinId,
                if (draft.modelType == ModelType.ALEX) ModelType.ALEX else ModelType.STEVE
            )
        }
        characterDummy = makeDummy()

        // NpcEntity — создаём локально для превью, не добавляем в мир
        npcPreview = NpcEntity(NpcEntityRegistry.NPC, world).also { npc ->
            val modelComp = npc.getOrCreate(NpcModelComponent.TYPE)
            if (draft.npcModelPath.isNotEmpty()) {
                modelComp.modelPath = draft.npcModelPath
            }
            // npcSkinId имеет приоритет; если не задан — используем общий skinId
            val effectiveNpcSkin = draft.npcSkinId.ifEmpty { draft.skinId }
            if (effectiveNpcSkin.isNotEmpty()) {
                modelComp.skinId = effectiveNpcSkin
            }
            org.slf4j.LoggerFactory.getLogger("BbfGui")
                .info("[AppearanceEditScreen] NPC preview created, modelPath=${modelComp.modelPath}, uuid=${npc.uuid}")
        }
    }

    private fun buildWidgets() {
        dummyView = EntityViewWidget(
            entity    = characterDummy,
            scale     = MODEL_SCALE,
            rotationY = 180f,
            followMouse = false,
            draggable = true
        )

        npcView = EntityViewWidget(
            entity    = npcPreview,
            scale     = MODEL_SCALE,
            rotationY = 180f,
            followMouse = false,
            draggable = true
        )

        backBtn = BbfButton("← Назад").also { btn ->
            btn.onClick { close() }
        }

        skinBtn = BbfButton("Скин").also { btn ->
            btn.onClick { /* TODO: открыть диалог выбора скина */ }
        }
        modelBtn = BbfButton(modelLabel()).also { btn ->
            btn.onClick {
                // Переключаем Стив/Алекс
                draft.modelType = if (draft.modelType == ModelType.STEVE) ModelType.ALEX else ModelType.STEVE
                btn.label = modelLabel()
                rebuildDummy()
            }
        }

        npcSkinBtn = BbfButton("Скин НПС").also { btn ->
            btn.onClick { /* TODO: открыть диалог выбора скина НПС → draft.npcSkinId = выбранный ID → rebuildNpcPreview() */ }
        }
        npcModelBtn = BbfButton("Модель НПС").also { btn ->
            btn.onClick { /* TODO: открыть диалог выбора модели НПС → draft.npcModelPath = выбранный путь → rebuildNpcPreview() */ }
        }
    }

    private fun modelLabel() = when (draft.modelType) {
        ModelType.STEVE -> "Модель: Стив"
        ModelType.ALEX  -> "Модель: Алекс"
    }

    /** Пересоздаёт CharacterDummy после смены скина/модели. */
    private fun rebuildDummy() {
        characterDummy = makeDummy()
        dummyView.entity = characterDummy
    }

    /** Создаёт CharacterDummy с правильно инициализированными yaw полями. */
    private fun makeDummy(): CharacterDummy {
        val client = MinecraftClient.getInstance()
        val world  = client.world ?: return characterDummy!!
        return CharacterDummy(
            world,
            draft.characterId ?: UUID.randomUUID(),
            draft.skinId,
            draft.modelType.name.lowercase(),
            DummyAnimationType.STAND_IDLE
        ).also { dummy ->
            // Все yaw поля в 180f — иначе интерполяция от 0 к 180 даёт вращение
            dummy.yaw         = 180f
            dummy.prevYaw     = 180f
            dummy.bodyYaw     = 180f
            dummy.prevBodyYaw = 180f
            dummy.headYaw     = 180f
        }
    }

    // ── Рендер ────────────────────────────────────────────────────────────

    override fun renderContent(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Фон
        ctx.fillRect(0, 0, width, height, BG_COLOR)

        val btnW   = (width * BTN_W_RATIO).toInt()
        val centerX = width / 2

        // Пропорции: каждая модель занимает ~25% ширины экрана
        val modelW  = (width * 0.25f).toInt()
        val modelH  = (height * 0.80f).toInt()
        val modelY  = (height * 0.10f).toInt()

        // NPC — левее центра
        val npcX    = centerX - modelW - PAD / 2
        // Dummy — правее центра
        val dummyX  = centerX + PAD / 2

        val rctx = RenderContext(ctx, 0, 0, width, height, mouseX, mouseY, delta)

        // ── Кнопка Назад ──────────────────────────────────────────────────
        val backCtx = rctx.child(PAD, PAD, btnW, BTN_H)
        backBtn.tick(backCtx); backBtn.render(backCtx)

        // ── NPC модель ────────────────────────────────────────────────────
        val npcCtx = rctx.child(npcX, modelY, modelW, modelH)
        org.slf4j.LoggerFactory.getLogger("BbfGui")
            .info("[AppearanceEditScreen] npcView.entity=${npcView.entity?.javaClass?.simpleName}, npcCtx=($npcX,$modelY,${modelW}x${modelH})")
        npcView.tick(npcCtx); npcView.render(npcCtx)

        // ── CharacterDummy ────────────────────────────────────────────────
        val dummyCtx = rctx.child(dummyX, modelY, modelW, modelH)
        dummyView.tick(dummyCtx); dummyView.render(dummyCtx)

        // ── Кнопки NPC (слева от NPC модели) ─────────────────────────────
        val npcBtnX = npcX - btnW - PAD
        val btnMidY = modelY + modelH / 2 - BTN_H - PAD / 2

        val npcSkinCtx  = rctx.child(npcBtnX, btnMidY,           btnW, BTN_H)
        val npcModelCtx = rctx.child(npcBtnX, btnMidY + BTN_H + PAD, btnW, BTN_H)
        npcSkinBtn.tick(npcSkinCtx);   npcSkinBtn.render(npcSkinCtx)
        npcModelBtn.tick(npcModelCtx); npcModelBtn.render(npcModelCtx)

        // ── Кнопки Dummy (справа от Dummy модели) ─────────────────────────
        val dummyBtnX = dummyX + modelW + PAD

        val skinCtx  = rctx.child(dummyBtnX, btnMidY,           btnW, BTN_H)
        val modelCtx = rctx.child(dummyBtnX, btnMidY + BTN_H + PAD, btnW, BTN_H)
        skinBtn.tick(skinCtx);   skinBtn.render(skinCtx)
        modelBtn.tick(modelCtx); modelBtn.render(modelCtx)
    }

    // ── Навигация ─────────────────────────────────────────────────────────

    /** Возвращает на родительский экран (или закрывает всё если его нет). */
    override fun close() {
        MinecraftClient.getInstance().setScreen(parentScreen)
    }

    // ── Ввод ──────────────────────────────────────────────────────────────

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toFloat()
        if (button == 0) {
            if (dummyView.isHovered) { dummyView.startDrag(mx); return true }
            if (npcView.isHovered)   { npcView.startDrag(mx);   return true }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean {
        dummyView.updateDrag(mouseX.toFloat())
        npcView.updateDrag(mouseX.toFloat())
        return super.mouseDragged(mouseX, mouseY, button, dx, dy)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        dummyView.endDrag()
        npcView.endDrag()
        return super.mouseReleased(mouseX, mouseY, button)
    }
}
