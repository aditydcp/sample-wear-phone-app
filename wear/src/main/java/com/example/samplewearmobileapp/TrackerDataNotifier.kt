package com.example.samplewearmobileapp

import java.util.function.Consumer

class TrackerDataNotifier {
    private val observers: MutableList<TrackerDataObserver> = ArrayList()
    fun addObserver(observer: TrackerDataObserver) {
        observers.add(observer)
    }

    fun removeObserver(observer: TrackerDataObserver) {
        observers.remove(observer)
    }

    fun notifyHeartRateTrackerObservers(hrData: HeartRateData) {
        observers.forEach(Consumer { observer: TrackerDataObserver ->
            observer.onHeartRateTrackerDataChanged(
                hrData
            )
        })
    }

    fun notifyPpgGreenTrackerObservers(ppgGreenData: PpgGreenData) {
        observers.forEach(Consumer { observer: TrackerDataObserver ->
            observer.onPpgGreenTrackerDataChanged(
                ppgGreenData
            )
        })
    }

    fun notifyError(errorResourceId: Int) {
        observers.forEach(Consumer { observer: TrackerDataObserver ->
            observer.onError(
                errorResourceId
            )
        })
    }

    companion object {
        var instance: TrackerDataNotifier? = null
            get() {
                if (field == null) {
                    field = TrackerDataNotifier()
                }
                return field
            }
            private set
    }
}