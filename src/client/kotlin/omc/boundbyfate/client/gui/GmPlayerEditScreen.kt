package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.client.state.ClientGmRegistry
import omc.boundbyfate.client.state.GmPlayerSnapshot
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.registry.BbfStats

class GmPlayerEditScreen(private val snapshot: GmPlayerSnapshot) :
    Screen(Text.literal("GM: ${snapshot.playerName}")) {

    // ── Editable state ────────────────────────────────────────────────────────
    private val stats = mutableMapOf<Identifier, Int>().also { m ->
        listOf(BbfStats.STRENGTH, BbfStats.DEXTERITY, BbfStats.CONSTITUTION,
               BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA).forEach { s ->
            m[s.id] = snapshot.statsData?.getStatValue(s.id)?.total ?: 10
        }
    }
    private var classId: Identifier? = snapshot.classData?.classId
    private var subclassId: Identifier? = snapshot.classData?.subclassId
    private var level: Int = snapshot.level
    private var experience: Int = snapshot.experience
    private var raceId: Identifier? = snapshot.raceData?.raceId
    private var gender: String = snapshot.gender ?: "male"
    private var alignment: String = snapshot.alignment
    private var profBonus: Int = when { level >= 17 -> 6; level >= 13 -> 5; level >= 9 -> 4; level >= 5 -> 3; else -> 2 }
    private var currentHp: Float = snapshot.currentHp
    private var maxHp: Float = snapshot.maxHp
    private val skills = mutableMapOf<Identifier, Int>().also { m ->
        snapshot.skillData?.proficiencies?.forEach { (id, lv) -> m[id] = lv }
    }
    private val features = mutableListOf<Identifier>().also { it.addAll(snapshot.grantedFeatures) }

    // ── UI state ──────────────────────────────────────────────────────────────
    private var statusMsg = ""; private var statusTimer = 0f
    private var classDropOpen = false; private var subDropOpen = false
    private var raceDropOpen = false; private var alignDropOpen = false
    private var featDropOpen = false; private var skillScroll = 0
    private var selectedFeat: Identifier? = null

    private data class Btn(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, val action: () -> Unit)
    private val btns = mutableListOf<Btn>()

    private val ALIGNMENTS = listOf("Lawful Good","Neutral Good","Chaotic Good",
        "Lawful Neutral","True Neutral","Chaotic Neutral",
        "Lawful Evil","Neutral Evil","Chaotic Evil")

    override fun init() { /* layout is dynamic */ }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        btns.clear()
        if (statusTimer > 0f) statusTimer -= delta * 0.05f

        val W = width; val H = height
        val pad = 6

        // ── HEADER ────────────────────────────────────────────────────────────
        // Back / Apply
        btn(context, mouseX, mouseY, pad, pad, 38, 11, "§7← Back") { MinecraftClient.getInstance().setScreen(GmScreen()) }
        btn(context, mouseX, mouseY, W - 46, pad, 40, 11, "§aApply") { applyAll() }

        // Name (left half of header)
        val headerH = 36
        box(context, pad, pad + 13, W / 2 - pad - 4, headerH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, snapshot.playerName, pad + 4, pad + 17, 1.0f, 0xFFD700)

        // Info box (right half of header)
        val infoX = W / 2; val infoY = pad + 13; val infoW = W / 2 - pad; val infoH = headerH
        box(context, infoX, infoY, infoW, infoH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())

        // Class + subclass
        val clsName = classId?.let { id -> ClientGmRegistry.classes.find { it.id == id }?.displayName ?: id.path } ?: "—"
        val subName = subclassId?.let { id ->
            classId?.let { cid -> ClientGmRegistry.classes.find { it.id == cid }?.subclasses?.find { it.id == id }?.displayName } ?: id.path
        } ?: "—"
        lbl(context, "Class:", infoX + 3, infoY + 3, 0.65f, 0x888888)
        btn(context, mouseX, mouseY, infoX + 28, infoY + 2, 60, 9, "§f$clsName §e▼") { classDropOpen = !classDropOpen; subDropOpen = false; raceDropOpen = false; alignDropOpen = false }
        lbl(context, "Sub:", infoX + 3, infoY + 13, 0.65f, 0x888888)
        btn(context, mouseX, mouseY, infoX + 22, infoY + 12, 66, 9, "§f$subName §e▼") { subDropOpen = !subDropOpen; classDropOpen = false; raceDropOpen = false; alignDropOpen = false }

        // Level + EXP
        lbl(context, "Lv:", infoX + 95, infoY + 3, 0.65f, 0x888888)
        btn(context, mouseX, mouseY, infoX + 110, infoY + 2, 8, 9, "§c-") { level = (level - 1).coerceAtLeast(1); recalcProfBonus() }
        lbl(context, "$level", infoX + 120, infoY + 3, 0.65f, 0xFFFFFF)
        btn(context, mouseX, mouseY, infoX + 128, infoY + 2, 8, 9, "§a+") { level = (level + 1).coerceAtMost(20); recalcProfBonus() }
        lbl(context, "EXP:", infoX + 95, infoY + 13, 0.65f, 0x888888)
        btn(context, mouseX, mouseY, infoX + 116, infoY + 12, 8, 9, "§c-") { experience = (experience - 100).coerceAtLeast(0) }
        lbl(context, "$experience", infoX + 126, infoY + 13, 0.65f, 0xFFFFFF)
        btn(context, mouseX, mouseY, infoX + 148, infoY + 12, 8, 9, "§a+") { experience += 100 }

        // Race + alignment + gender
        val raceName = raceId?.let { id -> ClientGmRegistry.races.find { it.id == id }?.displayName ?: id.path } ?: "—"
        lbl(context, "Race:", infoX + 3, infoY + 24, 0.65f, 0x888888)
        btn(context, mouseX, mouseY, infoX + 26, infoY + 23, 60, 9, "§f$raceName §e▼") { raceDropOpen = !raceDropOpen; classDropOpen = false; subDropOpen = false; alignDropOpen = false }
        lbl(context, "Align:", infoX + 95, infoY + 24, 0.65f, 0x888888)
        btn(context, mouseX, mouseY, infoX + 120, infoY + 23, 70, 9, "§f$alignment §e▼") { alignDropOpen = !alignDropOpen; classDropOpen = false; subDropOpen = false; raceDropOpen = false }
        val gIcon = when (gender) { "male" -> "♂"; "female" -> "♀"; else -> "⚧" }
        btn(context, mouseX, mouseY, infoX + infoW - 14, infoY + 23, 12, 9, gIcon) { gender = when (gender) { "male" -> "female"; "female" -> "other"; else -> "male" } }

        val bodyY = infoY + infoH + 4
        renderBody(context, mouseX, mouseY, pad, bodyY, W, H)
        renderDropdowns(context, mouseX, mouseY, infoX, infoY)

        if (statusTimer > 0f) {
            val a = (statusTimer * 255).toInt().coerceIn(0, 255)
            context.drawCenteredTextWithShadow(textRenderer, statusMsg, W / 2, H - 10, (a shl 24) or 0x55FF55)
        }
        super.render(context, mouseX, mouseY, delta)
    }

    private fun recalcProfBonus() {
        profBonus = when { level >= 17 -> 6; level >= 13 -> 5; level >= 9 -> 4; level >= 5 -> 3; else -> 2 }
    }

    private fun renderBody(context: DrawContext, mouseX: Int, mouseY: Int, pad: Int, bodyY: Int, W: Int, H: Int) {
        val statColW = 68; val midColX = pad + statColW + 4
        val rightColX = W - 140; val rightColW = 136
        val midColW = rightColX - midColX - 4

        // ── LEFT: ABILITY SCORES ──────────────────────────────────────────────
        box(context, pad, bodyY, statColW, H - bodyY - pad, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "ABILITY", pad + 4, bodyY + 3, 0.6f, 0xD4AF37)
        lbl(context, "SCORES", pad + 4, bodyY + 10, 0.6f, 0xD4AF37)

        val statOrder = listOf(BbfStats.STRENGTH, BbfStats.DEXTERITY, BbfStats.CONSTITUTION,
                               BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA)
        var sy = bodyY + 20
        statOrder.forEach { stat ->
            val v = stats[stat.id] ?: 10
            val mod = (v - 10) / 2
            val modStr = if (mod >= 0) "+$mod" else "$mod"
            val modColor = if (mod > 0) 0x55FF55 else if (mod < 0) 0xFF5555 else 0x888888
            val shortKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.short"
            val shortName = Text.translatable(shortKey).string
            val changed = v != (snapshot.statsData?.getStatValue(stat.id)?.total ?: 10)

            // Stat name
            lbl(context, shortName, pad + 4, sy, 0.65f, if (changed) 0xFFAA44 else 0xCCCCCC)
            // Value + mod
            lbl(context, "$v", pad + 28, sy, 0.85f, 0xFFFFFF)
            lbl(context, modStr, pad + 42, sy, 0.65f, modColor)
            // Buttons
            btn(context, mouseX, mouseY, pad + 2, sy - 1, 8, 9, "§c-") { stats[stat.id] = (v - 1).coerceAtLeast(1) }
            btn(context, mouseX, mouseY, pad + statColW - 11, sy - 1, 8, 9, "§a+") { stats[stat.id] = (v + 1).coerceAtMost(30) }
            sy += 14
        }

        // Proficiency bonus
        sy += 2
        box(context, pad + 2, sy, statColW - 4, 18, 0xCC222222.toInt(), 0xFF6a5a3a.toInt())
        lbl(context, "PROF BONUS", pad + 4, sy + 2, 0.55f, 0x888888)
        lbl(context, "+$profBonus", pad + 4, sy + 9, 0.75f, 0xFFD700)
        btn(context, mouseX, mouseY, pad + 36, sy + 4, 8, 9, "§c-") { profBonus = (profBonus - 1).coerceAtLeast(1) }
        btn(context, mouseX, mouseY, pad + 46, sy + 4, 8, 9, "§a+") { profBonus = (profBonus + 1).coerceAtMost(9) }

        // Passive perception
        sy += 22
        box(context, pad + 2, sy, statColW - 4, 14, 0xCC222222.toInt(), 0xFF6a5a3a.toInt())
        val wisVal = stats[BbfStats.WISDOM.id] ?: 10
        val wisMod = (wisVal - 10) / 2
        val percProf = skills[omc.boundbyfate.registry.BbfSkills.PERCEPTION.id] ?: 0
        val passPerc = 10 + wisMod + percProf * profBonus
        lbl(context, "PASSIVE PERC", pad + 4, sy + 2, 0.5f, 0x888888)
        lbl(context, "$passPerc", pad + 4, sy + 8, 0.75f, 0xFFFFFF)

        // ── MIDDLE: SAVES + SKILLS ────────────────────────────────────────────
        val savesH = 80; val skillsH = H - bodyY - savesH - pad * 2 - 8
        box(context, midColX, bodyY, midColW, savesH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "SAVING THROWS", midColX + 4, bodyY + 3, 0.65f, 0xD4AF37)
        renderSaves(context, mouseX, mouseY, midColX + 4, bodyY + 12, midColW - 8)

        box(context, midColX, bodyY + savesH + 4, midColW, skillsH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "SKILLS", midColX + 4, bodyY + savesH + 7, 0.65f, 0xD4AF37)
        renderSkills(context, mouseX, mouseY, midColX + 4, bodyY + savesH + 16, midColW - 8, skillsH - 20)

        // ── RIGHT: PARAMS + FEATURES ─────────────────────────────────────────
        val paramsH = 80
        box(context, rightColX, bodyY, rightColW, paramsH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "PARAMETERS", rightColX + 4, bodyY + 3, 0.65f, 0xD4AF37)
        renderParams(context, mouseX, mouseY, rightColX + 4, bodyY + 12, rightColW - 8)

        val featY = bodyY + paramsH + 4
        val featH = H - featY - pad
        box(context, rightColX, featY, rightColW, featH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "FEATURES & TRAITS", rightColX + 4, featY + 3, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, rightColX + rightColW - 14, featY + 2, 12, 9, "§a+") { featDropOpen = !featDropOpen }
        renderFeatures(context, mouseX, mouseY, rightColX + 4, featY + 14, rightColW - 8, featH - 18)
    }

    private fun renderSaves(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int) {
        val saves = ClientGmRegistry.skills.filter { it.isSavingThrow }
        saves.forEachIndexed { i, save ->
            val lv = skills[save.id] ?: 0
            val icon = when (lv) { 0 -> "§7○"; 1 -> "§a●"; else -> "§b◆" }
            btn(context, mouseX, mouseY, x, y + i * 10, 8, 8, icon) { skills[save.id] = (lv + 1) % 3 }
            val statMod = stats[save.linkedStat]?.let { (it - 10) / 2 } ?: 0
            val bonus = statMod + lv * profBonus
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"
            lbl(context, "§7$bonusStr", x + 10, y + i * 10 + 1, 0.65f, if (lv > 0) 0x55FF55 else 0xAAAAAA)
            lbl(context, save.displayName, x + 26, y + i * 10 + 1, 0.65f, if (lv > 0) 0x55FF55 else 0xCCCCCC)
        }
    }

    private fun renderSkills(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val skillList = ClientGmRegistry.skills.filter { !it.isSavingThrow }
        val visible = skillList.drop(skillScroll).take(h / 9)
        visible.forEachIndexed { i, skill ->
            val lv = skills[skill.id] ?: 0
            val icon = when (lv) { 0 -> "§7○"; 1 -> "§a●"; else -> "§b◆" }
            btn(context, mouseX, mouseY, x, y + i * 9, 8, 8, icon) { skills[skill.id] = (lv + 1) % 3 }
            val statMod = stats[skill.linkedStat]?.let { (it - 10) / 2 } ?: 0
            val bonus = statMod + lv * profBonus
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"
            lbl(context, "§7$bonusStr", x + 10, y + i * 9 + 1, 0.6f, if (lv > 0) 0x55FF55 else 0xAAAAAA)
            lbl(context, skill.displayName, x + 26, y + i * 9 + 1, 0.6f, if (lv > 0) 0x55FF55 else 0xCCCCCC)
        }
        if (skillScroll > 0) btn(context, mouseX, mouseY, x + w - 10, y, 10, 9, "§7▲") { skillScroll-- }
        if (skillScroll + h / 9 < skillList.size) btn(context, mouseX, mouseY, x + w - 10, y + h - 10, 10, 9, "§7▼") { skillScroll++ }
    }

    private fun renderParams(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int) {
        // HP
        lbl(context, "HP:", x, y, 0.65f, 0x888888)
        btn(context, mouseX, mouseY, x + 16, y - 1, 8, 9, "§c-") { currentHp = (currentHp - 1).coerceAtLeast(0f) }
        lbl(context, "${currentHp.toInt()}", x + 26, y, 0.75f, 0xFF5555)
        lbl(context, "/", x + 38, y, 0.65f, 0x888888)
        btn(context, mouseX, mouseY, x + 44, y - 1, 8, 9, "§c-") { maxHp = (maxHp - 1).coerceAtLeast(1f) }
        lbl(context, "${maxHp.toInt()}", x + 54, y, 0.75f, 0xFFFFFF)
        btn(context, mouseX, mouseY, x + 66, y - 1, 8, 9, "§a+") { maxHp += 1f }

        // Speed
        lbl(context, "Speed:", x, y + 14, 0.65f, 0x888888)
        val speedFt = (snapshot.speed * 200).toInt()
        lbl(context, "${speedFt}ft", x + 30, y + 14, 0.75f, 0xFFFFFF)

        // Death saves
        lbl(context, "Death Saves:", x, y + 28, 0.65f, 0x888888)
        lbl(context, "✓✓✓ ✗✗✗", x + 4, y + 36, 0.65f, 0xAAAAAA)
    }

    private fun renderFeatures(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        features.forEachIndexed { i, featId ->
            val featName = ClientGmRegistry.features.find { it.id == featId }?.displayName ?: featId.path
            val fy = y + i * 11
            if (fy + 10 > y + h) return@forEachIndexed
            val hovered = mouseX in x..(x + w - 14) && mouseY in fy..(fy + 10)
            lbl(context, featName, x, fy + 1, 0.65f, if (hovered) 0xFFD700 else 0xCCCCCC)
            btn(context, mouseX, mouseY, x + w - 12, fy, 10, 9, "§cX") { features.remove(featId) }
        }
        // Feature add dropdown
        if (featDropOpen) {
            val dropX = x; var dropY = y + features.size * 11
            ClientGmRegistry.features.take(10).forEach { feat ->
                btn(context, mouseX, mouseY, dropX, dropY, w - 4, 9, "§7${feat.displayName}") {
                    if (!features.contains(feat.id)) features.add(feat.id)
                    featDropOpen = false
                }
                dropY += 10
            }
        }
    }

    private fun renderDropdowns(context: DrawContext, mouseX: Int, mouseY: Int, infoX: Int, infoY: Int) {
        if (classDropOpen) {
            var dy = infoY + 11
            ClientGmRegistry.classes.forEach { cls ->
                btn(context, mouseX, mouseY, infoX + 28, dy, 60, 9,
                    if (cls.id == classId) "§a${cls.displayName}" else "§7${cls.displayName}") {
                    classId = cls.id; subclassId = null; classDropOpen = false
                }
                dy += 10
            }
        }
        if (subDropOpen) {
            val subs = classId?.let { cid -> ClientGmRegistry.classes.find { it.id == cid }?.subclasses } ?: emptyList()
            var dy = infoY + 21
            subs.forEach { sub ->
                btn(context, mouseX, mouseY, infoX + 22, dy, 66, 9,
                    if (sub.id == subclassId) "§a${sub.displayName}" else "§7${sub.displayName}") {
                    subclassId = sub.id; subDropOpen = false
                }
                dy += 10
            }
        }
        if (raceDropOpen) {
            var dy = infoY + 32
            ClientGmRegistry.races.forEach { race ->
                btn(context, mouseX, mouseY, infoX + 26, dy, 60, 9,
                    if (race.id == raceId) "§a${race.displayName}" else "§7${race.displayName}") {
                    raceId = race.id; raceDropOpen = false
                }
                dy += 10
            }
        }
        if (alignDropOpen) {
            var dy = infoY + 32
            ALIGNMENTS.forEach { al ->
                btn(context, mouseX, mouseY, infoX + 120, dy, 70, 9,
                    if (al == alignment) "§a$al" else "§7$al") {
                    alignment = al; alignDropOpen = false
                }
                dy += 10
            }
        }
    }

    private fun applyAll() {
        val statBuf = PacketByteBufs.create()
        statBuf.writeString(snapshot.playerName)
        statBuf.writeInt(stats.size)
        stats.forEach { (id, v) -> statBuf.writeIdentifier(id); statBuf.writeInt(v) }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_STATS, statBuf)

        val idBuf = PacketByteBufs.create()
        idBuf.writeString(snapshot.playerName)
        idBuf.writeBoolean(classId != null); if (classId != null) idBuf.writeIdentifier(classId!!)
        idBuf.writeBoolean(subclassId != null); if (subclassId != null) idBuf.writeIdentifier(subclassId!!)
        idBuf.writeInt(level)
        idBuf.writeBoolean(raceId != null); if (raceId != null) idBuf.writeIdentifier(raceId!!)
        idBuf.writeBoolean(true); idBuf.writeString(gender)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDENTITY, idBuf)

        val skillBuf = PacketByteBufs.create()
        skillBuf.writeString(snapshot.playerName)
        skillBuf.writeInt(skills.size)
        skills.forEach { (id, lv) -> skillBuf.writeIdentifier(id); skillBuf.writeInt(lv) }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_SKILLS, skillBuf)

        statusMsg = "§aApplied!"; statusTimer = 1f
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private fun btn(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, label: String, action: () -> Unit) {
        btns.add(Btn(x, y, w, h, label, action))
        val hov = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = if (hov) 0xCC4a3a2a.toInt() else 0xCC2b2321.toInt()
        val bd = if (hov) 0xFFd4a96a.toInt() else 0xFF6b5a3e.toInt()
        context.fill(x, y, x + w, y + h, bg)
        context.fill(x, y, x + w, y + 1, bd); context.fill(x, y + h - 1, x + w, y + h, bd)
        context.fill(x, y, x + 1, y + h, bd); context.fill(x + w - 1, y, x + w, y + h, bd)
        val m = context.matrices; m.push()
        m.translate((x + w / 2).toFloat(), (y + h / 2 - 3).toFloat(), 0f)
        m.scale(0.75f, 0.75f, 1f)
        val tw = textRenderer.getWidth(label)
        context.drawTextWithShadow(textRenderer, label, -(tw / 2), 0, 0xFFFFFF)
        m.pop()
    }

    private fun lbl(context: DrawContext, text: String, x: Int, y: Int, scale: Float, color: Int) {
        val m = context.matrices; m.push()
        m.translate(x.toFloat(), y.toFloat(), 0f); m.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color); m.pop()
    }

    private fun box(context: DrawContext, x: Int, y: Int, w: Int, h: Int, bg: Int, border: Int) {
        context.fill(x, y, x + w, y + h, bg)
        context.fill(x, y, x + w, y + 1, border); context.fill(x, y + h - 1, x + w, y + h, border)
        context.fill(x, y, x + 1, y + h, border); context.fill(x + w - 1, y, x + w, y + h, border)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        for (b in btns.reversed()) {
            if (mx in b.x..(b.x + b.w) && my in b.y..(b.y + b.h)) { b.action(); return true }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        skillScroll = (skillScroll - amount.toInt()).coerceAtLeast(0); return true
    }

    override fun shouldPause() = false
}
