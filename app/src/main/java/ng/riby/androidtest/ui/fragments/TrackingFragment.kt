package ng.riby.androidtest.ui.fragments

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_tracking.*
import ng.riby.androidtest.R
import ng.riby.androidtest.db.Distance
import ng.riby.androidtest.others.Constants.ACTION_PAUSE_SERVICE
import ng.riby.androidtest.others.Constants.ACTION_START_OR_RESUME_SERVICE
import ng.riby.androidtest.others.Constants.ACTION_STOP_SERVICE
import ng.riby.androidtest.others.Constants.MAP_ZOOM
import ng.riby.androidtest.others.Constants.POLYLINE_COLOR
import ng.riby.androidtest.others.Constants.POLYLINE_WIDTH
import ng.riby.androidtest.others.TrackingUtility
import ng.riby.androidtest.services.Polyline
import ng.riby.androidtest.services.Polylines
import ng.riby.androidtest.services.TrackingService
import ng.riby.androidtest.ui.viewmodels.MainViewModel
import java.util.*
import kotlin.math.round

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

    private var menu: Menu? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView.onCreate(savedInstanceState)

        btnToggleDistance.setOnClickListener {
            toggleRun()
        }

        btnFinishDistance.setOnClickListener {
            zoomToSeeWholeTrack()
            endRunAndSaveToDb()
        }

        mapView.getMapAsync {
            map = it
            addAllPolylines()
        }
        subscribeToObservers()
    }

    //zoom to too the whole details of the track
    private fun zoomToSeeWholeTrack(){
        val bounds = LatLngBounds.Builder()
        for(polyline in pathPoints){
            for(pos in polyline){
                bounds.include((pos))
            }
        }
        map?.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                        bounds.build(),
                        mapView.width,
                        mapView.height,
                        (mapView.height * 0.05).toInt()
                )
        )
    }

    //end run and save to db
    private fun endRunAndSaveToDb(){
        map?.snapshot { bmp ->
            var distanceInMeters = 0
            for(polyline in pathPoints){
                distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
            }

            val avgSpeed = round((distanceInMeters / 1000f) / (currentTimeInMillis / 1000f / 60 / 60) *10) / 10f
            val dateTimeStamp = Calendar.getInstance().timeInMillis
            val distance = Distance(bmp, dateTimeStamp, avgSpeed, distanceInMeters, currentTimeInMillis)
            viewModel.insertDistance(distance)
            Snackbar.make(
                    requireActivity().findViewById(R.id.rootView),
                    "Distance saved successfully",
                    Snackbar.LENGTH_LONG
            ).show()
            stopRun()
        }
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

    //create menu for cancel tracking
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.tool_bar_tracking_menu, menu)
        this.menu = menu
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if(currentTimeInMillis>0L){
            this.menu?.getItem(0)?.isVisible = true
        }
    }

    //Alert dialog to confirm cancel tracking
    private fun showCancelTackingDialog(){
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
                .setTitle("Cancel Tracking")
                .setMessage("Are you sure you want to cancel the tracking and delete all its data?")
                .setIcon(R.drawable.ic_delete)
                .setPositiveButton("Yes"){_, _ ->
                    stopRun()
                }
                .setNegativeButton("No"){ dialogInterface: DialogInterface?, _ ->
                    dialogInterface?.cancel()
                }
                .create()
        dialog.show()

    }

    private fun stopRun(){
        sendCommandToService(ACTION_STOP_SERVICE)
        findNavController().navigate((R.id.action_trackingFragment_to_distancesFragment))

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.mlCancelTracking ->{
                showCancelTackingDialog()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    //observe data from service and react to those changes
    private fun updateTracking(isTracking:Boolean){
        this.isTracking = isTracking
        if(!isTracking && currentTimeInMillis >0L){
            btnToggleDistance.text = "Start"
            btnFinishDistance.visibility = View.GONE
        }else if(isTracking){
            btnToggleDistance.text = "Stop"
            menu?.getItem(0)?.isVisible = true
            btnFinishDistance.visibility =  View.VISIBLE
        }
    }

    /*functionality to Toggle our tracking service
    ie. start tracking service if it is disturbed or pause state
    and stop if currently running
     */
    private fun toggleRun(){
        if(isTracking){
            menu?.getItem(0)?.isVisible = true
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