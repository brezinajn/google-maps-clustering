package cz.brezinajn.googlemapsclustering.sample

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.util.Log
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.CameraPosition
import cz.brezinajn.googlemapsclustering.sample.R
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import cz.brezinajn.googlemapsclustering.ClusterManager


class MapsActivity : FragmentActivity(), OnMapReadyCallback {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        if (savedInstanceState == null) {
            setupMapFragment()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        googleMap.setOnMapLoadedCallback { googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(NETHERLANDS, 0)) }
        val clusterManager = ClusterManager(
                        context = this,
                        googleMap = googleMap,
                        clusterItemTC = CITC,
                        onClusterItemClicked = {
                            Log.d(TAG, "onClusterItemClick")
                            true
                        },
                        onClusterClicked = {
                            if(googleMap.cameraPosition.zoom >= ZOOM_LEVEL_DETAIL){
//                                onClusterClick(cluster.items)
                            }else {
                                it.items.toCameraUpdate(CLUSTER_ZOOM_PADDING, ZOOM_LEVEL_DETAIL)
                                        ?.let(googleMap::animateCamera)
                            }
                             true
                        }
                )
        googleMap.setOnCameraIdleListener(clusterManager)

         RandomLocationGenerator.getSequence(NETHERLANDS)
                 .take(1_000_000)
                 .toList()
                 .let(clusterManager::setItems)
    }

    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.retainInstance = true
        mapFragment.getMapAsync(this)
    }

    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        private val NETHERLANDS = LatLngBounds(LatLng(50.77083, 3.57361), LatLng(53.35917, 7.10833))
    }
}
private const val CLUSTER_ZOOM_PADDING = 100
private const val ZOOM_LEVEL_DETAIL = 16f

private fun Collection<LatLng>.toCameraUpdate(
        padding: Int = 0,
        singleElementZoom: Float
): CameraUpdate? =
        when (size) {
            0 -> null
            1 -> first().getCameraUpdate(singleElementZoom)
            else -> fold(LatLngBounds.builder()) { acc, item ->
                acc.include(item)
                acc
            }.build()
                    .let { CameraUpdateFactory.newLatLngBounds(it, padding) }
        }

private fun LatLng.getCameraUpdate(zoomLevel: Float): CameraUpdate =
         CameraPosition.Builder()
                .tilt(0f)
                .bearing(0f)
                .target(this)
                .zoom(zoomLevel)
                .build()
                .let(CameraUpdateFactory::newCameraPosition)