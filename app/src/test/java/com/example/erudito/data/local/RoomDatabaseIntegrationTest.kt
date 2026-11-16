package com.example.erudito.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.erudito.data.local.dao.ArticleDao
import com.example.erudito.data.local.dao.CollectionDao
import com.example.erudito.data.local.database.EruditoDatabase
import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.data.local.entity.CollectionArticleCrossRef
import com.example.erudito.data.local.entity.CollectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDatabaseIntegrationTest {

    private lateinit var database: EruditoDatabase
    private lateinit var collectionDao: CollectionDao
    private lateinit var articleDao: ArticleDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EruditoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        collectionDao = database.collectionDao()
        articleDao = database.articleDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndRetrieveCollection() = runTest {
        val collection = CollectionEntity(name = "História Medieval")
        val id = collectionDao.insertCollection(collection)

        val retrieved = collectionDao.getCollectionById(id)
        assertNotNull(retrieved)
        assertEquals("História Medieval", retrieved?.name)
    }

    @Test
    fun renameCollectionInDatabase() = runTest {
        val id = collectionDao.insertCollection(CollectionEntity(name = "Nome Antigo"))
        val existing = collectionDao.getCollectionById(id)
        assertNotNull(existing)

        collectionDao.updateCollection(existing!!.copy(name = "Nome Atualizado"))

        val updated = collectionDao.getCollectionById(id)
        assertEquals("Nome Atualizado", updated?.name)
    }

    @Test
    fun articleCanBelongToMultipleCollectionsPerRN03() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "História"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "Artes"))

        val artId = articleDao.insertArticle(
            ArticleEntity(
                title = "Renascimento",
                url = "https://pt.wikipedia.org/wiki/Renascimento",
                description = "Movimento cultural",
                content = "Conteúdo...",
                imageUrl = null
            )
        )

        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col1Id, articleId = artId))
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col2Id, articleId = artId))

        val col1Articles = articleDao.getArticlesForCollection(col1Id).first()
        val col2Articles = articleDao.getArticlesForCollection(col2Id).first()

        assertEquals(1, col1Articles.size)
        assertEquals(artId, col1Articles[0].id)
        assertEquals(1, col2Articles.size)
        assertEquals(artId, col2Articles[0].id)
    }

    @Test
    fun preventDuplicateArticleInSameCollectionPerRN04() = runTest {
        val colId = collectionDao.insertCollection(CollectionEntity(name = "Física"))
        val artId = articleDao.insertArticle(
            ArticleEntity(
                title = "Relatividade",
                url = "https://pt.wikipedia.org/wiki/Relatividade",
                description = "Teoria de Einstein",
                content = "Conteúdo...",
                imageUrl = null
            )
        )

        // Primeira inserção
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = colId, articleId = artId))
        // Tentativa de duplicidade (OnConflictStrategy.IGNORE)
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = colId, articleId = artId))

        val articles = articleDao.getArticlesForCollection(colId).first()
        assertEquals(1, articles.size)
    }

    @Test
    fun deletingCollectionCascadesCrossRefWithoutDeletingArticlePerRN05() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "Coleção 1"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "Coleção 2"))

        val artId = articleDao.insertArticle(
            ArticleEntity(
                title = "Filosofia Grega",
                url = "https://pt.wikipedia.org/wiki/Filosofia_grega",
                description = "Origens do pensamento",
                content = "Conteúdo...",
                imageUrl = null
            )
        )

        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col1Id, articleId = artId))
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col2Id, articleId = artId))

        // Exclui a Coleção 1
        collectionDao.deleteCollectionById(col1Id)

        // 1. Coleção 1 foi removida
        assertNull(collectionDao.getCollectionById(col1Id))

        // 2. Artigo ainda existe na base de dados
        val articleStillExists = articleDao.getArticleById(artId)
        assertNotNull(articleStillExists)

        // 3. Associação com Coleção 2 continua intacta
        val col2Articles = articleDao.getArticlesForCollection(col2Id).first()
        assertEquals(1, col2Articles.size)
        assertEquals(artId, col2Articles[0].id)

        // 4. Associação com Coleção 1 foi removida via CASCADE
        val col1Articles = articleDao.getArticlesForCollection(col1Id).first()
        assertTrue(col1Articles.isEmpty())
    }

    @Test
    fun getAllCollectionsWithCountComputesCorrectAggregate() = runTest {
        val colId = collectionDao.insertCollection(CollectionEntity(name = "Ciências Gerais"))
        val art1 = articleDao.insertArticle(
            ArticleEntity(title = "A1", url = "http://1", description = null, content = null, imageUrl = null)
        )
        val art2 = articleDao.insertArticle(
            ArticleEntity(title = "A2", url = "http://2", description = null, content = null, imageUrl = null)
        )

        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = colId, articleId = art1))
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = colId, articleId = art2))

        val listWithCount = collectionDao.getAllCollectionsWithCount().first()
        val found = listWithCount.find { it.collection.id == colId }

        assertNotNull(found)
        assertEquals(2, found?.articleCount)
    }

    @Test
    fun databaseCallbackPopulatesInitialSeedAtomicallyWithoutDuplicates() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var seededDb: EruditoDatabase
        seededDb = Room.inMemoryDatabaseBuilder(context, EruditoDatabase::class.java)
            .addCallback(
                EruditoDatabase.DatabaseCallback(
                    ioDispatcher = Dispatchers.IO,
                    databaseProvider = { seededDb }
                )
            )
            .build()

        try {
            // Dispara abertura do banco e aguarda a conclusão da transação em background do seed
            val collections = withContext(Dispatchers.Default) {
                withTimeout(5_000L) {
                    seededDb.collectionDao().getAllCollectionsWithCount().first { it.size == 3 }
                }
            }
            val articles = withContext(Dispatchers.Default) {
                withTimeout(5_000L) {
                    seededDb.articleDao().getAllArticles().first { it.size == 3 }
                }
            }

            assertEquals(3, collections.size)
            assertEquals(3, articles.size)
            assertTrue(articles.all { it.wikipediaPageId == null })

            val philosophyCollection = collections.first { it.collection.name == "Filosofia & Pensamento Clássico" }
            assertEquals(2, philosophyCollection.articleCount)

            // Garante idempotência: nova execução do seed não deve duplicar registros
            EruditoDatabase.populateInitialLibrary(seededDb)
            assertEquals(3, seededDb.collectionDao().getCollectionCount())
            assertEquals(3, seededDb.articleDao().getArticleCount())
        } finally {
            seededDb.close()
        }
    }

    @Test
    fun databaseTransactionRollsBackPartialSeedOnFailure() = runTest {
        try {
            database.withTransaction {
                collectionDao.insertCollection(CollectionEntity(name = "Coleção Parcial"))
                throw IllegalStateException("Falha simulada no meio do seed")
            }
        } catch (_: IllegalStateException) {
            // Esperado
        }

        assertEquals(0, collectionDao.getCollectionCount())
        assertEquals(0, articleDao.getArticleCount())
    }

    @Test
    fun migration1To2PreservesCollectionsArticlesCrossRefsAndNullWikipediaPageIds() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_1_2_test_${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        // 1. Cria banco exatamente no schema v1 com dados legados
        val v1Helper = object : SQLiteOpenHelper(context, dbName, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `articles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `description` TEXT,
                        `content` TEXT,
                        `imageUrl` TEXT,
                        `lastUpdated` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collection_article_cross_ref` (
                        `collectionId` INTEGER NOT NULL,
                        `articleId` INTEGER NOT NULL,
                        PRIMARY KEY(`collectionId`, `articleId`),
                        FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`articleId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_article_cross_ref_collectionId` ON `collection_article_cross_ref` (`collectionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_article_cross_ref_articleId` ON `collection_article_cross_ref` (`articleId`)"
                )

                db.execSQL("INSERT INTO collections (id, name, createdAt) VALUES (1, 'Filosofia', 1000)")
                db.execSQL("INSERT INTO collections (id, name, createdAt) VALUES (2, 'História', 2000)")
                db.execSQL(
                    """
                    INSERT INTO articles (id, title, url, description, content, imageUrl, lastUpdated)
                    VALUES (10, 'Filosofia Grega', 'https://pt.wikipedia.org/wiki/Filosofia_grega', 'Origens', 'Conteúdo clássico', NULL, 3000)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO articles (id, title, url, description, content, imageUrl, lastUpdated)
                    VALUES (11, 'Renascimento', 'https://pt.wikipedia.org/wiki/Renascimento', 'Humanismo', 'Conteúdo renascentista', NULL, 4000)
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO collection_article_cross_ref (collectionId, articleId) VALUES (1, 10)")
                db.execSQL("INSERT INTO collection_article_cross_ref (collectionId, articleId) VALUES (1, 11)")
                db.execSQL("INSERT INTO collection_article_cross_ref (collectionId, articleId) VALUES (2, 11)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        v1Helper.writableDatabase.close()
        v1Helper.close()

        // 2. Abre com Room v3 executando MIGRATION_1_2 + MIGRATION_2_3 e validação de schema
        val migratedDb = Room.databaseBuilder(context, EruditoDatabase::class.java, dbName)
            .addMigrations(EruditoDatabase.MIGRATION_1_2, EruditoDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val migratedCollectionDao = migratedDb.collectionDao()
            val migratedArticleDao = migratedDb.articleDao()

            val collections = migratedCollectionDao.getAllCollectionsWithCount().first()
            assertEquals(2, collections.size)
            assertEquals(2, collections.first { it.collection.id == 1L }.articleCount)
            assertEquals(1, collections.first { it.collection.id == 2L }.articleCount)

            val articles = migratedArticleDao.getAllArticles().first()
            assertEquals(2, articles.size)
            assertTrue(articles.all { it.wikipediaPageId == null })
            assertTrue(articles.all { it.lastSyncedAt == null })

            val colIdsArt11 = migratedArticleDao.getCollectionIdsForArticle(11L).first().toSet()
            assertEquals(setOf(1L, 2L), colIdsArt11)

            // Fallback por URL deve enxergar as coleções do artigo migrado com wikipediaPageId NULL
            val observedViaWikiFallback = migratedArticleDao.getCollectionIdsForWikipediaArticle(
                wikipediaPageId = 9876L,
                url = "https://pt.wikipedia.org/wiki/Renascimento"
            ).first().toSet()
            assertEquals(setOf(1L, 2L), observedViaWikiFallback)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun uniqueIndexAllowsMultipleNullWikipediaPageIdsButRejectsDuplicateNonNullPageId() = runTest {
        articleDao.insertArticle(
            ArticleEntity(
                title = "Legado 1",
                url = "https://pt.wikipedia.org/wiki/Legado_1",
                description = null,
                content = null,
                imageUrl = null,
                wikipediaPageId = null
            )
        )
        articleDao.insertArticle(
            ArticleEntity(
                title = "Legado 2",
                url = "https://pt.wikipedia.org/wiki/Legado_2",
                description = null,
                content = null,
                imageUrl = null,
                wikipediaPageId = null
            )
        )
        articleDao.insertArticle(
            ArticleEntity(
                title = "Wiki A",
                url = "https://pt.wikipedia.org/wiki/Wiki_A",
                description = null,
                content = null,
                imageUrl = null,
                wikipediaPageId = 500L
            )
        )

        assertEquals(3, articleDao.getArticleCount())

        try {
            database.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO articles (title, url, description, content, imageUrl, lastUpdated, wikipediaPageId)
                VALUES ('Wiki Duplicado', 'https://pt.wikipedia.org/wiki/Outro', NULL, NULL, NULL, 1000, 500)
                """.trimIndent()
            )
            fail("Índice único de wikipediaPageId deveria impedir duplicidade não-nula")
        } catch (_: SQLiteConstraintException) {
            // Esperado pelo índice único
        }
    }

    @Test
    fun saveArticleAndAssociateCollectionsInsertsNewWikipediaArticle() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "Astronomia"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "Física"))

        val savedId = articleDao.saveArticleAndAssociateCollections(
            article = ArticleEntity(
                title = "Buraco Negro",
                url = "https://pt.wikipedia.org/wiki/Buraco_negro",
                description = "Região do espaço-tempo",
                content = "Conteúdo completo sobre buracos negros.",
                imageUrl = "https://upload.wikimedia.org/blackhole.jpg",
                lastUpdated = 12345L,
                wikipediaPageId = 4242L
            ),
            collectionIds = listOf(col1Id, col2Id)
        )

        assertTrue(savedId > 0L)
        assertEquals(1, articleDao.getArticleCount())

        val stored = articleDao.getArticleByWikipediaPageId(4242L)
        assertNotNull(stored)
        assertEquals(savedId, stored?.id)
        assertEquals("Buraco Negro", stored?.title)
        assertEquals(4242L, stored?.wikipediaPageId)

        val linkedCollectionIds = articleDao.getCollectionIdsForArticle(savedId).first().toSet()
        assertEquals(setOf(col1Id, col2Id), linkedCollectionIds)
    }

    @Test
    fun saveArticleAndAssociateCollectionsReusesExistingArticleByWikipediaPageIdAndUpdatesDataWithoutLosingAssociations() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "Física"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "História da Ciência"))

        val firstId = articleDao.saveArticleAndAssociateCollections(
            article = ArticleEntity(
                title = "Albert Einstein",
                url = "https://pt.wikipedia.org/wiki/Albert_Einstein",
                description = "Descrição inicial",
                content = "Texto inicial",
                imageUrl = null,
                lastUpdated = 1000L,
                wikipediaPageId = 777L
            ),
            collectionIds = listOf(col1Id)
        )

        val secondId = articleDao.saveArticleAndAssociateCollections(
            article = ArticleEntity(
                title = "Albert Einstein (Atualizado)",
                url = "https://pt.wikipedia.org/wiki/Albert_Einstein",
                description = "Físico teórico alemão",
                content = "Texto completo atualizado da Wikipédia",
                imageUrl = "https://upload.wikimedia.org/einstein.jpg",
                lastUpdated = 2000L,
                wikipediaPageId = 777L
            ),
            collectionIds = listOf(col1Id, col2Id, col2Id)
        )

        assertEquals(firstId, secondId)
        assertEquals(1, articleDao.getArticleCount())

        val updated = articleDao.getArticleById(firstId)
        assertEquals("Albert Einstein (Atualizado)", updated?.title)
        assertEquals("Físico teórico alemão", updated?.description)
        assertEquals("Texto completo atualizado da Wikipédia", updated?.content)
        assertEquals("https://upload.wikimedia.org/einstein.jpg", updated?.imageUrl)
        assertEquals(2000L, updated?.lastUpdated)

        val collectionIds = articleDao.getCollectionIdsForArticle(firstId).first()
        assertEquals(2, collectionIds.size)
        assertEquals(setOf(col1Id, col2Id), collectionIds.toSet())
        assertEquals(1, articleDao.getArticlesForCollection(col1Id).first().size)
        assertEquals(1, articleDao.getArticlesForCollection(col2Id).first().size)
    }

    @Test
    fun saveArticleAndAssociateCollectionsReusesLegacyNullPageIdArticleByUrlAndPreservesPriorAssociations() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "Artes"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "Filosofia"))

        val legacyId = articleDao.insertArticle(
            ArticleEntity(
                title = "Renascimento",
                url = "https://pt.wikipedia.org/wiki/Renascimento",
                description = "Descrição offline antiga",
                content = "Conteúdo antigo",
                imageUrl = null,
                lastUpdated = 500L,
                wikipediaPageId = null
            )
        )
        articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col1Id, articleId = legacyId))

        val reusedId = articleDao.saveArticleAndAssociateCollections(
            article = ArticleEntity(
                title = "Renascimento",
                url = "https://pt.wikipedia.org/wiki/Renascimento",
                description = "Movimento cultural europeu",
                content = "Conteúdo atualizado da Wikipédia",
                imageUrl = "https://upload.wikimedia.org/renascimento.jpg",
                lastUpdated = 1500L,
                wikipediaPageId = 9001L
            ),
            collectionIds = listOf(col2Id)
        )

        assertEquals(legacyId, reusedId)
        assertEquals(1, articleDao.getArticleCount())

        val updated = articleDao.getArticleById(legacyId)
        assertEquals(9001L, updated?.wikipediaPageId)
        assertEquals("Conteúdo atualizado da Wikipédia", updated?.content)
        assertEquals("https://upload.wikimedia.org/renascimento.jpg", updated?.imageUrl)

        val linkedCollections = articleDao.getCollectionIdsForArticle(legacyId).first().toSet()
        assertEquals(setOf(col1Id, col2Id), linkedCollections)
    }

    @Test
    fun legacyUrlFallbackDoesNotHijackArticleWithDifferentNonNullWikipediaPageId() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "Coleção A"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "Coleção B"))

        val firstId = articleDao.saveArticleAndAssociateCollections(
            article = ArticleEntity(
                title = "Artigo 1",
                url = "https://pt.wikipedia.org/wiki/Mesma_URL",
                description = null,
                content = "Texto 1",
                imageUrl = null,
                wikipediaPageId = 101L
            ),
            collectionIds = listOf(col1Id)
        )

        val secondId = articleDao.saveArticleAndAssociateCollections(
            article = ArticleEntity(
                title = "Artigo 2",
                url = "https://pt.wikipedia.org/wiki/Mesma_URL",
                description = null,
                content = "Texto 2",
                imageUrl = null,
                wikipediaPageId = 202L
            ),
            collectionIds = listOf(col2Id)
        )

        assertTrue(firstId != secondId)
        assertEquals(2, articleDao.getArticleCount())
        assertEquals(listOf(col1Id), articleDao.getCollectionIdsForWikipediaArticle(101L, "https://pt.wikipedia.org/wiki/Mesma_URL").first())
        assertEquals(listOf(col2Id), articleDao.getCollectionIdsForWikipediaArticle(202L, "https://pt.wikipedia.org/wiki/Mesma_URL").first())
    }

    @Test
    fun removingSingleAssociationPreservesArticleAndOtherCollectionLinks() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "Coleção 1"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "Coleção 2"))

        val articleId = articleDao.saveArticleAndAssociateCollections(
            article = ArticleEntity(
                title = "Sócrates",
                url = "https://pt.wikipedia.org/wiki/S%C3%B3crates",
                description = "Filósofo grego",
                content = "Conteúdo",
                imageUrl = null,
                wikipediaPageId = 314L
            ),
            collectionIds = listOf(col1Id, col2Id)
        )

        articleDao.deleteCrossRef(collectionId = col1Id, articleId = articleId)

        assertNotNull(articleDao.getArticleById(articleId))
        assertTrue(articleDao.getArticlesForCollection(col1Id).first().isEmpty())
        assertEquals(listOf(articleId), articleDao.getArticlesForCollection(col2Id).first().map { it.id })
        assertEquals(listOf(col2Id), articleDao.getCollectionIdsForWikipediaArticle(314L, "https://pt.wikipedia.org/wiki/S%C3%B3crates").first())
    }

    @Test
    fun saveArticleAndAssociateCollectionsRollsBackCompletelyWhenAnyCollectionDoesNotExist() = runTest {
        val validColId = collectionDao.insertCollection(CollectionEntity(name = "Válida"))
        val nonExistentColId = 9999L

        try {
            articleDao.saveArticleAndAssociateCollections(
                article = ArticleEntity(
                    title = "Artigo Abortado",
                    url = "https://pt.wikipedia.org/wiki/Artigo_abortado",
                    description = "Desc",
                    content = "Conteúdo",
                    imageUrl = null,
                    wikipediaPageId = 5555L
                ),
                collectionIds = listOf(validColId, nonExistentColId)
            )
            fail("Deveria falhar quando alguma coleção não existe")
        } catch (_: IllegalArgumentException) {
            // Esperado
        }

        // Rollback atômico: nem o artigo nem a associação com a coleção válida podem ter sido gravados
        assertEquals(0, articleDao.getArticleCount())
        assertNull(articleDao.getArticleByWikipediaPageId(5555L))
        assertTrue(articleDao.getArticlesForCollection(validColId).first().isEmpty())
    }

    @Test
    fun migration2To3PreservesAllDataAndSetsLastSyncedAtToNull() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_2_3_test_${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        val v2Helper = object : SQLiteOpenHelper(context, dbName, null, 2) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `articles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `description` TEXT,
                        `content` TEXT,
                        `imageUrl` TEXT,
                        `lastUpdated` INTEGER NOT NULL,
                        `wikipediaPageId` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_articles_wikipediaPageId` ON `articles` (`wikipediaPageId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collection_article_cross_ref` (
                        `collectionId` INTEGER NOT NULL,
                        `articleId` INTEGER NOT NULL,
                        PRIMARY KEY(`collectionId`, `articleId`),
                        FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`articleId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_article_cross_ref_collectionId` ON `collection_article_cross_ref` (`collectionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_article_cross_ref_articleId` ON `collection_article_cross_ref` (`articleId`)"
                )

                db.execSQL("INSERT INTO collections (id, name, createdAt) VALUES (1, 'Física', 1000)")
                db.execSQL("INSERT INTO collections (id, name, createdAt) VALUES (2, 'Filosofia', 2000)")
                db.execSQL(
                    """
                    INSERT INTO articles (id, title, url, description, content, imageUrl, lastUpdated, wikipediaPageId)
                    VALUES (10, 'Filosofia Grega', 'https://pt.wikipedia.org/wiki/Filosofia_grega', 'Curadoria', 'Texto curado', NULL, 3000, NULL)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO articles (id, title, url, description, content, imageUrl, lastUpdated, wikipediaPageId)
                    VALUES (20, 'Mecânica Quântica', 'https://pt.wikipedia.org/wiki/Mec%C3%A2nica_qu%C3%A2ntica', 'Física moderna', 'Texto quântico', 'https://upload.wikimedia.org/q.jpg', 4000, 9988)
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO collection_article_cross_ref (collectionId, articleId) VALUES (2, 10)")
                db.execSQL("INSERT INTO collection_article_cross_ref (collectionId, articleId) VALUES (1, 20)")
                db.execSQL("INSERT INTO collection_article_cross_ref (collectionId, articleId) VALUES (2, 20)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        v2Helper.writableDatabase.close()
        v2Helper.close()

        val migratedDb = Room.databaseBuilder(context, EruditoDatabase::class.java, dbName)
            .addMigrations(EruditoDatabase.MIGRATION_1_2, EruditoDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val migratedCollectionDao = migratedDb.collectionDao()
            val migratedArticleDao = migratedDb.articleDao()

            val collections = migratedCollectionDao.getAllCollectionsWithCount().first()
            assertEquals(2, collections.size)
            assertEquals(1, collections.first { it.collection.id == 1L }.articleCount)
            assertEquals(2, collections.first { it.collection.id == 2L }.articleCount)

            val articles = migratedArticleDao.getAllArticles().first()
            assertEquals(2, articles.size)
            assertTrue(articles.all { it.lastSyncedAt == null })

            val curated = migratedArticleDao.getArticleById(10L)
            assertNotNull(curated)
            assertNull(curated?.wikipediaPageId)
            assertEquals(3000L, curated?.lastUpdated)
            assertNull(curated?.lastSyncedAt)

            val wikiArticle = migratedArticleDao.getArticleById(20L)
            assertNotNull(wikiArticle)
            assertEquals(9988L, wikiArticle?.wikipediaPageId)
            assertEquals(4000L, wikiArticle?.lastUpdated)
            assertNull(wikiArticle?.lastSyncedAt)
            assertEquals(setOf(1L, 2L), migratedArticleDao.getCollectionIdsForArticle(20L).first().toSet())

            // Verifica atualização via @Update após migração preservando vínculos N:N
            migratedArticleDao.updateArticle(
                wikiArticle!!.copy(
                    content = "Texto quântico atualizado",
                    lastUpdated = 5000L,
                    lastSyncedAt = 5000L
                )
            )
            val refreshed = migratedArticleDao.getArticleById(20L)
            assertEquals("Texto quântico atualizado", refreshed?.content)
            assertEquals(5000L, refreshed?.lastUpdated)
            assertEquals(5000L, refreshed?.lastSyncedAt)
            assertEquals(setOf(1L, 2L), migratedArticleDao.getCollectionIdsForArticle(20L).first().toSet())
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun updateArticlePreservesPrimaryKeyAndAllCrossRefs() = runTest {
        val col1Id = collectionDao.insertCollection(CollectionEntity(name = "Astronomia"))
        val col2Id = collectionDao.insertCollection(CollectionEntity(name = "História"))

        val articleId = articleDao.saveArticleAndAssociateCollections(
            article = ArticleEntity(
                title = "Galileu Galilei",
                url = "https://pt.wikipedia.org/wiki/Galileu_Galilei",
                description = "Astrônomo italiano",
                content = "Texto original",
                imageUrl = "https://upload.wikimedia.org/galileu.jpg",
                lastUpdated = 1000L,
                wikipediaPageId = 1234L,
                lastSyncedAt = null
            ),
            collectionIds = listOf(col1Id, col2Id)
        )

        val stored = articleDao.getArticleById(articleId)!!
        articleDao.updateArticle(
            stored.copy(
                content = "Texto sincronizado",
                lastUpdated = 9000L,
                lastSyncedAt = 9000L
            )
        )

        val updated = articleDao.getArticleById(articleId)!!
        assertEquals(articleId, updated.id)
        assertEquals("Texto sincronizado", updated.content)
        assertEquals(9000L, updated.lastUpdated)
        assertEquals(9000L, updated.lastSyncedAt)
        assertEquals(setOf(col1Id, col2Id), articleDao.getCollectionIdsForArticle(articleId).first().toSet())
    }
}
