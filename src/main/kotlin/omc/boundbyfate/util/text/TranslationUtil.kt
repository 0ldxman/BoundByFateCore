package omc.boundbyfate.util.text

import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Утилиты для работы с локализацией.
 * 
 * Предоставляет helper методы для создания ключей локализации
 * и работы с TranslatableText.
 */
object TranslationUtil {
    
    /**
     * Создаёт ключ локализации для Definition.
     * 
     * Формат: "<type>.<namespace>.<path>"
     * 
     * @param type тип определения (например, "stat", "class", "race")
     * @param id идентификатор
     * @return ключ локализации
     */
    fun definitionKey(type: String, id: Identifier): String =
        "$type.${id.namespace}.${id.path}"
    
    /**
     * Создаёт ключ локализации для описания Definition.
     * 
     * Формат: "<type>.<namespace>.<path>.description"
     */
    fun definitionDescriptionKey(type: String, id: Identifier): String =
        "${definitionKey(type, id)}.description"
    
    /**
     * Создаёт локализованный Text для Definition.
     */
    fun definitionText(type: String, id: Identifier): Text =
        Text.translatable(definitionKey(type, id))
    
    /**
     * Создаёт локализованный Text для описания Definition.
     */
    fun definitionDescriptionText(type: String, id: Identifier): Text =
        Text.translatable(definitionDescriptionKey(type, id))
    
    /**
     * Создаёт ключ локализации для GUI.
     * 
     * Формат: "gui.boundbyfate-core.<path>"
     */
    fun guiKey(path: String): String = "gui.boundbyfate-core.$path"
    
    /**
     * Создаёт локализованный Text для GUI.
     */
    fun guiText(path: String): Text = Text.translatable(guiKey(path))
    
    /**
     * Создаёт ключ локализации для команды.
     * 
     * Формат: "command.boundbyfate-core.<path>"
     */
    fun commandKey(path: String): String = "command.boundbyfate-core.$path"
    
    /**
     * Создаёт локализованный Text для команды.
     */
    fun commandText(path: String): Text = Text.translatable(commandKey(path))
    
    /**
     * Создаёт ключ локализации для сообщения.
     * 
     * Формат: "message.boundbyfate-core.<path>"
     */
    fun messageKey(path: String): String = "message.boundbyfate-core.$path"
    
    /**
     * Создаёт локализованный Text для сообщения.
     */
    fun messageText(path: String): Text = Text.translatable(messageKey(path))
    
    /**
     * Создаёт локализованный Text с аргументами.
     * 
     * Пример:
     * ```kotlin
     * // en_us.json: "message.boundbyfate-core.level_up": "You reached level %s!"
     * val text = TranslationUtil.messageText("level_up", 5)
     * // Результат: "You reached level 5!"
     * ```
     */
    fun messageText(path: String, vararg args: Any): Text =
        Text.translatable(messageKey(path), *args)
}
