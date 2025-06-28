package com.example.syncup.viewmodel

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

fun <T> LiveData<T>.observeOnce(owner: LifecycleOwner, observer: (T) -> Unit) {
    val wrapper = object : Observer<T> {
        override fun onChanged(value: T) {
            observer(value)
            removeObserver(this) // Hanya observe sekali
        }
    }
    observe(owner, wrapper)
}