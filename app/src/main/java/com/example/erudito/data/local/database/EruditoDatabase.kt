package com.example.erudito.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.erudito.data.local.dao.ArticleDao
import com.example.erudito.data.local.dao.CollectionDao
import com.example.erudito.data.local.entity.ArticleEntity
import com.example.erudito.data.local.entity.CollectionArticleCrossRef
import com.example.erudito.data.local.entity.CollectionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CollectionEntity::class,
        ArticleEntity::class,
        CollectionArticleCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EruditoDatabase : RoomDatabase() {

    abstract fun collectionDao(): CollectionDao
    abstract fun articleDao(): ArticleDao

    companion object {
        @Volatile
        private var INSTANCE: EruditoDatabase? = null

        fun getInstance(context: Context): EruditoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EruditoDatabase::class.java,
                    "erudito_database.db"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialLibrary(database)
                    }
                }
            }

            private suspend fun populateInitialLibrary(database: EruditoDatabase) {
                val collectionDao = database.collectionDao()
                val articleDao = database.articleDao()

                // Pre-população de coleções iniciais da biblioteca pessoal
                val col1Id = collectionDao.insertCollection(
                    CollectionEntity(name = "Filosofia & Pensamento Clássico")
                )
                val col2Id = collectionDao.insertCollection(
                    CollectionEntity(name = "Ciência & Física Moderna")
                )
                val col3Id = collectionDao.insertCollection(
                    CollectionEntity(name = "História da Arte & Renascimento")
                )

                // Pre-população de artigos iniciais com conteúdo offline completo
                val art1Id = articleDao.insertArticle(
                    ArticleEntity(
                        title = "Filosofia Grega",
                        url = "https://pt.wikipedia.org/wiki/Filosofia_grega",
                        description = "O surgimento do pensamento racional no Ocidente antigo.",
                        content = """A filosofia grega surgiu no século VI a.C. na Grécia Antiga com Tales de Mileto e os filósofos pré-socráticos, marcando a transição do pensamento mítico para a reflexão racional e sistemática sobre a natureza (physis) e o cosmos.

Posteriormente, Sócrates inaugurou a reflexão ética e antropológica em Atenas, enfatizando o diálogo maiêutico e o autoexame moral ('conhece-te a ti mesmo'). Seu discípulo Platão fundou a Academia e formulou a Teoria das Ideias, enquanto Aristóteles, na esteira do Liceu, desenvolveu a lógica formal, a metafísica e a observação empírica da natureza.

O legado filosófico grego lançou os alicerces fundamentais da ciência, da política, do direito e da ética que moldaram a civilização ocidental.""",
                        imageUrl = null,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                val art2Id = articleDao.insertArticle(
                    ArticleEntity(
                        title = "Teoria da Relatividade",
                        url = "https://pt.wikipedia.org/wiki/Teoria_da_relatividade",
                        description = "As teorias de Albert Einstein que transformaram o conceito de espaço e tempo.",
                        content = """A Teoria da Relatividade é a denominação dada conjuntamente a duas teorias científicas formuladas por Albert Einstein: a Relatividade Restrita (1905) e a Relatividade Geral (1915).

A Relatividade Restrita postulou que a velocidade da luz no vácuo é a mesma para todos os observadores, independentemente do movimento da fonte, estabelecendo a equivalência entre massa e energia expressa pela famosa equação E = mc².

A Relatividade Geral estendeu esses conceitos para incluir a gravitação, descrevendo a gravidade não como uma força mística à distância, mas como a curvatura do tecido do espaço-tempo provocada pela presença de matéria e energia. Essa visão revolucionou a astrofísica contemporânea e permitiu a compreensão de buracos negros e da expansão do Universo.""",
                        imageUrl = null,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                val art3Id = articleDao.insertArticle(
                    ArticleEntity(
                        title = "Renascimento",
                        url = "https://pt.wikipedia.org/wiki/Renascimento",
                        description = "Movimento cultural, artístico e humanista na Europa dos séculos XIV a XVI.",
                        content = """O Renascimento foi um proeminente movimento cultural, artístico e científico europeu ocorrido entre os séculos XIV e XVI, que marcou o início da Idade Moderna. Originado nas ricas cidades-estado italianas, como Florença e Veneza, promoveu o humanismo, a valorização da razão e o resgate dos ideais da Antiguidade Clássica.

Grandes mestres como Leonardo da Vinci, Michelangelo, Rafael Sanzio e Galileu Galilei exemplificaram a busca pelo conhecimento universal, unindo ciência, proporção matemática e arte em obras sem precedentes na história humana.""",
                        imageUrl = null,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                // Associações iniciais N:N entre artigos e coleções
                articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col1Id, articleId = art1Id))
                articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col2Id, articleId = art2Id))
                articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col3Id, articleId = art3Id))
                // Associação do Renascimento também com Filosofia (RN03: um artigo pode pertencer a múltiplas coleções)
                articleDao.insertCrossRef(CollectionArticleCrossRef(collectionId = col1Id, articleId = art3Id))
            }
        }
    }
}
