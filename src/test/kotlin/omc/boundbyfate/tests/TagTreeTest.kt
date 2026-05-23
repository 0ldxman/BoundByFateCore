package omc.boundbyfate.tests

import omc.boundbyfate.core.tags.TagTree
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class TagTreeTest {

    @Test
    fun `test add and has tag`() {
        val tree = TagTree()
        tree.addTag("stat:strength:base:10")
        
        assertTrue(tree.hasTag("stat:strength:base:10"), "Should have full tag")
        assertTrue(tree.hasTag("stat:strength"), "Should have partial path")
        assertFalse(tree.hasTag("stat:agility"), "Should not have non-existent tag")
    }

    @Test
    fun `test parsed tag data`() {
        val raw = "stat:strength:bonus:crown:2"
        val tree = TagTree()
        tree.addTag(raw)
        
        val tags = tree.getParsedTags("stat:strength")
        val tag = tags.first()
        
        println("--- Tag Serialization Check ---")
        println("Raw string: $raw")
        println("Parsed Category: ${tag.category}")
        println("Parsed ID: ${tag.id}")
        println("Parsed Value: ${tag.asInt()}")
        println("-------------------------------")
        
        assertEquals("stat", tag.category)
        assertEquals("strength", tag.id)
        assertEquals(2, tag.asInt())
    }

    @Test
    fun `test get tags by prefix`() {
        val tree = TagTree()
        tree.addTag("stat:strength:base:10")
        tree.addTag("stat:strength:bonus:crown:2")
        
        val strengthTags = tree.getParsedTags("stat:strength")
        assertEquals(2, strengthTags.size)
    }

    @Test
    fun `test remove tag`() {
        val tree = TagTree()
        tree.addTag("stat:strength:base:10")
        tree.addTag("stat:strength:bonus:2")
        
        tree.removeTag("stat:strength:bonus")
        
        assertTrue(tree.hasTag("stat:strength:base:10"))
        assertFalse(tree.hasTag("stat:strength:bonus:2"))
        assertFalse(tree.hasTag("stat:strength:bonus"))
    }
}
