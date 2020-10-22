package ng.riby.androidtest.ui.viewmodels

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ng.riby.androidtest.db.Distance
import ng.riby.androidtest.repositories.MainRepository

class MainViewModel @ViewModelInject constructor(
        val mainRepository: MainRepository
): ViewModel() {

    fun insertDistance(distance: Distance) = viewModelScope.launch {
        mainRepository.insertDistance(distance)
    }
}
//Job of MainViewModel is to collect data from repository and provide it for those fragments
// that will need mainViewModel

//we need an instance of our main repo inside our main viewModel
