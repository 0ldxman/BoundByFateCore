package omc.boundbyfate.manual

import omc.boundbyfate.core.tags.TagTree

/**
 * Файл для быстрых ручных проверок в процессе разработки.
 * Можно запустить прямо из IDE.
 */
fun main() {
    println("=== BoundByFate Manual Quick Check ===")
    
    val tree = TagTree()
    
    println("Добавляем теги...")
    tree.addTag("stat:strength:base:10")
    tree.addTag("stat:strength:bonus:rage:4")
    tree.addTag("resistance:fire:1")
    
    println("Все теги силы:")
    tree.getParsedTags("stat:strength").forEach { tag -> 
        println(" - Path: ${tag.rawPath}, Value: ${tag.asInt()}") 
    }
    
    println("Проверка наличия resistance:fire: ${tree.hasTag("resistance:fire")}")
    
    println("Удаляем бонус ярости...")
    tree.removeTag("stat:strength:bonus:rage")
    
    println("Теги силы после удаления:")
    tree.getParsedTags("stat:strength").forEach { tag -> 
        println(" - ${tag.rawPath}") 
    }
    
    println("=== Проверка завершена ===")
}
