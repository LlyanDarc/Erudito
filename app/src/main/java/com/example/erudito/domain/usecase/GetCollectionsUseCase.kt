package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.Collection
import com.example.erudito.domain.repository.CollectionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetCollectionsUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository
) {
    operator fun invoke(): Flow<List<Collection>> {
        return collectionRepository.getCollections()
    }
}
