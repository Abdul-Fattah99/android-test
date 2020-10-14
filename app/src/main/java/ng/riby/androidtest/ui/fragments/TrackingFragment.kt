package ng.riby.androidtest.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.PolylineOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_tracking.*
import ng.riby.androidtest.R
import ng.riby.androidtest.others.Constants.ACTION_PAUSE_SERVICE
import ng.riby.androidtest.others.Constants.ACTION_START_OR_RESUME_SERVICE
import ng.riby.androidtest.others.Constants.MAP_ZOOM
import ng.riby.androidtest.others.Constants.POLYLINE_COLOR
import ng.riby.androidtest.others.Constants.POLYLINE_WIDTH
import ng.riby.androidtest.others.TrackingUtility
import ng.riby.androidtest.services.Polyline
import ng.riby.androidtest.services.Polylines
import ng.riby.androidtest.services.TrackingService
import ng.riby.androidtest.ui.viewmodels.MainViewModel

@AndroidEntryPoint
class TrackingFragment: Fragment(R.layout.fragment_tracking) {

    private val viewModel: MainViewModel by viewModels()

    //global variable for isTracking state and pathPoint list
    private var isTracking = false
    private var pathPoints= mutableListOf<Polyline>()


    private var currentTimeInMillis = 0L

    //Google map is actual map object
    //mapView is view that will display this google map
    private var map: GoogleMap? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView.onCreate(savedInstanceState)

        btnToggleDistance.setOnClickListener {
            toggleRun()
        }

        mapView.getMapAsync {
            map = it
            addAllPolylines()
        }



        subscribeToObservers()
    }

    /*we have to worry about adding polylines again cos addLatestPolyLine()
    is only to connect the 2 last polylines of polylines list but wont draw
    all of our polylines on map again when we rotate device
    */
    private fun addAllPolylines(){
        for(polyline in pathPoints){
            val polylineOptions = PolylineOptions()
                    .color(POLYLINE_COLOR)
                    .width(POLYLINE_WIDTH)
                    .addAll(polyline)
            map?.addPolyline(polylineOptions)
        }
    }

    //moves camera to users position when ever there is a new position in our polyline list in our pathpoints list
    private fun moveCameraToUser(){
        if(pathPoints.isNotEmpty()&& pathPoints.last().isNotEmpty()){
            map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                            pathPoints.last().last(),
                            MAP_ZOOM
                    )
            )

        }    }

    //observe data from service and react to those changes
    private fun updateTracking(isTracking:Boolean){
        this.isTracking = isTracking
        if(!isTracking){
            btnToggleDistance.text = "Start"
            btnFinishDistance.visibility = View.VISIBLE
        }else{
            btnToggleDistance.text = "Stop"
            btnFinishDistance.visibility =  View.GONE
        }
    }

    /*functionality to Toggle our tracking service
    ie. start tracking service if it is disturbed or pause state
    and stop if currently running
     */
    private fun toggleRun(){
        if(isTracking){
            sendCommandToService(ACTION_PAUSE_SERVICE)
        }else{
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }

    //subscribe to liveData object in our service
    private fun subscribeToObservers(){
        TrackingService.isTracking.observe(viewLifecycleOwner, Observer {
            updateTracking(it)
        })

        TrackingService.pathPoints.observe(viewLifecycleOwner, Observer {
            pathPoints = it
            addLatestPolyLine()
            moveCameraToUser()
        })

        TrackingService.timeInMilliSeconds.observe(viewLifecycleOwner, Observer {
            currentTimeInMillis = it
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(currentTimeInMillis, true)
            tvTimer.text = formattedTime
        })
    }


    //draw polyline
    private fun addLatestPolyLine(){
        if(pathPoints.isNotEmpty() && pathPoints.last().size >1){
            val preLastLatLng = pathPoints.last()[pathPoints.last().size-2]
            val lastLatLng = pathPoints.last().last()
            val polyLineOptions = PolylineOptions()
                    .color(POLYLINE_COLOR)
                    .width(POLYLINE_WIDTH)
                    .add(preLastLatLng)
                    .add(lastLatLng)
            map?.addPolyline(polyLineOptions)
        }
    }

    private fun sendCommandToService(action:String) =
            Intent(requireContext(), TrackingService::class.java).also {
                it.action = action
                requireContext().startService(it)
            }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
}