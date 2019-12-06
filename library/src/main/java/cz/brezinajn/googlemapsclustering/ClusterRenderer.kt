package cz.brezinajn.googlemapsclustering

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.content.Context
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.util.*

typealias Predicate<T> = (T) -> Boolean

class ClusterRenderer<T>(
        context: Context,
        private val mGoogleMap: GoogleMap,
        private val mIconGenerator: IconGenerator<T> = DefaultIconGenerator(context),
        private val clusterItemTC: ClusterItem<T>,
        private val onClusterItemClicked: Predicate<T>? = null,
        private val onClusterClicked: Predicate<Cluster<T>>? = null
) : OnMarkerClickListener {
    private val mClusters: MutableList<Cluster<T>> = ArrayList()
    private val mMarkers: MutableMap<Cluster<T>, Marker> = HashMap()

    //    private var mCallbacks: ClusterManager.Callbacks<T>? = null
    override fun onMarkerClick(marker: Marker): Boolean {
        val markerTag = marker.tag
        if (markerTag is Cluster<*>) {
            val cluster = marker.tag as Cluster<T>
            val clusterItems = cluster.items
            return if (clusterItems.size > 1) {
                onClusterClicked?.invoke(cluster) ?: false
            } else {
                onClusterItemClicked?.invoke(clusterItems.first()) ?: false
            }
        }
        return false
    }

//    fun setCallbacks(listener: ClusterManager.Callbacks<T>?) {
//        mCallbacks = listener
//    }

//    fun setIconGenerator(iconGenerator: IconGenerator<T>) {
//        mIconGenerator = iconGenerator
//    }

    fun render(clusters: List<Cluster<T>>) {
        val clustersToAdd: MutableList<Cluster<T>> = ArrayList()
        val clustersToRemove: MutableList<Cluster<T>> = ArrayList()
        for (cluster in clusters) {
            if (!mMarkers.containsKey(cluster)) {
                clustersToAdd.add(cluster)
            }
        }
        for (cluster in mMarkers.keys) {
            if (!clusters.contains(cluster)) {
                clustersToRemove.add(cluster)
            }
        }
        mClusters.addAll(clustersToAdd)
        mClusters.removeAll(clustersToRemove)
        // Remove the old clusters.
        for (clusterToRemove in clustersToRemove) {
            val markerToRemove = mMarkers[clusterToRemove]
            markerToRemove!!.zIndex = BACKGROUND_MARKER_Z_INDEX.toFloat()
            val parentCluster = findParentCluster(mClusters, clusterToRemove.latitude,
                    clusterToRemove.longitude)
            if (parentCluster != null) {
                animateMarkerToLocation(markerToRemove, LatLng(parentCluster.latitude,
                        parentCluster.longitude), true)
            } else {
                markerToRemove.remove()
            }
            mMarkers.remove(clusterToRemove)
        }
        // Add the new clusters.
        for (clusterToAdd in clustersToAdd) {
            var markerToAdd: Marker
            val markerIcon = getMarkerIcon(clusterToAdd)
            val markerTitle = getMarkerTitle(clusterToAdd)
            val markerSnippet = getMarkerSnippet(clusterToAdd)
            val parentCluster: Cluster<*>? = findParentCluster(clustersToRemove, clusterToAdd.latitude,
                    clusterToAdd.longitude)
            if (parentCluster != null) {
                markerToAdd = mGoogleMap.addMarker(MarkerOptions()
                        .position(LatLng(parentCluster.latitude, parentCluster.longitude))
                        .icon(markerIcon)
                        .title(markerTitle)
                        .snippet(markerSnippet)
                        .zIndex(FOREGROUND_MARKER_Z_INDEX.toFloat()))
                animateMarkerToLocation(markerToAdd,
                        LatLng(clusterToAdd.latitude, clusterToAdd.longitude), false)
            } else {
                markerToAdd = mGoogleMap.addMarker(MarkerOptions()
                        .position(LatLng(clusterToAdd.latitude, clusterToAdd.longitude))
                        .icon(markerIcon)
                        .title(markerTitle)
                        .snippet(markerSnippet)
                        .alpha(0.0f)
                        .zIndex(FOREGROUND_MARKER_Z_INDEX.toFloat()))
                animateMarkerAppearance(markerToAdd)
            }
            markerToAdd.tag = clusterToAdd
            mMarkers[clusterToAdd] = markerToAdd
        }
    }

    private fun getMarkerIcon(cluster: Cluster<T>): BitmapDescriptor {
        val clusterIcon: BitmapDescriptor
        val clusterItems = cluster.items
        clusterIcon = if (clusterItems.size > 1) {
            mIconGenerator.getClusterIcon(cluster)
        } else {
            mIconGenerator.getClusterItemIcon(clusterItems[0])
        }
        return clusterIcon
    }

    private fun getMarkerTitle(cluster: Cluster<T>): String? {
        clusterItemTC.run {
            val clusterItems = cluster.items
            return if (clusterItems.size > 1) {
                null
            } else {
                clusterItems[0].title
            }
        }
    }

    private fun getMarkerSnippet(cluster: Cluster<T>): String? {
        clusterItemTC.run {
            val clusterItems = cluster.items
            return if (clusterItems.size > 1) {
                null
            } else {
                clusterItems[0].snippet
            }
        }
    }

    private fun findParentCluster(clusters: List<Cluster<T>>,
                                  latitude: Double, longitude: Double): Cluster<T>? {
        for (cluster in clusters) {
            if (cluster.contains(latitude, longitude)) {
                return cluster
            }
        }
        return null
    }

    private fun animateMarkerToLocation(marker: Marker, targetLocation: LatLng,
                                        removeAfter: Boolean) {
        val objectAnimator = ObjectAnimator.ofObject(marker, "position",
                LatLngTypeEvaluator(), targetLocation)
        objectAnimator.interpolator = FastOutSlowInInterpolator()
        objectAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (removeAfter) {
                    marker.remove()
                }
            }
        })
        objectAnimator.start()
    }

    private fun animateMarkerAppearance(marker: Marker) {
        ObjectAnimator.ofFloat(marker, "alpha", 1.0f).start()
    }

    private class LatLngTypeEvaluator : TypeEvaluator<LatLng> {
        override fun evaluate(fraction: Float, startValue: LatLng, endValue: LatLng): LatLng {
            val latitude = (endValue.latitude - startValue.latitude) * fraction + startValue.latitude
            val longitude = (endValue.longitude - startValue.longitude) * fraction + startValue.longitude
            return LatLng(latitude, longitude)
        }
    }

    companion object {
        private const val BACKGROUND_MARKER_Z_INDEX = 0
        private const val FOREGROUND_MARKER_Z_INDEX = 1
    }

    init {
        mGoogleMap.setOnMarkerClickListener(this)
    }
}