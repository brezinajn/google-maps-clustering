package cz.brezinajn.googlemapsclustering

/**
 * An object representing a single cluster item (marker) on the map.
 */
interface ClusterItem<T> : QuadTreePoint<T> {
    /**
     * The title of the item.
     *
     * @return the title of the item
     */
    val T.title: String?

    /**
     * The snippet of the item.
     *
     * @return the snippet of the item
     */
    val T.snippet: String?
}