package com.example.erudito.domain.model

data class Collection(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val articleCount: Int = 0
)
