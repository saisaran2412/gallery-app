package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.core.storage.AppStorageDirs
import com.example.ui.navigation.FolioApp

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.core.storage.AppStorageDirs.clearTempPreviews(applicationContext)
        AppStorageDirs.ensureInitialized(applicationContext)
        com.example.core.storage.CacheCleaner.runIfDue(applicationContext)
        com.example.core.birthday.BirthdayNotifier.ensureChannel(applicationContext)
        com.example.core.birthday.BirthdayScheduler.scheduleNextAsync(applicationContext)
        enableEdgeToEdge()
        setContent {
            FolioApp()
        }
    }
}
