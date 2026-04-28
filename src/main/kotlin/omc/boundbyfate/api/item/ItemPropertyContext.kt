package omc.boundbyfate.api.item

import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import omc.boundbyfate.util.source.SourceReference

/**
 * Контекст применения свойства предмета.
 *
 * Передаётся в [ItemPropertyHandler] при каждом хуке.
 * Аналог [omc.boundbyfate.api.effect.EffectContext].
 *
 * ## Использование
 *
 * ```kotlin
 * object StatBonus : ItemPropertyHandler() {
 *     override fun onEquip(ctx: ItemPropertyContext) {
 *         val stat  = ctx.data.requireId("stat")
 *         val value = ctx.data.requireInt("value")
 *         // ctx.entity  — кто надел
 *         // ctx.item    — сам предмет
 *         // ctx.slot    — в какой слот
 *         // ctx.source  — SourceReference.item(itemId, slot) для снятия
 *     }
 * }
 * ```
 *
 * @property entity сущность которая держит/носит предмет
 * @property item предмет
 * @property slot слот экипировки
 * @property definition определение свойства с параметрами
 * @property source источник для идентификации при снятии
 * @property ticksHeld сколько тиков предмет в этом слоте (для tick хуков)
 * @property blockHit результат попадания по блоку (для onUseOnBlock)
 * @property entityHit результат попадания по сущности (для onUseOnEntity)
 */
data class ItemPropertyContext(
    val entity: LivingEntity,
    val item: ItemStack,
    val slot: EquipmentSlot,
    val definition: ItemPropertyDefinition,
    val source: SourceReference,
    val ticksHeld: Int = 0,
    val blockHit: BlockHitResult? = null,
    val entityHit: EntityHitResult? = null
) {
    /**
     * Удобный доступ к параметрам свойства.
     */
    val data: ItemPropertyData get() = definition.propertyData

    /**
     * Проверяет является ли сущность серверным игроком.
     */
    fun isServerPlayer(): Boolean = entity is ServerPlayerEntity

    /**
     * Возвращает сущность как серверного игрока или null.
     */
    fun asServerPlayer(): ServerPlayerEntity? = entity as? ServerPlayerEntity

    /**
     * Проверяет, находится ли предмет в основной руке.
     */
    fun isMainHand(): Boolean = slot == EquipmentSlot.MAINHAND

    /**
     * Проверяет, находится ли предмет в левой руке.
     */
    fun isOffhand(): Boolean = slot == EquipmentSlot.OFFHAND

    /**
     * Проверяет, является ли слот слотом брони.
     */
    fun isArmorSlot(): Boolean = slot in listOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    )
}
