package com.live.life.intoxication.filecleanup

import android.app.Application

class App: Application() {
    companion object{
        lateinit var instance: App
        lateinit var preference : Preference
    }
    override fun onCreate() {
        super.onCreate()
        instance =  this
        preference = Preference(this)
    }
}