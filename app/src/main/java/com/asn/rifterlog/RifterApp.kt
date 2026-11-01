package com.asn.rifterlog

import android.app.Application
import com.asn.rifterlog.data.PlaylistRepository
import com.asn.rifterlog.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RifterApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repo: PlaylistRepository
    lateinit var settings: Settings

    override fun onCreate() {
        super.onCreate()
        repo = PlaylistRepository(this)
        settings = Settings(this)
    }
}
