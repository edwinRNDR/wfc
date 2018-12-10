package org.openrndr.wfc

class OverlappingDecoder(private val model: Model,
                         private val colors: List<Color>,
                         private val patterns: Array<IntArray>) {


    fun decode(state: State, x:Int, y:Int) : Color {
        return if (state.observable) {
            decodeObservation(state, x, y)
        } else {
            decodeSuperposition(state, x, y)
        }
    }

    fun decodeObservation(state: State, x: Int, y: Int): Color {
        val dy = if (y < model.height - model.N + 1) {
            0
        } else {
            model.N - 1
        }
        val dx = if (x < model.width - model.N + 1) {
            0
        } else {
            model.N - 1
        }
        val c = colors[patterns[state.observed[x - dx + (y - dy) * model.width]][dx + dy * model.N]]
        return c
    }

    fun decodeSuperposition(state: State, x:Int, y:Int): Color {

        var r = 0
        var g = 0
        var b = 0
        var contributors = 0.0
        for (dy in 0 until model.N) {
            for (dx in 0 until model.N) {
                var sx = x - dx
                if (sx < 0) sx += model.width

                var sy = y - dy
                if (sy < 0) sy += model.height

                val s = sx + sy * model.width
                if (state.onBoundary(sx, sy)){
                    continue
                }
                for (t in 0 until state.T) {
                    if (state.wave[s][t]) {
                        contributors+= 1.0
                        val color = colors[patterns[t][dx + dy * model.N]]
                        r += (color.red )
                        g += color.green
                        b += (color.blue)
                    }
                }
            }

        }
        return Color((r/contributors).toInt(), (g/contributors).toInt(), (b/contributors).toInt())
    }

    /**
     *             for (i in 0 until wave.size) {
    var contributors = 0
    var r = 0
    var g = 0
    var b = 0
    val x = i % FMX
    val y = i / FMX

    for (dy in 0 until N){
    for (dx in 0 until N) {
    var sx = x - dx
    if (sx < 0) sx += FMX

    var sy = y - dy
    if (sy < 0) sy += FMY

    val s = sx + sy * FMX
    if (onBoundary(sx, sy)){
    continue
    }

    for (t in 0 until tCounter) {
    if (wave[s]?.get(t) == true) {
    contributors++
    val color = colors!![patterns?.get(t)!![dx + dy * N].toInt()]
    r += (color.red )
    g += color.green
    b += (color.blue)
    }
    }
    }

     */



}

class OverlappingModel(val state: State, val decoder: OverlappingDecoder)

fun overlappingModel(
    N: Int, bitmap: (Int, Int) -> Color,
    bitmapWidth: Int,
    bitmapHeight: Int,
    modelWidth: Int,
    modelHeight: Int,
    periodicInput: Boolean,
    periodicOutput: Boolean,
    symmetry: Int

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
    val C = colorMap.size

    println("$C entries in colorMap")

    val w = Math.pow(C * 1.0, N * N * 1.0).toDouble()

    fun pattern(f: (Int, Int) -> Int): IntArray {
        val result = IntArray(N * N)
        for (y in 0 until N) {
            for (x in 0 until N) {
                result[x + y * N] = f(x, y)
            }
        }
        return result
    }

    fun patternFromSample(x: Int, y: Int): IntArray {
        return pattern { dx, dy ->
            sample[(y + dy) % bitmapHeight][(x + dx) % bitmapWidth]
        }
    }

    fun rotate(p: IntArray): IntArray {
        return pattern { x, y ->
            p[N - 1 - y + x * N]
        }
    }

    fun reflect(p: IntArray): IntArray {
        return pattern { x, y ->
            p[N - 1 - x + y * N]
        }
    }

    fun index(p: IntArray): Long {
        var result = 0L
        var power = 1L
        for (i in 0 until p.size) {
            result += p[p.size - 1 - i] * power
            power *= C
        }
        return result

    }

    fun patternFromIndex(ind: Long): IntArray {
        var residue = ind
        var power = w.toLong()
        val result = IntArray(N * N)

        for (i in 0 until result.size) {
            power /= C
            var count = 0

            while (residue >= power) {
                residue -= power
                count++
            }
            result[i] = count
        }

        return result
    }

    val weights = mutableMapOf<Long, Int>()
    val ordering = mutableListOf<Long>()

    val endY = if (periodicInput) bitmapHeight else bitmapHeight - N + 1
    val endX = if (periodicInput) bitmapWidth else bitmapWidth - N + 1

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
                val ind = index(ps[k])
                if (weights.containsKey(ind)) {
                    weights[ind] = weights[ind] ?: 0 + 1
                } else {
                    weights[ind] = 1
                    ordering.add(ind)
                }
            }
        }
    }
    val T = weights.size
    println("number of weights: $T")
    //val ground = (ground + T) % T
    val baseWeights = DoubleArray(T)

    for (i in 0 until baseWeights.size) {
        val o = ordering[i]
        val w = weights[o] ?: 0
        baseWeights[i] = w.toDouble()
    }
    baseWeights

    val patterns = Array(T) { it -> patternFromIndex(ordering[it]) }

    fun agrees(p1: IntArray, p2: IntArray, dx: Int, dy: Int): Boolean {
        val xmin = if (dx < 0) 0 else dx
        val xmax = if (dx < 0) dx + N else N
        val ymin = if (dy < 0) 0 else dy
        val ymax = if (dy < 0) dy + N else N
        for (y in ymin until ymax) {
            for (x in xmin until xmax) {
                if (p1[x + N * y] != p2[x - dx + N * (y - dy)])
                    return false
            }
        }
        return true
    }

    val propagator = Array(4) { d ->
        Array(T) { t ->
            val list = mutableListOf<Int>()
            for (t2 in 0 until T) {
                if (agrees(patterns[t], patterns[t2], DX[d], DY[d])) {
                    list.add(t2)
                }
            }
            list.toIntArray()
        }
    }

    fun onBoundary(x: Int, y: Int): Boolean =
        !periodicOutput && (x + N > modelWidth || y + N > modelHeight || x < 0 || y < 0)

    val model = Model(N, modelWidth, modelHeight, periodicInput, periodicOutput)
    val state = State(model, baseWeights, propagator, ::onBoundary, T)
    val decoder = OverlappingDecoder(model, colors, patterns)
    return OverlappingModel(state, decoder)
}