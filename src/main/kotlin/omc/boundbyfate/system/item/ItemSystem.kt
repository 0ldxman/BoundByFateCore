package omc.boundbyfate.system.item

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registries.Registries
import net.minecraft.util.Identifier
import omc.boundbyfate.api.item.ItemDefinition
import omc.boundbyfate.api.item.ItemPropertyContext
import omc.boundbyfate.api.item.ItemPropertyDefinition
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.registry.ItemPropertyRegistry
import omc.boundbyfate.util.source.SourceReference
import omc.boundbyfate.util.source.SourceType
import org.slf4j.LoggerFactory

/**
 * Система управления свойствами предметов.
 *
 * Отвечает за:
 * - Применение свойств при экипировке
 * - Снятие свойств при снятии предмета
 * - Тикование свойств
 * - Мёрдж свойств из датапака и NBT
 * - Пересчёт при логине и reload датапаков
 *
 * ## Использование
 *
 * ```kotlin
 * // При надевании предмета (из Mixin или события Fabric)
 * ItemSystem.onEquip(entity, item, EquipmentSlot.MAINHAND)
 *
 * // При снятии предмета
 * ItemSystem.onUnequip(entity, item, EquipmentSlot.MAINHAND)
 *
 * // При логине или reload — пересчитать всё
 * ItemSystem.reapplyAll(entity)
 *
 * // Тик (каждый серверный тик)
 * ItemSystem.tick(entity, currentTick)
 * ```
 */
object ItemSystem {

    private val logger = LoggerFactory.getLogger(ItemSystem::class.java)

    /** NBT ключ для runtime свойств предмета */
    const val NBT_PROPERTIES_KEY = "bbf:properties"

    /** NBT ключ для переопределения владения */
    const val NBT_PROFICIENCY_OVERRIDE_KEY = "bbf:proficiency_override"

    // ── Экипировка ────────────────────────────────────────────────────────

    /**
     * Вызывается когда предмет надевается в слот.
     *
     * Применяет все свойства предмета (из датапака + NBT).
     */
    fun onEquip(entity: LivingEntity, item: ItemStack, slot: EquipmentSlot) {
        if (item.isEmpty) return

        val properties = resolveProperties(item)
        if (properties.isEmpty()) return

        val itemId = getItemId(item) ?: return
        val source = SourceReference.item(itemId, slot.name.lowercase())

        for (propDef in properties) {
            val handler = ItemPropertyRegistry.getHandler(propDef.id) ?: run {
                logger.warn("Item property handler '${propDef.id}' not found for item '$itemId'")
                return@run
            }

            val ctx = ItemPropertyContext(
                entity = entity,
                item = item,
                slot = slot,
                definition = propDef,
                source = source
            )

            try {
                handler.onEquip(ctx)
                logger.debug("Applied property '${propDef.id}' to '${entity.name.string}' from '$itemId' in $slot")
            } catch (e: Exception) {
                logger.error("Error applying property '${propDef.id}' for item '$itemId'", e)
            }
        }
    }

    /**
     * Вызывается когда предмет снимается из слота.
     *
     * Снимает все свойства предмета.
     */
    fun onUnequip(entity: LivingEntity, item: ItemStack, slot: EquipmentSlot) {
        if (item.isEmpty) return

        val properties = resolveProperties(item)
        if (properties.isEmpty()) return

        val itemId = getItemId(item) ?: return
        val source = SourceReference.item(itemId, slot.name.lowercase())

        for (propDef in properties) {
            val handler = ItemPropertyRegistry.getHandler(propDef.id) ?: continue

            val ctx = ItemPropertyContext(
                entity = entity,
                item = item,
                slot = slot,
                definition = propDef,
                source = source
            )

            try {
                handler.onUnequip(ctx)
                logger.debug("Removed property '${propDef.id}' from '${entity.name.string}' (item '$itemId' unequipped from $slot)")
            } catch (e: Exception) {
                logger.error("Error removing property '${propDef.id}' for item '$itemId'", e)
            }
        }
    }

    // ── Пересчёт ──────────────────────────────────────────────────────────

    /**
     * Пересчитывает все свойства всех надетых предметов.
     *
     * Вызывается при:
     * - Логине игрока
     * - Перезагрузке датапаков (/reload)
     *
     * Алгоритм:
     * 1. Снять все эффекты/способности с источником SourceType.ITEM
     * 2. Применить свойства всех надетых предметов заново
     */
    fun reapplyAll(entity: LivingEntity) {
        logger.debug("Reapplying all item properties for '${entity.name.string}'")

        // Шаг 1: снять всё с источником ITEM
        removeAllItemEffects(entity)

        // Шаг 2: применить заново для каждого надетого предмета
        for (slot in EquipmentSlot.entries) {
            val item = entity.getEquippedStack(slot)
            if (!item.isEmpty) {
                onEquip(entity, item, slot)
            }
        }
    }

    /**
     * Снимает все эффекты/способности от предметов.
     *
     * Используется при reapplyAll и при смене персонажа.
     */
    fun removeAllItemEffects(entity: LivingEntity) {
        // Снимаем все временные модификаторы статов от предметов
        val stats = entity.getOrCreate(omc.boundbyfate.component.components.EntityStatsData.TYPE)
        for (statId in stats.temporaryModifiers.keys.toList()) {
            val filtered = stats.temporaryModifiers[statId]
                ?.filter { it.source.type != omc.boundbyfate.util.source.SourceType.ITEM }
                ?: continue
            if (filtered.isEmpty()) stats.temporaryModifiers.remove(statId)
            else stats.temporaryModifiers[statId] = filtered
        }

        // Снимаем способности от предметов
        // (способности от предметов помечены source.type == ITEM — пока не отслеживаем,
        //  поэтому просто пересоздадим при reapplyAll)

        logger.debug("Removed all item effects from '${entity.name.string}'")
    }

    // ── Тик ──────────────────────────────────────────────────────────────

    /**
     * Тикует свойства всех надетых предметов.
     *
     * Вызывается каждый серверный тик.
     * Только тикующие свойства (isTicking = true) обрабатываются.
     */
    fun tick(entity: LivingEntity, currentTick: Long) {
        for (slot in EquipmentSlot.entries) {
            val item = entity.getEquippedStack(slot)
            if (item.isEmpty) continue

            val properties = resolveProperties(item)
            val itemId = getItemId(item) ?: continue
            val source = SourceReference.item(itemId, slot.name.lowercase())

            for (propDef in properties) {
                val handler = ItemPropertyRegistry.getHandler(propDef.id) ?: continue
                if (!handler.isTicking) continue
                if (currentTick % handler.tickInterval != 0L) continue

                val ticksHeld = getTicksHeld(entity, item, slot, currentTick)
                val ctx = ItemPropertyContext(
                    entity = entity,
                    item = item,
                    slot = slot,
                    definition = propDef,
                    source = source,
                    ticksHeld = ticksHeld
                )

                try {
                    when {
                        slot == EquipmentSlot.MAINHAND && handler.ticksInMainHand ->
                            handler.onHeldTick(ctx)
                        slot == EquipmentSlot.OFFHAND && handler.ticksInOffhand ->
                            handler.onOffhandTick(ctx)
                        slot.type == EquipmentSlot.Type.ARMOR && handler.ticksWhenWorn ->
                            handler.onWornTick(ctx)
                    }
                } catch (e: Exception) {
                    logger.error("Error ticking property '${propDef.id}' for item '$itemId'", e)
                }
            }
        }
    }

    // ── Боевые хуки ──────────────────────────────────────────────────────

    /**
     * Вызывается при атаке предметом в основной руке.
     */
    fun onAttack(entity: LivingEntity, weapon: ItemStack) {
        if (weapon.isEmpty) return
        invokePropertyHook(entity, weapon, EquipmentSlot.MAINHAND) { handler, ctx ->
            handler.onAttack(ctx)
        }
    }

    /**
     * Вызывается при попадании по цели предметом в основной руке.
     */
    fun onHit(entity: LivingEntity, weapon: ItemStack) {
        if (weapon.isEmpty) return
        invokePropertyHook(entity, weapon, EquipmentSlot.MAINHAND) { handler, ctx ->
            handler.onHit(ctx)
        }
    }

    // ── Хуки взаимодействия ───────────────────────────────────────────────

    /**
     * Вызывается при правом клике на блок.
     *
     * Перебирает свойства предмета в руке и вызывает [ItemPropertyHandler.onUseOnBlock].
     * Первое свойство вернувшее true останавливает обработку.
     *
     * @return true если хотя бы одно свойство обработало взаимодействие
     */
    fun onUseOnBlock(
        entity: LivingEntity,
        item: ItemStack,
        slot: EquipmentSlot,
        blockHit: net.minecraft.util.hit.BlockHitResult
    ): Boolean {
        if (item.isEmpty) return false

        val properties = resolveProperties(item)
        val itemId = getItemId(item) ?: return false
        val source = SourceReference.item(itemId, slot.name.lowercase())

        for (propDef in properties) {
            val handler = ItemPropertyRegistry.getHandler(propDef.id) ?: continue
            val ctx = ItemPropertyContext(
                entity = entity,
                item = item,
                slot = slot,
                definition = propDef,
                source = source,
                blockHit = blockHit
            )
            try {
                if (handler.onUseOnBlock(ctx)) return true
            } catch (e: Exception) {
                logger.error("Error in onUseOnBlock for property '${propDef.id}'", e)
            }
        }
        return false
    }

    /**
     * Вызывается при правом клике на сущность.
     *
     * @return true если хотя бы одно свойство обработало взаимодействие
     */
    fun onUseOnEntity(
        entity: LivingEntity,
        item: ItemStack,
        slot: EquipmentSlot,
        entityHit: net.minecraft.util.hit.EntityHitResult
    ): Boolean {
        if (item.isEmpty) return false

        val properties = resolveProperties(item)
        val itemId = getItemId(item) ?: return false
        val source = SourceReference.item(itemId, slot.name.lowercase())

        for (propDef in properties) {
            val handler = ItemPropertyRegistry.getHandler(propDef.id) ?: continue
            val ctx = ItemPropertyContext(
                entity = entity,
                item = item,
                slot = slot,
                definition = propDef,
                source = source,
                entityHit = entityHit
            )
            try {
                if (handler.onUseOnEntity(ctx)) return true
            } catch (e: Exception) {
                logger.error("Error in onUseOnEntity for property '${propDef.id}'", e)
            }
        }
        return false
    }

    /**
     * Вызывается при правом клике в воздух.
     *
     * @return true если хотя бы одно свойство обработало взаимодействие
     */
    fun onUse(entity: LivingEntity, item: ItemStack, slot: EquipmentSlot): Boolean {
        if (item.isEmpty) return false

        val properties = resolveProperties(item)
        val itemId = getItemId(item) ?: return false
        val source = SourceReference.item(itemId, slot.name.lowercase())

        for (propDef in properties) {
            val handler = ItemPropertyRegistry.getHandler(propDef.id) ?: continue
            val ctx = ItemPropertyContext(
                entity = entity,
                item = item,
                slot = slot,
                definition = propDef,
                source = source
            )
            try {
                if (handler.onUse(ctx)) return true
            } catch (e: Exception) {
                logger.error("Error in onUse for property '${propDef.id}'", e)
            }
        }
        return false
    }

    // ── Runtime NBT ───────────────────────────────────────────────────────

    /**
     * Добавляет runtime свойство к предмету через NBT.
     *
     * @param item предмет
     * @param property свойство для добавления
     */
    fun addRuntimeProperty(item: ItemStack, property: ItemPropertyDefinition) {
        val nbt = item.getOrCreateNbt()
        val propertiesArray = if (nbt.contains(NBT_PROPERTIES_KEY)) {
            nbt.getList(NBT_PROPERTIES_KEY, 8) // 8 = NbtString type
        } else {
            net.minecraft.nbt.NbtList()
        }

        // Проверяем дублирование
        val existing = getRuntimeProperties(item)
        if (existing.any { it.id == property.id }) {
            logger.debug("Runtime property '${property.id}' already exists on item, skipping")
            return
        }

        // Сериализуем через Gson
        val json = com.google.gson.JsonObject().apply {
            addProperty("id", property.id.toString())
            if (!property.data.isEmpty) {
                add("data", property.data)
            }
        }
        propertiesArray.add(net.minecraft.nbt.NbtString.of(json.toString()))
        nbt.put(NBT_PROPERTIES_KEY, propertiesArray)

        logger.debug("Added runtime property '${property.id}' to item")
    }

    /**
     * Удаляет runtime свойство из предмета.
     */
    fun removeRuntimeProperty(item: ItemStack, propertyId: Identifier) {
        val nbt = item.getNbt() ?: return
        if (!nbt.contains(NBT_PROPERTIES_KEY)) return

        val propertiesArray = nbt.getList(NBT_PROPERTIES_KEY, 8)
        val toRemove = mutableListOf<Int>()

        for (i in 0 until propertiesArray.size) {
            val json = JsonParser.parseString(propertiesArray.getString(i)).asJsonObject
            val id = Identifier.tryParse(json.get("id")?.asString ?: "") ?: continue
            if (id == propertyId) toRemove.add(i)
        }

        // Удаляем в обратном порядке
        for (i in toRemove.reversed()) {
            propertiesArray.removeAt(i)
        }

        if (propertiesArray.isEmpty()) {
            nbt.remove(NBT_PROPERTIES_KEY)
        }

        logger.debug("Removed runtime property '$propertyId' from item")
    }

    /**
     * Устанавливает переопределение владения для конкретного предмета.
     */
    fun setProficiencyOverride(item: ItemStack, proficiencyId: Identifier) {
        item.getOrCreateNbt().putString(NBT_PROFICIENCY_OVERRIDE_KEY, proficiencyId.toString())
    }

    /**
     * Убирает переопределение владения.
     */
    fun clearProficiencyOverride(item: ItemStack) {
        item.getNbt()?.remove(NBT_PROFICIENCY_OVERRIDE_KEY)
    }

    /**
     * Возвращает переопределение владения из NBT или null.
     */
    fun getProficiencyOverride(item: ItemStack): Identifier? {
        val nbt = item.getNbt() ?: return null
        if (!nbt.contains(NBT_PROFICIENCY_OVERRIDE_KEY)) return null
        return Identifier.tryParse(nbt.getString(NBT_PROFICIENCY_OVERRIDE_KEY))
    }

    // ── Внутренняя логика ─────────────────────────────────────────────────

    /**
     * Возвращает итоговый список свойств предмета.
     *
     * Мёрдж: базовые из датапака + runtime из NBT.
     * NBT свойства добавляются поверх, дедупликация по ID.
     */
    fun resolveProperties(item: ItemStack): List<ItemPropertyDefinition> {
        val itemId = getItemId(item) ?: return emptyList()

        // Базовые свойства из датапака
        val base = ItemPropertyRegistry.getItemDefinition(itemId)?.properties ?: emptyList()

        // Runtime свойства из NBT
        val runtime = getRuntimeProperties(item)

        // Мёрдж: base + runtime, дедупликация по ID (runtime не перезаписывает base)
        val baseIds = base.map { it.id }.toSet()
        val uniqueRuntime = runtime.filter { it.id !in baseIds }

        return base + uniqueRuntime
    }

    /**
     * Читает runtime свойства из NBT предмета.
     */
    private fun getRuntimeProperties(item: ItemStack): List<ItemPropertyDefinition> {
        val nbt = item.getNbt() ?: return emptyList()
        if (!nbt.contains(NBT_PROPERTIES_KEY)) return emptyList()

        val propertiesArray = nbt.getList(NBT_PROPERTIES_KEY, 8)
        val result = mutableListOf<ItemPropertyDefinition>()

        for (i in 0 until propertiesArray.size) {
            try {
                val json = JsonParser.parseString(propertiesArray.getString(i)).asJsonObject
                val id = Identifier.tryParse(json.get("id")?.asString ?: "") ?: continue
                val data = json.getAsJsonObject("data") ?: com.google.gson.JsonObject()
                result.add(ItemPropertyDefinition(id, data))
            } catch (e: Exception) {
                logger.warn("Failed to parse runtime property at index $i", e)
            }
        }

        return result
    }

    /**
     * Возвращает Minecraft item ID из ItemStack.
     */
    private fun getItemId(item: ItemStack): Identifier? {
        return net.minecraft.registry.Registries.ITEM.getId(item.item)
    }

    /**
     * Вспомогательный метод для вызова хука у всех свойств предмета.
     */
    private fun invokePropertyHook(
        entity: LivingEntity,
        item: ItemStack,
        slot: EquipmentSlot,
        hook: (omc.boundbyfate.api.item.ItemPropertyHandler, ItemPropertyContext) -> Unit
    ) {
        val properties = resolveProperties(item)
        val itemId = getItemId(item) ?: return
        val source = SourceReference.item(itemId, slot.name.lowercase())

        for (propDef in properties) {
            val handler = ItemPropertyRegistry.getHandler(propDef.id) ?: continue
            val ctx = ItemPropertyContext(
                entity = entity,
                item = item,
                slot = slot,
                definition = propDef,
                source = source
            )
            try {
                hook(handler, ctx)
            } catch (e: Exception) {
                logger.error("Error invoking property hook '${propDef.id}' for item '$itemId'", e)
            }
        }
    }

    /**
     * Возвращает сколько тиков предмет находится в слоте.
     * TODO: хранить в компоненте персонажа
     */
    private fun getTicksHeld(
        entity: LivingEntity,
        item: ItemStack,
        slot: EquipmentSlot,
        currentTick: Long
    ): Int = 0
}
