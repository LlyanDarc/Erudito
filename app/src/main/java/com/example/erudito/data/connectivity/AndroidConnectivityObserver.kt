package com.example.erudito.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.example.erudito.domain.connectivity.ConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context
) : ConnectivityObserver {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        trySend(isCurrentlyOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(hasUsableInternet(networkCapabilities))
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        val registered = runCatching {
            manager.registerDefaultNetworkCallback(callback)
        }.isSuccess

        awaitClose {
            if (registered) {
                runCatching {
                    manager.unregisterNetworkCallback(callback)
                }
            }
        }
    }.distinctUntilChanged()

    override fun isCurrentlyOnline(): Boolean {
        val manager = connectivityManager ?: return false
        return runCatching {
            val activeNetwork = manager.activeNetwork ?: return@runCatching false
            val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
            hasUsableInternet(capabilities)
        }.getOrDefault(false)
    }

    private fun hasUsableInternet(capabilities: NetworkCapabilities): Boolean {
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val notSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        } else {
            true
        }
        return hasInternet && isValidated && notSuspended
    }
}
