package com.example.erudito

import android.app.Application
import com.example.erudito.di.AppContainer
import com.example.erudito.di.DefaultAppContainer

import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp(Application::class)
class EruditoApplication : Hilt_EruditoApplication() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
