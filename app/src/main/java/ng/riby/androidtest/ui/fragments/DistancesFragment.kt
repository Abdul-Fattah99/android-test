package ng.riby.androidtest.ui.fragments

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_distances.*
import ng.riby.androidtest.R
import ng.riby.androidtest.adapters.DistanceAdapter
import ng.riby.androidtest.others.Constants.REQUEST_CODE_LOCATION_PERMISSION
import ng.riby.androidtest.others.SortType
import ng.riby.androidtest.others.TrackingUtility
import ng.riby.androidtest.ui.viewmodels.MainViewModel
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

@AndroidEntryPoint
class DistancesFragment: Fragment(R.layout.fragment_distances), EasyPermissions.PermissionCallbacks {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var distanceAdapter: DistanceAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermission()

        setupRecyclerView()

        when(viewModel.sortType){
            SortType.DATE -> spFilter.setSelection(0)
            SortType.DISTANCE_TIME -> spFilter.setSelection(1)
            SortType.DISTANCE -> spFilter.setSelection(3)
            SortType.AVG_SPEED -> spFilter.setSelection(4)
        }

        spFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when(position){
                    0 -> viewModel.sortDistance(SortType.DATE)
                    1 -> viewModel.sortDistance(SortType.DISTANCE_TIME)
                    2 -> viewModel.sortDistance(SortType.DISTANCE)
                    3 -> viewModel.sortDistance(SortType.AVG_SPEED)
                }
            }
        }

        viewModel.distances.observe(viewLifecycleOwner, Observer {
            distanceAdapter.submitList(it)
        })


        fab.setOnClickListener {
            findNavController().navigate(R.id.action_distancesFragment_to_trackingFragment)
        }
    }

    //set up recycler view
    private fun setupRecyclerView() = rvDistances.apply {
        distanceAdapter = DistanceAdapter()
        adapter = distanceAdapter
        layoutManager = LinearLayoutManager(requireContext())
    }

    private fun requestPermission(){
        if(TrackingUtility.hasLocationPermissions(requireContext())){
            return
        }
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P){
            EasyPermissions.requestPermissions(
                    this,
                    "Location permission required to use this app",
                    REQUEST_CODE_LOCATION_PERMISSION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            )
        }else{
            EasyPermissions.requestPermissions(
                    this,
                    "Location permission required to use this app",
                    REQUEST_CODE_LOCATION_PERMISSION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if(EasyPermissions.somePermissionPermanentlyDenied(this,perms)){
            AppSettingsDialog.Builder(this).build().show()

        } else{
            requestPermission()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)

    }
}