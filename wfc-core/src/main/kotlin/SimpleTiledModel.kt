package org.openrndr.wfc

class SimpleTiledModel(val tiles: List<Array<Color>>) {





}



class SimpleTiledModelBuilder() {

    class Neighbour(val left: String, val right:String, val leftAction:Int = 0, val     rightAction:Int = 0 )


    val neighbours = mutableListOf<Neighbour>()

    val tiles = mutableListOf<Array<Color>>()
    val action = mutableListOf<IntArray>()
    var unique = false
    var periodic = false
    var black = false
    var tileSize = 16

    private var tempStationary = mutableListOf<Double>()

    val firstOccurrence = mutableMapOf<String, Int>()

    fun neighbour(left:String, right:String, leftAction: Int = 0, rightAction: Int = 0) {

        neighbours.add(Neighbour(left, right, leftAction, rightAction))

    }

    fun tile(name: String, symmetry:Char, weight:Double=1.0) {

        val a: (Int) -> Int
        val b: (Int) -> Int
        val cardinality : Int

        when (symmetry) {
            'L' -> {
                cardinality = 4
                a = { (it + 1) % 4}
                b = { if ((it%2) == 0) it + 1 else it -1 }
            }
            'T' -> {
                cardinality = 4
                a = { (it + 1) % 4 }
                b = { if (it %2 ==0) it else 4 - it }
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
        val map = Array(8) { t  ->

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
        for (t in 0 until 8)
        action.add(map[t])

        for (t in 0 until cardinality) {
            tempStationary.add(weight)
        }
    }


    fun build() {
        val T = action.size
        val tempPropagator = Array(4) {
            Array(T) {
                BooleanArray(T)
            }
        }
        for (neighbour in neighbours) {
            val L = action[firstOccurrence[neighbour.left]!!][neighbour.leftAction]
            val U = action[L][1]

            val R = action[firstOccurrence[neighbour.right]!!][neighbour.rightAction]
            val D= action[R][1]

            tempPropagator[0][action[R][6]][action[L][6]] = true
            tempPropagator[0][action[L][4]][action[R][4]] = true
            tempPropagator[0][action[R][2]][action[R][2]] = true

            tempPropagator[1][U][D] = true
            tempPropagator[1][action[D][6]][action[U][6]] = true
            tempPropagator[1][action[U][4]][action[D][4]] = true
            tempPropagator[1][action[D][2]][action[U][2]] = true
        }

        for (t2 in 0 until T) {
            for (t1 in 0 until T) {
                tempPropagator[2][t2][t1] = tempPropagator[0][t1][t2]
                tempPropagator[3][t2][t1] = tempPropagator[1][t1][t2]
            }
        }
    }

}
