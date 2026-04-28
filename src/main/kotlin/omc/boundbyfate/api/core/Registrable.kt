package omc.boundbyfate.api.core

/**
 * Интерфейс для объектов, которые могут быть зарегистрированы в Registry.
 * 
 * Все Definition должны быть Registrable, чтобы их можно было
 * загрузить из JSON и зарегистрировать в системе.
 */
interface Registrable : Identifiable {
    /**
     * Валидация определения перед регистрацией.
     * 
     * @throws IllegalStateException если определение невалидно
     */
    fun validate() {
        // По умолчанию ничего не делаем
        // Конкретные Definition могут переопределить для своей валидации
    }
}
