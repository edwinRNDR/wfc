package org.openrndr.wfc.demo

import org.openrndr.KEY_ARROW_DOWN
import org.openrndr.KEY_ARROW_UP
import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.loadImage
import org.openrndr.math.mod
import org.openrndr.wfc.ObservationResult
import org.openrndr.wfc.demo.tilemodels.circleTiling
import org.openrndr.wfc.openrndr.decode
import org.openrndr.wfc.openrndr.tile
import org.openrndr.wfc.simpleTiledModel

/**
 * A very simple WFC drawer. Click to set cell to tile.
 * Spacebar - toggle auto-complete
 * Arrow up - next tile
 * Arrow down - previous tile
 */
fun main(args: Array<String>) = application {

    configure {
        width = 24 * 32
        height = 24 * 32
    }

    program {
        val result = colorBuffer(24*32, 24*32)
        val stm = circleTiling()
        stm.state.clear()
        var autoComplete = false
        var tileIndex = 0

        mouse.clicked.listen {
            val sx = (it.position.x / 32).toInt()
            val sy = (it.position.y / 32).toInt()
            stm.setState(sx, sy, tileIndex)
        }

        mouse.dragged.listen {
            val sx = (it.position.x / 32).toInt()
            val sy = (it.position.y / 32).toInt()
            stm.setState(sx, sy, tileIndex)
        }

        keyboard.keyDown.listen {
            if (it.key == KEY_SPACEBAR) {
                autoComplete = !autoComplete
            }
            if (it.key == KEY_ARROW_UP) {
                tileIndex = (tileIndex+1)% stm.state.waveCount
            }
            if (it.key == KEY_ARROW_DOWN) {
                tileIndex = mod(tileIndex-1, stm.state.waveCount)
            }
        }

        extend {
            if (autoComplete) {
                stm.state.observe()
            }
            stm.state.propagate()
            stm.decode(result)
            drawer.image(result)
        }
    }
}