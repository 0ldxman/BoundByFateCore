package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.WeaponDefinition
import omc.boundbyfate.api.combat.WeaponProperty
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Parses weapon definitions from JSON.
 *
 * Format:
 * ```json
 * {
 *   "displayName": "Длинный меч",
 *   "items": ["minecraft:iron_sword", "minecraft:diamond_sword"],
 *   "damage": "1d8",
 *   "versatileDamage": "1d10",
 *   "damageType": "boundbyfate-core:slashing",
 *   "properties": ["finesse", "versatile"]
 * }
 * ```
 *
 * Properties are short names matching [WeaponProperty] enum (case-insensitive).
 * damageType defaults to "boundbyfate-core:bludgeoning" if omitted.
 */
object WeaponParser {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun parse(id: Identifier, stream: InputStream): WeaponDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: run {
                logger.error("Weapon $id: root must be a JSON object")
                return null
            }

            val displayName = json.get("displayName")?.asString ?: run {
                logger.error("Weapon $id: missing 'displayName'")
                return null
            }

            val items = mutableListOf<Identifier>()
            json.getAsJsonArray("items")?.forEach { el ->
                try { items.add(Identifier(el.asString)) }
                catch (e: Exception) { logger.warn("$id: invalid item '${el.asString}'") }
            }

            if (items.isEmpty()) {
                logger.error("Weapon $id: 'items' must not be empty")
                return null
            }

            val damage = json.get("damage")?.asString ?: run {
                logger.error("Weapon $id: missing 'damage'")
                return null
            }

            val versatileDamage = json.get("versatileDamage")?.asString

            val damageType = json.get("damageType")?.asString?.let {
                try { Identifier(it) }
                catch (e: Exception) { logger.warn("$id: invalid damageType '$it'"); null }
            } ?: Identifier("boundbyfate-core", "bludgeoning")

            val properties = mutableSetOf<WeaponProperty>()
            json.getAsJsonArray("properties")?.forEach { el ->
                val name = el.asString.uppercase()
                try {
                    properties.add(WeaponProperty.valueOf(name))
                } catch (e: Exception) {
                    logger.warn("$id: unknown property '$name'")
                }
            }

            WeaponDefinition(
                id = id,
                displayName = displayName,
                items = items,
                damage = damage,
                versatileDamage = versatileDamage,
                damageType = damageType,
                properties = properties
            )
        } catch (e: Exception) {
            logger.error("Failed to parse weapon $id", e)
            null
        }
    }
}
