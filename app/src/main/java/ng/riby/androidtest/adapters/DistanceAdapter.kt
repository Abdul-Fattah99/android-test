package ng.riby.androidtest.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.item_view.view.*
import kotlinx.coroutines.withTimeoutOrNull
import ng.riby.androidtest.R
import ng.riby.androidtest.db.Distance
import ng.riby.androidtest.others.TrackingUtility
import java.text.SimpleDateFormat
import java.util.*

class DistanceAdapter: RecyclerView.Adapter<DistanceAdapter.DistanceViewHolder>() {
    inner class DistanceViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)

    /*list differ takes 2 lists and calculate diff btw the 2 lists
    and also returns them
     */
    val diffCallback = object : DiffUtil.ItemCallback<Distance>(){
        override fun areItemsTheSame(oldItem: Distance, newItem: Distance): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Distance, newItem: Distance): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    /*submit list to list differ so list differ calculate
    the differences and updates recyclerview accordingly
     */
    fun submitList(list: List<Distance>) = differ.submitList(list)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DistanceViewHolder {
        return DistanceViewHolder(
                LayoutInflater.from(parent.context).inflate(
                        R.layout.item_view,
                        parent,
                        false
                )
        )
    }

    override fun getItemCount(): Int {
       return differ.currentList.size
    }

    override fun onBindViewHolder(holder: DistanceViewHolder, position: Int) {
        val distance = differ.currentList[position]
        holder.itemView.apply {
            Glide.with(this).load(distance.img).into(ivDistanceImage)

            val calender =  Calendar.getInstance().apply {
                timeInMillis = distance.timeInMillis
            }
            val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            tvDate.text = dateFormat.format(calender.time)

            val avgSpeed = "${distance.averageSpeedInKMH}hm/hr"
            tvAvgSpeed.text = avgSpeed

            val distanceInKm = "${distance.distanceInMeters / 1000f} km"
            tvDistance.text = distanceInKm

            tvTime.text = TrackingUtility.getFormattedStopWatchTime(distance.timeInMillis)


        }
    }
}