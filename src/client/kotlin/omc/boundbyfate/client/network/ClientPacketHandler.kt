package omc.boundbyfate.client.network

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.particle.DefaultParticleType
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.WeaponDefinition
import omc.boundbyfate.api.combat.WeaponProperty
import omc.boundbyfate.client.render.FloatingTextRenderer
import omc.boundbyfate.client.skin.ClientSkinManager
import omc.boundbyfate.client.state.ClientFeatureState
import omc.boundbyfate.client.state.ClientGmData
import omc.boundbyfate.client.state.ClientPlayerData
import omc.boundbyfate.client.state.ClientWeaponRegistry
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.component.PlayerClassData
import omc.boundbyfate.component.PlayerRaceData
import omc.boundbyfate.network.BbfPackets

object ClientPacketHandler {

    fun register() {
        // Server → Client: spawn particles
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SPAWN_PARTICLES) { client, _, buf, _ ->
            val particleId = buf.readIdentifier()
            val x = buf.readDouble()
            val y = buf.readDouble()
            val z = buf.readDouble()
            val count = buf.readInt()
            val spread = buf.readFloat()
            val speed = buf.readFloat()
            client.execute { spawnParticles(client, particleId, x, y, z, count, spread, speed) }
        }

        // Server → Client: sync hotbar slots
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_FEATURE_SLOTS) { client, _, buf, _ ->
            val slots = Array<Identifier?>(10) { null }
            for (i in 0..9) {
                val hasFeature = buf.readBoolean()
                slots[i] = if (hasFeature) buf.readIdentifier() else null
            }
            client.execute { for (i in 0..9) ClientFeatureState.setHotbarSlot(i, slots[i]) }
        }

        // Server → Client: sync granted features
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_GRANTED_FEATURES) { client, _, buf, _ ->
            val count = buf.readInt()
            val features = (0 until count).map { buf.readIdentifier() }.toSet()
            client.execute {
                ClientFeatureState.grantedFeatures.clear()
                ClientFeatureState.grantedFeatures.addAll(features)
            }
        }

        // Server → Client: show floating attack roll text above target
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SHOW_ATTACK_ROLL) { client, _, buf, _ ->
            val x = buf.readDouble()
            val y = buf.readDouble()
            val z = buf.readDouble()
            val roll = buf.readInt()
            val bonus = buf.readInt()
            val hit = buf.readBoolean()
            val isCrit = buf.readBoolean()
            client.execute {
                val total = roll + bonus
                val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"
                val text = if (isCrit) "★$total ($roll$bonusStr)" else "$total ($roll$bonusStr)"
                val color = when {
                    isCrit -> 0xFFD700  // gold
                    hit    -> 0x55FF55  // green
                    else   -> 0xAA0000  // dark red
                }
                FloatingTextRenderer.add(text, color, x, y, z)
            }
        }

        // Server → Client: set custom player skin
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_PLAYER_SKIN) { _, _, buf, _ ->
            val playerName = buf.readString()
            val skinModel = buf.readString()
            val skinBase64 = buf.readString()
            ClientSkinManager.setSkin(playerName, skinBase64, skinModel)
        }

        // Server → Client: clear custom player skin
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.CLEAR_PLAYER_SKIN) { _, _, buf, _ ->
            val playerName = buf.readString()
            ClientSkinManager.clearSkin(playerName)
        }

        // Server → Client: sync weapon registry for tooltips
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_WEAPON_REGISTRY) { client, _, buf, _ ->            val count = buf.readInt()
            val definitions = (0 until count).map {
                val id = buf.readIdentifier()
                val displayName = buf.readString()
                val itemCount = buf.readInt()
                val items = (0 until itemCount).map { buf.readIdentifier() }
                val damage = buf.readString()
                val hasVersatile = buf.readBoolean()
                val versatileDamage = if (hasVersatile) buf.readString() else null
                val damageType = buf.readIdentifier()
                val propCount = buf.readInt()
                val properties = (0 until propCount).mapNotNull {
                    try { WeaponProperty.valueOf(buf.readString()) } catch (e: Exception) { null }
                }.toSet()
                WeaponDefinition(id, displayName, items, damage, versatileDamage, damageType, properties)
            }
            client.execute { ClientWeaponRegistry.update(definitions) }
        }

        // Server → Client: sync player character data
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_PLAYER_DATA) { client, _, buf, _ ->
            // Stats
            val statsCount = buf.readInt()
            val baseStats = mutableMapOf<net.minecraft.util.Identifier, Int>()
            repeat(statsCount) {
                val id = buf.readIdentifier()
                val value = buf.readInt()
                baseStats[id] = value
            }
            // Modifiers (currently 0)
            val modCount = buf.readInt()
            repeat(modCount) { buf.readIdentifier(); buf.readInt() }

            // Skills
            val skillCount = buf.readInt()
            val proficiencies = mutableMapOf<net.minecraft.util.Identifier, Int>()
            repeat(skillCount) {
                val id = buf.readIdentifier()
                val level = buf.readInt()
                proficiencies[id] = level
            }

            // Class
            val hasClass = buf.readBoolean()
            val classData = if (hasClass) {
                val classId = buf.readIdentifier()
                val classLevel = buf.readInt()
                val hasSubclass = buf.readBoolean()
                val subclassId = if (hasSubclass) buf.readIdentifier() else null
                PlayerClassData(classId, subclassId, classLevel)
            } else null

            // Race
            val hasRace = buf.readBoolean()
            val raceData = if (hasRace) PlayerRaceData(buf.readIdentifier()) else null

            // Level
            val level = buf.readInt()

            // Gender
            val hasGender = buf.readBoolean()
            val gender = if (hasGender) buf.readString() else null

            client.execute {
                ClientPlayerData.statsData = EntityStatData(baseStats = baseStats)
                ClientPlayerData.skillData = EntitySkillData(proficiencies = proficiencies)
                ClientPlayerData.classData = classData
                ClientPlayerData.raceData = raceData
                ClientPlayerData.level = level
                ClientPlayerData.gender = gender
            }
        }

        // Server → Client: sync GM players data
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_GM_PLAYERS) { client, _, buf, _ ->
            val count = buf.readInt()
            val snapshots = (0 until count).map {
                val name = buf.readString()

                // Base stats (without race/class bonuses)
                val statsCount = buf.readInt()
                val baseStats = mutableMapOf<net.minecraft.util.Identifier, Int>()
                repeat(statsCount) { baseStats[buf.readIdentifier()] = buf.readInt() }

                // Stat bonuses from race/class
                val bonusCount = buf.readInt()
                val statBonuses = mutableMapOf<net.minecraft.util.Identifier, Int>()
                repeat(bonusCount) { statBonuses[buf.readIdentifier()] = buf.readInt() }

                // Stat bonus breakdown
                val breakdownCount = buf.readInt()
                val statBonusBreakdown = mutableMapOf<net.minecraft.util.Identifier, List<String>>()
                repeat(breakdownCount) {
                    val statId = buf.readIdentifier()
                    val entryCount = buf.readInt()
                    val entries = (0 until entryCount).map { "${buf.readString()}|${buf.readInt()}" }
                    statBonusBreakdown[statId] = entries
                }

                // Skills
                val skillCount = buf.readInt()
                val proficiencies = mutableMapOf<net.minecraft.util.Identifier, Int>()
                repeat(skillCount) { proficiencies[buf.readIdentifier()] = buf.readInt() }

                // Locked skills (from class/race)
                val lockedSkillCount = buf.readInt()
                val lockedSkills = mutableSetOf<net.minecraft.util.Identifier>()
                repeat(lockedSkillCount) { lockedSkills.add(buf.readIdentifier()) }

                // Skill sources
                val skillSourceCount = buf.readInt()
                val skillSources = mutableMapOf<net.minecraft.util.Identifier, List<String>>()
                repeat(skillSourceCount) {
                    val skillId = buf.readIdentifier()
                    val entryCount = buf.readInt()
                    val entries = (0 until entryCount).map { buf.readString() }
                    skillSources[skillId] = entries
                }

                val hasClass = buf.readBoolean()
                val classData = if (hasClass) {
                    val classId = buf.readIdentifier()
                    val classLevel = buf.readInt()
                    val hasSubclass = buf.readBoolean()
                    val subclassId = if (hasSubclass) buf.readIdentifier() else null
                    PlayerClassData(classId, subclassId, classLevel)
                } else null
                val hasRace = buf.readBoolean()
                val raceData = if (hasRace) PlayerRaceData(buf.readIdentifier()) else null
                val level = buf.readInt()
                val hasGender = buf.readBoolean()
                val gender = if (hasGender) buf.readString() else null
                val hp = buf.readFloat()
                val maxHp = buf.readFloat()
                // Speed (base + modifier)
                val baseSpeed = buf.readInt()
                val speedModifier = buf.readInt()
                val speed = (baseSpeed + speedModifier).toFloat()
                // Scale (base + modifier)
                val baseScale = buf.readFloat()
                val scaleModifier = buf.readFloat()
                val scale = baseScale + scaleModifier
                val experience = buf.readInt()
                val alignment = buf.readString()

                // All granted features
                val featureCount = buf.readInt()
                val features = (0 until featureCount).map { buf.readIdentifier() }

                // Locked features (from race/class)
                val lockedFeatureCount = buf.readInt()
                val lockedFeatures = mutableSetOf<net.minecraft.util.Identifier>()
                repeat(lockedFeatureCount) { lockedFeatures.add(buf.readIdentifier()) }

                // Feature sources
                val featSourceCount = buf.readInt()
                val featureSources = mutableMapOf<net.minecraft.util.Identifier, String>()
                repeat(featSourceCount) {
                    val featId = buf.readIdentifier()
                    featureSources[featId] = buf.readString()
                }

                val vitality = buf.readInt()
                val scarCount = buf.readInt()
                omc.boundbyfate.client.state.GmPlayerSnapshot(
                    playerName = name,
                    statsData = EntityStatData(baseStats = baseStats),
                    statBonuses = statBonuses,
                    statBonusBreakdown = statBonusBreakdown,
                    skillData = EntitySkillData(proficiencies = proficiencies),
                    lockedSkills = lockedSkills,
                    skillSources = skillSources,
                    classData = classData, raceData = raceData,
                    level = level, experience = experience,
                    gender = gender, alignment = alignment,
                    currentHp = hp, maxHp = maxHp, 
                    speed = speed, scale = scale,
                    baseSpeed = baseSpeed, speedModifier = speedModifier,
                    baseScale = baseScale, scaleModifier = scaleModifier,
                    isOnline = true, grantedFeatures = features,
                    lockedFeatures = lockedFeatures,
                    featureSources = featureSources,
                    vitality = vitality, scarCount = scarCount
                )
            }
            client.execute {
                ClientGmData.update(snapshots)
                // Если открыт экран редактирования игрока — обновляем его с новым snapshot
                val currentScreen = client.currentScreen
                if (currentScreen is omc.boundbyfate.client.gui.GmPlayerEditScreen) {
                    val playerName = currentScreen.editingPlayerName
                    val newSnapshot = snapshots.find { it.playerName == playerName }
                    if (newSnapshot != null) {
                        client.setScreen(omc.boundbyfate.client.gui.GmPlayerEditScreen(newSnapshot))
                    }
                }
            }
        }

        // Server → Client: sync GM registry (classes, races, skills, features)
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_GM_REGISTRY) { client, _, buf, _ ->
            val classCount = buf.readInt()
            val classes = (0 until classCount).map {
                val id = buf.readIdentifier()
                val name = buf.readString()
                val subclassLevel = buf.readInt()
                val subCount = buf.readInt()
                val subs = (0 until subCount).map {
                    omc.boundbyfate.client.state.GmSubclassInfo(buf.readIdentifier(), buf.readString())
                }
                omc.boundbyfate.client.state.GmClassInfo(id, name, subs, subclassLevel)
            }
            val raceCount = buf.readInt()
            val races = (0 until raceCount).map {
                omc.boundbyfate.client.state.GmRaceInfo(buf.readIdentifier(), buf.readString())
            }
            val skillCount = buf.readInt()
            val skills = (0 until skillCount).map {
                omc.boundbyfate.client.state.GmSkillInfo(buf.readIdentifier(), buf.readString(), buf.readBoolean(), buf.readIdentifier())
            }
            val featCount = buf.readInt()
            val features = (0 until featCount).map {
                omc.boundbyfate.client.state.GmFeatureInfo(buf.readIdentifier(), buf.readString())
            }
            client.execute {
                omc.boundbyfate.client.state.ClientGmRegistry.update(classes, races, skills, features)
            }
        }

        // Server → Client: sync darkvision range
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_DARKVISION) { client, _, buf, _ ->
            val rangeFt = buf.readInt()
            client.execute { omc.boundbyfate.client.state.DarkvisionState.rangeFt = rangeFt }
        }

        // Server → Client: sync available skin names + base64 for GM picker
        ClientPlayNetworking.registerGlobalReceiver(BbfPackets.SYNC_SKIN_LIST) { client, _, buf, _ ->
            val count = buf.readInt()
            val entries = (0 until count).map { buf.readString() to buf.readString() } // name to base64
            client.execute {
                val names = entries.map { it.first }
                omc.boundbyfate.client.state.ClientGmRegistry.updateSkins(names)
                entries.forEach { (name, base64) ->
                    if (base64.isNotEmpty()) {
                        omc.boundbyfate.client.state.ClientGmRegistry.registerSkinTexture(name, base64)
                    }
                }
            }
        }
    }

    private fun spawnParticles(
        client: MinecraftClient,
        particleId: Identifier,
        x: Double, y: Double, z: Double,
        count: Int, spread: Float, speed: Float
    ) {
        val world = client.world ?: return
        val particleType = Registries.PARTICLE_TYPE.get(particleId)
        if (particleType is DefaultParticleType) {
            repeat(count) {
                val offsetX = (Math.random() - 0.5) * spread
                val offsetY = (Math.random() - 0.5) * spread
                val offsetZ = (Math.random() - 0.5) * spread
                world.addParticle(particleType, x + offsetX, y + offsetY, z + offsetZ, 0.0, speed.toDouble(), 0.0)
            }
        }
    }
}
