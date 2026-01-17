package com.verbspiel

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Database(entities = [Word::class, AppMeta::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun metaDao(): MetaDao
}

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val prefix: String,
    val root: String,
    val isReflexive: Boolean = false,
    val translation: String,
    val example: String,
    val isFavorite: Boolean = false,
    val isLearned: Boolean = false,
    // Statistics
    val timesShown: Int = 0,
    val correctCount: Int = 0,
    val failedCount: Int = 0,
    val triesCount: Int = 0,
    val lastShownAt: Long = 0L,
    val lastCorrectAt: Long = 0L,
    val lastFailedAt: Long = 0L
)

@Entity(tableName = "app_meta")
data class AppMeta(
    @PrimaryKey
    val key: String,
    val intValue: Int
)

fun formatRoot(root: String, isReflexive: Boolean): String {
    return if (isReflexive) "$root (sich)" else root
}

fun formatWord(word: Word): String {
    return word.prefix + formatRoot(word.root, word.isReflexive)
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words")
    suspend fun getAllWords(): List<Word>

    @Query("SELECT * FROM words WHERE isLearned = 1 ORDER BY (correctCount - failedCount) DESC")
    suspend fun getLearnedWordsSorted(): List<Word>

    @Query("SELECT * FROM words WHERE isFavorite = 1 ORDER BY (correctCount - failedCount) DESC")
    suspend fun getFavoriteWordsSorted(): List<Word>


    @Query(
        "SELECT * FROM words " +
            "WHERE isLearned = 0 " +
            "ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun getMixedPool(limit: Int): List<Word>

    @Query(
        "SELECT * FROM words " +
            "WHERE prefix = :prefix " +
            "ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun getPrefixPool(prefix: String, limit: Int): List<Word>

    @Query(
        "SELECT * FROM words " +
            "WHERE root = :root " +
            "ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun getRootPool(root: String, limit: Int): List<Word>

    @Query(
        "SELECT * FROM words " +
            "WHERE isFavorite = 1 " +
            "ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun getFavoritesPool(limit: Int): List<Word>

    @Query("SELECT DISTINCT prefix FROM words ORDER BY prefix")
    suspend fun getAllPrefixes(): List<String>

    @Query("SELECT DISTINCT root FROM words ORDER BY root")
    suspend fun getAllRoots(): List<String>

    @Query("SELECT * FROM words WHERE failedCount > 0 ORDER BY lastFailedAt DESC LIMIT :limit")
    suspend fun getRecentFailures(limit: Int = 20): List<Word>

    @Query("SELECT * FROM words WHERE correctCount > 0 ORDER BY lastCorrectAt DESC LIMIT :limit")
    suspend fun getRecentCorrect(limit: Int = 20): List<Word>

    @Query(
        "SELECT * FROM words WHERE correctCount > 0 " +
            "ORDER BY correctCount DESC, " +
            "CASE WHEN triesCount = 0 THEN 0 ELSE (correctCount * 100 / triesCount) END DESC, " +
            "triesCount DESC " +
            "LIMIT :limit"
    )
    suspend fun getTopCorrect(limit: Int = 20): List<Word>

    @Query(
        "SELECT * FROM words WHERE failedCount > 0 " +
            "ORDER BY failedCount DESC, " +
            "CASE WHEN triesCount = 0 THEN 0 ELSE (failedCount * 100 / triesCount) END DESC, " +
            "triesCount DESC " +
            "LIMIT :limit"
    )
    suspend fun getTopFailed(limit: Int = 20): List<Word>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: Word)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<Word>)

    @Update
    suspend fun updateWord(word: Word)

    @Update
    suspend fun updateWords(words: List<Word>)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun getCount(): Int

    // Optionally, query a specific word by id
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Int): Word?

    @Query("DELETE FROM words WHERE id IN (:ids)")
    suspend fun deleteWordsByIds(ids: List<Int>)
}

@Dao
interface MetaDao {
    @Query("SELECT * FROM app_meta WHERE key = :key")
    suspend fun getMeta(key: String): AppMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: AppMeta)
}

class WordRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java, "my-app-database"
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()

    private val wordDao = db.wordDao()
    private val metaDao = db.metaDao()

    suspend fun getAllWords(): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getAllWords()
    }

    suspend fun getLearnedWordsSorted(): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getLearnedWordsSorted()
    }

    suspend fun getFavoriteWordsSorted(): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getFavoriteWordsSorted()
    }


    suspend fun getMixedPool(limit: Int): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getMixedPool(limit)
    }

    suspend fun getPrefixPool(prefix: String, limit: Int): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getPrefixPool(prefix, limit)
    }

    suspend fun getRootPool(root: String, limit: Int): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getRootPool(root, limit)
    }

    suspend fun getFavoritesPool(limit: Int): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getFavoritesPool(limit)
    }

    suspend fun getAllPrefixes(): List<String> = withContext(Dispatchers.IO) {
        wordDao.getAllPrefixes()
    }

    suspend fun getAllRoots(): List<String> = withContext(Dispatchers.IO) {
        wordDao.getAllRoots()
    }

    suspend fun getWordById(id: Int): Word? = withContext(Dispatchers.IO) {
        wordDao.getWordById(id)
    }

    suspend fun getWordVersion(): Int = withContext(Dispatchers.IO) {
        metaDao.getMeta(META_WORDS_VERSION)?.intValue ?: 0
    }

    suspend fun setWordVersion(version: Int) = withContext(Dispatchers.IO) {
        metaDao.upsertMeta(AppMeta(META_WORDS_VERSION, version))
    }

    suspend fun getRetiredWords(): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getAllWords()
            .filter { it.isLearned }
            .sortedByDescending { it.correctCount - it.failedCount }
    }

    suspend fun addWord(word: Word) = withContext(Dispatchers.IO) { wordDao.insertWord(word) }

    suspend fun addWords(words: List<Word>) = withContext(Dispatchers.IO) {
        wordDao.insertWords(words)
    }

    suspend fun isEmpty(): Boolean = withContext(Dispatchers.IO) { wordDao.getCount() == 0 }

    suspend fun updateWordStats(word: Word) = withContext(Dispatchers.IO) {
        wordDao.updateWord(word)
    }

    suspend fun syncWords(newWords: List<Word>) = withContext(Dispatchers.IO) {
        val existing = wordDao.getAllWords()
        val existingMap = existing.associateBy { wordKey(it) }
        val newMap = newWords.associateBy { wordKey(it) }

        val toDelete = existing.filter { !newMap.containsKey(wordKey(it)) }
        if (toDelete.isNotEmpty()) {
            wordDao.deleteWordsByIds(toDelete.map { it.id })
        }

        val toInsert = newWords.filter { !existingMap.containsKey(wordKey(it)) }
        if (toInsert.isNotEmpty()) {
            wordDao.insertWords(toInsert)
        }

        val toUpdate = existing.mapNotNull { old ->
            val fresh = newMap[wordKey(old)] ?: return@mapNotNull null
            if (old.example == fresh.example) return@mapNotNull null
            old.copy(
                prefix = fresh.prefix,
                root = fresh.root,
                isReflexive = fresh.isReflexive,
                translation = fresh.translation,
                example = fresh.example
            )
        }
        if (toUpdate.isNotEmpty()) {
            wordDao.updateWords(toUpdate)
        }
    }

    suspend fun getRecentFailures(limit: Int = 20): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getRecentFailures(limit)
    }

    suspend fun getRecentCorrect(limit: Int = 20): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getRecentCorrect(limit)
    }

    suspend fun getTopCorrect(limit: Int = 20): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getTopCorrect(limit)
    }

    suspend fun getTopFailed(limit: Int = 20): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getTopFailed(limit)
    }

    companion object {
        private const val META_WORDS_VERSION = "words_version"

        private fun wordKey(word: Word): String {
            return "${word.prefix};${word.root};${word.isReflexive};${word.translation}"
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN timesShown INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN correctCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN failedCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN triesCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN lastShownAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN lastCorrectAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN lastFailedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS app_meta (key TEXT NOT NULL, intValue INTEGER NOT NULL, PRIMARY KEY(key))")
                db.execSQL("ALTER TABLE words ADD COLUMN isReflexive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE words SET isReflexive = CASE WHEN root LIKE '%(sich)%' THEN 1 ELSE 0 END")
                db.execSQL("UPDATE words SET root = REPLACE(root, ' (sich)', '')")
                db.execSQL("UPDATE words SET root = REPLACE(root, '(sich)', '')")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN isLearned INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: WordRepository? = null

        fun getInstance(context: Context): WordRepository {
            return INSTANCE ?: synchronized(this) {
                val wrapper = WordRepository(context)
                INSTANCE = wrapper
                wrapper
            }
        }
    }
}
