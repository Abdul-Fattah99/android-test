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
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.internal.operators.observable.ObservableElementAt
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
import java.lang.StringBuilder
import javax.inject.Inject


typealias Polyline =  MutableList<LatLng>
typealias Polylines = MutableList<Polyline>

@AndroidEntryPoint
class TrackingService : LifecycleService() {

    //boolean to tell if its the first run
    var isFirstRun = true

    var serviceKilled = false

    //to be able to request location update we need fusedLocationProviderClient
    @Inject
    lateinit var  fusedLocationProviderClient: FusedLocationProviderClient

    @Inject
    lateinit var baseNotificationBuilder: NotificationCompat.Builder

    lateinit var currentNotificationBuilder: NotificationCompat.Builder

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
        currentNotificationBuilder = baseNotificationBuilder
        postInitialValues()
        fusedLocationProviderClient =  FusedLocationProviderClient(this)
        isTracking.observe(this, Observer {
            updateLocationTracking(it)
            updateNotificationTrackingState(it)
        })
    }


    //functionality to stop or kill service inside service
    private fun killService(){
        serviceKilled = true
        isFirstRun = true
        pauseService()
        postInitialValues()
        stopForeground(true)
        stopSelf()
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
                    killService()
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


    private fun updateNotificationTrackingState(isTracking: Boolean){
        val notificationActionText = if(isTracking) "Pause" else "Resume"
        val pendingIntent = if(isTracking){
            val pauseIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            PendingIntent.getService(this, 1, pauseIntent, FLAG_UPDATE_CURRENT)
        } else{
            val resumeIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
        }
            PendingIntent.getService(this, 2, resumeIntent, FLAG_UPDATE_CURRENT)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

//        currentNotificationBuilder.javaClass.getDeclaredField("mAction").apply {
//            isAccessible = true
//            set(currentNotificationBuilder, ArrayList<NotificationCompat.Action>())
//
//        }


        if(!serviceKilled){
            currentNotificationBuilder = baseNotificationBuilder
                    .addAction(R.drawable.ic_pause_black_24dp, notificationActionText, pendingIntent)
            notificationManager.notify(NOTIFICATION_ID, currentNotificationBuilder.build())

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


        //start foreground service
        startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())

        timeRunInSeconds.observe(this, Observer {
            if(!serviceKilled){
                val notification = currentNotificationBuilder
                        .setContentText(TrackingUtility.getFormattedStopWatchTime(it * 1000L))
                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }

        })
    }


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