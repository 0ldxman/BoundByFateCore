package omc.boundbyfate.api.damage

import net.minecraft.util.Identifier

/**
 * Примеры использования системы типов урона и сопротивлений.
 * 
 * Этот файл демонстрирует различные сценарии работы с уроном.
 */
object DamageExamples {
    
    // ============================================================================
    // Пример 1: Базовое использование ResistanceLevel
    // ============================================================================
    
    fun example1_basicResistance() {
        println("=== Пример 1: Базовое использование ResistanceLevel ===")
        
        // Дварф имеет сопротивление к яду
        val baseDamage = 20f
        val resistance = ResistanceLevel.RESISTANCE
        val finalDamage = baseDamage * resistance.multiplier
        
        println("Базовый урон: $baseDamage")
        println("Сопротивление: ${resistance.name} (×${resistance.multiplier})")
        println("Итоговый урон: $finalDamage")
        // Вывод: 10.0 (20 × 0.5)
    }
    
    // ============================================================================
    // Пример 2: Иммунитет
    // ============================================================================
    
    fun example2_immunity() {
        println("\n=== Пример 2: Иммунитет ===")
        
        // Голем имеет иммунитет к яду
        val baseDamage = 50f
        val immunity = ResistanceLevel.IMMUNITY
        val finalDamage = baseDamage * immunity.multiplier
        
        println("Базовый урон: $baseDamage")
        println("Иммунитет: ${immunity.name} (×${immunity.multiplier})")
        println("Итоговый урон: $finalDamage")
        println("Иммунен: ${immunity.isImmune()}")
        // Вывод: 0.0 (50 × 0.0)
    }
    
    // ============================================================================
    // Пример 3: Уязвимость
    // ============================================================================
    
    fun example3_vulnerability() {
        println("\n=== Пример 3: Уязвимость ===")
        
        // Ледяной элементаль уязвим к огню
        val baseDamage = 15f
        val vulnerability = ResistanceLevel.VULNERABILITY
        val finalDamage = baseDamage * vulnerability.multiplier
        
        println("Базовый урон: $baseDamage")
        println("Уязвимость: ${vulnerability.name} (×${vulnerability.multiplier})")
        println("Итоговый урон: $finalDamage")
        // Вывод: 30.0 (15 × 2.0)
    }
    
    // ============================================================================
    // Пример 4: Комбинирование уровней
    // ============================================================================
    
    fun example4_combiningLevels() {
        println("\n=== Пример 4: Комбинирование уровней ===")
        
        // Раса даёт сопротивление, предмет даёт уязвимость
        val raceResistance = ResistanceLevel.RESISTANCE      // -1
        val itemVulnerability = ResistanceLevel.VULNERABILITY // +1
        val combined = ResistanceLevel.combine(raceResistance, itemVulnerability)
        
        println("Сопротивление от расы: ${raceResistance.name} (${raceResistance.value})")
        println("Уязвимость от предмета: ${itemVulnerability.name} (${itemVulnerability.value})")
        println("Итоговый уровень: ${combined.name} (${combined.value})")
        println("Множитель: ×${combined.multiplier}")
        // Вывод: NORMAL (0), ×1.0
        
        // Два источника сопротивления
        val twoResistances = ResistanceLevel.combine(
            ResistanceLevel.RESISTANCE,
            ResistanceLevel.RESISTANCE
        )
        println("\nДва сопротивления: ${twoResistances.name} (${twoResistances.value})")
        println("Множитель: ×${twoResistances.multiplier}")
        // Вывод: STRONG_RESISTANCE (-2), ×0.25
        
        // Три сопротивления = иммунитет
        val threeResistances = ResistanceLevel.combine(
            ResistanceLevel.RESISTANCE,
            ResistanceLevel.RESISTANCE,
            ResistanceLevel.RESISTANCE
        )
        println("\nТри сопротивления: ${threeResistances.name} (${threeResistances.value})")
        println("Множитель: ×${threeResistances.multiplier}")
        // Вывод: IMMUNITY (-3), ×0.0
    }
    
    // ============================================================================
    // Пример 5: Использование DamageCalculator
    // ============================================================================
    
    fun example5_damageCalculator() {
        println("\n=== Пример 5: DamageCalculator ===")
        
        val fireType = Identifier("dnd", "fire")
        val poisonType = Identifier("dnd", "poison")
        
        // Цель имеет сопротивление к огню и иммунитет к яду
        val resistances = mapOf(
            fireType to ResistanceLevel.RESISTANCE,
            poisonType to ResistanceLevel.IMMUNITY
        )
        
        // Огненный урон
        val fireDamage = DamageCalculator.calculate(
            baseDamage = 20f,
            damageType = fireType,
            resistances = resistances
        )
        println("Огненный урон: 20 → $fireDamage")
        // Вывод: 10.0
        
        // Урон ядом
        val poisonDamage = DamageCalculator.calculate(
            baseDamage = 15f,
            damageType = poisonType,
            resistances = resistances
        )
        println("Урон ядом: 15 → $poisonDamage")
        println("Иммунен к яду: ${DamageCalculator.isImmune(poisonType, resistances)}")
        // Вывод: 0.0, true
    }
    
    // ============================================================================
    // Пример 6: Множественные источники сопротивлений
    // ============================================================================
    
    fun example6_multipleSources() {
        println("\n=== Пример 6: Множественные источники ===")
        
        val fireType = Identifier("dnd", "fire")
        
        // Источники сопротивлений
        val sources = mapOf(
            Identifier("boundbyfate-core", "race_tiefling") to mapOf(
                fireType to -1  // Сопротивление от расы
            ),
            Identifier("boundbyfate-core", "spell_protection") to mapOf(
                fireType to -1  // Сопротивление от заклинания
            ),
            Identifier("boundbyfate-core", "cursed_item") to mapOf(
                fireType to 1   // Уязвимость от проклятого предмета
            )
        )
        
        // Вычисляем итоговый урон
        val baseDamage = 30f
        val finalDamage = DamageCalculator.calculateWithSources(
            baseDamage = baseDamage,
            damageType = fireType,
            sources = sources
        )
        
        val effectiveResistance = DamageCalculator.getEffectiveResistance(fireType, sources)
        
        println("Базовый урон: $baseDamage")
        println("Источники:")
        println("  - Раса (Тифлинг): -1 (сопротивление)")
        println("  - Заклинание: -1 (сопротивление)")
        println("  - Проклятый предмет: +1 (уязвимость)")
        println("Итоговый уровень: ${effectiveResistance.name} (${effectiveResistance.value})")
        println("Множитель: ×${effectiveResistance.multiplier}")
        println("Итоговый урон: $finalDamage")
        // Вывод: RESISTANCE (-1), ×0.5, 15.0
    }
    
    // ============================================================================
    // Пример 7: Реальный сценарий - Дварф против яда
    // ============================================================================
    
    fun example7_dwarfVsPoison() {
        println("\n=== Пример 7: Дварф против яда ===")
        
        val poisonType = Identifier("dnd", "poison")
        
        // Дварф имеет расовое сопротивление к яду
        val sources = mapOf(
            Identifier("dnd", "race_dwarf") to mapOf(
                poisonType to ResistanceLevel.RESISTANCE.value
            )
        )
        
        // Ядовитая ловушка наносит 24 урона
        val trapDamage = 24f
        val finalDamage = DamageCalculator.calculateWithSources(
            baseDamage = trapDamage,
            damageType = poisonType,
            sources = sources
        )
        
        println("Ядовитая ловушка активирована!")
        println("Базовый урон: $trapDamage")
        println("Дварф имеет сопротивление к яду (Dwarven Resilience)")
        println("Итоговый урон: $finalDamage")
        // Вывод: 12.0
    }
    
    // ============================================================================
    // Пример 8: Варвар в ярости
    // ============================================================================
    
    fun example8_barbarianRage() {
        println("\n=== Пример 8: Варвар в ярости ===")
        
        val slashingType = Identifier("dnd", "slashing")
        val piercingType = Identifier("dnd", "piercing")
        val bludgeoningType = Identifier("dnd", "bludgeoning")
        val fireType = Identifier("dnd", "fire")
        
        // Варвар в ярости имеет сопротивление к физическому урону
        val sources = mapOf(
            Identifier("dnd", "class_feature_rage") to mapOf(
                slashingType to ResistanceLevel.RESISTANCE.value,
                piercingType to ResistanceLevel.RESISTANCE.value,
                bludgeoningType to ResistanceLevel.RESISTANCE.value
            )
        )
        
        println("Варвар активировал Ярость!")
        println("Получает сопротивление к физическому урону")
        println()
        
        // Атака мечом (рубящий урон)
        val swordDamage = 18f
        val finalSwordDamage = DamageCalculator.calculateWithSources(
            baseDamage = swordDamage,
            damageType = slashingType,
            sources = sources
        )
        println("Атака мечом: $swordDamage → $finalSwordDamage")
        
        // Огненный шар (огненный урон - нет сопротивления)
        val fireballDamage = 28f
        val finalFireballDamage = DamageCalculator.calculateWithSources(
            baseDamage = fireballDamage,
            damageType = fireType,
            sources = sources
        )
        println("Огненный шар: $fireballDamage → $finalFireballDamage")
        // Вывод: 9.0 (меч), 28.0 (огонь)
    }
    
    // ============================================================================
    // Пример 9: Проверка типов урона
    // ============================================================================
    
    fun example9_damageTypes() {
        println("\n=== Пример 9: Типы урона D&D 5e ===")
        
        val damageTypes = listOf(
            "slashing" to listOf("physical", "weapon"),
            "piercing" to listOf("physical", "weapon"),
            "bludgeoning" to listOf("physical", "weapon"),
            "fire" to listOf("magical", "elemental"),
            "cold" to listOf("magical", "elemental"),
            "lightning" to listOf("magical", "elemental"),
            "thunder" to listOf("magical", "elemental"),
            "acid" to listOf("magical", "elemental"),
            "poison" to listOf("magical"),
            "necrotic" to listOf("magical", "dark"),
            "radiant" to listOf("magical", "holy"),
            "force" to listOf("magical", "pure"),
            "psychic" to listOf("magical", "mental")
        )
        
        println("Физический урон:")
        damageTypes.filter { it.second.contains("physical") }.forEach {
            println("  - ${it.first}")
        }
        
        println("\nМагический урон:")
        damageTypes.filter { it.second.contains("magical") }.forEach {
            println("  - ${it.first} (${it.second.joinToString(", ")})")
        }
    }
    
    // ============================================================================
    // Запуск всех примеров
    // ============================================================================
    
    @JvmStatic
    fun main(args: Array<String>) {
        example1_basicResistance()
        example2_immunity()
        example3_vulnerability()
        example4_combiningLevels()
        example5_damageCalculator()
        example6_multipleSources()
        example7_dwarfVsPoison()
        example8_barbarianRage()
        example9_damageTypes()
    }
}
