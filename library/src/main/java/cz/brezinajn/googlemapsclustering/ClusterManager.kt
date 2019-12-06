package cz.brezinajn.googlemapsclustering

import android.content.Context
import android.os.AsyncTask
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.model.LatLngBounds
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.pow

/**
 * Groups multiple items on a map into clusters based on the current zoom level.
 * Clustering occurs when the map becomes idle, so an instance of this class
 * must be set as a camera idle listener using [GoogleMap.setOnCameraIdleListener].
 *
 * @param <T> the type of an item to be clustered
</T> */
class ClusterManager<T>(
        context: Context,
        private val googleMap: GoogleMap,
        private val clusterItemTC: ClusterItem<T>,
        onClusterClicked: Predicate<Cluster<T>>? = null,
        onClusterItemClicked: Predicate<T>? = null,
        private val quadTree: QuadTree<T> = QuadTree(QUAD_TREE_BUCKET_CAPACITY, clusterItemTC),
        private val renderer: ClusterRenderer<T> = ClusterRenderer(
                context = context,
                googleMap = googleMap,
                clusterItemTC = clusterItemTC,
                onClusterClicked = onClusterClicked,
                onClusterItemClicked = onClusterItemClicked
        ),
        private var minClusterSize: Int = DEFAULT_MIN_CLUSTER_SIZE
) : OnCameraIdleListener {

    private val executor: Executor = Executors.newSingleThreadExecutor()
    private var quadTreeTask: AsyncTask<*, *, *>? = null
    private var clusterTask: AsyncTask<*, *, *>? = null


    /**
     * Sets items to be clustered thus replacing the old ones.
     *
     * @param clusterItems the items to be clustered
     */
    fun setItems(clusterItems: List<T>) {
        buildQuadTree(clusterItems)
    }


    override fun onCameraIdle() {
        cluster()
    }

    private fun buildQuadTree(clusterItems: List<T>) {
        quadTreeTask?.cancel(true)

        quadTreeTask = QuadTreeTask(clusterItems).executeOnExecutor(executor)
    }

    private fun cluster() {
        clusterTask?.cancel(true)
        clusterTask = ClusterTask(
                googleMap.projection.visibleRegion.latLngBounds,
                googleMap.cameraPosition.zoom
        ).executeOnExecutor(executor)
    }

    private fun getClusters(latLngBounds: LatLngBounds, zoomLevel: Float): List<Cluster<T>> {
        val clusters: MutableList<Cluster<T>> = mutableListOf()
        val tileCount = (2.0.pow(zoomLevel.toDouble()) * 2).toLong()
        val startLatitude = latLngBounds.northeast.latitude
        val endLatitude = latLngBounds.southwest.latitude
        val startLongitude = latLngBounds.southwest.longitude
        val endLongitude = latLngBounds.northeast.longitude
        val stepLatitude = 180.0 / tileCount
        val stepLongitude = 360.0 / tileCount
        if (startLongitude > endLongitude) { // Longitude +180°/-180° overlap.
// [start longitude; 180]
            getClustersInsideBounds(clusters, startLatitude, endLatitude,
                    startLongitude, 180.0, stepLatitude, stepLongitude)
            // [-180; end longitude]
            getClustersInsideBounds(clusters, startLatitude, endLatitude,
                    -180.0, endLongitude, stepLatitude, stepLongitude)
        } else {
            getClustersInsideBounds(clusters, startLatitude, endLatitude,
                    startLongitude, endLongitude, stepLatitude, stepLongitude)
        }
        return clusters
    }

    private fun getClustersInsideBounds(clusters: MutableList<Cluster<T>>,
                                        startLatitude: Double, endLatitude: Double,
                                        startLongitude: Double, endLongitude: Double,
                                        stepLatitude: Double, stepLongitude: Double) {
        clusterItemTC.run {
            val startX = ((startLongitude + 180.0) / stepLongitude).toLong()
            val startY = ((90.0 - startLatitude) / stepLatitude).toLong()
            val endX = ((endLongitude + 180.0) / stepLongitude).toLong() + 1
            val endY = ((90.0 - endLatitude) / stepLatitude).toLong() + 1
            for (tileX in startX..endX) { // todo flatten
                for (tileY in startY..endY) {
                    val north = 90.0 - tileY * stepLatitude
                    val west = tileX * stepLongitude - 180.0
                    val south = north - stepLatitude
                    val east = west + stepLongitude
                    val points = quadTree.queryRange(north, west, south, east)
                    if (points.isEmpty()) continue

                    if (points.size >= minClusterSize) {
                        val (totalLatitude, totalLongitude) = points.fold(0.0 to 0.0){acc, it ->
                            acc.first + it.latitude to acc.second + it.longitude
                        }

                        val latitude = totalLatitude / points.size
                        val longitude = totalLongitude / points.size
                        clusters.add(Cluster(latitude, longitude, points, north, west, south, east))
                    } else {
                        points.mapTo(clusters) {
                            Cluster(it.latitude, it.longitude, listOf(it), north, west, south, east)
                        }
                    }
                }
            }
        }
    }

    private inner class QuadTreeTask(private val clusterItems: List<T>) : AsyncTask<Unit?, Unit, Unit>() {

        override fun doInBackground(vararg params: Unit?) {
            quadTree.clear()
            clusterItems.forEach(quadTree::insert)
        }

        override fun onPostExecute(aUnit: Unit?) {
            cluster()
            quadTreeTask = null
        }

    }

    private inner class ClusterTask(private val latLngBounds: LatLngBounds, private val zoomLevel: Float) : AsyncTask<Unit?, Unit?, List<Cluster<T>>>() {
        override fun doInBackground(vararg params: Unit?): List<Cluster<T>> =
                getClusters(latLngBounds, zoomLevel)

        override fun onPostExecute(clusters: List<Cluster<T>>) {
            renderer.render(clusters)
            clusterTask = null
        }

    }

    companion object {
        private const val QUAD_TREE_BUCKET_CAPACITY = 4
        private const val DEFAULT_MIN_CLUSTER_SIZE = 1
    }
}