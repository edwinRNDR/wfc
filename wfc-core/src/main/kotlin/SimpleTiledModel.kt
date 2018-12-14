//package org.openrndr.wfc
//
//class SimpleTiledModel(val tiles: List<Array<Color>>) {
//
//
//
//
//
//}
//
//class SimpleTiledModelBuilder() {
//
//
//    val tiles = mutableListOf<Array<Color>>()
//    val action = mutableListOf<IntArray>()
//
//    fun tile(tile: Array<Color>, symmetry:Char ) {
//
//        val a: (Int) -> Int
//        val b: (Int) -> Int
//        val cardinality : Int
//
//        when (symmetry) {
//            'L' -> {
//                cardinality = 4
//                a = { (it + 1) % 4}
//                b = { if ((it%2) == 0) it + 1 else it -1 }
//            }
//            'T' -> {
//                cardinality = 4
//                a = { (it + 1) % 4 }
//                b = { if (it %2 ==0) it else 4 - it }
//            }
//            'I' -> {
//                cardinality = 2
//                a = { 1 - it }
//                b = { it }
//            }
//            '\\' -> {
//                cardinality = 2
//                a = { 1 - it }
//                b = { 1 - it }
//            }
//            else -> {
//                cardinality = 1
//                a = { it }
//                b = { it }
//            }
//        }
//
//
//        val T = action.size
//        val map = Array(8) { t  ->
//
//            intArrayOf(
//                T + t,
//                T + a(t),
//                T + a(a(t)),
//                T + a(a(a(t))),
//                T + b(t),
//                T + b(a(t)),
//                T + b(a(a(t))),
//                T + b(a(a(a(t))))
//            )
//
//        }
//        action.add()
//
//    }
//
//
//
//}
