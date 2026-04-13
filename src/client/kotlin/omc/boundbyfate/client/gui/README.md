# GUI System - Atlas-Based

Система GUI использующая атлас текстур для отрисовки интерфейса.

## 📁 Файлы

- **GuiAtlas.kt** - Определения всех регионов в атласе
- **NineSliceRenderer.kt** - Рендерер для окон и панелей (9-slice)
- **CharacterScreenAtlas.kt** - Пример использования (character sheet)

## 🎨 Использование

### 1. Простая отрисовка элемента

```kotlin
// Рисуем иконку
GuiAtlas.ICON_PROFICIENCY.draw(context, x, y)

// Рисуем с другим размером
GuiAtlas.ICON_STAT_BG.draw(context, x, y, 80, 90)
```

### 2. Отрисовка окна (9-slice)

```kotlin
// Окно любого размера
NineSliceRenderer.drawWindow(
    context,
    x = 100, y = 50,
    width = 400, height = 300
)
```

### 3. Отрисовка баннера/хедера

```kotlin
// Баннер с хайлайтом
NineSliceRenderer.drawHeader(
    context,
    x = 50, y = 20,
    width = 300,
    withHighlight = true
)
```

### 4. Создание кастомного экрана

```kotlin
class MyScreen : Screen(Text.literal("My Screen")) {
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        
        // Рисуем окно
        NineSliceRenderer.drawWindow(context, 100, 50, 400, 300)
        
        // Рисуем иконки
        GuiAtlas.ICON_HP_BG.draw(context, 120, 70)
        
        super.render(context, mouseX, mouseY, delta)
    }
}
```

## 📐 Координаты в атласе

Все координаты определены в `GuiAtlas.kt`:

### Окна (5x5 px):
- Углы: TL(0,0), TR(6,0), BL(0,6), BR(6,6)
- Стены: Top(12,0), Bottom(18,0), Left(12,6), Right(18,6)
- Фон: (24,0)

### Баннеры:
- Левый конец: (0,415) 66x97
- Правый конец: (67,415) 66x97
- Хайлайт: (138,459) 152x53
- Тайл: (291,459) 53x53

### Иконки:
- Фон характеристик: (403,340) 109x172
- Фон ХП: (388,197) 124x136
- Фон навыков: (488,0) 24x24
- Фон спасбросков: (445,0) 27x27
- Владение: (473,0) 14x14
- Успех: (426,0) 15x15
- Провал: (410,0) 15x16

## 🔧 Добавление новых элементов

1. Добавьте элемент в атлас (atlas.png)
2. Добавьте координаты в `GuiAtlas.kt`:

```kotlin
val MY_NEW_ICON = AtlasRegion(x, y, width, height)
```

3. Используйте:

```kotlin
GuiAtlas.MY_NEW_ICON.draw(context, screenX, screenY)
```

## 🎯 Примеры

### Окно с контентом

```kotlin
val windowX = 100
val windowY = 50
val windowWidth = 400
val windowHeight = 300

// Рисуем окно
NineSliceRenderer.drawWindow(context, windowX, windowY, windowWidth, windowHeight)

// Рисуем контент внутри
context.drawTextWithShadow(
    textRenderer,
    Text.literal("Hello World"),
    windowX + 20,
    windowY + 20,
    0xFFFFFF
)
```

### Stat box с иконкой

```kotlin
val x = 100
val y = 100

// Фон
GuiAtlas.ICON_STAT_BG.draw(context, x, y, 80, 90)

// Иконка владения
GuiAtlas.ICON_PROFICIENCY.draw(context, x + 5, y + 5)

// Текст
context.drawCenteredTextWithShadow(
    textRenderer,
    Text.literal("STR"),
    x + 40, y + 20,
    0xFFFFFF
)
```

## 📝 Заметки

- Все размеры в пикселях
- Координаты относительно верхнего левого угла
- 9-slice автоматически растягивает окна
- Тайлинг работает для повторяющихся элементов
