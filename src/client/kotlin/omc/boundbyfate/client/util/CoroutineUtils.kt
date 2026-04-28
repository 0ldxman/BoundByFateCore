package omc.boundbyfate.client.util

import kotlinx.coroutines.*
import net.minecraft.client.MinecraftClient

/**
 * Coroutine scope tied to the Minecraft client lifecycle.
 */
val MinecraftClient.coroutineScope: CoroutineScope
    get() = ClientCoroutineScope

object ClientCoroutineScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.Default
}

/**
 * Launch a coroutine in the client scope.
 */
fun scopeAsync(block: suspend CoroutineScope.() -> Unit): Job =
    ClientCoroutineScope.launch(block = block)
