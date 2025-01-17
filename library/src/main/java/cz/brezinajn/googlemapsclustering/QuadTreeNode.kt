package cz.brezinajn.googlemapsclustering

import kotlin.collections.ArrayList

internal class QuadTreeNode<T>(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        private val bucketSize: Int,
        private val quadTreePointTC: QuadTreePoint<T>
) {
    private val bounds: QuadTreeRect = QuadTreeRect(north, west, south, east)
    private val points: MutableList<T> = ArrayList(bucketSize)
    private var northWest: QuadTreeNode<T>? = null
    private var northEast: QuadTreeNode<T>? = null
    private var southWest: QuadTreeNode<T>? = null
    private var southEast: QuadTreeNode<T>? = null

    fun insert(point: T): Boolean {
        // Ignore objects that do not belong in this quad tree.
        quadTreePointTC.run {
            if (!bounds.contains(point.latitude, point.longitude)) return false
        }

        // If there is space in this quad tree, add the object here.
        if (points.size < bucketSize) {
            points.add(point)
            return true
        }

        // Otherwise, subdivide and then add the point to whichever node will accept it.
        if (northWest == null) subdivide()

        if (northWest?.insert(point) == true) return true
        if (northEast?.insert(point) == true) return true
        if (southWest?.insert(point) == true) return true

        return southEast!!.insert(point)
        // Otherwise, the point cannot be inserted for some unknown reason (this should never happen).

    }

    fun queryRange(range: QuadTreeRect, pointsInRange: List<T>): List<T> { // Automatically abort if the range does not intersect this quad.
        quadTreePointTC.run {
            if (!bounds.intersects(range)) return emptyList()

            // Check objects at this quad level.
            val inRange = points.filter { range.contains(it.latitude, it.longitude) }

            // Terminate here, if there are no children.
            if (northWest == null) return inRange

            // Otherwise, add the points from the children.
            val nwPoints = northWest?.queryRange(range, pointsInRange).orEmpty()
            val nePoints = northEast?.queryRange(range, pointsInRange).orEmpty()
            val swPoints = southWest?.queryRange(range, pointsInRange).orEmpty()
            val sePoints = southEast?.queryRange(range, pointsInRange).orEmpty()

            return inRange + nwPoints + nePoints + swPoints + sePoints
        }
    }

    private fun subdivide() {
        val northSouthHalf = bounds.north - (bounds.north - bounds.south) / 2.0
        val eastWestHalf = bounds.east - (bounds.east - bounds.west) / 2.0
        northWest = QuadTreeNode(bounds.north, bounds.west, northSouthHalf, eastWestHalf, bucketSize, quadTreePointTC)
        northEast = QuadTreeNode(bounds.north, eastWestHalf, northSouthHalf, bounds.east, bucketSize, quadTreePointTC)
        southWest = QuadTreeNode(northSouthHalf, bounds.west, bounds.south, eastWestHalf, bucketSize, quadTreePointTC)
        southEast = QuadTreeNode(northSouthHalf, eastWestHalf, bounds.south, bounds.east, bucketSize, quadTreePointTC)
    }
}