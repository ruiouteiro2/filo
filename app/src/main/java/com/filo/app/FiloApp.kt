package com.filo.app

import android.app.Application
import com.filo.app.core.prefs.FiloPrefs
import com.filo.app.data.FiloRepository

class FiloApp : Application() {

    lateinit var repository: FiloRepository
        private set

    lateinit var prefs: FiloPrefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = FiloPrefs(this)
        repository = FiloRepository(this)
    }
}
