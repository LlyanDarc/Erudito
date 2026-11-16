package com.example.erudito.domain.connectivity

import kotlinx.coroutines.flow.Flow

interface ConnectivityObserver {
    val isOnline: Flow<Boolean>
    fun isCurrentlyOnline(): Boolean
}
