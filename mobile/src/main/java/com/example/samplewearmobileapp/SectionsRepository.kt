package com.example.samplewearmobileapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.samplewearmobileapp.models.Section
import java.util.Date

class SectionsRepository {
    private val allSections: MutableLiveData<MutableList<Section>>

    init {
        val initialSection = MutableLiveData<MutableList<Section>>().apply {
            // hard-code data
            value = arrayListOf(
                Section(1,"Section",Date())
            )
        }
        allSections = initialSection
    }

    fun initiate() {
        allSections.value = arrayListOf(
            Section(0,"Section",Date())
        )
    }

    fun getAllSections(): LiveData<List<Section>> {
        val allSection = MutableLiveData<List<Section>>().apply {
            this.value = allSections.value as List<Section>
        }
        return allSection
    }

    fun addNewSection() {
        allSections.value!!.add(
            Section(allSections.value!!.size+1,"Section",Date())
        )
    }

    fun editSectionName(section: Section, newName: String) {
        for (sectionItem in allSections.value!!) {
            if (section == sectionItem) {
                section.name = newName
            }
        }
    }

    fun clearRepository() {
//        allSections.value = arrayListOf()
        allSections.value = arrayListOf(
            Section(0,"Section",Date())
        )
    }
}