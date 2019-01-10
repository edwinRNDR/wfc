package org.openrndr.wfc.demo

import org.openrndr.application
import org.openrndr.draw.colorBuffer
import org.openrndr.wfc.ObservationResult
import org.openrndr.wfc.demo.tilemodels.circleTiling
import org.openrndr.wfc.openrndr.decode

fun main(args: Array<String>) = application {

    configure {
        width = 24 * 32
        height = 24 * 32
    }

    program {
        val result = colorBuffer(24*32, 24*32)
        val stm = circleTiling()
        stm.state.clear()
        extend {
            val s = stm.state.observe()

            if (s == ObservationResult.CONTINUE) {
                val s2 = stm.state.propagate()
                if (s2 == ObservationResult.CONFLICT) {
                    println("conflict s2!")
                }

            }
            if (s == ObservationResult.CONFLICT) {
                println("conflict!")
            }

            stm.decode(result)

            drawer.image(result)
        }
    }
}