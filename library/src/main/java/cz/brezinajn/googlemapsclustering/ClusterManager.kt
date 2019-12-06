package cz.brezinajn.googlemapsclustering

import android.content.Context
import android.os.AsyncTask
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.model.LatLngBounds
import java.util.*
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
        private val mGoogleMap: GoogleMap,
        private val clusterItemTC: ClusterItem<T>,
        onClusterClicked: Predicate<Cluster<T>>? = null,
        onClusterItemClicked: Predicate<T>? = null,
        private val mQuadTree: QuadTree<T> = QuadTree(QUAD_TREE_BUCKET_CAPACITY, clusterItemTC),
        private val mRenderer: ClusterRenderer<T> = ClusterRenderer(
                context = context,
                mGoogleMap = mGoogleMap,
                clusterItemTC = clusterItemTC,
                onClusterClicked = onClusterClicked,
                onClusterItemClicked = onClusterItemClicked
        ),
        private var mMinClusterSize: Int = DEFAULT_MIN_CLUSTER_SIZE
) : OnCameraIdleListener {

    private val mExecutor: Executor = Executors.newSingleThreadExecutor()
    private var mQuadTreeTask: AsyncTask<*, *, *>? = null
    private var mClusterTask: AsyncTask<*, *, *>? = null


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
        if (mQuadTreeTask != null) {
            mQuadTreeTask!!.cancel(true)
        }
        mQuadTreeTask = QuadTreeTask(clusterItems).executeOnExecutor(mExecutor)
    }

    private fun cluster() {
        mClusterTask?.cancel(true)
        mClusterTask = ClusterTask(mGoogleMap.projection.visibleRegion.latLngBounds,
                mGoogleMap.cameraPosition.zoom).executeOnExecutor(mExecutor)
    }

    private fun getClusters(latLngBounds: LatLngBounds, zoomLevel: Float): List<Cluster<T>> {
        val clusters: MutableList<Cluster<T>> = ArrayList()
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
            for (tileX in startX..endX) {
                for (tileY in startY..endY) {
                    val north = 90.0 - tileY * stepLatitude
                    val west = tileX * stepLongitude - 180.0
                    val south = north - stepLatitude
                    val east = west + stepLongitude
                    val points = mQuadTree.queryRange(north, west, south, east)
                    if (points.isEmpty()) {
                        continue
                    }
                    if (points.size >= mMinClusterSize) {
                        var totalLatitude = 0.0
                        var totalLongitude = 0.0
                        for (point in points) {
                            totalLatitude += point.latitude
                            totalLongitude += point.longitude
                        }
                        val latitude = totalLatitude / points.size
                        val longitude = totalLongitude / points.size
                        clusters.add(Cluster(latitude, longitude,
                                points, north, west, south, east))
                    } else {
                        for (point in points) {
                            clusters.add(Cluster(point.latitude, point.longitude, listOf(point), north, west, south, east))
                        }
                    }
                }
            }
        }
    }

    private inner class QuadTreeTask(private val mClusterItems: List<T>) : AsyncTask<Unit?, Unit, Unit>() {

        override fun doInBackground(vararg params: Unit?) {
            mQuadTree.clear()
            mClusterItems.forEach(mQuadTree::insert)
        }

        override fun onPostExecute(aUnit: Unit?) {
            cluster()
            mQuadTreeTask = null
        }

    }

    private inner class ClusterTask(private val mLatLngBounds: LatLngBounds, private val mZoomLevel: Float) : AsyncTask<Void?, Void?, List<Cluster<T>>>() {
        protected override fun doInBackground(vararg params: Void?): List<Cluster<T>> {
            return getClusters(mLatLngBounds, mZoomLevel)
        }

        override fun onPostExecute(clusters: List<Cluster<T>>) {
            mRenderer.render(clusters)
            mClusterTask = null
        }

    }

    companion object {
        private const val QUAD_TREE_BUCKET_CAPACITY = 4
        private const val DEFAULT_MIN_CLUSTER_SIZE = 1
    }
}