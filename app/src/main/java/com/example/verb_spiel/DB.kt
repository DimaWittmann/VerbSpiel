package com.example.verb_spiel

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


@Database(entities = [Word::class, AppMeta::class], version = 3)
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
    val translation: String,
    val example: String,
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

fun isRetiredWord(word: Word): Boolean {
    return if (word.failedCount == 0) {
        word.correctCount >= 2
    } else {
        word.correctCount >= 4
    }
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words")
    suspend fun getAllWords(): List<Word>

    @Query("SELECT * FROM words WHERE failedCount > 0 ORDER BY lastFailedAt DESC LIMIT :limit")
    suspend fun getRecentFailures(limit: Int = 20): List<Word>

    @Query("SELECT * FROM words WHERE correctCount > 0 ORDER BY lastCorrectAt DESC LIMIT :limit")
    suspend fun getRecentCorrect(limit: Int = 20): List<Word>

    @Query("SELECT * FROM words WHERE correctCount > 0 ORDER BY correctCount DESC LIMIT :limit")
    suspend fun getTopCorrect(limit: Int = 20): List<Word>

    @Query("SELECT * FROM words WHERE failedCount > 0 ORDER BY failedCount DESC LIMIT :limit")
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    private val wordDao = db.wordDao()
    private val metaDao = db.metaDao()

    suspend fun getAllWords(): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getAllWords()
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
            .filter { isRetiredWord(it) }
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
        val existingMap = existing.associateBy { "${it.prefix};${it.root}" }
        val newMap = newWords.associateBy { "${it.prefix};${it.root}" }

        val toDelete = existing.filter { !newMap.containsKey("${it.prefix};${it.root}") }
        if (toDelete.isNotEmpty()) {
            wordDao.deleteWordsByIds(toDelete.map { it.id })
        }

        val toInsert = newWords.filter { !existingMap.containsKey("${it.prefix};${it.root}") }
        if (toInsert.isNotEmpty()) {
            wordDao.insertWords(toInsert)
        }

        val toUpdate = existing.mapNotNull { old ->
            val fresh = newMap["${old.prefix};${old.root}"] ?: return@mapNotNull null
            if (old.translation == fresh.translation && old.example == fresh.example) return@mapNotNull null
            old.copy(
                prefix = fresh.prefix,
                root = fresh.root,
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
