package cz.brezinajn.googlemapsclustering.sample

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import java.util.*


internal object RandomLocationGenerator {
    private val random = Random()

    fun getSequence(bounds: LatLngBounds): Sequence<LatLng> {
        val minLatitude = bounds.southwest.latitude
        val maxLatitude = bounds.northeast.latitude
        val minLongitude = bounds.southwest.longitude
        val maxLongitude = bounds.northeast.longitude
        return generateSequence {
            LatLng(minLatitude + (maxLatitude - minLatitude) * random.nextDouble(),
                    minLongitude + (maxLongitude - minLongitude) * random.nextDouble())
        }
    }
}