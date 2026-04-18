package omc.boundbyfate.system.identity

import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.component.*
import omc.boundbyfate.registry.BbfAttachments
import java.util.UUID

object MotivationSystem {

    private fun identity(player: ServerPlayerEntity) =
        player.getAttachedOrCreate(BbfAttachments.PLAYER_IDENTITY)

    private fun setMotivationData(player: ServerPlayerEntity, data: PlayerMotivationData) {
        val id = identity(player)
        player.setAttached(BbfAttachments.PLAYER_IDENTITY, id.copy(motivationData = data))
    }

    // ── Motivations ───────────────────────────────────────────────────────────

    fun getMotivations(player: ServerPlayerEntity): List<Motivation> =
        identity(player).motivationData.motivations

    fun addMotivation(player: ServerPlayerEntity, text: String, byGm: Boolean = true): String {
        val md = identity(player).motivationData
        val id = UUID.randomUUID().toString()
        setMotivationData(player, md.copy(motivations = md.motivations + Motivation(id, text, byGm)))
        return id
    }

    fun removeMotivation(player: ServerPlayerEntity, id: String): Boolean {
        val md = identity(player).motivationData
        val new = md.motivations.filter { it.id != id }
        if (new.size == md.motivations.size) return false
        // Freeze goals linked to this motivation
        val frozenGoals = md.goals.map { goal ->
            if (goal.motivationId == id && goal.status == GoalStatus.ACTIVE)
                goal.copy(status = GoalStatus.CANCELLED, closedAt = System.currentTimeMillis())
            else goal
        }
        setMotivationData(player, md.copy(motivations = new, goals = frozenGoals))
        return true
    }

    fun updateMotivation(player: ServerPlayerEntity, id: String, text: String): Boolean {
        val md = identity(player).motivationData
        val new = md.motivations.map { if (it.id == id) it.copy(text = text) else it }
        if (new == md.motivations) return false
        setMotivationData(player, md.copy(motivations = new))
        return true
    }

    // ── Proposals ─────────────────────────────────────────────────────────────

    fun getProposals(player: ServerPlayerEntity): List<MotivationProposal> =
        identity(player).motivationData.proposals

    /** Player proposes a motivation — goes to GM for approval. */
    fun addProposal(player: ServerPlayerEntity, text: String): String {
        val md = identity(player).motivationData
        val id = UUID.randomUUID().toString()
        val proposal = MotivationProposal(id, text, player.name.string)
        setMotivationData(player, md.copy(proposals = md.proposals + proposal))
        return id
    }

    /** GM accepts a proposal — converts it to a real motivation. */
    fun acceptProposal(player: ServerPlayerEntity, proposalId: String): Boolean {
        val md = identity(player).motivationData
        val proposal = md.proposals.find { it.id == proposalId } ?: return false
        val newProposals = md.proposals.filter { it.id != proposalId }
        val newMotivation = Motivation(UUID.randomUUID().toString(), proposal.text, addedByGm = false)
        setMotivationData(player, md.copy(proposals = newProposals, motivations = md.motivations + newMotivation))
        return true
    }

    /** GM rejects a proposal — removes it. */
    fun rejectProposal(player: ServerPlayerEntity, proposalId: String): Boolean {
        val md = identity(player).motivationData
        val new = md.proposals.filter { it.id != proposalId }
        if (new.size == md.proposals.size) return false
        setMotivationData(player, md.copy(proposals = new))
        return true
    }

    // ── Goals ─────────────────────────────────────────────────────────────────

    fun getGoals(player: ServerPlayerEntity): List<PersonalGoal> =
        identity(player).motivationData.goals

    fun addGoal(player: ServerPlayerEntity, title: String, description: String, motivationId: String?, tasks: List<String>): String {
        val md = identity(player).motivationData
        val id = UUID.randomUUID().toString()
        val goalTasks = tasks.mapIndexed { i, desc ->
            GoalTask(
                id = UUID.randomUUID().toString(),
                description = desc,
                goalDescriptionOverride = "",
                status = if (i == 0) TaskStatus.CURRENT else TaskStatus.CURRENT,
                order = i
            )
        }
        val goal = PersonalGoal(id, title, description, motivationId, goalTasks)
        setMotivationData(player, md.copy(goals = md.goals + goal))
        return id
    }

    fun removeGoal(player: ServerPlayerEntity, goalId: String): Boolean {
        val md = identity(player).motivationData
        val new = md.goals.filter { it.id != goalId }
        if (new.size == md.goals.size) return false
        setMotivationData(player, md.copy(goals = new))
        return true
    }

    /**
     * Advances the current task of a goal to the given status,
     * then moves to the next task (if any).
     */
    fun advanceTask(player: ServerPlayerEntity, goalId: String, taskStatus: TaskStatus, newDescription: String? = null): Boolean {
        val md = identity(player).motivationData
        val goal = md.goals.find { it.id == goalId } ?: return false
        if (goal.isFinished) return false

        val updatedTasks = goal.tasks.toMutableList()
        val currentIdx = goal.currentTaskIndex
        if (currentIdx < updatedTasks.size) {
            updatedTasks[currentIdx] = updatedTasks[currentIdx].copy(status = taskStatus)
        }

        // Find next CURRENT task
        val nextIdx = updatedTasks.indexOfFirst { it.status == TaskStatus.CURRENT && updatedTasks.indexOf(it) > currentIdx }
            .let { if (it == -1) currentIdx + 1 else it }

        val updatedGoal = goal.copy(
            tasks = updatedTasks,
            currentTaskIndex = nextIdx,
            description = newDescription ?: goal.description
        )
        val newGoals = md.goals.map { if (it.id == goalId) updatedGoal else it }
        setMotivationData(player, md.copy(goals = newGoals))
        return true
    }

    /** Marks the entire goal as completed. */
    fun completeGoal(player: ServerPlayerEntity, goalId: String, newDescription: String? = null): Boolean =
        closeGoal(player, goalId, GoalStatus.COMPLETED, newDescription)

    /** Marks the entire goal as failed. */
    fun failGoal(player: ServerPlayerEntity, goalId: String, newDescription: String? = null): Boolean =
        closeGoal(player, goalId, GoalStatus.FAILED, newDescription)

    private fun closeGoal(player: ServerPlayerEntity, goalId: String, status: GoalStatus, newDescription: String?): Boolean {
        val md = identity(player).motivationData
        val goal = md.goals.find { it.id == goalId } ?: return false
        val updated = goal.copy(
            status = status,
            closedAt = System.currentTimeMillis(),
            description = newDescription ?: goal.description
        )
        setMotivationData(player, md.copy(goals = md.goals.map { if (it.id == goalId) updated else it }))
        return true
    }

    fun updateGoalDescription(player: ServerPlayerEntity, goalId: String, description: String): Boolean {
        val md = identity(player).motivationData
        val new = md.goals.map { if (it.id == goalId) it.copy(description = description) else it }
        if (new == md.goals) return false
        setMotivationData(player, md.copy(goals = new))
        return true
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    /** Adds a new task to a goal. */
    fun addTask(player: ServerPlayerEntity, goalId: String, description: String, goalDescOverride: String = ""): String? {
        val md = identity(player).motivationData
        val goal = md.goals.find { it.id == goalId } ?: return null
        
        val taskId = UUID.randomUUID().toString()
        val newOrder = (goal.tasks.maxOfOrNull { it.order } ?: -1) + 1
        val newTask = GoalTask(taskId, description, goalDescOverride, TaskStatus.CURRENT, newOrder)
        
        val updatedGoal = goal.copy(tasks = goal.tasks + newTask)
        val newGoals = md.goals.map { if (it.id == goalId) updatedGoal else it }
        setMotivationData(player, md.copy(goals = newGoals))
        return taskId
    }

    /** Updates an existing task. */
    fun updateTask(player: ServerPlayerEntity, goalId: String, taskId: String, description: String?, goalDescOverride: String?, status: TaskStatus?): Boolean {
        val md = identity(player).motivationData
        val goal = md.goals.find { it.id == goalId } ?: return false
        
        val updatedTasks = goal.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(
                    description = description ?: task.description,
                    goalDescriptionOverride = goalDescOverride ?: task.goalDescriptionOverride,
                    status = status ?: task.status
                )
            } else task
        }
        
        if (updatedTasks == goal.tasks) return false
        
        val updatedGoal = goal.copy(tasks = updatedTasks)
        val newGoals = md.goals.map { if (it.id == goalId) updatedGoal else it }
        setMotivationData(player, md.copy(goals = newGoals))
        return true
    }

    /** Deletes a task from a goal. */
    fun deleteTask(player: ServerPlayerEntity, goalId: String, taskId: String): Boolean {
        val md = identity(player).motivationData
        val goal = md.goals.find { it.id == goalId } ?: return false
        
        val newTasks = goal.tasks.filter { it.id != taskId }
        if (newTasks.size == goal.tasks.size) return false
        
        // Reorder remaining tasks
        val reorderedTasks = newTasks.mapIndexed { index, task -> task.copy(order = index) }
        
        val updatedGoal = goal.copy(tasks = reorderedTasks)
        val newGoals = md.goals.map { if (it.id == goalId) updatedGoal else it }
        setMotivationData(player, md.copy(goals = newGoals))
        return true
    }

    /** Reorders a task within a goal. */
    fun reorderTask(player: ServerPlayerEntity, goalId: String, taskId: String, newOrder: Int): Boolean {
        val md = identity(player).motivationData
        val goal = md.goals.find { it.id == goalId } ?: return false
        
        val task = goal.tasks.find { it.id == taskId } ?: return false
        val oldOrder = task.order
        
        if (oldOrder == newOrder || newOrder < 0 || newOrder >= goal.tasks.size) return false
        
        // Reorder tasks
        val reorderedTasks = goal.tasks.sortedBy { it.order }.toMutableList()
        reorderedTasks.removeAt(oldOrder)
        reorderedTasks.add(newOrder, task)
        
        // Update order values
        val finalTasks = reorderedTasks.mapIndexed { index, t -> t.copy(order = index) }
        
        val updatedGoal = goal.copy(tasks = finalTasks)
        val newGoals = md.goals.map { if (it.id == goalId) updatedGoal else it }
        setMotivationData(player, md.copy(goals = newGoals))
        return true
    }

    /** Updates goal title and description. */
    fun updateGoal(player: ServerPlayerEntity, goalId: String, title: String?, description: String?, status: GoalStatus?, motivationId: String?): Boolean {
        val md = identity(player).motivationData
        val goal = md.goals.find { it.id == goalId } ?: return false
        
        val updatedGoal = goal.copy(
            title = title ?: goal.title,
            description = description ?: goal.description,
            status = status ?: goal.status,
            motivationId = motivationId ?: goal.motivationId,
            closedAt = if (status != null && status != GoalStatus.ACTIVE) System.currentTimeMillis() else goal.closedAt
        )
        
        val newGoals = md.goals.map { if (it.id == goalId) updatedGoal else it }
        setMotivationData(player, md.copy(goals = newGoals))
        return true
    }
}
