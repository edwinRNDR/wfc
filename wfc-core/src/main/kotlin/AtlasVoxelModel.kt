package org.openrndr.wfc

import org.openrndr.wfc.vox.Voxel
import org.openrndr.wfc.vox.denseVoxels
import org.openrndr.wfc.vox.readVox
import java.io.File
import java.util.*

class AtlasVoxelModel(
    val state: State3D,
    val voxelSize: Int,
    val tiles: List<AtlasTile>
) {
    fun setState(x: Int, y: Int, z: Int, observation: Int): ObservationResult {
        val cellIndex = state.xyzToIndex(x, y, z)
        if (state.wave[cellIndex][observation]) {
            state.observed[cellIndex] = observation
            for (waveIndex in 0 until state.waveCount) {
                if (state.wave[cellIndex][waveIndex] != (waveIndex == observation)) {
                    val res = state.ban(cellIndex, waveIndex)
                    if (res == ObservationResult.CONFLICT) {
                        return ObservationResult.CONFLICT
                    }
                }
            }
        }
        return ObservationResult.CONTINUE
    }

    fun setFloorConstraints() {
        // find all tiles that do not have down-going connections
        val floorStates =
            tiles.mapIndexed { index, it -> Pair(index, it) }.filter { (it.second.depths - 0).isEmpty() }
                .map { it.first }
        val floatStates =
            tiles.mapIndexed { index, it -> Pair(index, it) }.filter { 0 !in it.second.depths }
                .map { it.first }

        println("floor states: $floorStates")
        println("float states: $floatStates")

        for (y in 0 until state.height) {
            for (x in 0 until state.width) {
                for (w in floatStates) {
                    state.ban(state.xyzToIndex(x, y, 0), w)
                }
            }
        }

        for (z in 1 until state.depth) {
            for (y in 0 until state.height) {
                for (x in 0 until state.width) {
                    for (w in floorStates) {
                        state.ban(state.xyzToIndex(x, y, z), w)
                    }
                }
            }
        }
    }

    fun setCeilingConstraints() {
        val fillStates =
            tiles.mapIndexed { index, it -> Pair(index, it) }
                .filter { it.second.voxels.any { it.color != Color(-1, -1, -1) } }
                .map { it.first }


        for (y in 0 until state.height) {
            for (x in 0 until state.width) {
                for (w in fillStates) {
                    state.ban(state.xyzToIndex(x, y, state.depth - 1), w)
                }
            }
        }

    }

    fun setWallConstraints() {
        val fillStates =
            tiles.mapIndexed { index, it -> Pair(index, it) }
                .filter { it.second.voxels.any { it.color != Color(-1, -1, -1) } }
                .map { it.first }

        for (x in 0 until state.width) {
            for (z in 0 until state.depth) {
                for (w in fillStates) {
                    state.ban(state.xyzToIndex(x, 0, z), w)
                    state.ban(state.xyzToIndex(x, state.height - 1, z), w)
                    state.ban(state.xyzToIndex(0, x, z), w)
                    state.ban(state.xyzToIndex(state.width - 1, x, z), w)
                }
            }
        }

    }

    fun decode(): Array<Voxel> {
        val voxels = mutableListOf<Voxel>()
        for (z in 0 until state.depth) {
            for (y in 0 until state.height) {
                for (x in 0 until state.width) {
                    voxels.addAll(decode(x, y, z))
                }
            }
        }
        return voxels.toTypedArray()
    }

    fun decode(x: Int, y: Int, z: Int): Array<Voxel> =
        if (state.observable) {
            decodeObservation(x, y, z)
        } else {
            decodeSuperposition(x, y, z)
        }

    fun decodeObservation(x: Int, y: Int, z: Int): Array<Voxel> =
        tiles[state.observed[state.xyzToIndex(x, y, z)]].voxels.map {
            Voxel(x * voxelSize + it.x, y * voxelSize + it.y, z * voxelSize + it.z, it.color)
        }.toTypedArray()

    fun decodeSuperposition(x: Int, y: Int, z: Int): Array<Voxel> {
        val counts = Array(voxelSize * voxelSize * voxelSize) { 0 }
        var contributors = 0.0
        for (waveIndex in 0 until state.waveCount) {
            if (state.wave[state.xyzToIndex(x, y, z)][waveIndex]) {
                contributors++
                for (v in tiles[waveIndex].voxels) {
                    counts[v.x + v.y * voxelSize + v.z * voxelSize * voxelSize] += 1
                }
            }
        }

        val voxels = mutableListOf<Voxel>()

        if (contributors > 1) {
            for (w in 0 until voxelSize) {
                for (v in 0 until voxelSize) {
                    for (u in 0 until voxelSize) {
                        if (counts[u + v * voxelSize + w * voxelSize * voxelSize] > contributors / 2) {
                            voxels.add(
                                Voxel(
                                    x * voxelSize + u, y * voxelSize + v,
                                    z * voxelSize + w, Color(127, 127, 127)
                                )
                            )
                        }
                    }
                }
            }
            return voxels.toTypedArray()
        } else if (contributors == 1.0) {
            for (w in 0 until voxelSize) {
                for (v in 0 until voxelSize) {
                    for (u in 0 until voxelSize) {
                        if (counts[u + v * voxelSize + w * voxelSize * voxelSize] > contributors / 2) {
                            voxels.add(
                                Voxel(
                                    x * voxelSize + u, y * voxelSize + v,
                                    z * voxelSize + w, Color(255, 0xc0, 0xcb)
                                )
                            )
                        }
                    }
                }
            }
            return voxels.toTypedArray()

        } else return emptyArray()

    }
}

class AtlasTile(val id: Int, val name: String, val voxels: Array<Voxel>, val weight: Double) {
    val connections: Array<MutableSet<AtlasTile>> = Array(6) { mutableSetOf<AtlasTile>() }
    val depths = mutableSetOf<Int>()
}

private data class AtlasPattern(val pattern: Array<Color>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as AtlasPattern
        return Arrays.equals(pattern, other.pattern)
    }

    override fun hashCode(): Int = Arrays.hashCode(pattern)
}


class AtlasVoxelModelBuilder {
    var seed = 0
    var width = 16
    var height = 16
    var depth = 16

    var linkAtlasses = false
    private val tiles = mutableMapOf<AtlasPattern, AtlasTile>()
    private val tileIndex = mutableListOf<AtlasTile>()
    var periodic = false
    var tileSize = 6

    val patternsInAtlas = mutableListOf<Set<Int>>()

    fun tiles(tileDirectory: File, weight: Double) {
        assert(tileDirectory.isDirectory)


        tileDirectory.listFiles { f, n ->

            n.endsWith("vox") && "no-pattern" !in n
        }.forEach {
            val voxFile = readVox(it)

            val tx = voxFile.width / tileSize
            val ty = voxFile.height / tileSize
            val tz = voxFile.depth / tileSize


            val denseVox = denseVoxels(voxFile)

            for (z in 0 until tz) {
                for (y in 0 until ty) {
                    for (x in 0 until tx) {

                        val subVox = Array(tileSize * tileSize * tileSize) {
                            val w = it / (tileSize * tileSize)
                            val v = (it / tileSize) % tileSize
                            val u = it % tileSize
                            denseVox[(x * tileSize + u) + (y * tileSize + v) * voxFile.width + (z * tileSize + w) * voxFile.width * voxFile.height]
                        }

                        val sparseVox = Array(tileSize * tileSize * tileSize) {
                            val w = it / (tileSize * tileSize)
                            val v = (it / tileSize) % tileSize
                            val u = it % tileSize
                            Voxel(u, v, w, subVox[it])
                        }.filter { it.color.red > 0 }.toTypedArray()

                        val key = AtlasPattern(subVox)
                        if (!tiles.containsKey(key)) {
                            println("${tileIndex.size} -- ${it.name} $x $y $z")
                            val tile = AtlasTile(tiles.size, "${it.name} $x $y $z", sparseVox, weight)
                            tiles.put(key, tile)
                            tileIndex.add(tile)
                        }
                    }
                }
            }
        }
        println("total tiles: $tileIndex")
    }

    fun atlas(atlas: File) {
        val voxFile = readVox(atlas)
        val denseVox = denseVoxels(voxFile)

        val tx = voxFile.width / tileSize
        val ty = voxFile.height / tileSize
        val tz = voxFile.depth / tileSize

        fun xyzToIndex(x: Int, y: Int, z: Int): Int {
            return x + y * voxFile.width + z * voxFile.width * voxFile.height
        }

        val patterns = Array(tx * ty * tz) { -1 }

        for (w in 0 until tz) {
            for (v in 0 until ty) {
                for (u in 0 until tx) {
                    val pattern = Array(tileSize * tileSize * tileSize) { Color(-1, -1, -1) }
                    for (z in 0 until tileSize) {
                        for (y in 0 until tileSize) {
                            for (x in 0 until tileSize) {
                                pattern[x + y * tileSize + z * tileSize * tileSize] =
                                        denseVox[xyzToIndex(x + u * tileSize, y + v * tileSize, z + w * tileSize)]
                            }
                        }
                    }
                    val tile = tiles[AtlasPattern(pattern)]
                    if (tile != null) {
                        //println("tile: ${tile.id}")
                        patterns[u + v * tx + w * tx * ty] = tile.id
                        tile.depths.add(w)
                    } else {
                        //println("unrecognized tile at ${u * tileSize} ${v * tileSize} ${w * tileSize}")
                    }
                }
            }
        }

        patternsInAtlas.add(patterns.toSet())

        for (w in 0 until tz) {
            for (v in 0 until ty) {
                for (u in 0 until tx) {
                    val source = patterns[u + v * tx + w * tx * ty]
                    if (source != -1) {
                        for (d in 0 until 6) {
                            val nx = u + DX3[d]
                            val ny = v + DY3[d]
                            val nz = w + DZ3[d]
                            if (nx >= 0 && ny >= 0 && nz >= 0 && nx < tx && ny < ty && nz < tz) {
                                val target = patterns[nx + ny * tx + nz * tx * ty]
                                if (target != -1) {
                                    tileIndex[source].connections[d].add(tileIndex[target])
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun build(): AtlasVoxelModel {

        val voidTile = tileIndex.find { it.voxels.isEmpty() }!!

        val waveCount = tiles.size
        val tempPropagator = Array(6) {
            Array(waveCount) {
                BooleanArray(waveCount) { false }
            }
        }

        if (linkAtlasses) {
            for (patterns in patternsInAtlas) {
                for (w in 0 until tileIndex.size) {
                    if (true || (w != -1 && w !in patterns)) {
                        for (p in patterns) {
                            if (p != -1) {
                                for (direction in 0 until 4) {
                                    if (tileIndex[p].connections[direction].contains(voidTile)) {
                                        if (tileIndex[w].connections[opposite3[direction]].contains(voidTile)) {
                                            tileIndex[p].connections[direction].add(tileIndex[w])
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        for (outer in 0 until tileIndex.size) {
            for (direction in 0 until 6) {
                for (inner in tileIndex[outer].connections[direction]) {
                    tempPropagator[direction][outer][inner.id] = true
                }
            }
        }

        val propagator = Array(6) { d ->
            Array(waveCount) { t1 ->
                val sp = mutableListOf<Int>()
                val tp = tempPropagator[d][t1]
                for (t2 in 0 until waveCount) {
                    if (tp[t2]) {
                        sp.add(t2)
                    }
                }
                if (sp.size == 0) {
                    println("warning: propagator for $t1/$d -- ${tileIndex[t1].name} is empty")
                }

                sp.toIntArray()
            }
        }

        // -- verify if propagator is symmetric
        for (dir in 0 until 6) {
            for (w in 0 until tileIndex.size) {
                for (p in propagator[dir][w]) {
                    if (w !in propagator[opposite3[dir]][p]) {
                        println("propagator is not symmetric $dir-${opposite3[dir]}, $w is not in $p")
                    }
                }
            }
        }

        val onBoundary =
            { x: Int, y: Int, z: Int -> !periodic && (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) }

        val waveWeights = DoubleArray(tiles.size) { tileIndex[it].weight }
        val tw = waveWeights.sum()
        waveWeights.forEachIndexed { index, v -> waveWeights[index] = v / tw }

        val state = State3D(seed, width, height, depth, waveWeights, propagator, onBoundary, null, null)
        return AtlasVoxelModel(state, tileSize, tileIndex)
    }
}

fun atlasVoxelModel(init: AtlasVoxelModelBuilder.() -> Unit): AtlasVoxelModel {
    val stmb = AtlasVoxelModelBuilder()
    stmb.init()
    return stmb.build()
}