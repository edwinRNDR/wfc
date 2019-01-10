package org.openrndr.wfc.demo

import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.extras.camera.Debug3D
import org.openrndr.math.Vector3
import org.openrndr.math.mod
import org.openrndr.wfc.ObservationResult3D
import org.openrndr.wfc.atlasVoxelModel
import org.openrndr.wfc.demo.voxelrenderer.VoxelRenderer

import java.io.File
import java.lang.RuntimeException

/**
 * Atlas Voxel demo
 */
fun main(args: Array<String>) = application {
    configure {
        width = 24 * 32
        height = 24 * 32
    }

    program {
        var finished = false

        val voxelRenderer = VoxelRenderer()
        val ovm = atlasVoxelModel {
            tileSize = 6
            depth = 8
            linkAtlasses = true
            tiles(File("data/voxels/atlas/starbase-mk2/tiles/buildings"), 30.0)
            tiles(File("data/voxels/atlas/starbase-mk2/tiles/roads"), 1.0)
            atlas(File("data/voxels/atlas/starbase-mk2/starbase-buildings.vox"))
            atlas(File("data/voxels/atlas/starbase-mk2/starbase-roads.vox"))
        }

        keyboard.keyDown.listen{
            // -- restart when spacebar is pressed
            if (it.key == KEY_SPACEBAR) {
                finished = false
                ovm.state.clear()
                ovm.setFloorConstraints()
                ovm.setCeilingConstraints()
                ovm.setWallConstraints()
                val s = ovm.state.propagate()
                if (s is ObservationResult3D.Conflict) {
                    throw RuntimeException("broken start")
                }
            }
        }

        ovm.state.clear()
        ovm.setFloorConstraints()
        ovm.setCeilingConstraints()
        ovm.setWallConstraints()

        val s3 = ovm.state.propagate()
        if (s3 is ObservationResult3D.Conflict) {
            throw RuntimeException("broken start")
        }
        val restore = mutableListOf(ovm.state.copy())

        for (i in 0 until 10) restore.add(ovm.state.copy())

        // -- 3d camera controls
        extend(Debug3D())
        var frame = 0

        extend {
            frame ++
            if (!finished) {
                val s = ovm.state.observe()

                if (s == ObservationResult3D.Continue) {
                    val s2 = ovm.state.propagate()

                    if (s2 is ObservationResult3D.Conflict) {
                        println("== conflict after propagate ==")
                        restore[(frame-10)%restore.size].copyInto(ovm.state)

                    } else {
                        ovm.state.copyInto(restore[mod(frame-10,restore.size)])
                    }
                } else if (s is ObservationResult3D.Conflict) {
                    println("== conflict after observe ==")
                    restore[mod(frame-5,restore.size)].copyInto(ovm.state)
                } else if (s == ObservationResult3D.Finished) {
                    println("finished")
                    finished = true
                    println(ovm.state.observed[ovm.state.xyzToIndex(0, 0, 0)])
                }
            }

            val voxels = ovm.decode()

            drawer.translate(
                -ovm.state.width * ovm.voxelSize * 0.5,
                -ovm.state.height * ovm.voxelSize * 0.5,
                -ovm.state.depth * ovm.voxelSize * 0.5
            )
            drawer.rotate(Vector3.UNIT_X, -90.0)
            voxelRenderer.render(drawer, voxels)
        }
    }
}