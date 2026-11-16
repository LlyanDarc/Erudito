package com.example.erudito.domain.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeConnectivityObserver(
    initialOnline: Boolean = true
) : ConnectivityObserver {

    private val onlineFlow = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = onlineFlow.asStateFlow()

    override fun isCurrentlyOnline(): Boolean = onlineFlow.value

    fun setOnline(online: Boolean) {
        onlineFlow.value = online
    }
}
