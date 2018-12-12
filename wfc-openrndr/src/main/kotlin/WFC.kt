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
    symmetry: Int, ignoreFrequencies: Boolean
): OverlappingModel {
    input.shadow.download()
    return overlappingModel(
        seed, N, input.shadow, modelWidth, modelHeight,
        periodicInput, periodicOutput, symmetry, ignoreFrequencies
    )
}

fun overlappingModel(
    seed:Int,
    N: Int,
    shadow: ColorBufferShadow,
    modelWidth: Int,
    modelHeight: Int,
    periodicInput: Boolean,
    periodicOutput: Boolean,
    symmetry: Int, ignoreFrequencies: Boolean
): OverlappingModel {

    fun bitmap(x: Int, y: Int): Color {
        val c = shadow.read(x, y)
        return Color((c.r * 255).toInt(), (c.g * 255).toInt(), (c.b * 255).toInt())
    }

    return overlappingModel(
        seed, N, ::bitmap, shadow.colorBuffer.width, shadow.colorBuffer.height, modelWidth, modelHeight,
        periodicInput, periodicOutput, symmetry, ignoreFrequencies
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