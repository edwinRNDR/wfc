package org.openrndr.wfc

import java.util.*
import kotlin.random.Random

internal val DX3 = intArrayOf(-1, 0, 1, 0, 0, 0)
internal val DY3 = intArrayOf(0, 1, 0, -1, 0, 0)
internal val DZ3 = intArrayOf(0, 0, 0, 0, 1, -1)


internal val opposite3 = intArrayOf(2, 3, 0, 1, 5, 4)

private fun DoubleArray.random(r: Double): Int {
    var sum = this.sum()

    if (sum == 0.0) {
        for (j in 0 until this.size) this[j] = 1.0
        sum = this.sum()
    }

    for (j in 0 until this.size) this[j] /= sum

    var i = 0
    var x = 0.0

    while (i < this.size) {
        x += this[i]
        if (r <= x) return i
        i++
    }

    return 0
}

private data class Coord3(val x: Int, val y: Int, val z: Int)

sealed class ObservationResult3D {
    object Continue : ObservationResult3D()
    object Finished : ObservationResult3D()
    data class Conflict(val x: Int, val y: Int, val z: Int, val wave: Int, val direction: Int) : ObservationResult3D()
}


/**
 * 3D state and solver
 */
class State3D(
    private val seed: Int,
    val width: Int,
    val height: Int,
    val depth: Int,
    private val waveWeights: DoubleArray,
    val propagator: Array<Array<IntArray>>,
    val onBoundary: (Int, Int, Int) -> Boolean,
    val prior: ((Int, Int, Int, Int) -> Double)?,
    val bias: ((Int, Int, Int) -> Double)?
) {
    val waveCount = waveWeights.size
    val random = Random(seed)
    val wave = Array(width * height * depth) { BitSet(waveCount) }
    private val compatible = Array(width * height * depth) {
        Array(waveCount) {
            IntArray(6)
        }
    }

    private val weightLogWeights = DoubleArray(waveCount) { waveIndex ->
        waveWeights[waveIndex] * Math.log(waveWeights[waveIndex])
    }
    private val startingEntropy: Double
    private val sumOfWeights: Double
    private val sumOfWeightLogWeights: Double

    var observed: IntArray = IntArray(width * height * depth) { -1 }
    private val sumsOfOnes = IntArray(width * height * depth)
    private val sumsOfWeights = DoubleArray(width * height * depth)
    private val sumsOfWeightLogWeights = DoubleArray(width * height * depth)
    private val entropies = DoubleArray(width * height * depth)

    private val stack = Stack<Pair<Int, Int>>()

    fun xyzToIndex(x: Int, y: Int, z: Int): Int {
        return x + y * width + z * (width * height)
    }

    private fun indexToXyz(index: Int): Coord3 {
        return Coord3(index % width, (index / width) % height, index / (width * height))
    }

    var observable: Boolean = false
        private set

    init {
        var sumOfWeights = 0.0
        var sumOfWeightLogWeights = 0.0

        for (waveIndex in 0 until waveCount) {
            sumOfWeights += waveWeights[waveIndex]
            sumOfWeightLogWeights += weightLogWeights[waveIndex]
        }
        if (sumOfWeights == 0.0) {
            throw IllegalStateException("cannot start with total weight of 0")
        }

        this.sumOfWeights = sumOfWeights
        this.sumOfWeightLogWeights = sumOfWeightLogWeights
        startingEntropy = Math.log(sumOfWeights) - sumOfWeightLogWeights / sumOfWeights
    }

    fun observe(): ObservationResult3D {
        var min = 1E+3
        var argmin = -1

        for (cellIndex in 0 until wave.size) {
            val (cx, cy, cz) = indexToXyz(cellIndex)

            if (onBoundary(cx, cy, cz))
                continue

            val amount = sumsOfOnes[cellIndex]
            if (amount == 0) {
                return ObservationResult3D.Conflict(cx, cy, cz, -1, -1)
            }

            val entropy = entropies[cellIndex]
            if (amount > 1 && entropy <= min) {
                val noise = 1E-6 * random.nextDouble() + (bias?.invoke(cx, cy, cz) ?: 0.0)
                if (entropy + noise < min) {
                    min = entropy + noise
                    argmin = cellIndex
                }
            }
        }

        if (argmin == -1) {
            observable = true
            for (cellIndex in 0 until wave.size) {
                for (waveIndex in 0 until waveCount) {
                    if (wave[cellIndex][waveIndex]) {
                        observed[cellIndex] = waveIndex
                        break
                    }
                }
            }
            return ObservationResult3D.Finished
        }

        val distribution = if (prior == null) {
            DoubleArray(waveCount) { waveIndex ->
                if (wave[argmin][waveIndex]) waveWeights[waveIndex] else 0.0
            }
        } else {
            val (cx, cy, cz) = indexToXyz(argmin)
            DoubleArray(waveCount) { waveIndex ->
                if (wave[argmin][waveIndex]) waveWeights[waveIndex] * prior.invoke(cx, cy, cz, waveIndex) else 0.0
            }
        }

        val observation = distribution.random(random.nextDouble())

        observed[argmin] = observation
        for (waveIndex in 0 until waveCount) {
            if (wave[argmin][waveIndex] != (waveIndex == observation)) {
                val res = ban(argmin, waveIndex)
                if (res is ObservationResult3D.Conflict) {
                    return res
                }
            }
        }
        return ObservationResult3D.Continue
    }


    // -- this is broken, don't use it.
    fun blast(x: Int, y: Int, z: Int, blastWidth: Int = 3, blastHeight: Int = 3, blastDepth: Int = 3) {
        fun resetCell(x: Int, y: Int, z: Int) {
            val cellIndex = xyzToIndex(x, y, z)
            observable = false
            observed[cellIndex] = -1

            sumsOfWeightLogWeights[cellIndex] = sumOfWeightLogWeights
            sumsOfWeights[cellIndex] = sumOfWeights
            sumsOfOnes[cellIndex] = waveCount
            entropies[cellIndex] = startingEntropy
            for (waveIndex in 0 until waveCount) {
                wave[cellIndex][waveIndex] = true
                for (direction in 0 until 6) {
                    compatible[cellIndex][waveIndex][direction] = propagator[direction][waveIndex].size
                }
            }
        }

        val blasted = mutableListOf<Coord3>()
        for (w in -blastDepth..blastDepth) {
            for (v in -blastHeight..blastHeight) {
                for (u in -blastWidth..blastWidth) {
                    val nx = x + u
                    val ny = y + v
                    val nz = z + w
                    if (nx >= 0 && ny >= 0 && nz >= 0 && nx < width && ny < height && nz < depth) {
                        resetCell(nx, ny, nz)
                        blasted.add(Coord3(nx, ny, nz))
                    }
                }
            }
        }

        blasted.sortByDescending { Math.max(Math.max(Math.abs(it.x), Math.abs(it.y)), Math.abs(it.z)) }

        for ((cx, cy, cz) in blasted) {
            val cellIndex = xyzToIndex(cx, cy, cz)
            if (cx >= 0 && cy >= 0 && cz >= 0 && cx < width && cy < height && cz < depth) {
                for (direction in 0 until 6) {
                    val nx = cx + DX3[direction]
                    val ny = cy + DY3[direction]
                    val nz = cz + DZ3[direction]

                    if (nx >= 0 && ny >= 0 && nz >= 0 && nx < width && ny < height && nz < depth) {
                        val neighbourIndex = xyzToIndex(nx, ny, nz)
                        val counts = IntArray(waveCount) { 0 }

                        for (waveIndex in 0 until waveCount) {
                            if (wave[neighbourIndex][waveIndex]) {
                                for (wavePropagated in propagator[opposite3[direction]][waveIndex]) {
                                    counts[wavePropagated]++
                                }
                            }
                        }
                        if (counts.sum() == 0) {
                            throw IllegalStateException("inert cell at $nx, $ny, $nz")
                        }

                        for (waveIndex in 0 until waveCount) {
                            compatible[cellIndex][waveIndex][direction] = counts[waveIndex]
                            if (counts[waveIndex] == 0) {
                                val res = ban(cellIndex, waveIndex)
                                if (res is ObservationResult3D.Conflict) {
                                    throw IllegalStateException("conflict at ${res.x}, ${res.y},${res.z} while recovering at level, wi $waveIndex")
                                }
                                stack.clear() // -- destroy the stack
                            }
                        }
                    }
                }
            }
        }
    }

    fun propagate(): ObservationResult3D {
        while (stack.isNotEmpty()) {
            val (cellIndex, waveIndex) = stack.pop()
            val (cx, cy, cz) = indexToXyz(cellIndex)

            for (direction in 0 until 6) {
                var nx = cx + DX3[direction]
                var ny = cy + DY3[direction]
                var nz = cz + DZ3[direction]

                if (onBoundary(nx, ny, nz))
                    continue

                if (nx < 0) {
                    nx += width
                } else if (nx >= width) {
                    nx -= width
                }

                if (ny < 0) {
                    ny += height
                } else if (ny >= height) {
                    ny -= height
                }

                if (nz < 0) {
                    nz += depth
                } else if (nz >= depth) {
                    nz -= depth
                }

                val neighbourIndex = xyzToIndex(nx, ny, nz)
                val opp = opposite3[direction]

                for (propagationIndex in 0 until propagator[direction][waveIndex].size) {
                    val propagationWave = propagator[direction][waveIndex][propagationIndex]
                    compatible[neighbourIndex][propagationWave][direction]--
                    if (compatible[neighbourIndex][propagationWave][direction] == 0) {
                        val result = ban(neighbourIndex, propagationWave)
                        if (result is ObservationResult3D.Conflict) {
                            println("propagation conflict at ($cx,$cy,$cz) vs ($nx,$ny,$nz) - $direction (${DX3[direction]},${DY3[direction]},${DZ3[direction]}) - state: $waveIndex->$propagationWave")
                            return result.copy(direction = direction)
                        }
                    }
                }
            }
        }
        return ObservationResult3D.Continue
    }

    fun ban(cellIndex: Int, waveIndex: Int): ObservationResult3D {
        if (wave[cellIndex][waveIndex]) {
            wave[cellIndex][waveIndex] = false
            for (direction in 0 until 6) {
                compatible[cellIndex][waveIndex][direction] = 0
            }
            stack.push(Pair(cellIndex, waveIndex))

            sumsOfOnes[cellIndex] -= 1
            if (sumsOfOnes[cellIndex] == 0) {
                val (cx, cy, cz) = indexToXyz(cellIndex)
                println(("ran out of options at ($cx, $cy, $cz), last option was $waveIndex"))
                return ObservationResult3D.Conflict(cx, cy, cz, waveIndex, -1)
            }

            val sum = sumsOfWeights[cellIndex]
            entropies[cellIndex] += sumsOfWeightLogWeights[cellIndex] / sum - Math.log(sum)

            sumsOfWeights[cellIndex] -= waveWeights[waveIndex]
            sumsOfWeightLogWeights[cellIndex] -= weightLogWeights[waveIndex]

            val sum2 = sumsOfWeights[cellIndex]
            entropies[cellIndex] -= sumsOfWeightLogWeights[cellIndex] / sum2 - Math.log(sum2)
            return ObservationResult3D.Continue
        } else {
            return ObservationResult3D.Continue
        }
    }

    fun clear() {
        stack.clear()
        observable = false
        for (cellIndex in 0 until wave.size) {
            for (waveIndex in 0 until waveCount) {
                wave[cellIndex][waveIndex] = true
                for (direction in 0 until 6) {
                    compatible[cellIndex][waveIndex][direction] =
                            propagator[opposite3[direction]][waveIndex].size
                }

            }
            observed[cellIndex] = -1
            sumsOfOnes[cellIndex] = waveWeights.size
            sumsOfWeights[cellIndex] = sumOfWeights
            sumsOfWeightLogWeights[cellIndex] = sumOfWeightLogWeights
            entropies[cellIndex] = startingEntropy
        }
    }

    fun copy(): State3D {
        val copy = State3D(seed, width, height, depth, waveWeights, propagator, onBoundary, prior, bias)
        copyInto(copy)
        return copy
    }

    fun copyInto(copy: State3D) {
        copy.observable = false

        sumsOfOnes.forEachIndexed { index, d -> copy.sumsOfOnes[index] = d }
        sumsOfWeights.forEachIndexed { index, d -> copy.sumsOfWeights[index] = d }
        sumsOfWeightLogWeights.forEachIndexed { index, d -> copy.sumsOfWeightLogWeights[index] = d }
        entropies.forEachIndexed { index, d -> copy.entropies[index] = d }

        observed.forEachIndexed { index, d -> copy.observed[index] = d }
        for (j in 0 until wave.size) {
            for (i in 0 until waveCount) {
                copy.wave[j][i] = wave[j][i]
            }
        }

        for (j in 0 until compatible.size) {
            for (i in 0 until compatible[j].size) {
                for (k in 0 until compatible[j][i].size) {
                    copy.compatible[j][i][k] = compatible[j][i][k]
                }
            }
        }
        copy.stack.clear()
    }
}