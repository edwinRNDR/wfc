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

/**
 * Simple dithering demo
 */

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
        val image = loadImage("data/overlap/g-001.png")
        val priorImage = loadImage("data/prior/head.png")
        priorImage.shadow.download()
        val size = 200
        val model = overlappingModel(
            (Math.random() * 100000).toInt(), patternWidth, image, size, size,
            true, false, 8, false
        ) { colors, patterns, x, y, w ->
            var r = 0.0
            var g = 0.0
            var b = 0.0

            var pr = 0.0
            var pg = 0.0
            var pb = 0.0
            for (dy in 0 until patternWidth) {
                for (dx in 0 until patternWidth) {
                    val c = colors[patterns[w][dx + dy * patternWidth]]
                    val pc = priorImage.shadow.read(
                        (x - dx).coerceIn(0, stateWidth - 1),
                        (y - dy).coerceIn(0, stateHeight)
                    )
                    r += c.red
                    g += c.green
                    b += c.blue
                    pr += pc.r
                    pg += pc.g
                    pb += pc.b
                }
            }
            r /= 255.0
            g /= 255.0
            b /= 255.0

            r /= patternWidth * patternWidth
            g /= patternWidth * patternWidth
            b /= patternWidth * patternWidth
            pr /= patternWidth * patternWidth
            pg /= patternWidth * patternWidth
            pb /= patternWidth * patternWidth

            val dr = r - pr
            val dg = g - pg
            val db = b - pb

            val d = Math.sqrt(dr * dr + dg * dg + db * db)
            Math.exp(-d * 10.0)
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
                    conflicts++
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
            drawer.image(output, output.bounds, Rectangle(0.0, 0.0, size * 4.0, size * 4.0))
        }
    }
}