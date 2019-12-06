package cz.brezinajn.googlemapsclustering

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.SparseArray
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * The implementation of [IconGenerator] that generates icons with the default style
 * and caches them for subsequent use. To customize the style of generated icons use
 */
class DefaultIconGenerator<T>(
        private val mContext: Context,
        private val mIconStyle: IconStyle = createDefaultIconStyle(mContext)
) : IconGenerator<T> {
    //    private val mIconStyle: IconStyle
    private var mClusterItemIcon: BitmapDescriptor? = null
    private val mClusterIcons = SparseArray<BitmapDescriptor>()
    /**
     * Sets a custom icon style used to generate marker icons.
     *
     * @param iconStyle the custom icon style used to generate marker icons
     */
//    fun setIconStyle(iconStyle: IconStyle) {
//        mIconStyle = iconStyle
//    }

    override fun getClusterIcon(cluster: Cluster<T>): BitmapDescriptor {
        val clusterBucket = getClusterIconBucket(cluster)
        var clusterIcon = mClusterIcons[clusterBucket]
        if (clusterIcon == null) {
            clusterIcon = createClusterIcon(clusterBucket)
            mClusterIcons.put(clusterBucket, clusterIcon)
        }
        return clusterIcon
    }

    override fun getClusterItemIcon(clusterItem: T): BitmapDescriptor {
        if (mClusterItemIcon == null) {
            mClusterItemIcon = createClusterItemIcon()
        }
        return mClusterItemIcon!!
    }


    private fun createClusterIcon(clusterBucket: Int): BitmapDescriptor {
        @SuppressLint("InflateParams") val clusterIconView = LayoutInflater.from(mContext)
                .inflate(R.layout.map_cluster_icon, null) as TextView
        clusterIconView.background = createClusterBackground()
        clusterIconView.setTextColor(mIconStyle.clusterTextColor)
        clusterIconView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                mIconStyle.clusterTextSize.toFloat())
        clusterIconView.text = getClusterIconText(clusterBucket)
        clusterIconView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        clusterIconView.layout(0, 0, clusterIconView.measuredWidth,
                clusterIconView.measuredHeight)
        val iconBitmap = Bitmap.createBitmap(clusterIconView.measuredWidth,
                clusterIconView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(iconBitmap)
        clusterIconView.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(iconBitmap)
    }

    private fun createClusterBackground(): Drawable {
        val gradientDrawable = GradientDrawable()
        gradientDrawable.shape = GradientDrawable.OVAL
        gradientDrawable.setColor(mIconStyle.clusterBackgroundColor)
        gradientDrawable.setStroke(mIconStyle.clusterStrokeWidth,
                mIconStyle.clusterStrokeColor)
        return gradientDrawable
    }

    private fun createClusterItemIcon(): BitmapDescriptor {
        return BitmapDescriptorFactory.fromResource(mIconStyle.clusterIconResId)
    }

    private fun getClusterIconBucket(cluster: Cluster<T>): Int {
        val itemCount = cluster.items.size
        if (itemCount <= CLUSTER_ICON_BUCKETS[0]) {
            return itemCount
        }
        for (i in 0 until CLUSTER_ICON_BUCKETS.size - 1) {
            if (itemCount < CLUSTER_ICON_BUCKETS[i + 1]) {
                return CLUSTER_ICON_BUCKETS[i]
            }
        }
        return CLUSTER_ICON_BUCKETS[CLUSTER_ICON_BUCKETS.size - 1]
    }

    private fun getClusterIconText(clusterIconBucket: Int): String {
        return if (clusterIconBucket < CLUSTER_ICON_BUCKETS[0]) clusterIconBucket.toString() else "$clusterIconBucket+"
    }

    companion object {
        private val CLUSTER_ICON_BUCKETS = intArrayOf(10, 20, 50, 100, 500, 1000, 5000, 10000, 20000, 50000, 100_000, 200_000, 500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000, 20_000_000, 50_000_00, 100_000_0000)
    }

    /**
     * Creates an icon generator with the default icon style.
     */

}

private fun createDefaultIconStyle(context: Context): IconStyle {
    return IconStyle.Builder(context).build()
}