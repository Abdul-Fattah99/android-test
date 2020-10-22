package ng.riby.androidtest.others

import android.content.Context
import android.location.Location
import android.os.Build
import ng.riby.androidtest.services.Polyline
import pub.devrel.easypermissions.EasyPermissions
import java.sql.Time
import java.util.concurrent.TimeUnit
import java.util.jar.Manifest
import kotlin.math.min


//object because it will only have functions we dnt need an instance of this class

object TrackingUtility {

//use our polyline not google map polyline
    fun calculatePolylineLength(polyline: Polyline) : Float{
    var distance = 0f
    for (i in 0..polyline.size - 2){
        val pos1 = polyline[i]
        val pos2 = polyline[i + 1]

        val result = FloatArray(1)
        Location.distanceBetween(
                pos1.latitude,
                pos1.longitude,
                pos2.latitude,
                pos2.longitude,
                result
        )
        distance += result[0]
    }
    return distance
}


    //ANDROID Q NOT PRESENT
    fun hasLocationPermissions(context: Context) =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                EasyPermissions.hasPermissions(
                        context,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } else {
                EasyPermissions.hasPermissions(
                        context,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION)
            }


    fun getFormattedStopWatchTime(ms: Long, includeMillis: Boolean = false): String {
        var milliseconds = ms
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        milliseconds -= TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        milliseconds -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
        if (!includeMillis) {
            return "${if (hours < 10) "0" else ""}$hours:" +
                    "${if (minutes < 10) "0" else ""}$minutes:" +
                    "${if (seconds < 10) "0" else ""}$seconds:"
        }
        milliseconds -= TimeUnit.SECONDS.toMillis(seconds)
        milliseconds /= 10

        return "${if (hours < 10) "0" else ""}$hours:" +
                "${if (minutes < 10) "0" else ""}$minutes:" +
                "${if (seconds < 10) "0" else ""}$seconds:" +
                "${if (milliseconds < 10) "0" else ""}$milliseconds:"
    }
}