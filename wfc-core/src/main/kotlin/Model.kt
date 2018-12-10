package org.openrndr.wfc

import java.util.*

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

class Model(
    val N: Int,
    val width: Int,
    val height: Int,
    val periodicInput: Boolean,
    val periodicOutput: Boolean
)

enum class ObservationResult {
    CONTINUE,
    FINISHED,
    CONFLICT
}

class State(
    val model: Model,
    val weights: DoubleArray,
    private val propagator: Array<Array<IntArray>>,
    val onBoundary: (Int, Int) -> Boolean,
    val T: Int
) {
    val wave = Array(model.width * model.height) { BooleanArray(T) }
    val compatible = Array(model.width * model.height) {
        Array(T) {
            IntArray(4)
        }
    }

    private val weightLogWeights = DoubleArray(T)
    var sumOfWeights = 0.0
    private var sumOfWeightLogWeights = 0.0

    var sumsOfOnes = IntArray(model.width * model.height)
    private var sumsOfWeights = DoubleArray(model.width * model.height)
    private var sumsOfWeightLogWeights = DoubleArray(model.width * model.height)

    private var entropies = DoubleArray(model.width * model.height)
    private val stack = Stack<Pair<Int, Int>>()

    private val startingEntropy: Double
    var observable: Boolean = false
        private set
    var observed: IntArray = IntArray(model.width * model.height)

    init {
        for (t in 0 until T) {
            weightLogWeights[t] = weights[t] * Math.log(weights[t])
            sumOfWeights += weights[t]
            sumOfWeightLogWeights += weightLogWeights[t]
        }
        if (sumOfWeights == 0.0) {
            throw IllegalStateException("cannot start with total weight of 0")
        }

        startingEntropy = Math.log(sumOfWeights) - sumOfWeightLogWeights / sumOfWeights
    }

//    fun run(seed:Int, limit:Int = Int.MAX_VALUE) : Boolean {
//        clear()
//        for (i in 0 until limit) {
//            val result = observe()
//            if (result != null) {
//                return result
//            }
//            propagate()
//        }
//        return true
//    }

    fun observe(): ObservationResult {
        var min = 1E+3
        var argmin = -1

        for (i in 0 until wave.size) {
            if (onBoundary(i % model.width, i / model.width))
                continue

            val amount = sumsOfOnes[i]
            if (amount == 0) {
                return ObservationResult.CONFLICT
            }

            val entropy = entropies[i]
            if (amount > 1 && entropy <= min) {
                val noise = 1E-6 * Math.random()
                if (entropy + noise < min) {
                    min = entropy + noise
                    argmin = i
                }
            }
        }

        if (argmin == -1) {
            observable = true
            for (i in 0 until wave.size) {
                for (t in 0 until T) {
                    if (wave[i][t]) {
                        observed[i] = t
                        break
                    }
                }
            }
            return ObservationResult.FINISHED
        }
        val distribution = DoubleArray(T)
        for (t in 0 until T) {
            distribution[t] = if (wave[argmin][t]) weights[t] else 0.0
        }
        val r = distribution.random(Math.random())
        val w = wave[argmin]
        for (t in 0 until T) {
            if (w[t] != (t == r)) ban(argmin, t)
        }
        return ObservationResult.CONTINUE
    }

    fun propagate() {
        while (stack.isNotEmpty()) {
            val e1 = stack.pop()
            val i1 = e1.first
            val x1 = i1 % model.width
            val y1 = i1 / model.width

            for (d in 0 until 4) {
                val dx = DX[d]
                val dy = DY[d]

                var x2 = x1 + dx
                var y2 = y1 + dy

                if (onBoundary(x2, y2))
                    continue

                if (x2 < 0) {
                    x2 += model.width
                } else if (x2 >= model.width) {
                    x2 -= model.width
                }

                if (y2 < 0) {
                    y2 += model.height
                } else if (x2 >= model.height) {
                    y2 -= model.height
                }

                val i2 = x2 + y2 * model.width
                val p = propagator[d][e1.second]
                val compat = compatible[i2]
                for (l in 0 until p.size) {
                    val t2 = p[l]
                    val comp = compat[t2]

                    comp[d]--
                    if (comp[d] == 0) {
                        ban(i2, t2)
                    }
                }
            }
        }
    }

    fun ban(i: Int, t: Int) {
        wave[i][t] = false
        val comp = compatible[i][t]
        for (d in 0 until 4) {
            comp[d] = 0
        }
        stack.push(Pair(i, t))

        val sum = sumsOfWeights[i]
        entropies[i] += sumsOfWeightLogWeights[i] / sum - Math.log(sum)
        sumsOfOnes[i] -= 1
        sumsOfWeights[i] -= weights[t]
        sumsOfWeightLogWeights[i] -= weightLogWeights[t]

        val sum2 = sumsOfWeights[i]
        entropies[i] -= sumsOfWeightLogWeights[i] / sum - Math.log(sum2)
    }

    fun clear() {
        observable = false
        for (i in 0 until wave.size) {
            for (t in 0 until T) {
                wave[i][t] = true
                for (d in 0 until 4) {
                    compatible[i][t][d] = propagator[opposite[d]][t].size
                }
                sumsOfOnes[i] = weights.size
                sumsOfWeights[i] = sumOfWeights
                sumsOfWeightLogWeights[i] = sumOfWeightLogWeights
                entropies[i] = startingEntropy
            }
        }
    }
}