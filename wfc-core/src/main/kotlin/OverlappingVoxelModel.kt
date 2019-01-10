package org.openrndr.wfc

import org.openrndr.wfc.vox.VoxFile
import org.openrndr.wfc.vox.Voxel
import org.openrndr.wfc.vox.denseVoxels
import java.util.*

private data class Pattern(val pattern: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Pattern
        return Arrays.equals(pattern, other.pattern)
    }

    override fun hashCode(): Int = Arrays.hashCode(pattern)
}


class OverlappingVoxelModel(
    val patternWidth: Int,
    val patterns: Array<IntArray>,
    val colors: List<Color>,
    val state: State3D
) {

    fun setFloorConstraints(floorColors: Set<Color>) {
        val floorStates = mutableListOf<Int>()
        val fillStates = mutableListOf<Int>()
        val fillXStates = mutableListOf<Int>()
        val fillYStates = mutableListOf<Int>()
        val fillZStates = mutableListOf<Int>()

        for (i in 0 until patterns.size) {

            // find all floor states
            x@ for (x in 0 until patternWidth) {
                for (y in 0 until patternWidth) {
                    if (colors[patterns[i][x + y * patternWidth]] in floorColors) {
                        floorStates.add(i)
                        break@x
                    }
                }
            }

            outer@
            for (k in 0 until patternWidth * patternWidth * patternWidth) {
                if (colors[patterns[i][k]].red >= 0) {
                    fillStates.add(i)
                    break@outer
                }
            }
//            // find non-void states, states for which the 0-element is non-void
//            if (colors[patterns[i][0]].red >= 0) {
//                fillStates.add(i)
//            }

//            // x-terminators
//            outer@ for (y in 0 until patternWidth)
//                for (z in 0 until patternWidth)
//                    if (colors[patterns[i][patternWidth - 1 + y * patternWidth + z * patternWidth * patternWidth]].red >= 0) {
//                        fillXStates.add(i)
//                        break@outer
//                    }
//
//            // y-terminators
//            outer@ for (x in 0 until patternWidth)
//                for (z in 0 until patternWidth)
//                    if (colors[patterns[i][(patternWidth - 1) * patternWidth + x + z * patternWidth * patternWidth]].red >= 0) {
//                        fillYStates.add(i)
//                        break@outer
//                    }
//
//            // z-terminators
//            outer@ for (x in 0 until patternWidth)
//                for (y in 0 until patternWidth)
//                    if (colors[patterns[i][(patternWidth - 1) * patternWidth * patternWidth + x + y * patternWidth]].red >= 0) {
//                        fillZStates.add(i)
//                        break@outer
//                    }
        }


        for (x in 0 until state.width) {
            for (y in 0 until state.height) {
                for (w in 0 until state.waveCount) {
                    // ban non-floor states from floor cells
                    if (w !in floorStates) {
                        state.ban(state.xyzToIndex(x, y, 0), w)
                    }
                }
                for (z in 1 until state.depth) {
                    // ban floor states from non-floor cells
                    for (w in floorStates) {
                        state.ban(state.xyzToIndex(x, y, z), w)
                    }
                }
            }
        }

        // ban non-void states from front and back
        for (x in 0 until state.width) {
            for (z in 1 until state.depth) {
                for (w in fillStates) {
                    state.ban(state.xyzToIndex(x, 0, z), w)
//                    state.ban(state.xyzToIndex(x, 1, z), w)
                    state.ban(state.xyzToIndex(x, state.height - 1, z), w)
                    state.ban(state.xyzToIndex(x, state.height - 2, z), w)
                    state.ban(state.xyzToIndex(x, state.height - 3, z), w)
                }
            }
        }

        // ban non-void states from left and right
        for (y in 0 until state.height) {
            for (z in 1 until state.depth) {
                for (w in fillStates) {
                    state.ban(state.xyzToIndex(0, y, z), w)
                    state.ban(state.xyzToIndex(state.width - 1, y, z), w)
                    state.ban(state.xyzToIndex(state.width - 2, y, z), w)
                    state.ban(state.xyzToIndex(state.width - 3, y, z), w)
                }
                for (w in fillXStates) {
                    state.ban(state.xyzToIndex(state.width - 1, y, z), w)
                }
            }
        }

        // ban non-void states from ceiling
        for (x in 0 until state.width) {
            for (y in 0 until state.height) {
                for (w in fillStates) {
                    state.ban(state.xyzToIndex(x, y, state.depth - 1), w)
                    state.ban(state.xyzToIndex(x, y, state.depth - 2), w)
                    state.ban(state.xyzToIndex(x, y, state.depth - 3), w)
                }
                for (w in fillZStates) {
                    state.ban(state.xyzToIndex(x, y, state.depth - 1), w)
                }
            }
        }
    }

    fun decode(): Array<Voxel> {
        val result = mutableListOf<Voxel>()
        for (z in 0 until state.depth) {
            for (y in 0 until state.height) {
                for (x in 0 until state.width) {
                    val c = decode(x, y, z)
                    if (c.red > -1) {
                        result.add(Voxel(x, y, z, c))
                    }
                }
            }
        }
        return result.toTypedArray()
    }

    fun decode(x: Int, y: Int, z: Int): Color {
        if (state.observed[state.xyzToIndex(x, y, z)] != -1) {
            return decodeObservation(x, y, z)
        } else {
            return Color(-1, -1, -1)
        }

//        return if (state.observable) {
//            decodeObservation(x, y, z)
//        } else {
//            decodeSuperposition(x, y, z)
//        }
    }

    fun decodeObservation(x: Int, y: Int, z: Int): Color {
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

        val dz = if (z < state.depth - patternWidth + 1) {
            0
        } else {
            patternWidth - 1
        }

        val stateIdx = state.xyzToIndex(x - dx, y - dy, z - dz)
        val patternIdx = dx + dy * patternWidth + dz * patternWidth * patternWidth


        if (state.observed[stateIdx] == -1) {
            return Color(-1, -1, -1)
        }

        val c = colors[patterns[state.observed[stateIdx]][patternIdx]]
        return c
    }

    fun decodeSuperposition(x: Int, y: Int, z: Int): Color {
        var r = 0
        var g = 0
        var b = 0
        var contributors = 0.0
        for (dz in 0 until patternWidth) {
            for (dy in 0 until patternWidth) {
                for (dx in 0 until patternWidth) {
                    var sx = x - dx
                    if (sx < 0) sx += state.width

                    var sy = y - dy
                    if (sy < 0) sy += state.height

                    var sz = z - dz
                    if (sz < 0) sz += state.depth

                    val cellIndex = state.xyzToIndex(sx, sy, sz)
                    if (state.onBoundary(sx, sy, sz)) {
                        continue
                    }

                    val patternIndex = dx + dy * patternWidth + dz * patternWidth * patternWidth

                    for (waveIndex in 0 until state.waveCount) {
                        if (state.wave[cellIndex][waveIndex]) {
                            val color = colors[patterns[waveIndex][patternIndex]]
                            if (color.red != -1) {
                                contributors += 1.0
                                r += (color.red)
                                g += color.green
                                b += (color.blue)
                            }
                        }
                    }
                }
            }
        }

        return if (contributors > 0.0)
            Color((r / contributors).toInt(), (g / contributors).toInt(), (b / contributors).toInt())
        else {
            Color(-1, -1, -1)
        }
    }
}

typealias OverlappingPriorFunction3D = ((List<Color>, Array<IntArray>, Int, Int, Int, Int) -> Double)

/**
 * Create an OverlapingVoxelModel from a `VoxFile`
 */
fun overlappingVoxelModel(
    seed: Int, voxFile: VoxFile, patternWidth: Int,
    modelWidth: Int, modelHeight: Int, modelDepth: Int,
    periodicInput: Boolean, periodicOutput: Boolean,
    symmetry: Int, ignoreFrequencies: Boolean, prior: OverlappingPriorFunction3D? = null
): OverlappingVoxelModel {

    val denseVox = denseVoxels(voxFile)

    fun voxFunc(x: Int, y: Int, z: Int): Color {
        return denseVox[x + y * voxFile.width + z * voxFile.width * voxFile.height]
    }

    return overlappingVoxelModel(
        seed, ::voxFunc, voxFile.width, voxFile.height, voxFile.depth, patternWidth,
        modelWidth, modelHeight, modelDepth, periodicInput, periodicOutput, symmetry, ignoreFrequencies, prior
    )
}

/**
 * Create an OverlappingVoxelModel
 */
fun overlappingVoxelModel(
    seed: Int,
    voxel: (Int, Int, Int) -> Color,
    voxelWidth: Int,
    voxelHeight: Int,
    voxelDepth: Int,
    patternWidth: Int,
    modelWidth: Int,
    modelHeight: Int,
    modelDepth: Int,
    periodicInput: Boolean,
    periodicOutput: Boolean,
    symmetry: Int, ignoreFrequencies: Boolean,
    prior: OverlappingPriorFunction3D? = null
): OverlappingVoxelModel {
    val sample = Array(voxelDepth) { Array(voxelHeight) { IntArray(voxelWidth) } }
    val colorMap = mutableMapOf<Color, Int>()
    val colors = mutableListOf<Color>()

    for (z in 0 until voxelDepth) {
        for (y in 0 until voxelHeight) {
            for (x in 0 until voxelWidth) {
                val color = voxel(x, y, z)
                val index = colorMap.getOrPut(color) {
                    colors.add(color)
                    colorMap.size
                }
                sample[z][y][x] = index
            }
        }
    }

    println("number of colors: ${colors.size} ${colorMap.size}")
    val stateCount = colorMap.size

    fun pattern(f: (Int, Int, Int) -> Int): IntArray {
        val result = IntArray(patternWidth * patternWidth * patternWidth)
        for (z in 0 until patternWidth) {
            for (y in 0 until patternWidth) {
                for (x in 0 until patternWidth) {
                    result[x + y * patternWidth + z * patternWidth * patternWidth] = f(x, y, z)
                }
            }
        }
        return result
    }

    fun patternFromSample(x: Int, y: Int, z: Int): IntArray {
        return pattern { dx, dy, dz ->
            sample[(z + dz) % voxelDepth][(y + dy) % voxelHeight][(x + dx) % voxelWidth]
        }
    }

    fun rotate(pattern: IntArray): IntArray {
        return pattern { x, y, z ->
            pattern[(patternWidth - 1 - z) + y * patternWidth + x * patternWidth * patternWidth]
        }
    }

    fun reflect(pattern: IntArray): IntArray {
        return pattern { x, y, z ->
            pattern[patternWidth - 1 - x + (y * patternWidth) + (z * patternWidth * patternWidth)]
        }
    }

    val patternOccurrences = mutableMapOf<Pattern, Int>()

    val endZ = voxelDepth
    val endY = if (periodicInput) voxelHeight else voxelHeight - patternWidth + 1
    val endX = if (periodicInput) voxelWidth else voxelWidth - patternWidth + 1

    val hashedPatterns = mutableListOf<Pattern>()

    for (z in 0 until endZ) {
        for (y in 0 until endY) {
            for (x in 0 until endX) {
                val ps0 = patternFromSample(x, y, z)
                val ps1 = reflect(ps0)
                val ps2 = rotate(ps0)
                val ps3 = reflect(ps2)
                val ps4 = rotate(ps2)
                val ps5 = reflect(ps4)
                val ps6 = rotate(ps4)
                val ps7 = reflect(ps6)

                val ps = arrayOf(ps0, ps1, ps2, ps3, ps4, ps5, ps6, ps7)
                for (k in 0 until symmetry) {
                    val hash = Pattern(ps[k])
                    if (patternOccurrences.containsKey(hash)) {
                        patternOccurrences[hash] = (patternOccurrences[hash]!! + 1)
                    } else {
                        patternOccurrences[hash] = 1
                        hashedPatterns.add(hash)
                    }
                }
            }
        }
    }
    println("total patterns: ${patternOccurrences.size} ")
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

    val patterns = Array(waveCount) { it -> hashedPatterns[it].pattern }

    fun agrees(p1: IntArray, p2: IntArray, dx: Int, dy: Int, dz: Int): Boolean {
        val xmin = if (dx < 0) 0 else dx
        val xmax = if (dx < 0) dx + patternWidth else patternWidth
        val ymin = if (dy < 0) 0 else dy
        val ymax = if (dy < 0) dy + patternWidth else patternWidth
        val zmin = if (dz < 0) 0 else dz
        val zmax = if (dz < 0) dz + patternWidth else patternWidth

        for (z in zmin until zmax) {
            for (y in ymin until ymax) {
                for (x in xmin until xmax) {
                    if (p1[x + patternWidth * y + z * patternWidth * patternWidth] !=
                        p2[(x - dx) + patternWidth * (y - dy) + patternWidth * patternWidth * (z - dz)]
                    ) {
                        return false
                    }
                }
            }
        }
        return true
    }

    val propagator = Array(6) { neighbour ->
        Array(waveCount) { waveIndex ->
            val list = mutableListOf<Int>()
            for (t2 in 0 until waveCount) {
                if (agrees(patterns[waveIndex], patterns[t2], DX3[neighbour], DY3[neighbour], DZ3[neighbour])) {
                    list.add(t2)
                }
            }
            list.toIntArray()
        }
    }

    fun onBoundary(x: Int, y: Int, z: Int): Boolean =
        !periodicOutput && (x + patternWidth > modelWidth
                || y + patternWidth > modelHeight
                || z + patternWidth > modelDepth
                || x < 0
                || y < 0
                || z < 0)

    val realPrior: ((Int, Int, Int, Int) -> Double)? = if (prior != null) {
        { x, y, z, w -> prior(colors, patterns, x, y, z, w) }
    } else {
        null
    }

    val state = State3D(seed, modelWidth, modelHeight, modelDepth, waveWeights, propagator, ::onBoundary, realPrior, null)
    return OverlappingVoxelModel(patternWidth, patterns, colors, state)
}