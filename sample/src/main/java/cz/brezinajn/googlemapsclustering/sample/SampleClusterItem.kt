package cz.brezinajn.googlemapsclustering.sample

import com.google.android.gms.maps.model.LatLng
import cz.brezinajn.googlemapsclustering.ClusterItem


object CITC : ClusterItem<LatLng> {
    override val LatLng.title: String?
        get() = "Title"
    override val LatLng.snippet: String?
        get() = "Snippet"
    override val LatLng.latitude: Double
        get() = latitude
    override val LatLng.longitude: Double
        get() = longitude
}