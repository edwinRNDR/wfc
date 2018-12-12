package org.openrndr.wfc.demo

import org.openrndr.application
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.loadImage
import org.openrndr.ffmpeg.ScreenRecorder
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
        extend(ScreenRecorder()) {
            frameRate = 60
            quitAfterMaximum = true
            maximumDuration = 30.0
        }

        val image = loadImage("data/overlap/RNDR.png")
        val size = 200
        val model = overlappingModel((Math.random()*100000).toInt(),4, image, size, size, true, false, symmetry = 8)
        val output = colorBuffer(size, size)



        val copy = model.state.copy()

        println(copy)
        println(model.state)

        model.state.clear()
        model.state.copyInto(copy)

        var res = ObservationResult.CONTINUE
        var index = 0
        var conflicts = 0
        extend {
            if (res == ObservationResult.FINISHED) {
                model.state.clear()
                copy.clear()
                index = 0
            }
            if (res != ObservationResult.CONFLICT) {
                if (index % 10 == 0) {
                    model.state.copyInto(copy)
                }
            }
            for (i in 0 until 10) {
                res = model.state.observe()
                if (res == ObservationResult.CONFLICT) {
                    println("trying to fix conlict")
                    copy.copyInto(model.state)
                    conflicts ++
                }

                if (res == ObservationResult.CONTINUE) {
                    conflicts = 0
                    model.state.propagate()
                }
                if (conflicts == 10) {
                    res = ObservationResult.FINISHED
                }

            }
            index++
            model.decode(model.state, output)
            output.filter(MinifyingFilter.NEAREST, MagnifyingFilter.NEAREST)
            drawer.image(output, output.bounds, Rectangle(0.0, 0.0, size*4.0, size*4.0))
        }
    }
}