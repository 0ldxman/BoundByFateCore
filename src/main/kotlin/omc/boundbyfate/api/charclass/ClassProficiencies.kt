package omc.boundbyfate.api.charclass

import omc.boundbyfate.api.proficiency.ProficiencyGrants

/**
 * Начальные владения класса.
 *
 * Это просто type alias для ProficiencyGrants.
 * Используем существующую систему proficiency вместо дублирования.
 */
typealias ClassProficiencies = ProficiencyGrants
