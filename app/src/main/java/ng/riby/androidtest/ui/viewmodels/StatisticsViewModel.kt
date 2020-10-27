package ng.riby.androidtest.ui.viewmodels

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import ng.riby.androidtest.repositories.MainRepository

class StatisticsViewModel @ViewModelInject constructor(
        val mainRepository: MainRepository
): ViewModel(){

    val totalTime = mainRepository.getTotalTimeInMillis()
    val totalDistance = mainRepository.getTotalDistance()
    val totalAvgSpeed = mainRepository.getTotalAverageSpeed()


    val distancesSortedByDate = mainRepository.getAllDistancesSortedByDate()

}