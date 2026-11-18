package com.example.erudito.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.erudito.data.local.database.EruditoDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectionRepositoryImplTest {

    private lateinit var database: EruditoDatabase
    private lateinit var repository: CollectionRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EruditoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CollectionRepositoryImpl(database.collectionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `renameCollection updates existing collection name in Room`() = runTest {
        val id = repository.createCollection("Filosofia")

        repository.renameCollection(id, "Filosofia Antiga")

        val updated = repository.getCollectionById(id)
        assertEquals("Filosofia Antiga", updated?.name)
    }

    @Test
    fun `renameCollection throws NoSuchElementException when collection id does not exist`() = runTest {
        try {
            repository.renameCollection(9999L, "Inexistente")
            fail("Deveria lançar NoSuchElementException para coleção inexistente")
        } catch (e: NoSuchElementException) {
            assertEquals("Coleção não encontrada.", e.message)
        }
    }
}
