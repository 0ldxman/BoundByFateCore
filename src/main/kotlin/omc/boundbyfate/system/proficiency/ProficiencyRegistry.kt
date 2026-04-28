package omc.boundbyfate.system.proficiency

import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import omc.boundbyfate.api.proficiency.ProficiencyDefinition
import omc.boundbyfate.api.proficiency.ProficiencyMatch
import org.slf4j.LoggerFactory

/**
 * Реестр владений (Proficiencies).
 * 
 * Хранит все определения владений и предоставляет API для работы с ними.
 * Поддерживает runtime изменения для ГМ.
 */
object ProficiencyRegistry {
    private val logger = LoggerFactory.getLogger(ProficiencyRegistry::class.java)
    
    /**
     * Базовые определения владений из JSON (неизменяемые).
     */
    private val definitions = mutableMapOf<Identifier, ProficiencyDefinition>()
    
    /**
     * Runtime изменения matches (изменяемые ГМ).
     * 
     * Ключ: ID владения
     * Значение: Список дополнительных matches
     */
    private val runtimeMatches = mutableMapOf<Identifier, MutableSet<String>>()
    
    /**
     * Кэш распарсенных matches для производительности.
     */
    private val matchCache = mutableMapOf<Identifier, List<ProficiencyMatch>>()
    
    /**
     * Регистрирует владение из JSON.
     */
    fun register(definition: ProficiencyDefinition) {
        definitions[definition.id] = definition
        invalidateCache(definition.id)
        logger.debug("Registered proficiency: ${definition.id}")
    }
    
    /**
     * Получает определение владения по ID.
     */
    fun get(id: Identifier): ProficiencyDefinition? {
        return definitions[id]
    }
    
    /**
     * Получает все зарегистрированные владения.
     */
    fun getAll(): Collection<ProficiencyDefinition> {
        return definitions.values
    }
    
    /**
     * Получает владения по тегу.
     */
    fun getByTag(tag: String): List<ProficiencyDefinition> {
        return definitions.values.filter { it.hasTag(tag) }
    }
    
    /**
     * Проверяет, зарегистрировано ли владение.
     */
    fun contains(id: Identifier): Boolean {
        return definitions.containsKey(id)
    }
    
    /**
     * Получает все matches для владения (базовые + runtime).
     */
    fun getMatches(proficiency: Identifier): List<String> {
        val base = definitions[proficiency]?.matches ?: emptyList()
        val runtime = runtimeMatches[proficiency] ?: emptySet()
        return base + runtime
    }
    
    /**
     * Получает распарсенные matches для владения.
     */
    fun getParsedMatches(proficiency: Identifier): List<ProficiencyMatch> {
        return matchCache.getOrPut(proficiency) {
            val matches = getMatches(proficiency)
            matches.mapNotNull { str ->
                try {
                    when {
                        str.startsWith("#") -> {
                            val tagId = str.substring(1)
                            ProficiencyMatch.Tag(Identifier.tryParse(tagId) ?: return@mapNotNull null)
                        }
                        str.startsWith("@") -> {
                            val profId = str.substring(1)
                            ProficiencyMatch.Proficiency(Identifier.tryParse(profId) ?: return@mapNotNull null)
                        }
                        else -> {
                            ProficiencyMatch.Item(Identifier.tryParse(str) ?: return@mapNotNull null)
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse match '$str' for proficiency $proficiency", e)
                    null
                }
            }
        }
    }
    
    /**
     * Добавляет runtime match для владения.
     * 
     * Используется ГМ для динамического изменения владений.
     */
    fun addRuntimeMatch(proficiency: Identifier, match: String) {
        if (!contains(proficiency)) {
            logger.warn("Cannot add runtime match to non-existent proficiency: $proficiency")
            return
        }
        
        runtimeMatches.getOrPut(proficiency) { mutableSetOf() }.add(match)
        invalidateCache(proficiency)
        logger.info("Added runtime match '$match' to proficiency $proficiency")
    }
    
    /**
     * Удаляет runtime match для владения.
     */
    fun removeRuntimeMatch(proficiency: Identifier, match: String) {
        runtimeMatches[proficiency]?.remove(match)
        invalidateCache(proficiency)
        logger.info("Removed runtime match '$match' from proficiency $proficiency")
    }
    
    /**
     * Очищает все runtime matches для владения.
     */
    fun clearRuntimeMatches(proficiency: Identifier) {
        runtimeMatches.remove(proficiency)
        invalidateCache(proficiency)
        logger.info("Cleared all runtime matches for proficiency $proficiency")
    }
    
    /**
     * Получает все runtime matches.
     */
    fun getAllRuntimeMatches(): Map<Identifier, Set<String>> {
        return runtimeMatches.toMap()
    }
    
    /**
     * Проверяет, подходит ли предмет под владение.
     * 
     * @param proficiency ID владения
     * @param item проверяемый предмет
     * @param visited множество уже посещённых владений (для предотвращения циклов)
     * @return true если предмет подходит
     */
    fun matches(proficiency: Identifier, item: ItemStack, visited: MutableSet<Identifier> = mutableSetOf()): Boolean {
        // Защита от циклических зависимостей
        if (proficiency in visited) {
            logger.warn("Circular proficiency dependency detected: $proficiency")
            return false
        }
        visited.add(proficiency)
        
        val matches = getParsedMatches(proficiency)
        
        for (match in matches) {
            when (match) {
                is ProficiencyMatch.Tag -> {
                    // Проверка по тегу
                    val tagKey = TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, match.tag)
                    if (item.isIn(tagKey)) {
                        return true
                    }
                }
                
                is ProficiencyMatch.Item -> {
                    // Проверка по конкретному предмету
                    val itemId = Registries.ITEM.getId(item.item)
                    if (itemId == match.item) {
                        return true
                    }
                }
                
                is ProficiencyMatch.Proficiency -> {
                    // Рекурсивная проверка другого владения
                    if (matches(match.proficiency, item, visited)) {
                        return true
                    }
                }
            }
        }
        
        return false
    }
    
    /**
     * Находит все владения которые подходят для предмета.
     */
    fun findMatchingProficiencies(item: ItemStack): List<Identifier> {
        return definitions.keys.filter { profId ->
            matches(profId, item)
        }
    }
    
    /**
     * Инвалидирует кэш для владения.
     */
    private fun invalidateCache(proficiency: Identifier) {
        matchCache.remove(proficiency)
    }
    
    /**
     * Очищает весь реестр (для перезагрузки).
     */
    fun clear() {
        definitions.clear()
        runtimeMatches.clear()
        matchCache.clear()
        logger.info("Proficiency registry cleared")
    }
    
    /**
     * Валидирует все владения (проверка на циклические зависимости).
     */
    fun validate() {
        for (definition in definitions.values) {
            try {
                definition.validate()
                
                // Проверка на циклические зависимости
                val visited = mutableSetOf<Identifier>()
                checkCycles(definition.id, visited, mutableSetOf())
            } catch (e: Exception) {
                logger.error("Validation failed for proficiency ${definition.id}", e)
            }
        }
    }
    
    /**
     * Проверяет циклические зависимости в иерархии владений.
     */
    private fun checkCycles(
        proficiency: Identifier,
        visited: MutableSet<Identifier>,
        stack: MutableSet<Identifier>
    ) {
        if (proficiency in stack) {
            throw IllegalStateException("Circular proficiency dependency detected: $proficiency")
        }
        
        if (proficiency in visited) {
            return
        }
        
        visited.add(proficiency)
        stack.add(proficiency)
        
        val matches = getParsedMatches(proficiency)
        for (match in matches) {
            if (match is ProficiencyMatch.Proficiency) {
                checkCycles(match.proficiency, visited, stack)
            }
        }
        
        stack.remove(proficiency)
    }
}
