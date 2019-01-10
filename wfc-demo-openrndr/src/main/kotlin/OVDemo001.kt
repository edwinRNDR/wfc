package org.openrndr.wfc.demo

import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.draw.BufferMultisample
import org.openrndr.extras.camera.Debug3D
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Vector3
import org.openrndr.wfc.Color
import org.openrndr.wfc.ObservationResult
import org.openrndr.wfc.demo.voxelrenderer.VoxelRenderer
import org.openrndr.wfc.overlappingVoxelModel
import org.openrndr.wfc.vox.readVox
import java.lang.RuntimeException

/**
 * Overlapping Voxel demo
 * Warning: this doesn't seem to work so well currently
 */
fun main(args: Array<String>) = application {

    configure {
        width = 24 * 32
        height = 24 * 32
    }

    program {
        extend(ScreenRecorder()) {
            frameRate = 60
            quitAfterMaximum = true
            maximumDuration = 60.0
            multisample = BufferMultisample.SampleCount(8)
        }

        var finished = false

        val voxelRenderer = VoxelRenderer()
        val voxFile = readVox("data/voxels/overlap/tower-floor-prior.vox")
        val ovm = overlappingVoxelModel(401, voxFile, 2, 64, 64, 16, false,
            false, 1, true,null)


        val floorColors = setOf(Color(34, 34,34), Color(170, 170, 170))

        keyboard.keyDown.listen{
            if (it.key == KEY_SPACEBAR) {
                finished = false
                ovm.state.clear()
                ovm.setFloorConstraints(floorColors)
                val s3 = ovm.state.propagate()
                if (s3 == ObservationResult.CONFLICT) {
                    throw RuntimeException("broken start")
                }
            }
        }

        ovm.state.clear()
        ovm.setFloorConstraints(floorColors)
        val s3 = ovm.state.propagate()
        if (s3 == ObservationResult.CONFLICT) {
            throw RuntimeException("broken start")
        }
        val restore = ovm.state.copy()

        extend(Debug3D())
        var frame = 0
        extend {
            frame ++

            if (!finished) {
                if (frame % 5 == 1)
                    ovm.state.copyInto(restore)
                val s = ovm.state.observe()

                if (s == ObservationResult.CONTINUE) {
                    val s2 = ovm.state.propagate()

                    if (s2 == ObservationResult.CONFLICT) {
                        println("conflict 2, restore")
                        restore.copyInto(ovm.state)
                        //ovm.state.clear()
                    }

                } else if (s == ObservationResult.CONFLICT) {
                    println("conflict :( attempting restore")
                    restore.copyInto(ovm.state)

                } else if (s == ObservationResult.FINISHED) {
                    println("finished")
                    finished = true
                }

            }
            val voxels = ovm.decode()

            drawer.translate(
                -ovm.state.width * 1 * 0.5,
                -ovm.state.height * 1 * 0.5,
                -ovm.state.depth * 1 * 0.5
            )
            drawer.rotate(Vector3.UNIT_X, -90.0)
            voxelRenderer.render(drawer, voxels)
        }
    }
}