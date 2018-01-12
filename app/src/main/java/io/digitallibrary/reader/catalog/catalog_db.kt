package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.content.Context
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.File

@Entity(tableName = "books")
data class Book(
        @PrimaryKey
        var dbid: Long? = null,
        var id: String = "",
        var title: String? = null,
        var downloaded: Boolean = false,
        @ColumnInfo(name = "reading_level")
        var readingLevel: Int? = null,
        var language: String? = null,
        var license: String? = null,
        var author: String? = null,
        var publisher: String? = null,
        @ColumnInfo(name = "reading_position")
        var readingPosition: String? = null,
        var image: String? = null,
        var thumb: String? = null,
        @ColumnInfo(name = "epub_link")
        var ePubLink: String? = null,
        var description: String? = null,
        var updated: OffsetDateTime? = null,
        var created: OffsetDateTime? = null,
        var published: OffsetDateTime? = null,
        @ColumnInfo(name = "category_id")
        var categoryId: String? = null
) {
    fun getBookFilePath(): String {
        return "/books/$id.epub"
    }

    fun getBookFile(context: Context): File {
        return context.getExternalFilesDir(getBookFilePath())
    }
}

@Dao
interface BookDao {
    @Insert
    fun insert(book: Book)

    @Update
    fun update(book: Book)

    @Delete
    fun delete(book: Book)

    @Query("DELETE FROM books")
    fun deleteAll()

    @Query("SELECT * FROM books WHERE downloaded = 1")
    fun getDownloadedBooks(): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE category_id = :arg0")
    fun getBooks(categoryId: String): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE id = :arg0")
    fun getBook(bookId: String): Book

    @Query("SELECT * FROM books WHERE id IN (SELECT book_id FROM book_downloads WHERE download_id = :arg0)")
    fun getBookFromDownloadId(downloadId: String): Book
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey
    var dbid: Long? = null,
    var id: String = "",
    var title: String? = "",
    var link: String? = "",
    var language: String? = null,
    @ColumnInfo(name = "view_order")
    var viewOrder: Int? = null,
    var updated: OffsetDateTime? = null,
    var description: String? = ""
)

@Entity()
data class BookCategoryMap(
        @PrimaryKey
        var dbid: Long? = null,
        @ColumnInfo(name = "book_id")
        var bookId: String? = null,
        @ColumnInfo(name = "category_id")
        var categoryId: String? = null
)

@Dao
interface CategoryDao {
    @Insert
    fun insert(category: Category)

    @Update
    fun update(category: Category)

    @Delete
    fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE language = :arg0 ORDER BY view_order")
    fun getCategories(language: String): List<Category>

    @Query("SELECT * FROM categories WHERE language = :arg0 ORDER BY view_order")
    fun getLiveCategories(language: String): LiveData<List<Category>>

    @Query("DELETE FROM categories")
    fun deleteAll()

    @Query("SELECT * FROM categories")
    fun getAllCategories(): LiveData<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :arg0")
    fun getCategory(categoryId: String): Category
}

@Entity(tableName = "book_downloads")
data class BookDownload(
        @PrimaryKey
        var dbid: Long? = null,
        @ColumnInfo(name = "book_id")
        var bookId: String? = null,
        @ColumnInfo(name = "download_id")
        var downloadId: Long? = null
)

@Dao
interface BookDownloadDao{
    @Insert
    fun insert(bookDownload: BookDownload)

    @Delete
    fun delete(bookDownload: BookDownload)

    @Query("DELETE FROM book_downloads WHERE book_id = :arg0")
    fun delete(bookId: String)

    @Query("SELECT * FROM book_downloads WHERE book_id = :arg0")
    fun getBookDownload(bookId: String?): BookDownload?
}

object TimeTypeConverters {
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    @TypeConverter
    @JvmStatic
    fun toOffsetDateTime(value: String?): OffsetDateTime? {
        return value?.let {
            return formatter.parse(value, OffsetDateTime::from)
        }
    }

    @TypeConverter
    @JvmStatic
    fun fromOffsetDateTime(date: OffsetDateTime?): String? {
        return date?.format(formatter)
    }
}

@TypeConverters(TimeTypeConverters::class)
@Database(entities = [(Category::class), (Book::class), (BookDownload::class), (BookCategoryMap::class)], version = 1)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun categoryDao(): CategoryDao
    abstract fun bookDownloadDao(): BookDownloadDao
}