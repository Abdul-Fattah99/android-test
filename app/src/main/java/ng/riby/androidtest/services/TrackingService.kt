package ng.riby.androidtest.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.graphics.DiscretePathEffect
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ng.riby.androidtest.R
import ng.riby.androidtest.others.Constants.ACTION_PAUSE_SERVICE
import ng.riby.androidtest.others.Constants.ACTION_SHOW_TRACKING_FRAGMENT
import ng.riby.androidtest.others.Constants.ACTION_START_OR_RESUME_SERVICE
import ng.riby.androidtest.others.Constants.ACTION_STOP_SERVICE
import ng.riby.androidtest.others.Constants.FASTEST_LOCATION_INTERVAL
import ng.riby.androidtest.others.Constants.LOCATION_UPDATE_INTERVAL
import ng.riby.androidtest.others.Constants.NOTIFICATION_CHANNEL_ID
import ng.riby.androidtest.others.Constants.NOTIFICATION_CHANNEL_NAME
import ng.riby.androidtest.others.Constants.NOTIFICATION_ID
import ng.riby.androidtest.others.Constants.TIMER_UPDATE_INTERVAL
import ng.riby.androidtest.others.TrackingUtility
import ng.riby.androidtest.ui.MainActivity
import timber.log.Timber


typealias Polyline =  MutableList<LatLng>
typealias Polylines = MutableList<Polyline>
class TrackingService : LifecycleService() {

    //boolean to tell if its the first run
    var isFirstRun = true

    //to be able to request location update we need fusedLocationProviderClient
    lateinit var  fusedLocationProviderClient: FusedLocationProviderClient


    //current time run i seconds
    private val timeRunInSeconds =  MutableLiveData<Long>()

    //background tracking user location
    companion object{
        //current time run in milliseconds
        //because we want to observe onChanges from the outside from tracking fragment on the live data
        val timeInMilliSeconds = MutableLiveData<Long>()

        val isTracking = MutableLiveData<Boolean>()
        //holds all track location from specific movement
        val pathPoints = MutableLiveData<Polylines>()
    }

    private fun postInitialValues(){
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        timeRunInSeconds.postValue(0L)
        timeInMilliSeconds.postValue(0L)
    }

    override fun onCreate() {
        super.onCreate()
        postInitialValues()
        fusedLocationProviderClient =  FusedLocationProviderClient(this)
        isTracking.observe(this, Observer {
            updateLocationTracking(it)
        })
    }


    //called whenever we send a command to our service
    //action to start or resume, stop, pause
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when(it.action){
                ACTION_START_OR_RESUME_SERVICE ->{
                    if(isFirstRun){
                        startForegroundService()
                        isFirstRun = false
                    }else{
                        Timber.d("Resuming service")
                        startTimer()
                    }
                }
                ACTION_PAUSE_SERVICE ->{
                    Timber.d("paused service")
                    pauseService()
                }
                ACTION_STOP_SERVICE ->{
                    Timber.d("stopped service")
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    //fun that starts timer.will track actual time and trigger our observers so observers can observe on those time changes
    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeMoved = 0L
    private var timeStarted = 0L
    private var lastSecondTimeStamp = 0L

    private fun startTimer(){
        //adds empty polylines to an empty list of latlng coordinates at the end of polyliines list
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!){
                lapTime = System.currentTimeMillis() - timeStarted

                //post the new lapTime
                timeInMilliSeconds.postValue(timeMoved + lapTime)

                if (timeInMilliSeconds.value!! > lastSecondTimeStamp + 1000L){
                    timeRunInSeconds.postValue(timeRunInSeconds.value!! + 1)
                    lastSecondTimeStamp += 1000L
                }
                delay(TIMER_UPDATE_INTERVAL)

            }
            timeMoved += lapTime
        }
    }

    //pause service
    private fun pauseService(){
        isTracking.postValue(false)
        isTimerEnabled = false
    }


    //to get actual location result
    val locationCallback = object :LocationCallback(){
        override fun onLocationResult(result: LocationResult?) {
            super.onLocationResult(result)
            if(isTracking.value!!){
                result?.locations?.let {  locations->
                    for (location in locations){
                        addPathPoint(location)
                        Timber.d("New location : ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }

    //updates location tracking
    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean){
        if(isTracking){
            if(TrackingUtility.hasLocationPermissions(this)){
                val request = LocationRequest().apply {
                     interval  = LOCATION_UPDATE_INTERVAL
                    fastestInterval = FASTEST_LOCATION_INTERVAL
                    priority = PRIORITY_HIGH_ACCURACY
                }
                fusedLocationProviderClient.requestLocationUpdates(
                        request,
                        locationCallback,
                        Looper.getMainLooper()
                )
            }
        }else{
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }

    }


    //adds coordinates to the last polyline of our polyline list
    private fun addPathPoint(location: Location?){
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)

            pathPoints.value?.apply {
                last().add(pos)
                pathPoints.postValue(this)
            }
        }
    }

    //adds empty polyLines to an empty list of latLng coordinates at the end of polyliines list

    private fun addEmptyPolyline() = pathPoints.value?.apply {
        add(mutableListOf())
        pathPoints.postValue(this)
    }?: pathPoints.postValue(mutableListOf(mutableListOf()))

    //called when we start service for the first time and not when we resume it
    private fun startForegroundService(){
        startTimer()
        isTracking.postValue(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
        as NotificationManager

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            createNotificationChannel(notificationManager)
        }

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setAutoCancel(false)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_directions_run_black_24dp)
                .setContentTitle("Tracking App")
                .setContentText("00:00:00")
                .setContentIntent(getMainActivityPendingIntent())

        //start foreground service
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }



    //returns pending intent
    private fun getMainActivityPendingIntent()= PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).also {
                it.action = ACTION_SHOW_TRACKING_FRAGMENT
            },
            FLAG_UPDATE_CURRENT
    )

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager){
        val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}