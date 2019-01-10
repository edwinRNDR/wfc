package org.openrndr.wfc.demo.tilemodels

import org.openrndr.draw.loadImage
import org.openrndr.wfc.SimpleTiledModel
import org.openrndr.wfc.openrndr.tile
import org.openrndr.wfc.simpleTiledModel

fun circleTiling(): SimpleTiledModel =
    simpleTiledModel {

        periodic = false
        width = 24
        height = 24
        tileSize = 32

        tile("b_half", loadImage("data/tiling/circles/b_half.png"), 'T')
        tile("b_i", loadImage("data/tiling/circles/b_i.png"), 'I')
        tile("b_quarter", loadImage("data/tiling/circles/b_quarter.png"), 'L')
        tile("w_half", loadImage("data/tiling/circles/w_half.png"), 'T')
        tile("w_i", loadImage("data/tiling/circles/w_i.png"), 'I')
        tile("w_quarter", loadImage("data/tiling/circles/w_quarter.png"), 'L')
        tile("b", loadImage("data/tiling/circles/b.png"), 'X')
        tile("w", loadImage("data/tiling/circles/w.png"), 'X')

        neighbour(
            "b_half", "b_half",
            0 to 0, 1 to 3, 3 to 1, 0 to 3, 0 to 2
        )
        neighbour(
            "b_half", "b_i",
            0 to 0, 3 to 3, 1 to 0
        )
        neighbour(
            "b_half", "b_quarter",
            0 to 0, 1 to 0, 2 to 0, 3 to 1
        )

        neighbour(
            "b_i", "b_i",
            0 to 0, 1 to 1
        )

        neighbour(
            "b_i", "b_quarter",
            0 to 0, 1 to 1
        )

        neighbour(
            "b_quarter", "b_quarter",
            0 to 1, 1 to 0, 2 to 0, 0 to 2
        )

        neighbour(
            "b_half", "w_half",
            1 to 1, 0 to 1, 3 to 0, 3 to 3
        )

        neighbour(
            "b_half", "w_i",
            0 to 1, 1 to 1, 3 to 0
        )

        neighbour(
            "b_half", "w_quarter",
            0 to 1, 0 to 2, 1 to 1, 3 to 0
        )

        neighbour(
            "b_i", "w_half",
            0 to 1, 1 to 0, 1 to 3
        )

        neighbour(
            "b_i", "w_i",
            0 to 1, 1 to 0
        )

        neighbour(
            "b_i", "w_quarter",
            0 to 1, 1 to 0
        )

        neighbour(
            "b_quarter", "w_half",
            0 to 0, 0 to 3, 0 to 2, 1 to 1
        )

        neighbour("b_quarter", "w_i", 0 to 0, 1 to 1)

        neighbour(
            "b_quarter", "w_quarter",
            0 to 0, 0 to 3, 1 to 1, 1 to 2
        )

        neighbour(
            "w_half", "w_half",
            0 to 0, 1 to 3, 3 to 1, 0 to 3, 0 to 2
        )

        neighbour(
            "w_half", "w_i",
            0 to 0, 3 to 3, 1 to 0
        )
        neighbour(
            "w_half", "w_quarter",
            0 to 0, 1 to 0, 2 to 0, 3 to 1
        )

        neighbour("w_i", "w_i", 0 to 0, 1 to 1)
        neighbour("w_i", "w_quarter", 0 to 0, 1 to 1)

        neighbour(
            "w_quarter", "w_quarter",
            0 to 1, 1 to 0, 2 to 0, 0 to 2
        )

        neighbour("b", "b", 0 to 0)
        neighbour("b", "b_half", 0 to 1)

        neighbour("b", "b_i", 0 to 1)
        neighbour("b", "b_quarter", 0 to 1)

        neighbour("b", "w_half", 0 to 0, 0 to 3)
        neighbour("b", "w_i", 0 to 0)

        neighbour("b", "w_quarter", 0 to 0)

        neighbour("w", "w", 0 to 0)
        neighbour("w", "w_half", 0 to 1)
        neighbour("w", "w_i", 0 to 1)
        neighbour("w", "w_quarter", 0 to 1)

        neighbour("w", "b_half", 0 to 0, 0 to 3)
        neighbour("w", "b_i", 0 to 0)

        neighbour("w", "b_quarter", 0 to 0)

    }