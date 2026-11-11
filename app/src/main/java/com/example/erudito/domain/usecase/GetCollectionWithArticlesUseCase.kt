package com.example.erudito.domain.usecase

import com.example.erudito.domain.model.CollectionWithArticles
import com.example.erudito.domain.repository.CollectionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetCollectionWithArticlesUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository
) {
    operator fun invoke(collectionId: Long): Flow<CollectionWithArticles?> {
        return collectionRepository.getCollectionWithArticles(collectionId)
    }
}
