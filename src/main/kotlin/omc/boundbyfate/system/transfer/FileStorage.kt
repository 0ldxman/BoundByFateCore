package omc.boundbyfate.system.transfer

import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Хранилище файлов на сервере.
 *
 * Файлы хранятся в `world/boundbyfate/files/<category>/<fileId>.<extension>`.
 * Переживают рестарт сервера.
 *
 * Все операции выполняются в IO потоке — не вызывать с server thread напрямую.
 */
object FileStorage {

    private val logger = LoggerFactory.getLogger(FileStorage::class.java)
    private lateinit var baseDir: Path

    /**
     * Инициализирует хранилище.
     * Вызывается при старте сервера.
     */
    fun init(server: MinecraftServer) {
        baseDir = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
            .resolve("boundbyfate/files")
        FileCategory.entries.forEach { category ->
            baseDir.resolve(category.folder).createDirectories()
        }
        logger.info("FileStorage initialized at $baseDir")
    }

    /**
     * Сохраняет файл на диск.
     *
     * @param category категория файла
     * @param fileId ID файла
     * @param extension расширение без точки
     * @param bytes байты файла
     */
    fun save(category: FileCategory, fileId: String, extension: String, bytes: ByteArray) {
        val path = resolvePath(category, fileId, extension)
        path.writeBytes(bytes)
        logger.info("Saved file: ${path.name} (${bytes.size} bytes)")
    }

    /**
     * Загружает файл с диска.
     *
     * @return байты файла или null если файл не найден
     */
    fun load(category: FileCategory, fileId: String, extension: String): ByteArray? {
        val path = resolvePath(category, fileId, extension)
        if (!path.exists()) return null
        return path.readBytes()
    }

    /**
     * Удаляет файл с диска.
     *
     * @return true если файл был удалён
     */
    fun delete(category: FileCategory, fileId: String, extension: String): Boolean {
        val path = resolvePath(category, fileId, extension)
        if (!path.exists()) return false
        path.deleteExisting()
        logger.info("Deleted file: ${path.name}")
        return true
    }

    /**
     * Возвращает список всех файлов в категории.
     */
    fun listFiles(category: FileCategory): List<StoredFileInfo> {
        val dir = baseDir.resolve(category.folder)
        if (!dir.exists()) return emptyList()

        return dir.listDirectoryEntries()
            .filter { it.isRegularFile() }
            .mapNotNull { path ->
                val name = path.nameWithoutExtension
                val ext = path.extension
                if (name.isNotEmpty() && ext.isNotEmpty()) {
                    StoredFileInfo(
                        fileId = name,
                        category = category,
                        extension = ext,
                        totalSize = path.fileSize()
                    )
                } else null
            }
    }

    /**
     * Возвращает список всех файлов на сервере (все категории).
     */
    fun listAllFiles(): List<StoredFileInfo> =
        FileCategory.entries.flatMap { listFiles(it) }

    /**
     * Проверяет существование файла.
     */
    fun exists(category: FileCategory, fileId: String, extension: String): Boolean =
        resolvePath(category, fileId, extension).exists()

    private fun resolvePath(category: FileCategory, fileId: String, extension: String): Path =
        baseDir.resolve(category.folder).resolve("$fileId.$extension")
}

/**
 * Информация о хранимом файле.
 */
data class StoredFileInfo(
    val fileId: String,
    val category: FileCategory,
    val extension: String,
    val totalSize: Long
)
