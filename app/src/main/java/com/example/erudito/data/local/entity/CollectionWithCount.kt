package com.example.erudito.data.local.entity

import androidx.room.Embedded

data class CollectionWithCount(
    @Embedded
    val collection: CollectionEntity,
    val articleCount: Int
)
