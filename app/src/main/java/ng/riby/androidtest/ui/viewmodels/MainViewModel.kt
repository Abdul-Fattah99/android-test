package ng.riby.androidtest.ui.viewmodels

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ng.riby.androidtest.db.Distance
import ng.riby.androidtest.others.SortType
import ng.riby.androidtest.repositories.MainRepository

class MainViewModel @ViewModelInject constructor(
        val mainRepository: MainRepository
): ViewModel() {

    fun insertDistance(distance: Distance) = viewModelScope.launch {
        mainRepository.insertDistance(distance)
    }

    //for now
   private val distanceSortedByDate = mainRepository.getAllDistancesSortedByDate()

    private val distancesSortedByDistance = mainRepository.getAllDistancesSortedByDistance()

    private val distancesSortedByAverageSpeed = mainRepository.getAllDistancesSortedByAverageSpeed()

    private val distancesSortedByTimeInMillis = mainRepository.getAllDistancesSortedByTimeInMillis()

    val distances = MediatorLiveData<List<Distance>>()

    //default sort type
    var sortType = SortType.DATE

    init {
        distances.addSource(distanceSortedByDate){ result ->
            if(sortType == SortType.DATE){
                result.let { distances.value = it }
            }
        }

        distances.addSource(distancesSortedByAverageSpeed){ result ->
            if(sortType == SortType.AVG_SPEED){
                result.let { distances.value = it }
            }
        }

        distances.addSource(distancesSortedByDistance){ result ->
            if(sortType == SortType.DISTANCE){
                result.let { distances.value = it }
            }
        }

        distances.addSource(distancesSortedByTimeInMillis){ result ->
            if(sortType == SortType.DISTANCE_TIME){
                result.let { distances.value = it }
            }
        }
    }


    fun sortDistance(sortType: SortType) = when(sortType){
        SortType.DATE -> distanceSortedByDate.value?.let { distances.value = it }

        SortType.DISTANCE_TIME -> distancesSortedByTimeInMillis.value?.let { distances.value = it }

        SortType.AVG_SPEED -> distancesSortedByAverageSpeed.value?.let { distances.value = it }

        SortType.DISTANCE -> distancesSortedByDistance.value?.let { distances.value = it }

    }.also {
        this.sortType = sortType
    }

}
//Job of MainViewModel is to collect data from repository and provide it for those fragments
// that will need mainViewModel

//we need an instance of our main repo inside our main viewModel








