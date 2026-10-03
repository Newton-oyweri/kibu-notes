package com.kibu.notes

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The promise, read off the package Android actually installed.
 *
 * Three things assert this now, and they are deliberately not one thing.
 * app/build.gradle.kts fails the build on the merged manifest, before an APK
 * exists. FormatRegistryTest asks the package manager in a Robolectric
 * sandbox. This asks a real Android about a real installed package, which is
 * the only one of the three that could catch something introduced between the
 * manifest and the device.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class ZeroPermissionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theInstalledAppRequestsNothingTheReaderWouldSee() {
        val info = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS
        )
        val platform = info.requestedPermissions.orEmpty()
            .filter { it.startsWith("android.permission.") && it != "android.permission.INTERNET" }
        assertThat(platform).isEmpty()
    }

    /** Nothing outside the self-granted signature permission and internet permission. */
    @Test
    fun theOnlyPermissionIsTheSelfGrantedReceiverAndInternetOne() {
        val allowed = setOf(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            "android.permission.INTERNET"
        )
        val info = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS
        )
        val unexpected = info.requestedPermissions.orEmpty().filterNot { it in allowed }
        assertThat(unexpected).isEmpty()
    }
}
