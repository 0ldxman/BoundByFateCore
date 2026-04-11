package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.classes.ClassDefinition
import omc.boundbyfate.api.classes.SubclassDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for class and subclass definitions.
 *
 * Populated by ClassLoader on server start from JSON datapacks.
 * Can also be used by other mods to register classes programmatically.
 */
object ClassRegistry {
    private val classes = ConcurrentHashMap<Identifier, ClassDefinition>()
    private val subclasses = ConcurrentHashMap<Identifier, SubclassDefinition>()

    // ── Classes ───────────────────────────────────────────────────────────────

    fun registerClass(definition: ClassDefinition): ClassDefinition {
        val existing = classes.putIfAbsent(definition.id, definition)
        require(existing == null) { "Class with ID ${definition.id} is already registered" }
        return definition
    }

    fun getClass(id: Identifier): ClassDefinition? = classes[id]

    fun getClassOrThrow(id: Identifier): ClassDefinition =
        getClass(id) ?: throw IllegalArgumentException("Unknown class ID: $id")

    fun getAllClasses(): Collection<ClassDefinition> = classes.values.toList()

    fun containsClass(id: Identifier): Boolean = classes.containsKey(id)

    // ── Subclasses ────────────────────────────────────────────────────────────

    fun registerSubclass(definition: SubclassDefinition): SubclassDefinition {
        val existing = subclasses.putIfAbsent(definition.id, definition)
        require(existing == null) { "Subclass with ID ${definition.id} is already registered" }
        return definition
    }

    fun getSubclass(id: Identifier): SubclassDefinition? = subclasses[id]

    fun getSubclassOrThrow(id: Identifier): SubclassDefinition =
        getSubclass(id) ?: throw IllegalArgumentException("Unknown subclass ID: $id")

    fun getSubclassesFor(classId: Identifier): Collection<SubclassDefinition> =
        subclasses.values.filter { it.parentClass == classId }

    fun getAllSubclasses(): Collection<SubclassDefinition> = subclasses.values.toList()

    // ── Reload ────────────────────────────────────────────────────────────────

    fun clearAll() {
        classes.clear()
        subclasses.clear()
    }

    val classCount: Int get() = classes.size
    val subclassCount: Int get() = subclasses.size
}
