package omc.boundbyfate.core.tags

/**
 * Объект распарсенного тега по протоколу категория:идентификатор:данные
 */
data class ParsedTag(val rawPath: String) {
    private val segments = rawPath.split(":")
    
    val category: String = segments.getOrNull(0) ?: ""
    val id: String = segments.getOrNull(1) ?: ""
    
    /** Все сегменты после категории и ID */
    val data: List<String> = if (segments.size > 2) segments.drop(2) else emptyList()
    
    /** Последний сегмент как строковое значение */
    val value: String = segments.lastOrNull() ?: ""

    fun asInt(): Int = value.toIntOrNull() ?: 0
    fun asDouble(): Double = value.toDoubleOrNull() ?: 0.0
    fun asBoolean(): Boolean = value.lowercase() == "true"
    
    override fun toString(): String = rawPath
}

/**
 * Ядро системы тегов.
 * Теги хранятся в виде иерархического дерева.
 */
class TagTree {
    private val root = TagNode("")

    /** Добавляет тег в дерево */
    fun addTag(tagPath: String) {
        val segments = tagPath.split(":")
        var current = root
        for (segment in segments) {
            current = current.children.getOrPut(segment) { TagNode(segment) }
        }
    }

    /** Проверяет наличие пути */
    fun hasTag(path: String): Boolean = findNode(path) != null

    /** Удаляет тег или ветку */
    fun removeTag(path: String) {
        val segments = path.split(":")
        if (segments.isEmpty()) return
        removeRecursive(root, segments, 0)
    }

    /** Получает все теги в виде объектов ParsedTag */
    fun getParsedTags(pathPrefix: String): List<ParsedTag> {
        val node = findNode(pathPrefix) ?: return emptyList()
        val result = mutableListOf<String>()
        collectTags(node, pathPrefix, result)
        return result.map { ParsedTag(it) }
    }

    /** Быстрое получение числового значения первого найденного тега */
    fun getIntValue(path: String): Int = getParsedTags(path).firstOrNull()?.asInt() ?: 0

    private fun findNode(path: String): TagNode? {
        val segments = path.split(":")
        var current = root
        for (segment in segments) {
            current = current.children[segment] ?: return null
        }
        return current
    }

    private fun collectTags(node: TagNode, currentPath: String, result: MutableList<String>) {
        if (node.children.isEmpty()) {
            result.add(currentPath)
            return
        }
        for ((name, child) in node.children) {
            collectTags(child, if (currentPath.isEmpty()) name else "$currentPath:$name", result)
        }
    }

    private fun removeRecursive(current: TagNode, segments: List<String>, index: Int): Boolean {
        if (index == segments.size) return true
        val segment = segments[index]
        val child = current.children[segment] ?: return false
        if (removeRecursive(child, segments, index + 1)) {
            current.children.remove(segment)
        }
        return current.children.isEmpty()
    }

    private class TagNode(val name: String) {
        val children = mutableMapOf<String, TagNode>()
    }
}
