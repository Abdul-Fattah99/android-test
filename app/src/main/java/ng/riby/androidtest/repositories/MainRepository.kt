package ng.riby.androidtest.repositories

import ng.riby.androidtest.db.Distance
import ng.riby.androidtest.db.DistanceDAO
import javax.inject.Inject

class MainRepository @Inject constructor(
        val distanceDAO: DistanceDAO
) {
    suspend fun insertDistance(distance: Distance) = distanceDAO.insertDistance(distance)

    suspend fun deleteDistance(distance: Distance) = distanceDAO.deleteDistance(distance)

    fun getAllDistancesSortedByDate() = distanceDAO.getAllDistancesSortedByDate()

    fun getAllDistancesSortedByDistance() = distanceDAO.getAllDistancesSortedByDistance()

    fun getAllDistancesSortedByTimeInMillis() = distanceDAO.getAllDistancesSortedByTimeInMillis()

    fun getAllDistancesSortedByAverageSpeed() = distanceDAO.getAllDistancesSortedByAverageSpeed()

    fun getTotalAverageSpeed() = distanceDAO.getTotalAverageSpeed()

    fun getTotalDistance() = distanceDAO.getTotalDistance()

    fun getTotalTimeInMillis() = distanceDAO.getTotalTimeInMillis()

}