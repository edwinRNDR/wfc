package org.openrndr.wfc.demo.voxelrenderer

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Vector3
import org.openrndr.wfc.vox.Voxel
import org.openrndr.wfc.vox.denseVoxels

/*
A voxel tessellator. Decides per voxel which sides to emit
 */

class VoxelTessellator {
    val vertices = vertexBuffer(vertexFormat {
        position(3)
        normal(3)
        color(4)
    }, 16384*80 )

    var count = 0

    fun update(width: Int, height: Int, depth: Int, voxels: Array<Voxel>) {
        val denseVoxels = denseVoxels(width, height, depth, voxels)
        val dx = arrayOf(-1, 1, 0, 0, 0, 0)
        val dy = arrayOf(0, 0, -1, 1, 0, 0)
        val dz = arrayOf(0, 0, 0, 0, -1, 1)

        val tx = arrayOf(0.0, 0.0, 1.0, -1.0, -1.0, 1.0)
        val ty = arrayOf(-1.0, 1.0, 0.0, 0.0, 0.0, 0.0)
        val tz = arrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        val bx = arrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val by = arrayOf(0.0, 0.0, 0.0, 0.0, 1.0, 1.0)
        val bz = arrayOf(1.0, 1.0, 1.0, 1.0, 0.0, 0.0)

        val bs = (0 until 6).map {
            Vector3(bx[it], by[it], bz[it]) * 0.5
        }.toTypedArray()

        val ts = (0 until 6).map {
            Vector3(tx[it], ty[it], tz[it]) * 0.5
        }

        val normals = (0 until 6).map {
            Vector3(dx[it].toDouble(), dy[it].toDouble(), dz[it].toDouble())
        }.toTypedArray()

        count = vertices.put {
            for (voxel in voxels) {
                for (i in 0 until 6) {
                    val nx = voxel.x + dx[i]
                    val ny = voxel.y + dy[i]
                    val nz = voxel.z + dz[i]

                    val output =
                        if (nx > 0 && ny > 0 && nz > 0 && nx < width && ny < height && nz < height) {
                            val neighbourIndex = nx + ny * width + nz * width * height
                            denseVoxels[neighbourIndex].red < 0
                        } else {
                            true
                        }

                    if (output) {
                        val c = ColorRGBa(voxel.color.red / 255.0, voxel.color.green / 255.0, voxel.color.blue / 255.0)
                        val n = normals[i]
                        val b = Vector3(voxel.x * 1.0, voxel.y * 1.0, voxel.z * 1.0) + n * 0.5

                        val p00 = b - ts[i] - bs[i]
                        val p01 = b - ts[i] + bs[i]
                        val p11 = b + ts[i] + bs[i]
                        val p10 = b + ts[i] - bs[i]

                        write(p11); write(n); write(c)
                        write(p01); write(n); write(c)
                        write(p00); write(n); write(c)

                        write(p00); write(n); write(c)
                        write(p10); write(n); write(c)
                        write(p11); write(n); write(c)
                    }
                }
            }
        }
    }
}