package com.example.samplewearmobileapp.model

import java.util.*

/**
 * Class to implement a LinkedList whose size cannot exceed maxSize.
 * Only the following methods of LinkedList have been modified to do this:
 * add
 * addLast
 * addFirst
 *
 * @param <T>
 */
class FixedSizeList<T : Any>(size: Int) : LinkedList<T>() {
    private val maxSize: Int

    /**
     * Primary constructor for FixedSizeList.
     *
     * @param size The maximum size. Should be > 0;
     */
    init {
        maxSize = size
    }

    override fun add(element: T): Boolean {
        maintainSize()
        return super.add(element)
    }

    override fun addLast(element: T) {
        maintainSize()
        super.addLast(element)
    }

    override fun addFirst(element: T) {
        maintainSize()
        super.addFirst(element)
    }

    /**
     * Sets the last element to this value. The current size should not be
     * zero. Will not be available if the specified type of this List is List
     * (as opposed to FixedSizeList.)
     *
     * @param t The value to set.
     */
    fun setLast(t: T) {
        set(size - 1, t)
    }

    /**
     * Gets the maximum size for this List.
     *
     * @return The maximum size.
     */
    fun maxSize(): Int {
        return maxSize
    }

    /**
     * Maintains the maxSize of the list. Expected to be called before a new
     * (single) item is added.
     */
    private fun maintainSize() {
        val len = size
        if (len == maxSize) {
            removeFirst()
        }
    }
}