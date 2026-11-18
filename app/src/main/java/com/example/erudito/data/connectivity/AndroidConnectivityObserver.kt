package com.example.erudito.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.example.erudito.domain.connectivity.ConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class AndroidConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context
) : ConnectivityObserver {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val callbackLock = Any()
    private val activeConsumers = linkedSetOf<ProducerScope<Boolean>>()
    private var sharedNetworkCallback: ConnectivityManager.NetworkCallback? = null

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        trySend(isCurrentlyOnline())
        registerConsumer(manager, this)

        awaitClose {
            unregisterConsumer(manager, this)
        }
    }.distinctUntilChanged()

    private fun registerConsumer(
        manager: ConnectivityManager,
        consumer: ProducerScope<Boolean>
    ) {
        val callbackToRegister: ConnectivityManager.NetworkCallback?
        synchronized(callbackLock) {
            activeConsumers.add(consumer)
            if (sharedNetworkCallback == null) {
                val callback = createSharedCallback()
                sharedNetworkCallback = callback
                callbackToRegister = callback
            } else {
                callbackToRegister = null
            }
        }

        if (callbackToRegister != null) {
            val registered = runCatching {
                manager.registerDefaultNetworkCallback(callbackToRegister)
            }.isSuccess
            if (!registered) {
                synchronized(callbackLock) {
                    if (sharedNetworkCallback === callbackToRegister) {
                        sharedNetworkCallback = null
                    }
                }
            }
        }
    }

    private fun unregisterConsumer(
        manager: ConnectivityManager,
        consumer: ProducerScope<Boolean>
    ) {
        val callbackToUnregister: ConnectivityManager.NetworkCallback?
        synchronized(callbackLock) {
            activeConsumers.remove(consumer)
            if (activeConsumers.isEmpty()) {
                callbackToUnregister = sharedNetworkCallback
                sharedNetworkCallback = null
            } else {
                callbackToUnregister = null
            }
        }

        if (callbackToUnregister != null) {
            runCatching {
                manager.unregisterNetworkCallback(callbackToUnregister)
            }
        }
    }

    private fun createSharedCallback(): ConnectivityManager.NetworkCallback {
        return object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                dispatchConnectivity(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                dispatchConnectivity(hasUsableInternet(networkCapabilities))
            }

            override fun onLost(network: Network) {
                dispatchConnectivity(isCurrentlyOnline())
            }

            override fun onUnavailable() {
                dispatchConnectivity(false)
            }
        }
    }

    private fun dispatchConnectivity(online: Boolean) {
        val consumersSnapshot = synchronized(callbackLock) {
            activeConsumers.toList()
        }
        consumersSnapshot.forEach { consumer ->
            consumer.trySend(online)
        }
    }

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
