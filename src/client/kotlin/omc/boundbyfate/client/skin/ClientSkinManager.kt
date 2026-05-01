package omc.boundbyfate.client.skin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import omc.boundbyfate.client.transfer.FileCache
import omc.boundbyfate.data.world.character.ModelType
import omc.boundbyfate.system.transfer.FileCategory
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Клиентский менеджер скинов.
 *
 * Хранит зарегистрированные текстуры скинов и предоставляет их рендереру.
 * Загружает скины из [FileCache] — файлы уже должны быть скачаны через FileTransferSystem.
 *
 * ## Использование в рендерере
 *
 * ```kotlin
 * // Получить текстуру скина
 * val texture = ClientSkinManager.getTexture("elio_skin") ?: defaultTexture
 *
 * // Получить тип модели (для игрока)
 * val modelType = ClientSkinManager.getModelType("elio_skin")
 * ```
 *
 * ## Жизненный цикл
 *
 * 1. FileTransferSystem получает skin.png → вызывает [onSkinFileReceived]
 * 2. [onSkinFileReceived] регистрирует текстуру в TextureManager
 * 3. Рендерер запрашивает текстуру через [getTexture]
 * 4. При отключении от сервера — [clearAll] освобождает текстуры
 */
@Environment(EnvType.CLIENT)
object ClientSkinManager {

    private val logger = LoggerFactory.getLogger(ClientSkinManager::class.java)

    /**
     * Зарегистрированные скины.
     * Ключ — skinId (имя файла без расширения).
     */
    private val skins = ConcurrentHashMap<String, SkinEntry>()

    // ── Регистрация ───────────────────────────────────────────────────────

    /**
     * Вызывается когда FileTransferSystem получил файл скина.
     * Регистрирует текстуру в TextureManager Minecraft.
     *
     * @param skinId ID скина (имя файла без расширения)
     * @param bytes байты PNG файла
     * @param modelType тип модели (по умолчанию STEVE, уточняется при назначении персонажу)
     */
    fun onSkinFileReceived(skinId: String, bytes: ByteArray, modelType: ModelType = ModelType.STEVE) {
        MinecraftClient.getInstance().execute {
            registerTexture(skinId, bytes, modelType)
        }
    }

    /**
     * Гарантирует что скин загружен из кеша.
     * Вызывается когда клиент узнаёт skinId из синхронизации компонента/WorldData.
     *
     * Если скин уже зарегистрирован — ничего не делает.
     * Если нет — пытается загрузить из FileCache.
     *
     * @param skinId ID скина
     * @param modelType тип модели
     */
    fun ensureLoaded(skinId: String, modelType: ModelType = ModelType.STEVE) {
        if (skinId.isEmpty() || skins.containsKey(skinId)) return

        val bytes = FileCache.load(FileCategory.SKIN, skinId, "png")
        if (bytes == null) {
            logger.warn("Skin '$skinId' not found in FileCache, cannot load")
            return
        }

        MinecraftClient.getInstance().execute {
            registerTexture(skinId, bytes, modelType)
        }
    }

    // ── Получение ─────────────────────────────────────────────────────────

    /**
     * Возвращает Identifier текстуры скина или null если скин не загружен.
     *
     * ```kotlin
     * val texture = ClientSkinManager.getTexture("elio_skin") ?: defaultSkinTexture
     * ```
     */
    fun getTexture(skinId: String): Identifier? {
        if (skinId.isEmpty()) return null
        return skins[skinId]?.textureId
    }

    /**
     * Возвращает тип модели для скина.
     * Если скин не загружен — возвращает STEVE по умолчанию.
     */
    fun getModelType(skinId: String): ModelType {
        if (skinId.isEmpty()) return ModelType.STEVE
        return skins[skinId]?.modelType ?: ModelType.STEVE
    }

    /**
     * Проверяет загружен ли скин.
     */
    fun isLoaded(skinId: String): Boolean = skinId.isNotEmpty() && skins.containsKey(skinId)

    // ── Очистка ───────────────────────────────────────────────────────────

    /**
     * Освобождает все зарегистрированные текстуры.
     * Вызывается при отключении от сервера.
     */
    fun clearAll() {
        val client = MinecraftClient.getInstance()
        skins.forEach { (_, entry) ->
            client.textureManager.destroyTexture(entry.textureId)
        }
        skins.clear()
        logger.info("Cleared all skin textures")
    }

    /**
     * Освобождает конкретный скин.
     */
    fun clear(skinId: String) {
        val entry = skins.remove(skinId) ?: return
        MinecraftClient.getInstance().textureManager.destroyTexture(entry.textureId)
        logger.debug("Cleared skin texture: $skinId")
    }

    // ── Внутренняя логика ─────────────────────────────────────────────────

    /**
     * Регистрирует текстуру в TextureManager.
     * Должен вызываться на render thread (через client.execute).
     */
    private fun registerTexture(skinId: String, bytes: ByteArray, modelType: ModelType) {
        try {
            val image = NativeImage.read(ByteArrayInputStream(bytes))
            val texture = NativeImageBackedTexture(image)
            val textureId = Identifier("boundbyfate-core", "skin/$skinId")

            val client = MinecraftClient.getInstance()

            // Уничтожаем старую текстуру если была
            skins[skinId]?.let { client.textureManager.destroyTexture(it.textureId) }

            client.textureManager.registerTexture(textureId, texture)
            skins[skinId] = SkinEntry(textureId, modelType)

            logger.debug("Registered skin texture: $skinId (${modelType.name})")
        } catch (e: Exception) {
            logger.error("Failed to register skin texture '$skinId'", e)
        }
    }

    // ── Типы ──────────────────────────────────────────────────────────────

    /**
     * Запись о зарегистрированном скине.
     *
     * @property textureId Identifier текстуры в TextureManager
     * @property modelType тип модели (влияет на UV-маппинг рук)
     */
    data class SkinEntry(
        val textureId: Identifier,
        val modelType: ModelType
    )
}
