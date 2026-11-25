package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Folio Album`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Folio Album", appName)
    }

    @Test
    fun `internet permission is strictly absent from merged manifest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(
            "App must remain strictly offline with zero INTERNET permission",
            permissions.contains(Manifest.permission.INTERNET)
        )
    }
}
