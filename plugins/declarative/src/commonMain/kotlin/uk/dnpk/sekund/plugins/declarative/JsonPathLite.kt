package uk.dnpk.sekund.plugins.declarative

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The small part of JSONPath that definitions need: `$` (the whole document), `.key` and `[index]`,
 * e.g. `$.weather[0].icon` or `$.list[-1]` (negative indexes count from the end).
 * No wildcards, filters or recursive descent, which keeps definitions predictable.
 */
object JsonPathLite {
    /** The element at [path] inside [root], or null if the path doesn't exist or isn't valid. */
    fun find(root: JsonElement?, path: String): JsonElement? {
        if (root == null) return null
        if (!path.startsWith("$")) return null
        var current: JsonElement = root
        for (step in steps(path.substring(1)) ?: return null) {
            current = when (step) {
                is Step.Key -> (current as? JsonObject)?.get(step.name) ?: return null
                is Step.Index -> {
                    val array = current as? JsonArray ?: return null
                    val index = if (step.index < 0) array.size + step.index else step.index
                    array.getOrNull(index) ?: return null
                }
            }
        }
        return current
    }

    private sealed class Step {
        data class Key(val name: String) : Step()

        data class Index(val index: Int) : Step()
    }

    /** Splits `.a.b[0].c` into steps, or returns null if it isn't written correctly. */
    private fun steps(rest: String): List<Step>? {
        val result = mutableListOf<Step>()
        var position = 0
        while (position < rest.length) {
            when (rest[position]) {
                '.' -> {
                    var end = position + 1
                    while (end < rest.length && rest[end] != '.' && rest[end] != '[') end += 1
                    val name = rest.substring(position + 1, end)
                    if (name.isEmpty()) return null
                    result.add(Step.Key(name))
                    position = end
                }
                '[' -> {
                    val end = rest.indexOf(']', position)
                    if (end < 0) return null
                    val index = rest.substring(position + 1, end).trim().toIntOrNull() ?: return null
                    result.add(Step.Index(index))
                    position = end + 1
                }
                else -> return null
            }
        }
        return result
    }
}
