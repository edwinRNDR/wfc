package org.openrndr.wfc.openrndr

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ColorBufferShadow
import org.openrndr.wfc.*

fun overlappingModel(
    seed: Int,
    N: Int,
    input: ColorBuffer,
    modelWidth: Int,
    modelHeight: Int,
    periodicInput: Boolean,
    periodicOutput: Boolean,
    symmetry: Int, ignoreFrequencies: Boolean,
    prior: OverlappingPriorFunction? = null
): OverlappingModel {
    input.shadow.download()
    return overlappingModel(
        seed, N, input.shadow, modelWidth, modelHeight,
        periodicInput, periodicOutput, symmetry, ignoreFrequencies,
        prior
    )
}

fun overlappingModel(
    seed: Int,
    N: Int,
    shadow: ColorBufferShadow,
    modelWidth: Int,
    modelHeight: Int,
    periodicInput: Boolean,
    periodicOutput: Boolean,
    symmetry: Int, ignoreFrequencies: Boolean,
    prior: OverlappingPriorFunction? = null
): OverlappingModel {

    fun bitmap(x: Int, y: Int): Color {
        val c = shadow.read(x, y)
        return Color((c.r * 255).toInt(), (c.g * 255).toInt(), (c.b * 255).toInt())
    }

    return overlappingModel(
        seed, N, ::bitmap, shadow.colorBuffer.width, shadow.colorBuffer.height, modelWidth, modelHeight,
        periodicInput, periodicOutput, symmetry, ignoreFrequencies,
        prior
    )
}

fun OverlappingModel.decode(shadow: ColorBufferShadow) {
    for (y in 0 until Math.min(state.height, shadow.colorBuffer.height)) {
        for (x in 0 until Math.min(state.width, shadow.colorBuffer.width)) {
            val c = decode(x, y)
            shadow.write(x, y, ColorRGBa(c.red / 255.0, c.green / 255.0, c.blue / 255.0))
        }
    }
}

fun OverlappingModel.decode(state: State, output: ColorBuffer) {
    decode(output.shadow)
    output.shadow.upload()
}

fun SimpleTiledModelBuilder.tile(name:String, data: ColorBuffer, symmetry: Char, weight:Double = 1.0) {
    data.shadow.download()
    val tileData = Array(data.width * data.height) {
        val x = it % data.width
        val y = it / data.width
        val c = data.shadow.read(x, y)
        Color((c.r*255).toInt(), (c.g *255).toInt(), (c.b * 255).toInt())
    }
    data.shadow.destroy()
    tile(name, tileData, symmetry, weight)
}


fun SimpleTiledModel.decode(output: ColorBuffer) {

    for (sy in 0 until state.height) {
        for (sx in 0 until state.width) {
            val tile = decode(sx, sy)

            for (j in 0 until tileHeight) {
                for (i in 0 until tileWidth) {
                    val u = sx * tileWidth + i
                    val v = sy * tileHeight + j

                    val c = tile[j * tileWidth + i]
                    val r = c.red / 255.0
                    val b = c.blue / 255.0
                    val g = c.green / 255.0
                    output.shadow.write(u, v, r, g, b, 1.0)
                }
            }
        }
    }
    output.shadow.upload()
}