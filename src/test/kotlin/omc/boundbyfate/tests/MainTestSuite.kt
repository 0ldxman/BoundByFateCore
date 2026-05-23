package omc.boundbyfate.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Главный файл тестов, который можно использовать как точку входа.
 * GitHub Actions будет запускать все тесты в этой папке.
 */
class MainTestSuite {

    @Test
    @DisplayName("Статус системы")
    fun testSystemStatus() {
        println("Запуск всех модульных тестов BoundByFate...")
        // В JUnit 5 тесты в других файлах (как TagTreeTest) 
        // будут найдены автоматически при запуске `./gradlew test`
    }
}
