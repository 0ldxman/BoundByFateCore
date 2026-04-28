package omc.boundbyfate.registry

import omc.boundbyfate.api.stat.StatDefinition
import omc.boundbyfate.registry.core.BbfRegistry

/**
 * Registry для характеристик (Stats).
 * 
 * Хранит все определения характеристик (STR, DEX, CON, INT, WIS, CHA).
 */
object StatRegistry : BbfRegistry<StatDefinition>("stats") {
    
    override fun onRegistrationComplete() {
        super.onRegistrationComplete()
        
        // Можно добавить пост-обработку
        // Например, валидацию ссылок между определениями
    }
}
