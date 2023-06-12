package com.example.samplewearmobileapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.example.samplewearmobileapp.models.Section

class MainViewModel(application: Application): AndroidViewModel(application) {
    private val repository: SectionsRepository = SectionsRepository()
    private val allSections: LiveData<List<Section>> = repository.getAllSections()

    fun getSectionsList(): LiveData<List<Section>> {
        return allSections
    }

    fun addNewSection() {
        repository.addNewSection()
    }

    fun editSectionName(section: Section, newName: String) {
        repository.editSectionName(section, newName)
    }

    /**
     * Initiate sectioning.
     * Use when starting recording.
     */
    fun initiateSectioning() {
        repository.initiate()
    }

    /**
     * Clear/reset the sections list.
     * Use when re-starting recording.
     */
    fun clearSectionsList() {
        repository.clearRepository()
    }
}