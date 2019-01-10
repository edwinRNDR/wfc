package org.openrndr.wfc


private fun mod(a:Int, b:Int): Int {
    //return ((a%b) + b) % b
    return a % b
}

class SimpleTiledModel(
    val state: State,
    val tileWidth: Int,
    val tileHeight: Int,
    val tiles: List<Tile>
) {
    fun setState(x: Int, y:Int, observation:Int): ObservationResult {
        val cellIndex = y * state.width + x
        if (state.wave[cellIndex][observation]) {
            state.observed[cellIndex] = observation
            for (waveIndex in 0 until state.waveCount) {
                if (state.wave[cellIndex][waveIndex] != (waveIndex == observation)) {
                    val res = state.ban(cellIndex, waveIndex)
                    if (res == ObservationResult.CONFLICT) {
                        return ObservationResult.CONFLICT
                    }
                }
            }
        }
        return ObservationResult.CONTINUE
    }

    fun decode(x: Int, y: Int): Array<Color> =
        if (state.observable) {
            decodeObservation(x, y)
        } else {
            decodeSuperposition(x, y)
        }

    fun decodeObservation(x: Int, y: Int): Array<Color> =
        tiles[state.observed[y * state.width + x]].data

    fun decodeSuperposition(x: Int, y: Int): Array<Color> {
        val result = Array(tileWidth * tileHeight) {
            IntArray(3)
        }
        var contributors = 0.0
        for (waveIndex in 0 until state.waveCount) {
            if (state.wave[y * state.width + x][waveIndex]) {
                contributors++
                for (v in 0 until tileHeight) {
                    for (u in 0 until tileWidth) {
                        val tileIndex = v * tileWidth + u
                        result[tileIndex][0] += tiles[waveIndex].data[tileIndex].red
                        result[tileIndex][1] += tiles[waveIndex].data[tileIndex].green
                        result[tileIndex][2] += tiles[waveIndex].data[tileIndex].blue
                    }
                }
            }
        }
        for (v in 0 until tileHeight) {
            for (u in 0 until tileWidth) {
                val tileIndex = v * tileWidth + u
                result[tileIndex][0] = (result[tileIndex][0] / contributors).toInt()
                result[tileIndex][1] = (result[tileIndex][1] / contributors).toInt()
                result[tileIndex][2] = (result[tileIndex][2] / contributors).toInt()
            }
        }
        return result.map { Color(it[0], it[1], it[2]) }.toTypedArray()
    }
}

class Tile(val data: Array<Color>, val rotations: Int)

class SimpleTiledModelBuilder {
    class Neighbour(val left: String, val right: String, val leftAction: Int = 0, val rightAction: Int = 0)

    var seed = 0
    var width = 16
    var height = 16
    val neighbours = mutableListOf<Neighbour>()

    val tiles = mutableListOf<Tile>()
    val action = mutableListOf<IntArray>()
    var periodic = false
    var tileSize = 16

    private var tempStationary = mutableListOf<Double>()

    val firstOccurrence = mutableMapOf<String, Int>()

    fun neighbour(left: String, right: String, leftAction: Int = 0, rightAction: Int = 0) {
        neighbours.add(Neighbour(left, right, leftAction, rightAction))
    }

    fun neighbour(left: String, right: String, vararg actions: Pair<Int, Int>) {
        for (action in actions) {
            neighbour(left, right, action.first, action.second)
        }
    }

    fun tile(name: String, data: Array<Color>, symmetry: Char, weight: Double = 1.0) {
        val a: (Int) -> Int
        val b: (Int) -> Int
        val cardinality: Int

        when (symmetry) {
            'L' -> {
                cardinality = 4
                a = { mod((it + 1), 4) }
                b = { if (mod(it,2) == 0) it + 1 else it - 1 }
            }
            'T' -> {
                cardinality = 4
                a = { mod(it + 1,  4)}
                b = { if (mod(it, 2) == 0) it else 4 - it }
            }
            'I' -> {
                cardinality = 2
                a = { 1 - it }
                b = { it }
            }
            '\\' -> {
                cardinality = 2
                a = { 1 - it }
                b = { 1 - it }
            }
            else -> {
                cardinality = 1
                a = { it }
                b = { it }
            }
        }

        val T = action.size
        /*

				map[t][0] = t;
				map[t][1] = a(t);
				map[t][2] = a(a(t));
				map[t][3] = a(a(a(t)));
				map[t][4] = b(t);
				map[t][5] = b(a(t));
				map[t][6] = b(a(a(t)));
				map[t][7] = b(a(a(a(t))));
         */

        val map = Array(cardinality) { t ->
            intArrayOf(
                T + t,
                T + a(t),
                T + a(a(t)),
                T + a(a(a(t))),
                T + b(t),
                T + b(a(t)),
                T + b(a(a(t))),
                T + b(a(a(a(t))))
            )
        }

        firstOccurrence.put(name, action.size)
        for (t in 0 until cardinality) {
            action.add(map[t])
        }

        for (t in 0 until cardinality) {
            tempStationary.add(weight)
        }

        fun tile(f: (Int, Int) -> Color): Array<Color> =
            Array(tileSize * tileSize) {
                val x = it % tileSize
                val y = it / tileSize
                f(x, y)
            }

        fun rotate(array: Array<Color>): Array<Color> =
            tile { x, y -> array[tileSize - 1 - y + x * tileSize] }

        tiles.add(Tile(data, 0))
        for (t in 1 until cardinality) {
            tiles.add(Tile(rotate(tiles[T + t - 1].data), t))
        }
    }

    fun build(): SimpleTiledModel {
        val waveCount = action.size
        val tempPropagator = Array(4) {
            Array(waveCount) {
                BooleanArray(waveCount) { false }
            }
        }
        for (neighbour in neighbours) {

            val L = action[firstOccurrence[neighbour.left]!!][neighbour.leftAction]
            val D = action[L][1]

            val R = action[firstOccurrence[neighbour.right]!!][neighbour.rightAction]
            val U = action[R][1]

            tempPropagator[0][R][L] = true
            tempPropagator[0][action[R][6]][action[L][6]] = true
            tempPropagator[0][action[L][4]][action[R][4]] = true
            tempPropagator[0][action[L][2]][action[R][2]] = true

            tempPropagator[1][U][D] = true
            tempPropagator[1][action[D][6]][action[U][6]] = true
            tempPropagator[1][action[U][4]][action[D][4]] = true
            tempPropagator[1][action[D][2]][action[U][2]] = true
        }

        for (t2 in 0 until waveCount) {
            for (t1 in 0 until waveCount) {
                tempPropagator[2][t2][t1] = tempPropagator[0][t1][t2]
                tempPropagator[3][t2][t1] = tempPropagator[1][t1][t2]
            }
        }


        val propagator = Array(4) { d ->
            Array(waveCount) { t1 ->
                val sp = mutableListOf<Int>()
                val tp = tempPropagator[d][t1]

                for (t2 in 0 until waveCount) {
                    if (tp[t2]) {
                        sp.add(t2)
                    }
                }
                sp.toIntArray()
            }
        }

        val onBoundary = { x: Int, y: Int -> !periodic && (x < 0 || y < 0 || x >= width || y >= height) }
        val waveWeights = tempStationary.toDoubleArray()
        val tw = waveWeights.sum()
        waveWeights.forEachIndexed { index, v -> waveWeights[index] = v / tw }

        val state = State(seed, width, height, waveWeights, propagator, onBoundary, null)
        return SimpleTiledModel(state, tileSize, tileSize, tiles)
    }
}

fun simpleTiledModel(init: SimpleTiledModelBuilder.() -> Unit): SimpleTiledModel {
    val stmb = SimpleTiledModelBuilder()
    stmb.init()
    return stmb.build()
}
