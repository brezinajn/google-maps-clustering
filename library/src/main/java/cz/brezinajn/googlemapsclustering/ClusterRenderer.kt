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
        private val googleMap: GoogleMap,
        private val iconGenerator: IconGenerator<T> = DefaultIconGenerator(context),
        private val clusterItemTC: ClusterItem<T>,
        private val onClusterItemClicked: Predicate<T>? = null,
        private val onClusterClicked: Predicate<Cluster<T>>? = null
) : OnMarkerClickListener {
    private val clusters: MutableList<Cluster<T>> = mutableListOf()
    private val markers: MutableMap<Cluster<T>, Marker> = HashMap() // mapOf?

    override fun onMarkerClick(marker: Marker): Boolean {
        val cluster = marker.tag as? Cluster<T> ?: return false
        return if (cluster.items.size > 1) {
            onClusterClicked?.invoke(cluster) ?: false
        } else {
            onClusterItemClicked?.invoke(cluster.items.first()) ?: false
        }
    }


    fun render(clusters: List<Cluster<T>>) {
        val clustersToAdd: List<Cluster<T>> = clusters.filterNot(markers::containsKey)
        val clustersToRemove: List<Cluster<T>> = markers.keys.filterNot(clusters::contains)

        this.clusters.addAll(clustersToAdd)
        this.clusters.removeAll(clustersToRemove)
        // Remove the old clusters.
        clustersToRemove.forEach { clusterToRemove ->
            val markerToRemove = markers[clusterToRemove]
            markerToRemove!!.zIndex = BACKGROUND_MARKER_Z_INDEX.toFloat()
            val parentCluster = findParentCluster(this.clusters, clusterToRemove.latitude, clusterToRemove.longitude)
            if (parentCluster != null) {
                animateMarkerToLocation(markerToRemove, LatLng(parentCluster.latitude, parentCluster.longitude), true)
            } else {
                markerToRemove.remove()
            }
            markers.remove(clusterToRemove)
        }
        // Add the new clusters.
        clustersToAdd.forEach { clusterToAdd ->
            val markerIcon = getMarkerIcon(clusterToAdd)
            val markerTitle = getMarkerTitle(clusterToAdd)
            val markerSnippet = getMarkerSnippet(clusterToAdd)
            val parentCluster: Cluster<*>? = findParentCluster(clustersToRemove, clusterToAdd.latitude,
                    clusterToAdd.longitude)
            val markerToAdd: Marker
            if (parentCluster != null) {
                markerToAdd = googleMap.addMarker(MarkerOptions()
                        .position(LatLng(parentCluster.latitude, parentCluster.longitude))
                        .icon(markerIcon)
                        .title(markerTitle)
                        .snippet(markerSnippet)
                        .zIndex(FOREGROUND_MARKER_Z_INDEX.toFloat()))
                animateMarkerToLocation(markerToAdd,
                        LatLng(clusterToAdd.latitude, clusterToAdd.longitude), false)
            } else {
                markerToAdd = googleMap.addMarker(MarkerOptions()
                        .position(LatLng(clusterToAdd.latitude, clusterToAdd.longitude))
                        .icon(markerIcon)
                        .title(markerTitle)
                        .snippet(markerSnippet)
                        .alpha(0.0f)
                        .zIndex(FOREGROUND_MARKER_Z_INDEX.toFloat()))
                animateMarkerAppearance(markerToAdd)
            }
            markerToAdd.tag = clusterToAdd
            markers[clusterToAdd] = markerToAdd
        }
    }

    private fun getMarkerIcon(cluster: Cluster<T>): BitmapDescriptor =
            if (cluster.items.size > 1) iconGenerator.getClusterIcon(cluster)
            else iconGenerator.getClusterItemIcon(cluster.items[0])

    private fun getMarkerTitle(cluster: Cluster<T>): String? = clusterItemTC.run {
        if (cluster.items.size > 1) null
        else cluster.items[0].title
    }


    private fun getMarkerSnippet(cluster: Cluster<T>): String? = clusterItemTC.run {
        if (cluster.items.size > 1) null
        else cluster.items[0].snippet

    }

    private fun findParentCluster(clusters: List<Cluster<T>>,
                                  latitude: Double, longitude: Double): Cluster<T>? =
            clusters.find { it.contains(latitude, longitude) }


    private fun animateMarkerToLocation(marker: Marker, targetLocation: LatLng,
                                        removeAfter: Boolean) {
        val objectAnimator = ObjectAnimator.ofObject(marker, "position", LatLngTypeEvaluator(), targetLocation)
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
        googleMap.setOnMarkerClickListener(this)
    }
}