package omc.boundbyfate.client.gui.screens.draft

import net.minecraft.util.Identifier
import omc.boundbyfate.data.world.character.*
import java.util.UUID

/**
 * Клиентский черновик редактирования персонажа.
 *
 * Живёт пока открыт [CharacterEditScreen] и его дочерние экраны.
 * Все изменения в UI пишутся сюда — реальные данные не трогаются
 * до нажатия кнопки "Сохранить".
 *
 * ## Режимы
 *
 * - [isNewCharacter] = true  → создание нового персонажа (source = null)
 * - [isNewCharacter] = false → редактирование существующего (source != null)
 *
 * ## Использование
 *
 * ```kotlin
 * // Создание нового персонажа
 * val draft = CharacterEditDraft()
 *
 * // Редактирование существующего
 * val draft = CharacterEditDraft(source = existingCharacter)
 *
 * // Изменение поля
 * draft.name = "Ричард Зорге"
 *
 * // Применение к реальным данным (при сохранении)
 * val updated = draft.toCharacterData()
 * ```
 */
class CharacterEditDraft(source: CharacterData? = null) {

    /** true — создание нового персонажа, false — редактирование существующего. */
    val isNewCharacter: Boolean = source == null

    /** UUID существующего персонажа (null при создании нового). */
    val characterId: UUID? = source?.id

    /** UUID владельца персонажа. */
    var ownerId: UUID? = source?.ownerId

    // ── Identity ──────────────────────────────────────────────────────────

    /** Отображаемое имя персонажа. */
    var name: String = source?.identity?.displayName ?: ""

    /** Время создания персонажа (сохраняется при редактировании). */
    val createdAt: Long = source?.identity?.createdAt ?: 0L

    /** Внешний вид. */
    var skinId: String = source?.identity?.appearance?.skinId ?: ""
    var modelType: ModelType = source?.identity?.appearance?.modelType ?: ModelType.STEVE

    /**
     * Путь к GLTF модели НПС-версии персонажа.
     * Формат: "namespace:models/entity/name.gltf"
     * Пустая строка — используется дефолтная модель.
     */
    var npcModelPath: String = source?.identity?.appearance?.npcModelPath ?: ""

    /**
     * ID скина для НПС-версии персонажа из FileTransferSystem (FileCategory.SKIN).
     * Пустая строка — используется общий [skinId].
     */
    var npcSkinId: String = source?.identity?.appearance?.npcSkinId ?: ""

    // ── Race ──────────────────────────────────────────────────────────────

    /** ID расы. */
    var raceId: Identifier? = source?.race?.raceId

    /** ID подрасы. */
    var subraceId: Identifier? = source?.race?.subraceId

    /** Пол персонажа (свободная строка — зависит от расы). */
    var gender: String = source?.race?.gender ?: ""

    /** Дата рождения в игровом летоисчислении (свободная строка). */
    var dateOfBirth: String = source?.race?.dateOfBirth ?: ""

    // ── Class ─────────────────────────────────────────────────────────────

    /** ID класса. */
    var classId: Identifier? = source?.charClass?.classId

    /** ID подкласса (null пока не выбран). */
    var subclassId: Identifier? = source?.charClass?.subclassId

    /** История левелапов (не редактируется напрямую через UI). */
    var levelUpHistory: List<LevelUpRecord> = source?.charClass?.levelUpHistory ?: emptyList()

    // ── Progression ───────────────────────────────────────────────────────

    /** Текущий уровень. */
    var level: Int = source?.progression?.level ?: 1

    /** Опыт на текущем уровне. */
    var experienceInLevel: Int = source?.progression?.experienceInLevel ?: 0

    // ── Stats ─────────────────────────────────────────────────────────────

    /** Базовые значения характеристик. Ключ — ID стата. */
    var baseStats: MutableMap<String, Int> = source?.stats?.baseStats?.toMutableMap() ?: mutableMapOf()

    /** Список ID владений. */
    var proficiencies: MutableList<Identifier> = source?.stats?.proficiencies?.toMutableList() ?: mutableListOf()

    /** Жизненная сила (0–5, где 5 = полная). */
    var vitalityPoints: Int = run {
        val scale = source?.stats?.vitality?.vitalityScale ?: 100
        // vitalityScale 100 = 5 очков, 80 = 4, 60 = 3, 40 = 2, 20 = 1, 0 = 0
        (scale / 20).coerceIn(0, 5)
    }

    /** Шрамы персонажа. */
    var scars: MutableList<Scar> = source?.stats?.vitality?.scars?.toMutableList() ?: mutableListOf()

    /** Известные способности. */
    var knownAbilities: MutableList<Identifier> = source?.stats?.abilities?.knownAbilities?.toMutableList() ?: mutableListOf()

    // ── WorldView ─────────────────────────────────────────────────────────

    // WorldView редактируется на отдельном экране — пока заглушка
    var worldView: CharacterWorldView = source?.worldView ?: CharacterWorldView()

    // ── Конвертация обратно в CharacterData ───────────────────────────────

    /**
     * Создаёт [CharacterData] из текущего состояния черновика.
     * Вызывается при нажатии "Сохранить".
     */
    fun toCharacterData(): CharacterData {
        val now = System.currentTimeMillis()
        val existingId = characterId ?: UUID.randomUUID()

        val identity = CharacterIdentity(
            displayName = name,
            createdAt = if (isNewCharacter) now else createdAt,
            lastPlayedAt = now,
            appearance = CharacterAppearance(
                skinId = skinId,
                modelType = modelType,
                npcModelPath = npcModelPath,
                npcSkinId = npcSkinId
            )
        )

        val race = CharacterRace(
            raceId = raceId ?: Identifier.of("boundbyfate-core", "unknown")!!,
            subraceId = subraceId,
            gender = gender,
            dateOfBirth = dateOfBirth
        )

        val charClass = CharacterClass(
            classId = classId ?: Identifier.of("boundbyfate-core", "unknown")!!,
            subclassId = subclassId,
            levelUpHistory = levelUpHistory
        )

        val vitality = CharacterVitality(
            vitalityScale = (vitalityPoints * 20).coerceIn(0, 100),
            scars = scars
        )

        val stats = CharacterStats(
            baseStats = baseStats,
            proficiencies = proficiencies,
            vitality = vitality,
            abilities = CharacterAbilities(knownAbilities = knownAbilities)
        )

        return CharacterData(
            id = existingId,
            ownerId = ownerId,
            identity = identity,
            race = race,
            charClass = charClass,
            progression = CharacterProgression(level = level, experienceInLevel = experienceInLevel),
            worldView = worldView,
            stats = stats
        )
    }

    /** Есть ли несохранённые изменения относительно источника. */
    fun isDirty(source: CharacterData): Boolean =
        name != source.identity.displayName ||
        raceId != source.race.raceId ||
        gender != source.race.gender ||
        classId != source.charClass.classId ||
        subclassId != source.charClass.subclassId ||
        level != source.progression.level ||
        baseStats != source.stats.baseStats ||
        proficiencies != source.stats.proficiencies ||
        vitalityPoints != (source.stats.vitality.vitalityScale / 20)
}