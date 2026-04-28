package omc.boundbyfate.api.core

import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Базовый интерфейс для всех определений (Definition) в системе.
 * 
 * Definition — это неизменяемое описание игрового элемента из Registries.
 * Примеры: ClassDefinition, RaceDefinition, StatDefinition, FeatDefinition.
 * 
 * Философия:
 * - Registries хранят Definition (правила игры)
 * - World Data хранит ID и базовые значения (лист персонажа)
 * - Attachments хранят runtime состояние (текущее состояние)
 * 
 * Локализация:
 * - JSON хранит ключи локализации (например, "stat.boundbyfate-core.strength")
 * - Lang файлы содержат переводы
 * - getTranslatedName() возвращает локализованный Text
 */
interface Definition {
    /**
     * Уникальный идентификатор определения.
     * Используется для ссылок из World Data и других систем.
     */
    val id: Identifier
    
    /**
     * Ключ локализации для имени.
     * Формат: "<type>.<namespace>.<path>"
     * Например: "stat.boundbyfate-core.strength"
     */
    fun getTranslationKey(): String
    
    /**
     * Ключ локализации для описания.
     * Формат: "<type>.<namespace>.<path>.description"
     * Например: "stat.boundbyfate-core.strength.description"
     */
    fun getDescriptionKey(): String = "${getTranslationKey()}.description"
    
    /**
     * Получает локализованное имя.
     */
    fun getTranslatedName(): Text = Text.translatable(getTranslationKey())
    
    /**
     * Получает локализованное описание.
     */
    fun getTranslatedDescription(): Text = Text.translatable(getDescriptionKey())
}
