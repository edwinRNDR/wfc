package org.openrndr.wfc.demo

import org.openrndr.application
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.loadImage
import org.openrndr.shape.Rectangle
import org.openrndr.wfc.ObservationResult
import org.openrndr.wfc.openrndr.decode
import org.openrndr.wfc.openrndr.overlappingModel

fun main(args: Array<String>) = application {
    configure {
        width = 800
        height = 800
    }

    program {
        val image = loadImage("data/overlap/RNDR-3.png")
        val size = 200
        val model = overlappingModel((Math.random()*100000).toInt(),2, image, size, size,
            false, false, 1, true)
        val output = colorBuffer(size, size)

        model.state.clear()

        var res = ObservationResult.CONTINUE
        extend {
            if (res != ObservationResult.CONTINUE) {
                model.state.clear()
            }
            for (i in 0 until 10) {
                res = model.state.observe()
                if (res != ObservationResult.CONTINUE) {
                    break
                }
                model.state.propagate()
            }
            model.decode(model.state, output)
            output.filter(MinifyingFilter.NEAREST, MagnifyingFilter.NEAREST)
            drawer.image(output, output.bounds, Rectangle(0.0, 0.0, size*4.0, size*4.0))
        }
    }
}