package com.hksalameh.shoppinglist

import kotlin.math.roundToInt

internal data class PhotoRegion(val left: Int, val top: Int, val right: Int, val bottom: Int)

internal fun photoTextRegions(mask: BooleanArray, width: Int, height: Int): List<PhotoRegion> {
    require(mask.size == width * height)
    return occupiedRanges(height, maxOf(16, (height * 0.034f).roundToInt())) { y ->
        countAtLeast(width, 6) { x -> mask[y * width + x] }
    }.flatMap { block ->
        val columns = occupiedRanges(width, maxOf(18, (width * 0.055f).roundToInt())) { x ->
            countAtLeast(block.last - block.first + 1, 3) { dy -> mask[(block.first + dy) * width + x] }
        }.filter { it.last - it.first >= 12 }

        columns.asReversed().flatMap { column ->
            val columnWidth = column.last - column.first + 1
            occupiedRanges(block.last - block.first + 1, 4) { dy ->
                val y = block.first + dy
                countAtLeast(columnWidth, maxOf(4, (columnWidth * 0.025f).roundToInt())) { dx ->
                    mask[y * width + column.first + dx]
                }
            }.flatMap rowRegions@ { row ->
                val top = block.first + row.first
                val bottom = block.first + row.last + 1
                val lineHeight = bottom - top
                if (lineHeight < 5) return@rowRegions emptyList()
                val wordGap = if (columns.size > 1) {
                    maxOf(20, (width * 0.036f).roundToInt(), (lineHeight * 1.24f).roundToInt())
                } else {
                    columnWidth
                }
                occupiedRanges(columnWidth, wordGap) { dx ->
                    val x = column.first + dx
                    countAtLeast(lineHeight, 2) { dy -> mask[(top + dy) * width + x] }
                }.filter { it.last - it.first >= 8 }.asReversed().map { text ->
                    val pad = (lineHeight / 3).coerceIn(5, 19)
                    PhotoRegion(
                        (column.first + text.first - pad).coerceAtLeast(0),
                        (top - pad).coerceAtLeast(0),
                        (column.first + text.last + 1 + pad).coerceAtMost(width),
                        (bottom + pad).coerceAtMost(height)
                    )
                }
            }
        }
    }.take(120)
}

private inline fun countAtLeast(size: Int, required: Int, present: (Int) -> Boolean): Boolean {
    var count = 0
    for (index in 0 until size) {
        if (present(index) && ++count >= required) return true
    }
    return false
}

private inline fun occupiedRanges(size: Int, gap: Int, occupied: (Int) -> Boolean): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var first = -1
    var last = -1
    for (i in 0 until size) {
        if (!occupied(i)) continue
        if (first < 0 || i - last > gap) {
            if (first >= 0) ranges.add(first..last)
            first = i
        }
        last = i
    }
    if (first >= 0) ranges.add(first..last)
    return ranges
}
