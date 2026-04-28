package omc.boundbyfate.client.transfer

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import omc.boundbyfate.system.transfer.FileCategory
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Клиентский кеш файлов полученных от сервера.
 *
 * Файлы хранятся в `.minecraft/boundbyfate-cache/<category>/<fileId>.<extension>`.
 * Кеш переживает перезапуск клиента — при подключении к серверу
 * клиент сравнивает кеш со списком сервера и скачивает только новые файлы.
 *
 * Все операции выполняются в IO потоке.
 */
@Environment(EnvType.CLIENT)
object FileCache {

    private val logger = LoggerFactory.getLogger(FileCache::class.java)
    private val baseDir: Path = net.fabricmc.loader.api.FabricLoader.getInstance()
        .gameDir
        .resolve("boundbyfate-cache")

    init {
        FileCategory.entries.forEach { category ->
            baseDir.resolve(category.folder).createDirectories()
        }
        logger.info("FileCache initialized at $baseDir")
    }

    /**
     * Сохраняет файл в кеш.
     */
    fun save(category: FileCategory, fileId: String, extension: String, bytes: ByteArray) {
        val path = resolvePath(category, fileId, extension)
        path.writeBytes(bytes)
        logger.debug("Cached: ${path.name} (${bytes.size} bytes)")
    }

    /**
     * Загружает файл из кеша.
     *
     * @return байты файла или null если файла нет в кеше
     */
    fun load(category: FileCategory, fileId: String, extension: String): ByteArray? {
        val path = resolvePath(category, fileId, extension)
        if (!path.exists()) return null
        return path.readBytes()
    }

    /**
     * Проверяет наличие файла в кеше.
     */
    fun exists(category: FileCategory, fileId: String, extension: String): Boolean =
        resolvePath(category, fileId, extension).exists()

    /**
     * Возвращает список всех файлов в кеше для категории.
     */
    fun listFiles(category: FileCategory): List<CachedFileInfo> {
        val dir = baseDir.resolve(category.folder)
        if (!dir.exists()) return emptyList()

        return dir.listDirectoryEntries()
            .filter { it.isRegularFile() }
            .mapNotNull { path ->
                val name = path.nameWithoutExtension
                val ext = path.extension
                if (name.isNotEmpty() && ext.isNotEmpty()) {
                    CachedFileInfo(name, category, ext, path.fileSize())
                } else null
            }
    }

    /**
     * Удаляет файл из кеша.
     */
    fun delete(category: FileCategory, fileId: String, extension: String) {
        val path = resolvePath(category, fileId, extension)
        if (path.exists()) {
            path.deleteExisting()
            logger.debug("Deleted from cache: $fileId.$extension")
        }
    }

    private fun resolvePath(category: FileCategory, fileId: String, extension: String): Path =
        baseDir.resolve(category.folder).resolve("$fileId.$extension")
}

data class CachedFileInfo(
    val fileId: String,
    val category: FileCategory,
    val extension: String,
    val size: Long
)


