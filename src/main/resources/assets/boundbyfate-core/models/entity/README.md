# NPC Models

Сюда кладутся GLTF/GLB модели для НПС-версий персонажей.

## Формат пути

В `NpcModelComponent.modelPath` и `CharacterAppearance.npcModelPath` путь указывается как:

```
boundbyfate-core:models/entity/имя_модели.gltf
```

Что соответствует этому файлу:

```
assets/boundbyfate-core/models/entity/имя_модели.gltf
```

## Поддерживаемые форматы

- `.gltf` / `.glb` — GLTF 2.0 (рекомендуется)
- `.obj` — Wavefront OBJ
- `.fbx` — FBX
- Bedrock `.json` модели

## Дефолтная модель

`boundbyfate-core:models/entity/classic.gltf` — используется если у персонажа не задана своя модель.

## Анимации

Анимации должны быть встроены в GLTF файл. Управление через `NpcModelComponent`:

```kotlin
npcModel.playAnimation("idle")          // основной слой
npcModel.playAnimation("happy", layer = "emotion")  // аддитивный слой
```
