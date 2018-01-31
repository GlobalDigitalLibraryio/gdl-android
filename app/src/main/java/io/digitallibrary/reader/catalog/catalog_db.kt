package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter

@Entity(tableName = "books", indices = [(Index(value = [("id")], name = "book_id_index", unique = true))])
data class Book(
        @PrimaryKey
        var dbid: Long? = null,
        var id: String = "",
        var title: String? = null,
        var downloaded: String? = null,
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
        var state: Int? = null
)

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(books: List<Book>)

    @Update
    fun update(book: Book)

    @Delete
    fun delete(books: List<Book>)

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun getBook(bookId: String): Book?

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun getLiveBook(bookId: String): LiveData<Book>

    @Query(value = "SELECT books.dbid, id, title, downloaded, reading_level, books.language, " +
            "license, author, publisher, reading_position, image, thumb, epub_link, description, updated, " +
            "created, published, state FROM books JOIN book_categories_map ON books.id = book_categories_map.book_id " +
            "WHERE book_categories_map.category_id = :categoryId ORDER BY book_categories_map.view_order")
    fun getBooks(categoryId: String): List<Book>

    @Query(value = "SELECT books.dbid, id, title, downloaded, reading_level, books.language, " +
            "license, author, publisher, reading_position, image, thumb, epub_link, description, updated, " +
            "created, published, state FROM books JOIN book_categories_map ON books.id = book_categories_map.book_id " +
            "WHERE book_categories_map.category_id = :categoryId ORDER BY book_categories_map.view_order")
    fun getLiveBooks(categoryId: String): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE downloaded NOT NULL")
    fun getLiveDownloadedBooks(): LiveData<List<Book>>

    @Query("SELECT COUNT(dbid) FROM books WHERE language = :language LIMIT 1")
    fun haveLanguage(language: String): Boolean
}

@Entity(tableName = "categories", indices = [(Index(value = [("id")], name = "category_id_index", unique = true))])
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

@Dao
interface CategoryDao {
    @Insert
    fun insert(category: Category)

    @Update
    fun update(category: Category)

    @Delete
    fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    fun getCategory(categoryId: String): Category

    @Query("SELECT * FROM categories WHERE language = :language ORDER BY view_order")
    fun getCategories(language: String): List<Category>

    @Query("SELECT * FROM categories WHERE language = :language ORDER BY view_order")
    fun getLiveCategories(language: String): LiveData<List<Category>>
}

@Entity(tableName = "book_categories_map")
data class BookCategoryMap(
        @PrimaryKey
        var dbid: Long? = null,
        @ColumnInfo(name = "book_id")
        var bookId: String? = null,
        @ColumnInfo(name = "category_id")
        var categoryId: String? = null,
        @ColumnInfo(name = "view_order")
        var viewOrder: Int? = null,
        var language: String? = null
)

@Dao
interface BookCategoryMapDao {
    @Insert
    fun insert(bookCategoryMap: List<BookCategoryMap>)

    @Update
    fun update(bookCategoryMap: BookCategoryMap)

    @Delete
    fun delete(bookCategoryMap: List<BookCategoryMap>)

    @Query(value = "SELECT * FROM book_categories_map WHERE category_id = :categoryId ORDER BY view_order")
    fun getBookCategoryMaps(categoryId: String): List<BookCategoryMap>
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
interface BookDownloadDao {
    @Insert
    fun insert(bookDownload: BookDownload)

    @Delete
    fun delete(bookDownload: BookDownload)

    @Query("DELETE FROM book_downloads WHERE book_id = :bookId")
    fun delete(bookId: String)

    @Query("DELETE FROM book_downloads WHERE book_id = :bookId")
    fun delete(bookId: List<String>)

    @Query("SELECT * FROM book_downloads WHERE book_id = :bookId")
    fun getBookDownload(bookId: String?): BookDownload?

    @Query("SELECT * FROM book_downloads WHERE download_id = :downloadId")
    fun getBookDownload(downloadId: Long?): BookDownload?

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
    abstract fun bookCategoryMapDao(): BookCategoryMapDao
    abstract fun bookDownloadDao(): BookDownloadDao
}