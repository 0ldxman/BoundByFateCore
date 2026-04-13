package omc.boundbyfate.system.ability

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.dice.DiceRoller
import omc.boundbyfate.component.ConcentrationData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import org.slf4j.LoggerFactory
import kotlin.math.max

/**
 * Система концентрации.
 * 
 * Управляет концентрацией на заклинаниях:
 * - Отслеживает активное заклинание концентрации
 * - Обрабатывает спасброски при получении урона
 * - Прерывает концентрацию при необходимости
 */
object ConcentrationSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    
    /**
     * Начинает концентрацию на заклинании.
     * Прерывает предыдущую концентрацию, если она была.
     */
    fun startConcentration(player: ServerPlayerEntity, abilityId: Identifier) {
        // Прерываем предыдущую концентрацию
        val existing = player.getAttachedOrElse(BbfAttachments.CONCENTRATION, null)
        if (existing != null) {
            breakConcentration(player, "Started new concentration")
        }
        
        // Начинаем новую концентрацию
        val data = ConcentrationData(
            abilityId = abilityId,
            startTick = player.world.time
        )
        player.setAttached(BbfAttachments.CONCENTRATION, data)
        
        // Отправляем обновление клиенту
        omc.boundbyfate.network.ServerPacketHandler.updateConcentration(player)
        
        logger.debug("${player.name.string} started concentrating on $abilityId")
    }
    
    /**
     * Прерывает концентрацию.
     */
    fun breakConcentration(player: ServerPlayerEntity, reason: String) {
        val data = player.getAttachedOrElse(BbfAttachments.CONCENTRATION, null)
            ?: return
        
        player.setAttached(BbfAttachments.CONCENTRATION, null)
        
        // Отправляем обновление клиенту
        omc.boundbyfate.network.ServerPacketHandler.updateConcentration(player)
        
        // TODO: Отправить пакет клиенту
        // TODO: Прервать эффекты заклинания
        
        logger.debug("${player.name.string} lost concentration on ${data.abilityId}: $reason")
    }
    
    /**
     * Проверяет концентрацию при получении урона.
     * Вызывается из LivingEntityDamageMixin.
     * 
     * @param player Игрок, получивший урон
     * @param damage Количество урона
     */
    fun onDamage(player: ServerPlayerEntity, damage: Float) {
        val data = player.getAttachedOrElse(BbfAttachments.CONCENTRATION, null)
            ?: return
        
        // DC = 10 или половина урона (что больше)
        val dc = max(10, (damage / 2).toInt())
        
        // Спасбросок на Constitution
        val success = makeConcentrationSave(player, dc)
        
        if (!success) {
            breakConcentration(player, "Failed concentration save (DC $dc)")
        } else {
            logger.debug("${player.name.string} maintained concentration (DC $dc)")
        }
    }
    
    /**
     * Получает активное заклинание концентрации.
     */
    fun getConcentration(player: ServerPlayerEntity): Identifier? {
        return player.getAttachedOrElse(BbfAttachments.CONCENTRATION, null)?.abilityId
    }
    
    /**
     * Проверяет, концентрируется ли игрок на заклинании.
     */
    fun isConcentrating(player: ServerPlayerEntity): Boolean {
        return player.getAttachedOrElse(BbfAttachments.CONCENTRATION, null) != null
    }
    
    // ═══ PRIVATE HELPERS ═══
    
    /**
     * Выполняет спасбросок на концентрацию.
     */
    private fun makeConcentrationSave(player: ServerPlayerEntity, dc: Int): Boolean {
        val stats = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        
        val rollResult = DiceRoller.rollD20()
        val roll = rollResult.total - rollResult.modifier // Get just the die roll
        val conMod = stats?.getStatValue(BbfStats.CONSTITUTION.id)?.dndModifier ?: 0
        
        // TODO: Добавить бонус мастерства, если есть proficiency в CON saves
        val total = roll + conMod
        
        return total >= dc
    }
}
