package omc.boundbyfate.client.tooltip

import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Represents a single section of BbF tooltip content.
 *
 * A section has a header and a list of lines.
 * It can be "hidden" (obfuscated) when the character doesn't know what the item is.
 *
 * @property header Section header text, e.g. "Владение"
 * @property lines Content lines shown under the header
 * @property obfuscated When true, content is shown as ??? (character doesn't know)
 */
data class BbfTooltipSection(
    val header: String,
    val lines: List<String>,
    val obfuscated: Boolean = false
) {
    /** Renders this section into tooltip lines. */
    fun render(): List<Text> {
        if (lines.isEmpty()) return emptyList()
        val result = mutableListOf<Text>()

        result.add(Text.literal(header).formatted(Formatting.DARK_GRAY))

        if (obfuscated) {
            lines.forEach { _ ->
                result.add(
                    Text.literal("  ???").formatted(Formatting.DARK_GRAY, Formatting.OBFUSCATED)
                )
            }
        } else {
            lines.forEach { line ->
                result.add(
                    Text.literal("  $line").formatted(Formatting.GRAY)
                )
            }
        }

        return result
    }
}
