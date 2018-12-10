package org.openrndr.wfc

import java.util.*
import kotlin.random.Random

internal val DX = intArrayOf(-1, 0, 1, 0)
internal val DY = intArrayOf(0, 1, 0, -1)
private val opposite = intArrayOf(2, 3, 0, 1)

fun DoubleArray.random(r: Double): Int {
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

enum class ObservationResult {
    CONTINUE,
    FINISHED,
    CONFLICT
}

class State(
    private val seed: Int,
    val width: Int,
    val height: Int,
    val waveWeights: DoubleArray,
    private val propagator: Array<Array<IntArray>>,
    val onBoundary: (Int, Int) -> Boolean
) {

    val waveCount = waveWeights.size
    val random = Random(seed)
    val wave = Array(width * height) { BooleanArray(waveCount) }
    private val compatible = Array(width * height) {
        Array(waveCount) {
            IntArray(4)
        }
    }

    private val weightLogWeights = DoubleArray(waveCount)
    private val sumOfWeights: Double
    private val sumOfWeightLogWeights: Double

    private val sumsOfOnes = IntArray(width * height)
    private val sumsOfWeights = DoubleArray(width * height)
    private val sumsOfWeightLogWeights = DoubleArray(width * height)

    private val entropies = DoubleArray(width * height)
    private val stack = Stack<Pair<Int, Int>>()

    private val startingEntropy: Double
    var observable: Boolean = false
        private set
    var observed: IntArray = IntArray(width * height)

    init {
        var sumOfWeights = 0.0
        var sumOfWeightLogWeights = 0.0

        for (waveIndex in 0 until waveCount) {
            weightLogWeights[waveIndex] = waveWeights[waveIndex] * Math.log(waveWeights[waveIndex])
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

    fun observe(): ObservationResult {
        var min = 1E+3
        var argmin = -1

        for (cellIndex in 0 until wave.size) {
            if (onBoundary(cellIndex % width, cellIndex / width))
                continue

            val amount = sumsOfOnes[cellIndex]
            if (amount == 0) {
                return ObservationResult.CONFLICT
            }

            val entropy = entropies[cellIndex]
            if (amount > 1 && entropy <= min) {
                val noise = 1E-6 * random.nextDouble()
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
            return ObservationResult.FINISHED
        }

        val distribution = DoubleArray(waveCount)
        for (waveIndex in 0 until waveCount) {
            distribution[waveIndex] = if (wave[argmin][waveIndex]) waveWeights[waveIndex] else 0.0
        }
        val r = distribution.random(random.nextDouble())
        for (waveIndex in 0 until waveCount) {
            if (wave[argmin][waveIndex] != (waveIndex == r)) ban(argmin, waveIndex)
        }
        return ObservationResult.CONTINUE
    }

    fun propagate() {
        while (stack.isNotEmpty()) {
            val (cellIndex, waveIndex) = stack.pop()

            val cx = cellIndex % width
            val cy = cellIndex / width

            for (neighbour in 0 until 4) {
                var nx = cx + DX[neighbour]
                var ny = cy + DY[neighbour]

                if (onBoundary(nx, ny))
                    continue

                if (nx < 0) {
                    nx += width
                } else if (nx >= width) {
                    nx -= width
                }

                if (ny < 0) {
                    ny += height
                } else if (nx >= height) {
                    ny -= height
                }


                val neighbourIndex = nx + ny * width
                val p = propagator[neighbour][waveIndex]
                val compat = compatible[neighbourIndex]
                for (l in 0 until p.size) {
                    val t2 = p[l]
                    val comp = compat[t2]
                    comp[neighbour]--
                    if (comp[neighbour] == 0) {
                        ban(neighbourIndex, t2)
                    }
                }
            }
        }
    }

    fun ban(cellIndex: Int, waveIndex: Int) {
        wave[cellIndex][waveIndex] = false
        for (neighbour in 0 until 4) {
            compatible[cellIndex][waveIndex][neighbour] = 0
        }
        stack.push(Pair(cellIndex, waveIndex))

        val sum = sumsOfWeights[cellIndex]
        entropies[cellIndex] += sumsOfWeightLogWeights[cellIndex] / sum - Math.log(sum)
        sumsOfOnes[cellIndex] -= 1
        sumsOfWeights[cellIndex] -= waveWeights[waveIndex]
        sumsOfWeightLogWeights[cellIndex] -= weightLogWeights[waveIndex]

        val sum2 = sumsOfWeights[cellIndex]
        entropies[cellIndex] -= sumsOfWeightLogWeights[cellIndex] / sum - Math.log(sum2)
    }

    fun clear() {
        observable = false
        for (cellIndex in 0 until wave.size) {
            for (waveIndex in 0 until waveCount) {
                wave[cellIndex][waveIndex] = true
                for (neighbour in 0 until 4) {
                    compatible[cellIndex][waveIndex][neighbour] =
                            propagator[opposite[neighbour]][waveIndex].size
                }
                sumsOfOnes[cellIndex] = waveWeights.size
                sumsOfWeights[cellIndex] = sumOfWeights
                sumsOfWeightLogWeights[cellIndex] = sumOfWeightLogWeights
                entropies[cellIndex] = startingEntropy
            }
        }
    }
}