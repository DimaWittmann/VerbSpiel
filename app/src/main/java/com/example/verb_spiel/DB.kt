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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Database(entities = [Word::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
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

    @Query("SELECT COUNT(*) FROM words")
    suspend fun getCount(): Int

    // Optionally, query a specific word by id
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Int): Word?
}

class WordRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java, "my-app-database"
    )
        .fallbackToDestructiveMigration()
        .build()

    private val wordDao = db.wordDao()

    suspend fun getAllWords(): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getAllWords()
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
