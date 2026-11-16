package com.example.erudito.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidConnectivityObserverTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var observer: AndroidConnectivityObserver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        observer = AndroidConnectivityObserver(context)
    }

    private fun buildCapabilities(
        hasInternet: Boolean,
        isValidated: Boolean,
        notSuspended: Boolean = true
    ): NetworkCapabilities {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        val shadowCaps = shadowOf(capabilities)
        if (hasInternet) {
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        if (isValidated) {
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        if (notSuspended) {
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        }
        return capabilities
    }

    @Test
    fun `isCurrentlyOnline returns true when active network has INTERNET VALIDATED and NOT_SUSPENDED`() {
        val activeNetwork = connectivityManager.activeNetwork!!
        val capabilities = buildCapabilities(hasInternet = true, isValidated = true)
        shadowOf(connectivityManager).setNetworkCapabilities(activeNetwork, capabilities)

        assertTrue(observer.isCurrentlyOnline())
    }

    @Test
    fun `isCurrentlyOnline returns false when active network has INTERNET but lacks VALIDATED`() {
        val activeNetwork = connectivityManager.activeNetwork!!
        val unvalidatedCaps = buildCapabilities(hasInternet = true, isValidated = false)
        shadowOf(connectivityManager).setNetworkCapabilities(activeNetwork, unvalidatedCaps)

        assertFalse(observer.isCurrentlyOnline())
    }

    @Test
    fun `isCurrentlyOnline returns false when active network lacks INTERNET or is null`() {
        val activeNetwork = connectivityManager.activeNetwork!!
        val capabilitiesWithoutInternet = buildCapabilities(hasInternet = false, isValidated = true)
        shadowOf(connectivityManager).setNetworkCapabilities(activeNetwork, capabilitiesWithoutInternet)

        assertFalse(observer.isCurrentlyOnline())

        shadowOf(connectivityManager).setActiveNetworkInfo(null)
        assertFalse(observer.isCurrentlyOnline())
    }

    @Test
    fun `isOnline flow emits connection loss and recovery and unregisters NetworkCallback on cancel`() = runTest {
        val activeNetwork = connectivityManager.activeNetwork!!
        val onlineCaps = buildCapabilities(hasInternet = true, isValidated = true)
        val unvalidatedCaps = buildCapabilities(hasInternet = true, isValidated = false)
        val offlineCaps = buildCapabilities(hasInternet = false, isValidated = false)
        shadowOf(connectivityManager).setNetworkCapabilities(activeNetwork, onlineCaps)

        val emissions = mutableListOf<Boolean>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.isOnline.take(4).toList(emissions)
        }

        val registeredCountWhileActive = shadowOf(connectivityManager).networkCallbacks.size
        assertTrue(registeredCountWhileActive > 0)

        val callbacks = shadowOf(connectivityManager).networkCallbacks.toList()

        // Perda de validação (portal cativo / sem saída real) -> Offline
        callbacks.forEach { it.onCapabilitiesChanged(activeNetwork, unvalidatedCaps) }
        // Recuperação completa -> Online
        callbacks.forEach { it.onCapabilitiesChanged(activeNetwork, onlineCaps) }
        // Perda total de rede -> Offline via onLost
        shadowOf(connectivityManager).setNetworkCapabilities(activeNetwork, offlineCaps)
        callbacks.forEach { it.onLost(activeNetwork) }

        collectJob.join()
        assertEquals(listOf(true, false, true, false), emissions)
        assertEquals(0, shadowOf(connectivityManager).networkCallbacks.size)
    }

    @Test
    fun `isOnline unregisters NetworkCallback when collector job is cancelled`() = runTest {
        val activeNetwork = connectivityManager.activeNetwork!!
        shadowOf(connectivityManager).setNetworkCapabilities(
            activeNetwork,
            buildCapabilities(hasInternet = true, isValidated = true)
        )

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.isOnline.collect()
        }
        assertTrue(shadowOf(connectivityManager).networkCallbacks.isNotEmpty())

        job.cancel()
        assertTrue(shadowOf(connectivityManager).networkCallbacks.isEmpty())
    }
}
