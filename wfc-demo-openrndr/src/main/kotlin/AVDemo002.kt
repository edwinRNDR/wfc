package org.openrndr.wfc.demo

import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.dnky.*
import org.openrndr.extras.camera.Debug3D
import org.openrndr.math.Vector3
import org.openrndr.math.mod
import org.openrndr.wfc.ObservationResult3D
import org.openrndr.wfc.atlasVoxelModel
import org.openrndr.wfc.demo.voxelrenderer.VoxelRenderer

import org.openrndr.draw.BufferMultisample
import org.openrndr.draw.DrawPrimitive
import org.openrndr.extras.meshgenerators.boxMesh
import org.openrndr.extras.meshgenerators.groundPlaneMesh
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.transforms.rotate
import org.openrndr.math.transforms.transform

import java.io.File
import java.lang.RuntimeException

/**
 * Atlas Voxel demo with DNKY renderer
 */
fun main(args: Array<String>) = application {
    configure {
        width = 24 * 32
        height = 24 * 32
    }

    program {

        val pr = photographicRenderer()

//        // -- setup video recording
//        extend(ScreenRecorder()) {
//            frameRate = 60
//            quitAfterMaximum = true
//            maximumDuration = 60.0
//            multisample = BufferMultisample.SampleCount(8)
//        }

        val ovm = atlasVoxelModel {
            tileSize = 6
            depth = 8
            linkAtlasses = true
            tiles(File("data/voxels/atlas/starbase-mk2/tiles/buildings"), 30.0)
            tiles(File("data/voxels/atlas/starbase-mk2/tiles/roads"), 1.0)
            atlas(File("data/voxels/atlas/starbase-mk2/starbase-buildings.vox"))
            atlas(File("data/voxels/atlas/starbase-mk2/starbase-roads.vox"))
        }

        val voxelRenderer = VoxelRenderer()

        val s = scene {
            node {
                transform = transform {
                    translate(0.0, 40.0, 0.0)
                    rotate(Vector3.UNIT_X, 90.0)
                }
                spotLight {
                    color = ColorRGBa.WHITE.shade(1.0)
                    shadows = true
                    innerAngle = 0.0
                    outerAngle = 45.0
                    linearAttenuation = 0.05
                }
            }

            node {
                hemisphereLight {
                    upColor = ColorRGBa.WHITE.shade(0.05)
                    downColor = ColorRGBa(0.05, 0.1, 0.05).shade(0.05)
                }
            }

            node {
                this.transform = transform {
                    translate(
                        0.0,
                        -ovm.state.height * ovm.voxelSize * 0.25,
                        0.0)
                }

                mesh {
                    geometry = geometry(groundPlaneMesh(500.0,500.0))
                    basicMaterial {
                        color = ColorRGBa.WHITE
                        roughness = 1.0
                        metalness = 1.0
                    }
                }
            }

            node {
                this.transform = transform {
                    rotate(Vector3.UNIT_X, 270.0)
                    translate(
                    -ovm.state.width * ovm.voxelSize * 0.5,
                    -ovm.state.height * ovm.voxelSize * 0.5,
                    -ovm.state.depth * ovm.voxelSize * 0.5)
                }

                instancedMesh {
                    attributes += voxelRenderer.offsets
                    geometry = geometry(voxelRenderer.cube, DrawPrimitive.TRIANGLES)
                    update {
                        instances = voxelRenderer.count
                    }

                    basicMaterial {
                        vertexTransform = """
                            x_position += i_offset;
                            """
                        color = ColorRGBa.WHITE
                        metalness = 1.0
                        roughness = 1.0

                        texture {
                            source = TextureFromCode("texture.rgb = vec3(cos(v_worldPosition.y*1.0)*0.5+0.5);")
                            target = TextureTarget.ROUGNESS
                        }


                        texture {
                            source = TextureFromCode("texture.rgb = vec3(cos(v_worldPosition.y*0.5)*0.5+0.5);")
                            target = TextureTarget.METALNESS
                        }

                        texture {
                            source = TextureFromCode("texture.rgb = pow(vi_color.rgb, vec3(2.2));")
                            target = TextureTarget.COLOR
                        }

                        texture {
                            source = TextureFromCode("""
                                float tx = smoothstep(0.0, 0.1/1.0, mod(0.6+v_worldPosition.x/1.0,1.0) ) * smoothstep(0.2/1.0, 0.1/1.0, mod(0.6+v_worldPosition.x/1.0,1.0) );
                                float tz = smoothstep(0.0, 0.1/1.0, mod(0.6+v_worldPosition.z/1.0,1.0) ) * smoothstep(0.2/1.0, 0.1/1.0, mod(0.6+v_worldPosition.z/1.0,1.0) );
                                float ty = smoothstep(0.0, 0.1/1.0, mod(0.6+v_worldPosition.y/1.0,1.0) ) * smoothstep(0.2/1.0, 0.1/1.0, mod(0.6+v_worldPosition.y/1.0,1.0) );
                                texture.rgb =  vec3(1.0 - 0.1*(ty+tz+tx)); //vec3(1.0 - 0.2*(max(0.0,max(tx,tz))));
                                """)
                            target = TextureTarget.COLOR
                        }

                    }
                }
            }
            node {
                mesh {
                    geometry = geometry(boxMesh(500.0, 500.0, 500.0, invert = true))
                    basicMaterial {
                        metalness = 0.0
                        roughness = 1.0
                        color = ColorRGBa.BLACK
                    }
                }
            }
        }
        var finished = false

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
            pr.fogColor = ColorRGBa.PINK.toLinear()
            pr.fogDensity = 0.0001
            pr.focalPlane = 4.0
            pr.aperture = 2.0
            pr.exposure = 1.0
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

            voxelRenderer.update(voxels)
            pr.renderer.draw(drawer, s, PerspectiveCamera(1.0, 1.0, 1.0, 1.0, 1.0))
        }
    }
}