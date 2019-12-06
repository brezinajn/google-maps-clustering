package cz.brezinajn.googlemapsclustering

import java.util.*

class QuadTree<T>(private val bucketSize: Int, private val quadTreePointTC: QuadTreePoint<T>) {
    private var root: QuadTreeNode<T>
    fun insert(point: T) {
        root.insert(point)
    }

    fun queryRange(north: Double, west: Double, south: Double, east: Double): List<T> {
        val points: MutableList<T> = ArrayList()
        root.queryRange(QuadTreeRect(north, west, south, east), points)
        return points
    }

    fun clear() {
        root = createRootNode(bucketSize)
    }

    private fun createRootNode(bucketSize: Int): QuadTreeNode<T> {
        return QuadTreeNode(90.0, -180.0, -90.0, 180.0, bucketSize, quadTreePointTC)
    }

    init {
        this.root = createRootNode(bucketSize)
    }
}