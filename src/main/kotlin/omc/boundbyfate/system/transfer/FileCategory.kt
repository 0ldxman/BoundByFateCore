package omc.boundbyfate.system.transfer

/**
 * Категория передаваемого файла.
 *
 * Определяет папку хранения на сервере и в кеше клиента.
 */
enum class FileCategory(val folder: String) {
    MUSIC("music"),
    SKIN("skins"),
    MODEL("models")
}
