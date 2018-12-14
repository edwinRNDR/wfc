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

        val stateHeight = 200
        val stateWidth = 200
        val patternWidth = 2
        val image = loadImage("data/overlap/d-003.png")
        val priorImage = loadImage("data/prior/mondriaan.png")
        priorImage.shadow.download()
        val size = 200
        val model = overlappingModel((Math.random()*100000).toInt(),patternWidth, image, size, size,
            true, false, 8, true ) {
            colors, patterns, x,y,w ->

            val dy = if (y < stateHeight - patternWidth + 1) {
                0
        } else {
            patternWidth - 1
        }
            val dx = if (x < stateWidth - patternWidth + 1) {
                0
            } else {
                patternWidth - 1
            }
            val c = colors[patterns[w][dx + dy * patternWidth]]

            val p = priorImage.shadow.read(x, y)

            val dr = c.red/255.0 - p.r
            val dg = c.green/255.0 - p.g
            val db = c.blue/255.0 - p.b

            val d = Math.sqrt(dr* dr + dg*dg + db*db)

            Math.exp(-d*40.0)


        }


        val output = colorBuffer(size, size)
        val copy = model.state.copy()

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
            for (i in 0 until 100) {
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