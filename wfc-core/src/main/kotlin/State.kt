package org.openrndr.wfc

import java.util.*
import kotlin.random.Random

internal val DX = intArrayOf(-1, 0, 1, 0)
internal val DY = intArrayOf(0, 1, 0, -1)
private val opposite = intArrayOf(2, 3, 0, 1)

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

enum class ObservationResult {
    CONTINUE,
    FINISHED,
    CONFLICT
}

class State(
    private val seed: Int,
    val width: Int,
    val height: Int,
    private val waveWeights: DoubleArray,
    private val propagator: Array<Array<IntArray>>,
    val onBoundary: (Int, Int) -> Boolean,
    val prior: ((Int, Int, Int) -> Double)?
) {

    val waveCount = waveWeights.size
    val random = Random(seed)
    val wave = Array(width * height) { BitSet(waveCount) }
    private val compatible = Array(width * height) {
        Array(waveCount) {
            IntArray(4)
        }
    }

    private val weightLogWeights = DoubleArray(waveCount) { waveIndex ->
        waveWeights[waveIndex] * Math.log(waveWeights[waveIndex])
    }
    private val startingEntropy: Double
    private val sumOfWeights: Double
    private val sumOfWeightLogWeights: Double

    var observed: IntArray = IntArray(width * height)
    private val sumsOfOnes = IntArray(width * height)
    private val sumsOfWeights = DoubleArray(width * height)
    private val sumsOfWeightLogWeights = DoubleArray(width * height)
    private val entropies = DoubleArray(width * height)

    private val stack = Stack<Pair<Int, Int>>()


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

        val distribution = if (prior == null) {
            DoubleArray(waveCount) { waveIndex ->
                if (wave[argmin][waveIndex]) waveWeights[waveIndex] else 0.0
            }
        } else {
            val cy = argmin / width
            val cx = argmin % width
            DoubleArray(waveCount) { waveIndex ->
                if (wave[argmin][waveIndex]) waveWeights[waveIndex] * prior.invoke(cx, cy, waveIndex) else 0.0
            }
        }

        val observation = distribution.random(random.nextDouble())

        for (waveIndex in 0 until waveCount) {
            if (wave[argmin][waveIndex] != (waveIndex == observation)) {
                val res = ban(argmin, waveIndex)
                if (res == ObservationResult.CONFLICT) {
                    return ObservationResult.CONFLICT
                }
            }
        }
        return ObservationResult.CONTINUE
    }

    fun propagate(): ObservationResult {
        while (stack.isNotEmpty()) {
            val (cellIndex, waveIndex) = stack.pop()

            val cx = cellIndex % width
            val cy = cellIndex / width

            for (direction in 0 until 4) {
                var nx = cx + DX[direction]
                var ny = cy + DY[direction]

                if (onBoundary(nx, ny))
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

                val neighbourIndex = nx + ny * width

                for (propagationIndex in 0 until propagator[direction][waveIndex].size) {
                    val propagationWave = propagator[direction][waveIndex][propagationIndex]
                    compatible[neighbourIndex][propagationWave][direction]--
                    if (compatible[neighbourIndex][propagationWave][direction] == 0) {
                        val result = ban(neighbourIndex, propagationWave)
                        if (result == ObservationResult.CONFLICT) {
                            return ObservationResult.CONFLICT
                        }
                    }
                }
            }
        }
        return ObservationResult.CONTINUE
    }

    fun ban(cellIndex: Int, waveIndex: Int) : ObservationResult {
        wave[cellIndex][waveIndex] = false
        for (direction in 0 until 4) {
            compatible[cellIndex][waveIndex][direction] = 0
        }
        stack.push(Pair(cellIndex, waveIndex))

        sumsOfOnes[cellIndex] -= 1
        if (sumsOfOnes[cellIndex] == 0) {
            return ObservationResult.CONFLICT
        }

        val sum = sumsOfWeights[cellIndex]
        entropies[cellIndex] += sumsOfWeightLogWeights[cellIndex] / sum - Math.log(sum)


        sumsOfWeights[cellIndex] -= waveWeights[waveIndex]
        sumsOfWeightLogWeights[cellIndex] -= weightLogWeights[waveIndex]

        val sum2 = sumsOfWeights[cellIndex]
        entropies[cellIndex] -= sumsOfWeightLogWeights[cellIndex] / sum2 - Math.log(sum2)
        return ObservationResult.CONTINUE
    }

    fun clear() {
        stack.clear()
        observable = false
        for (cellIndex in 0 until wave.size) {
            for (waveIndex in 0 until waveCount) {
                wave[cellIndex][waveIndex] = true
                for (direction in 0 until 4) {
                    compatible[cellIndex][waveIndex][direction] =
                            propagator[opposite[direction]][waveIndex].size
                }

            }
            sumsOfOnes[cellIndex] = waveWeights.size
            sumsOfWeights[cellIndex] = sumOfWeights
            sumsOfWeightLogWeights[cellIndex] = sumOfWeightLogWeights
            entropies[cellIndex] = startingEntropy
        }
    }

    fun copy() : State {
        val copy = State(seed, width, height, waveWeights, propagator, onBoundary, prior)
        copyInto(copy)
        return copy
    }

    fun copyInto(copy : State) {
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