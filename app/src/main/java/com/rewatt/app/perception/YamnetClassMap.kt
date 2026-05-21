package com.rewatt.app.perception

object YamnetClassMap {
    fun parseLabels(csv: String): List<String> {
        val indexedLabels = csv.lineSequence()
            .drop(1)
            .mapNotNull(::indexedDisplayNameFromCsvLine)
            .toList()
        val maxIndex = indexedLabels.maxOfOrNull { it.first } ?: return emptyList()
        return MutableList(maxIndex + 1) { index -> "sound class $index" }
            .also { labels ->
                indexedLabels.forEach { (index, displayName) ->
                    labels[index] = displayName
                }
            }
    }

    private fun indexedDisplayNameFromCsvLine(line: String): Pair<Int, String>? {
        val columns = parseCsvLine(line)
        val index = columns.getOrNull(0)?.toIntOrNull() ?: return null
        val displayName = columns.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return null
        return index to displayName
    }

    private fun parseCsvLine(line: String): List<String> {
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    columns += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        columns += current.toString()
        return columns
    }
}
