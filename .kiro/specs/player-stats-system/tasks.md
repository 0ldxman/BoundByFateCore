# Implementation Tasks: Player Stats System

## Task 1: Core Data Models and Registry

### 1.1 Create StatDefinition data class
- [ ] Create `src/main/kotlin/omc/boundbyfate/api/stat/StatDefinition.kt`
- [ ] Implement data class with: id, shortName, displayName, minValue, maxValue, defaultValue
- [ ] Add validation in init block
- [ ] Add KDoc documentation

### 1.2 Create StatValue data class
- [ ] Create `src/main/kotlin/omc/boundbyfate/api/stat/StatValue.kt`
- [ ] Implement immutable data class with: base, total, dndModifier
- [ ] Implement `compute()` companion function with D&D formula
- [ ] Add unit tests for modifier calculation

### 1.3 Create StatModifier data class
- [ ] Create `src/main/kotlin/omc/boundbyfate/api/stat/StatModifier.kt`
- [ ] Implement data class with: sourceId, type, value
- [ ] Create ModifierType enum (FLAT, OVERRIDE)
- [ ] Add Codec for serialization

### 1.4 Create StatRegistry
- [ ] Create `src/main/kotlin/omc/boundbyfate/registry/StatRegistry.kt`
- [ ] Implement thread-safe registry with ConcurrentHashMap
- [ ] Add register(), get(), getOrThrow(), getAll() methods
- [ ] Add validation for duplicate IDs

### 1.5 Create BbfStats with built-in stats
- [ ] Create `src/main/kotlin/omc/boundbyfate/registry/BbfStats.kt`
- [ ] Register 6 D&D stats: STR, CON, DEX, INT, WIS, CHA
- [ ] Set appropriate min/max/default values
- [ ] Call registration in BoundByFateCore.onInitialize()

## Task 2: Entity Stats Data Layer

### 2.1 Create EntityStatData
- [ ] Create `src/main/kotlin/omc/boundbyfate/component/EntityStatData.kt`
- [ ] Implement immutable data class with baseStats and modifiers maps
- [ ] Implement getStatValue() with modifier stacking logic
- [ ] Implement withBase(), withModifier(), withoutModifiersFrom()
- [ ] Implement getAllStats() for bulk access

### 2.2 Create Codec for EntityStatData
- [ ] Add CODEC companion object using RecordCodecBuilder
- [ ] Serialize baseStats as Map<Identifier, Int>
- [ ] Serialize modifiers as Map<Identifier, List<StatModifier>>
- [ ] Test serialization/deserialization

### 2.3 Register Attachment Type
- [ ] Add ENTITY_STATS to BbfAttachments
- [ ] Use AttachmentRegistry.createPersistent with Codec
- [ ] Test attachment persistence through world reload

## Task 3: Effect System

### 3.1 Create StatEffect interface
- [ ] Create `src/main/kotlin/omc/boundbyfate/api/stat/StatEffect.kt`
- [ ] Define fun interface with apply(entity, statValue)
- [ ] Add KDoc with usage examples

### 3.2 Create StatEffectBinding
- [ ] Create `src/main/kotlin/omc/boundbyfate/api/stat/StatEffectBinding.kt`
- [ ] Implement data class linking effect to statId
- [ ] Add to StatDefinition

### 3.3 Create EntityAttributeStatEffect
- [ ] Create `src/main/kotlin/omc/boundbyfate/system/stat/effect/EntityAttributeStatEffect.kt`
- [ ] Implement StatEffect with EntityAttribute and formula
- [ ] Use unique UUID for AttributeModifier
- [ ] Remove old modifiers before applying new

### 3.4 Create MaxHealthStatEffect
- [ ] Create `src/main/kotlin/omc/boundbyfate/system/stat/effect/MaxHealthStatEffect.kt`
- [ ] Implement formula: base 20 HP + CON modifier * 2
- [ ] Use EntityAttributes.GENERIC_MAX_HEALTH

### 3.5 Create MovementSpeedStatEffect
- [ ] Create `src/main/kotlin/omc/boundbyfate/system/stat/effect/MovementSpeedStatEffect.kt`
- [ ] Implement DEX-based speed modification
- [ ] Use EntityAttributes.GENERIC_MOVEMENT_SPEED

### 3.6 Create StatEffectProcessor
- [ ] Create `src/main/kotlin/omc/boundbyfate/system/stat/StatEffectProcessor.kt`
- [ ] Implement applyAll(entity, statsData)
- [ ] Implement reapply(entity, statId, statsData)
- [ ] Add removeAllBbfModifiers() helper

## Task 4: Configuration System

### 4.1 Create CharacterStatProfile
- [ ] Create `src/main/kotlin/omc/boundbyfate/config/CharacterStatProfile.kt`
- [ ] Implement data class matching JSON structure
- [ ] Add Codec for JSON deserialization
- [ ] Add validation logic

### 4.2 Create MobStatProfile
- [ ] Create `src/main/kotlin/omc/boundbyfate/config/MobStatProfile.kt`
- [ ] Implement data class for mob configs
- [ ] Add Codec for JSON deserialization

### 4.3 Create CharacterConfigLoader
- [ ] Create `src/main/kotlin/omc/boundbyfate/config/CharacterConfigLoader.kt`
- [ ] Implement load(playerName) from `world/boundbyfate/characters/`
- [ ] Add error handling for missing/invalid files
- [ ] Cache loaded configs in memory

### 4.4 Create MobStatConfigLoader
- [ ] Create `src/main/kotlin/omc/boundbyfate/config/MobStatConfigLoader.kt`
- [ ] Implement load(mobTypeId) from `world/boundbyfate/mobs/`
- [ ] Add caching

### 4.5 Implement config reload
- [ ] Add reload() method to loaders
- [ ] Clear cache and reload all configs
- [ ] Reapply effects to existing entities

## Task 5: Event Integration

### 5.1 Player Join Handler
- [ ] Create `src/main/kotlin/omc/boundbyfate/event/PlayerStatsHandler.kt`
- [ ] Listen to ServerPlayConnectionEvents.JOIN
- [ ] Load CharacterStatProfile
- [ ] Create EntityStatData with base stats
- [ ] Apply race modifiers (stub for now)
- [ ] Apply class modifiers (stub for now)
- [ ] Attach to player
- [ ] Apply all effects

### 5.2 Mob Spawn Handler
- [ ] Listen to ServerEntityEvents.ENTITY_LOAD
- [ ] Check if entity is LivingEntity
- [ ] Load MobStatProfile if exists
- [ ] Attach EntityStatData if config found
- [ ] Apply effects

## Task 6: Commands

### 6.1 Create StatsCommand base structure
- [ ] Create `src/main/kotlin/omc/boundbyfate/command/StatsCommand.kt`
- [ ] Register `/bbf stats` literal
- [ ] Add permission check (level >= 2)

### 6.2 Implement /bbf stats info
- [ ] Add `info` subcommand
- [ ] Add optional player argument
- [ ] Display all stats with base/total/modifier
- [ ] Display active modifiers by source
- [ ] Format output with colors

### 6.3 Implement /bbf stats set
- [ ] Add `set <stat> <value> [player]` subcommand
- [ ] Validate stat ID exists
- [ ] Validate value in range
- [ ] Update EntityStatData
- [ ] Reapply effects
- [ ] Send confirmation message

### 6.4 Implement /bbf stats modifier add
- [ ] Add `modifier add <stat> <source> <value> [player]` subcommand
- [ ] Create StatModifier with FLAT type
- [ ] Add to EntityStatData
- [ ] Reapply effects
- [ ] Send confirmation

### 6.5 Implement /bbf stats modifier remove
- [ ] Add `modifier remove <source> [player]` subcommand
- [ ] Remove all modifiers from source
- [ ] Reapply effects
- [ ] Send confirmation with count

### 6.6 Implement /bbf stats reload
- [ ] Add `reload` subcommand
- [ ] Call reload() on both config loaders
- [ ] Reapply stats to all online players
- [ ] Send confirmation with count

### 6.7 Register StatsCommand
- [ ] Add registration in BoundByFateCore.onInitialize()
- [ ] Test all command variations

## Task 7: Testing

### 7.1 Unit Tests for StatValue
- [ ] Test D&D modifier formula for values 1-30
- [ ] Test edge cases (negative, zero, very large)
- [ ] Test clamping to min/max

### 7.2 Unit Tests for EntityStatData
- [ ] Test withModifier() immutability
- [ ] Test modifier stacking (multiple FLAT)
- [ ] Test OVERRIDE modifier behavior
- [ ] Test withoutModifiersFrom()

### 7.3 Integration Test: Player Join
- [ ] Create test character config
- [ ] Simulate player join
- [ ] Verify EntityStatData attached
- [ ] Verify effects applied (check EntityAttribute)

### 7.4 Integration Test: Commands
- [ ] Test `/bbf stats info` output
- [ ] Test `/bbf stats set` changes values
- [ ] Test `/bbf stats modifier add/remove`
- [ ] Test `/bbf stats reload`

### 7.5 Integration Test: Persistence
- [ ] Set stats on player
- [ ] Save and reload world
- [ ] Verify stats persisted correctly

## Task 8: Documentation and Polish

### 8.1 Add KDoc to public APIs
- [ ] Document StatRegistry
- [ ] Document StatEffect interface
- [ ] Document EntityStatData methods
- [ ] Add usage examples

### 8.2 Create example configs
- [ ] Create example character config in docs/
- [ ] Create example mob config in docs/
- [ ] Document config format in README

### 8.3 Add logging
- [ ] Log config loading (INFO level)
- [ ] Log validation warnings (WARN level)
- [ ] Log stat changes (DEBUG level)

### 8.4 Error messages
- [ ] Improve command error messages
- [ ] Add helpful hints for common mistakes
- [ ] Localize messages (optional)

## Task 9: Build and Deploy

### 9.1 Local testing
- [ ] Test in development environment
- [ ] Verify all commands work
- [ ] Test with multiple players
- [ ] Test mob stats

### 9.2 Commit and push
- [ ] Stage all changes
- [ ] Commit with descriptive message
- [ ] Push to GitHub

### 9.3 GitHub Actions build
- [ ] Verify build passes
- [ ] Download and test JAR artifact
- [ ] Verify in actual Minecraft server

## Task 10: Integration Preparation (Future)

### 10.1 Race integration stubs
- [ ] Create RaceRegistry interface (empty for now)
- [ ] Add getRaceModifiers() stub
- [ ] Document integration points

### 10.2 Class integration stubs
- [ ] Create ClassRegistry interface (empty for now)
- [ ] Add getClassModifiers() stub
- [ ] Document integration points

### 10.3 Skills system preparation
- [ ] Document how skills will use stats
- [ ] Plan skill check API
- [ ] Add TODO comments for future integration
