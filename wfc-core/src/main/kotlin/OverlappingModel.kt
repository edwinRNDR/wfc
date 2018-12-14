package org.openrndr.wfc

class OverlappingModel(
    val patternWidth: Int,
    val periodicInput: Boolean,
    val periodicOutput: Boolean,
    val patterns: Array<IntArray>,
    val colors: List<Color>,
    val state: State
) {

    fun decode(x: Int, y: Int): Color {
        return if (state.observable) {
            decodeObservation(x, y)
        } else {
            decodeSuperposition(x, y)
        }
    }

    fun decodeObservation(x: Int, y: Int): Color {
        val dy = if (y < state.height - patternWidth + 1) {
            0
        } else {
            patternWidth - 1
        }
        val dx = if (x < state.width - patternWidth + 1) {
            0
        } else {
            patternWidth - 1
        }
        val c = colors[patterns[state.observed[x - dx + (y - dy) * state.width]][dx + dy * patternWidth]]
        return c
    }

    fun decodeSuperposition(x: Int, y: Int): Color {
        var r = 0
        var g = 0
        var b = 0
        var contributors = 0.0
        for (dy in 0 until patternWidth) {
            for (dx in 0 until patternWidth) {
                var sx = x - dx
                if (sx < 0) sx += state.width

                var sy = y - dy
                if (sy < 0) sy += state.height

                val cellIndex = sx + sy * state.width
                if (state.onBoundary(sx, sy)) {
                    continue
                }
                for (waveIndex in 0 until state.waveCount) {
                    if (state.wave[cellIndex][waveIndex]) {
                        contributors += 1.0
                        val color = colors[patterns[waveIndex][dx + dy * patternWidth]]
                        r += (color.red)
                        g += color.green
                        b += (color.blue)
                    }
                }
            }
        }
        return Color((r / contributors).toInt(), (g / contributors).toInt(), (b / contributors).toInt())
    }
}

typealias OverlappingPriorFunction = ((List<Color>, Array<IntArray>, Int, Int, Int) -> Double)

fun overlappingModel(
    seed: Int,
    patternWidth: Int, bitmap: (Int, Int) -> Color,
    bitmapWidth: Int,
    bitmapHeight: Int,
    modelWidth: Int,
    modelHeight: Int,
    periodicInput: Boolean,
    periodicOutput: Boolean,
    symmetry: Int, ignoreFrequencies: Boolean,
    prior: OverlappingPriorFunction? = null
): OverlappingModel {
    val sample = Array(bitmapHeight) { IntArray(bitmapWidth) }
    val colorMap = mutableMapOf<Color, Int>()
    val colors = mutableListOf<Color>()

    for (y in 0 until bitmapHeight) {
        for (x in 0 until bitmapWidth) {
            val color = bitmap(x, y)
            val index = colorMap.getOrPut(color) {
                colors.add(color)
                colorMap.size
            }
            sample[y][x] = index
        }
    }
    val stateCount = colorMap.size
    val w = Math.pow(stateCount * 1.0, patternWidth * patternWidth * 1.0)

    fun pattern(f: (Int, Int) -> Int): IntArray {
        val result = IntArray(patternWidth * patternWidth)
        for (y in 0 until patternWidth) {
            for (x in 0 until patternWidth) {
                result[x + y * patternWidth] = f(x, y)
            }
        }
        return result
    }

    fun patternFromSample(x: Int, y: Int): IntArray {
        return pattern { dx, dy ->
            sample[(y + dy) % bitmapHeight][(x + dx) % bitmapWidth]
        }
    }

    fun rotate(pattern: IntArray): IntArray {
        return pattern { x, y ->
            pattern[patternWidth - 1 - y + x * patternWidth]
        }
    }

    fun reflect(pattern: IntArray): IntArray {
        return pattern { x, y ->
            pattern[patternWidth - 1 - x + y * patternWidth]
        }
    }

    fun hashFromPattern(pattern: IntArray): Long {
        var result = 0L
        var power = 1L
        for (i in 0 until pattern.size) {
            result += pattern[pattern.size - 1 - i] * power
            power *= stateCount
        }
        return result
    }

    fun patternFromHash(index: Long): IntArray {
        var residue = index
        var power = w.toLong()
        val pattern = IntArray(patternWidth * patternWidth)

        for (i in 0 until pattern.size) {
            power /= stateCount
            var count = 0

            while (residue >= power) {
                residue -= power
                count++
            }
            pattern[i] = count
        }
        return pattern
    }

    val patternOccurrences = mutableMapOf<Long, Int>()
    val hashedPatterns = mutableListOf<Long>()

    val endY = if (periodicInput) bitmapHeight else bitmapHeight - patternWidth + 1
    val endX = if (periodicInput) bitmapWidth else bitmapWidth - patternWidth + 1

    for (y in 0 until endY) {
        for (x in 0 until endX) {
            val ps0 = patternFromSample(x, y)
            val ps1 = reflect(ps0)
            val ps2 = rotate(ps0)
            val ps3 = reflect(ps2)
            val ps4 = rotate(ps2)
            val ps5 = reflect(ps4)
            val ps6 = rotate(ps4)
            val ps7 = reflect(ps6)

            val ps = arrayOf(ps0, ps1, ps2, ps3, ps4, ps5, ps6, ps7)
            for (k in 0 until symmetry) {
                val hash = hashFromPattern(ps[k])
                if (patternOccurrences.containsKey(hash)) {
                    patternOccurrences[hash] = (patternOccurrences[hash]!! + 1)
                } else {
                    patternOccurrences[hash] = 1
                    hashedPatterns.add(hash)
                }
            }
        }
    }

    val waveCount = patternOccurrences.size
    val waveWeights = DoubleArray(waveCount) { waveIndex ->
        val o = hashedPatterns[waveIndex]
        (patternOccurrences[o]!!).toDouble()
    }

    val sum = waveWeights.sum()
    waveWeights.forEachIndexed { index, d -> waveWeights[index] /= sum }


    if (ignoreFrequencies) {
        waveWeights.forEachIndexed { index, d -> waveWeights[index] = 1.0 }
    }

    val patterns = Array(waveCount) { it -> patternFromHash(hashedPatterns[it]) }

    fun agrees(p1: IntArray, p2: IntArray, dx: Int, dy: Int): Boolean {
        val xmin = if (dx < 0) 0 else dx
        val xmax = if (dx < 0) dx + patternWidth else patternWidth
        val ymin = if (dy < 0) 0 else dy
        val ymax = if (dy < 0) dy + patternWidth else patternWidth
        for (y in ymin until ymax) {
            for (x in xmin until xmax) {
                if (p1[x + patternWidth * y] != p2[x - dx + patternWidth * (y - dy)])
                    return false
            }
        }
        return true
    }

    val propagator = Array(4) { neighbour ->
        Array(waveCount) { waveIndex ->
            val list = mutableListOf<Int>()
            for (t2 in 0 until waveCount) {
                if (agrees(patterns[waveIndex], patterns[t2], DX[neighbour], DY[neighbour])) {
                    list.add(t2)
                }
            }
            list.toIntArray()
        }
    }

    fun onBoundary(x: Int, y: Int): Boolean =
        !periodicOutput && (x + patternWidth > modelWidth || y + patternWidth > modelHeight || x < 0 || y < 0)


    val realPrior: ((Int, Int, Int) -> Double)? = if (prior != null) {
        { x, y, w -> prior(colors, patterns, x, y, w) }
    } else {
        null
    }

    val state = State(seed, modelWidth, modelHeight, waveWeights, propagator, ::onBoundary, realPrior)
    return OverlappingModel(patternWidth, periodicInput, periodicOutput, patterns, colors, state)
}