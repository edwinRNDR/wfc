package org.openrndr.wfc.demo.voxelrenderer

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extras.meshgenerators.boxMesh
import org.openrndr.math.Vector3
import org.openrndr.wfc.vox.Voxel

class VoxelRenderer {
    val cube = boxMesh()
    val offsets = vertexBuffer(vertexFormat { attribute("offset", VertexElementType.VECTOR3_FLOAT32)
    attribute("color", VertexElementType.VECTOR4_FLOAT32)}, 16384*20)

    var count = 0

    fun render(drawer: Drawer, voxels: Array<Voxel>) {
        render(drawer, voxels.map { Vector3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) },
            voxels.map { ColorRGBa(it.color.red/255.0,
            it.color.green/255.0, it.color.blue/255.0, 1.0)})
    }

    fun update(voxels: Array<Voxel>) {
        update(voxels.map { Vector3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) },
            voxels.map { ColorRGBa(it.color.red/255.0,
                it.color.green/255.0, it.color.blue/255.0, 1.0)})
    }
    fun update(positions: List<Vector3>, colors:List<ColorRGBa>) {
        offsets.put {
            //            positions.forEach {
//                write(it)
//            }
            for (i in 0 until positions.size) {
                write(positions[i])
                write(colors[i])
            }
        }

        count = positions.size
    }

    fun render(drawer: Drawer, positions: List<Vector3>, colors:List<ColorRGBa>) {
        offsets.put {
//            positions.forEach {
//                write(it)
//            }
            for (i in 0 until positions.size) {
                write(positions[i])
                write(colors[i])
            }
        }
        drawer.isolated {
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
            drawer.depthWrite = true
            drawer.shadeStyle = shadeStyle {
                vertexTransform = """
                    x_position += i_offset;
                """.trimIndent()

                fragmentTransform = """
                    float t = normalize(va_normal).z * 0.5 + 0.5;
                    x_fill.rgb = (mix(vec3(0.1,0.1,0.1), vec3(1.0), t) * vi_color.rgb) * min(1.0, (100.0 + v_viewPosition.z)/100.0);

                """.trimIndent()
            }

            drawer.vertexBufferInstances(listOf(cube), listOf(offsets), DrawPrimitive.TRIANGLES, positions.size, 0)
        }
    }
}