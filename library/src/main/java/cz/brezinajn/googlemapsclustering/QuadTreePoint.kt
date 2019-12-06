package cz.brezinajn.googlemapsclustering

interface QuadTreePoint<T> {
    val T.latitude: Double
    val T.longitude: Double
}