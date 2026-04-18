package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

enum class GoalStatus { ACTIVE, COMPLETED, FAILED, CANCELLED }
enum class TaskStatus { PENDING, CURRENT, COMPLETED, FAILED, CANCELLED }

data class GoalTask(
    val id: String,
    val description: String,
    val goalDescriptionOverride: String = "",  // Description shown for goal when this task is active
    val status: TaskStatus = TaskStatus.CURRENT,
    val order: Int = 0  // Task order in the list
) {
    companion object {
        val CODEC: Codec<GoalTask> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(GoalTask::id),
                Codec.STRING.fieldOf("description").forGetter(GoalTask::description),
                Codec.STRING.optionalFieldOf("goalDescriptionOverride", "").forGetter(GoalTask::goalDescriptionOverride),
                Codec.STRING.fieldOf("status").xmap(
                    { TaskStatus.valueOf(it) }, { it.name }
                ).forGetter(GoalTask::status),
                Codec.INT.optionalFieldOf("order", 0).forGetter(GoalTask::order)
            ).apply(instance, ::GoalTask)
        }
    }
}

data class PersonalGoal(
    val id: String,
    val title: String,
    val description: String,
    val motivationId: String?,       // linked motivation (null = standalone)
    val tasks: List<GoalTask> = emptyList(),
    val currentTaskIndex: Int = 0,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null
) {
    val currentTask: GoalTask? get() = tasks.getOrNull(currentTaskIndex)
    val isFinished: Boolean get() = status != GoalStatus.ACTIVE

    companion object {
        val CODEC: Codec<PersonalGoal> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(PersonalGoal::id),
                Codec.STRING.fieldOf("title").forGetter(PersonalGoal::title),
                Codec.STRING.fieldOf("description").forGetter(PersonalGoal::description),
                Codec.STRING.optionalFieldOf("motivationId", "").forGetter { it.motivationId ?: "" },
                Codec.list(GoalTask.CODEC).optionalFieldOf("tasks", emptyList()).forGetter(PersonalGoal::tasks),
                Codec.INT.optionalFieldOf("currentTaskIndex", 0).forGetter(PersonalGoal::currentTaskIndex),
                Codec.STRING.fieldOf("status").xmap(
                    { GoalStatus.valueOf(it) }, { it.name }
                ).forGetter(PersonalGoal::status),
                Codec.LONG.optionalFieldOf("createdAt", 0L).forGetter(PersonalGoal::createdAt),
                Codec.LONG.optionalFieldOf("closedAt", -1L).forGetter { it.closedAt ?: -1L }
            ).apply(instance) { id, title, desc, motId, tasks, taskIdx, status, created, closed ->
                PersonalGoal(id, title, desc, motId.ifEmpty { null }, tasks, taskIdx, status, created, if (closed == -1L) null else closed)
            }
        }
    }
}

data class Motivation(
    val id: String,
    val text: String,
    val addedByGm: Boolean = true,   // false = proposed by player, accepted by GM
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        val CODEC: Codec<Motivation> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(Motivation::id),
                Codec.STRING.fieldOf("text").forGetter(Motivation::text),
                Codec.BOOL.optionalFieldOf("addedByGm", true).forGetter(Motivation::addedByGm),
                Codec.BOOL.optionalFieldOf("isActive", true).forGetter(Motivation::isActive),
                Codec.LONG.optionalFieldOf("createdAt", 0L).forGetter(Motivation::createdAt)
            ).apply(instance, ::Motivation)
        }
    }
}

/** Pending proposal from a player — awaiting GM approval. */
data class MotivationProposal(
    val id: String,
    val text: String,
    val proposedBy: String,          // player name
    val proposedAt: Long = System.currentTimeMillis()
) {
    companion object {
        val CODEC: Codec<MotivationProposal> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(MotivationProposal::id),
                Codec.STRING.fieldOf("text").forGetter(MotivationProposal::text),
                Codec.STRING.fieldOf("proposedBy").forGetter(MotivationProposal::proposedBy),
                Codec.LONG.optionalFieldOf("proposedAt", 0L).forGetter(MotivationProposal::proposedAt)
            ).apply(instance, ::MotivationProposal)
        }
    }
}

data class PlayerMotivationData(
    val motivations: List<Motivation> = emptyList(),
    val proposals: List<MotivationProposal> = emptyList(),
    val goals: List<PersonalGoal> = emptyList()
) {
    companion object {
        val CODEC: Codec<PlayerMotivationData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.list(Motivation.CODEC).optionalFieldOf("motivations", emptyList()).forGetter(PlayerMotivationData::motivations),
                Codec.list(MotivationProposal.CODEC).optionalFieldOf("proposals", emptyList()).forGetter(PlayerMotivationData::proposals),
                Codec.list(PersonalGoal.CODEC).optionalFieldOf("goals", emptyList()).forGetter(PlayerMotivationData::goals)
            ).apply(instance, ::PlayerMotivationData)
        }
    }
}
