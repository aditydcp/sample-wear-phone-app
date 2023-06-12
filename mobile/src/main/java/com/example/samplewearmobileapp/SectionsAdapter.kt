package com.example.samplewearmobileapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.samplewearmobileapp.databinding.SectionListItemBinding
import com.example.samplewearmobileapp.models.Section
import com.example.samplewearmobileapp.utils.AppUtils
import com.example.samplewearmobileapp.utils.AppUtils.convertToLocalDateTimeViaMillisecond
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date


class SectionsAdapter(sectionEvents: SectionEvents)
    : RecyclerView.Adapter<SectionsAdapter.ViewHolder>() {

    private lateinit var binding: SectionListItemBinding
    private var sectionList: List<Section> = arrayListOf()
    private var listener: SectionEvents = sectionEvents

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        fun bind(section: Section, listener: SectionEvents, binding: SectionListItemBinding) {
            binding.sectionNumber.text = section.id.toString()
            binding.sectionName.text = section.name

            val time = convertToLocalDateTimeViaMillisecond(section.startTime)
            binding.sectionStartTime.text = time.format(DateTimeFormatter.ISO_TIME).toString()

            itemView.setOnClickListener {
                listener.onSectionClicked(section)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.section_list_item, parent, false)
        binding = SectionListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding.root)
    }

    override fun getItemCount(): Int = sectionList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sectionList[position], listener, binding)
    }

    /**
     * Method used for list updates
     */
    fun setSections(sections: List<Section>) {
        sectionList = sections
        notifyDataSetChanged()
    }

    /**
     * Events for user interaction on RecyclerView
     */
    interface SectionEvents {
        fun onSectionClicked(section: Section)
    }
}