package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
import android.arch.paging.DataSource
import android.arch.persistence.room.*
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter

const val BOOK_STATE_REMOVED_FROM_GDL = 1

/**
 * Table for books.
 */
@Entity(tableName = "books")
data class Book(
        @PrimaryKey
        var id: String = "",
        var title: String? = null,
        var downloaded: String? = null,
        @ColumnInfo(name = "downloaded_datetime")
        var downloadedDateTime: OffsetDateTime? = null,
        @ColumnInfo(name = "reading_level")
        var readingLevel: String? = null,
        @ColumnInfo(name = "language_link")
        var languageLink: String? = null,
        var license: String? = null,
        var author: String? = null,
        var publisher: String? = null,
        @ColumnInfo(name = "reading_position")
        var readingPosition: String? = null,
        var image: String? = null,
        var thumb: String? = null,
        @ColumnInfo(name = "epub_link")
        var ePubLink: String? = null,
        @ColumnInfo(name = "pdf_link")
        var pdfLink: String? = null,
        var description: String? = null,
        var updated: OffsetDateTime? = null,
        var created: OffsetDateTime? = null,
        var published: OffsetDateTime? = null,
        var version: Long? = null,
        var state: Int? = null
)

@Dao
abstract class BookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(book: Book): Long

    @Update
    abstract fun update(book: Book)

    @Update
    abstract fun update(book: List<Book>)

    @Delete
    abstract fun delete(books: List<Book>)

    /**
     * Insert if book doesn't already exist.
     * Update if book id exists in db.
     * When updating, we need to get the old fields that are not from the OPDS feeds
     * and copy them to the new book, so we don't overwrite any data we need.
     */
    @Transaction
    open fun insertOrUpdate(books: List<Book>) {
        books.forEach { newBook ->
            val id = insert(newBook)
            if (id == -1L) {
                getBook(newBook.id)?.let { oldBook ->
                    newBook.downloaded = oldBook.downloaded
                    newBook.downloadedDateTime= oldBook.downloadedDateTime
                    newBook.readingPosition = oldBook.readingPosition
                }
                update(newBook)
            }
        }
    }

    @Query("DELETE FROM books WHERE language_link = :languageLink AND downloaded IS NULL AND version < :currentVersion")
    abstract fun deleteOldNotDownloaded(languageLink: String, currentVersion: Long)

    @Query("SELECT * FROM books WHERE language_link = :languageLink AND  downloaded IS NOT NULL AND version < :currentVersion")
    abstract fun getOldDownloaded(languageLink: String, currentVersion: Long): List<Book>

    @Query("SELECT * FROM books WHERE id = :bookId")
    abstract fun getBook(bookId: String): Book?

    @Query("SELECT * FROM books WHERE id = :bookId")
    abstract fun getLiveBook(bookId: String): LiveData<Book>

    @Query(value = "SELECT id, title, downloaded, reading_level, books.language_link, license, author, " +
            "publisher, reading_position, image, thumb, epub_link, pdf_link, description, updated, " +
            "created, published, books.version, state " +
            "FROM books JOIN selection_book " +
            "ON books.id = selection_book.book_id " +
            "WHERE selection_book.selection_link = :selectionLink " +
            "ORDER BY selection_book.view_order")
    abstract fun getBooks(selectionLink: String): List<Book>

    @Query(value = "SELECT id, title, downloaded, reading_level, books.language_link, license, author, " +
            "publisher, reading_position, image, thumb, epub_link, pdf_link, description, updated, " +
            "created, published, books.version, state " +
            "FROM books JOIN selection_book " +
            "ON books.id = selection_book.book_id " +
            "WHERE selection_book.selection_link = :selectionLink " +
            "ORDER BY selection_book.view_order")
    abstract fun getLivePagedBooks(selectionLink: String): DataSource.Factory<Int, Book>

    @Query("SELECT * FROM books WHERE downloaded IS NOT NULL ORDER BY DATETIME(downloaded_datetime) DESC")
    abstract fun getLivePagedDownloadedBooks(): DataSource.Factory<Int, Book>

    @Query("SELECT COUNT(id) FROM books WHERE language_link = :languageLink LIMIT 1")
    abstract fun haveLanguage(languageLink: String): Boolean

    @Query("SELECT MAX(version) FROM books WHERE language_link = :languageLink")
    abstract fun maxVersion(languageLink: String): Long
}

@Entity(tableName = "categories")
data class Category(
        @PrimaryKey
        var link: String = "",
        var title: String? = "",
        @ColumnInfo(name = "language_link")
        var languageLink: String? = null,
        @ColumnInfo(name = "view_order")
        var viewOrder: Int? = null
)

@Dao
abstract class CategoryDao {
    @Insert
    abstract fun insert(categories: List<Category>)

    @Update
    abstract fun update(categories: List<Category>)

    @Delete
    abstract fun delete(categories: List<Category>)

    @Transaction
    open fun updateCategories(categoriesToInsert: List<Category>, categoriesToDelete: List<Category>, categoriesToUpdate: List<Category>) {
        insert(categoriesToInsert)
        delete(categoriesToDelete)
        update(categoriesToUpdate)
    }

    @Query("SELECT * FROM categories ORDER BY view_order LIMIT 1")
    abstract fun getFirstCategory(): Category

    @Query("SELECT * FROM categories WHERE language_link = :languageLink ORDER BY view_order")
    abstract fun getCategories(languageLink: String): List<Category>

    @Query("SELECT * FROM categories WHERE language_link = :languageLink ORDER BY view_order")
    abstract fun getLiveCategories(languageLink: String): LiveData<List<Category>>
}

/**
 * Table with the available selections (Level 1-5, New Arrivals, etc.) for each language.
 */
@Entity(tableName = "selections",
        foreignKeys = [
            ForeignKey(entity = Category::class,
                parentColumns = ["link"],
                childColumns = ["category_link"],
                onDelete = android.arch.persistence.room.ForeignKey.CASCADE)]
)
data class Selection(
        @PrimaryKey
        var link: String = "",
        var title: String? = "",
        @ColumnInfo(name = "category_link")
        var categoryLink: String? = "",
        @ColumnInfo(name = "view_order")
        var viewOrder: Int? = null
)

@Dao
abstract class SelectionDao {
    @Insert
    abstract fun insert(selection: List<Selection>)

    @Update
    abstract fun update(selection: List<Selection>)

    @Delete
    abstract fun delete(selection: List<Selection>)

    @Transaction
    open fun updateSelections(selectionsToInsert: List<Selection>, selectionsToDelete: List<Selection>, selectionsToUpdate: List<Selection>) {
        insert(selectionsToInsert)
        delete(selectionsToDelete)
        update(selectionsToUpdate)
    }

    @Query("SELECT * FROM selections WHERE link = :link")
    abstract fun getSelection(link: String): Selection

    @Query("SELECT * FROM selections WHERE category_link = :categoryLink ORDER BY view_order")
    abstract fun getSelections(categoryLink: String): List<Selection>

    @Query("SELECT * FROM selections WHERE category_link = :categoryLink ORDER BY view_order")
    abstract fun getLiveSelections(categoryLink: String): LiveData<List<Selection>>
}

/**
 * Table that keep track of which selections a book belongs to. This also keeps track of the
 * order books are shown in the UI.
 */
@Entity(tableName = "selection_book",
        primaryKeys = ["book_id", "selection_link"],
        foreignKeys = [
            ForeignKey(entity = Book::class,
                    parentColumns = ["id"],
                    childColumns = ["book_id"],
                    onDelete = ForeignKey.CASCADE),
            ForeignKey(entity = Selection::class,
                    parentColumns = ["link"],
                    childColumns = ["selection_link"],
                    onDelete = ForeignKey.CASCADE)
        ],
        indices = [
            Index(value = ["selection_link", "view_order"],
                    name = "selection_link_view_order_index",
                    unique = false)
        ])
data class SelectionBook(
        @ColumnInfo(name = "selection_link")
        var selectionLink: String = "",
        @ColumnInfo(name = "book_id")
        var bookId: String = "",
        @ColumnInfo(name = "view_order")
        var viewOrder: Int? = null,
        @ColumnInfo(name = "language_link")
        var languageLink: String? = null,
        var version: Long? = null
)

@Dao
abstract class SelectionBookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(selectionBook: SelectionBook): Long

    @Update
    abstract fun update(selectionBook: SelectionBook)

    @Transaction
    open fun insertOrUpdate(books: List<SelectionBook>) {
        books.forEach {
            val id = insert(it)
            if (id == -1L) {
                update(it)
            }
        }
    }

    @Delete
    abstract fun delete(selectionBooks: List<SelectionBook>)

    @Query("DELETE FROM selection_book WHERE language_link = :languageLink AND version < :currentVersion")
    abstract fun deleteOld(languageLink: String, currentVersion: Long)
}


/**
 * Table that maps books to downloads in the DownloadManager.
 */
@Entity(tableName = "book_downloads",
        foreignKeys = [
            ForeignKey(entity = Book::class,
                    parentColumns = ["id"],
                    childColumns = ["book_id"],
                    onDelete = ForeignKey.CASCADE)
        ])
data class BookDownload(
        @PrimaryKey
        @ColumnInfo(name = "book_id")
        var bookId: String = "",
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

    @Query("DELETE FROM book_downloads WHERE book_id in (:bookIds)")
    fun delete(bookIds: List<String>)

    @Query("SELECT * FROM book_downloads WHERE book_id = :bookId")
    fun getBookDownload(bookId: String): BookDownload?

    @Query("SELECT * FROM book_downloads WHERE download_id = :downloadId")
    fun getBookDownload(downloadId: Long): BookDownload?

}

/**
 * List of available book languages.
 *
 * This list is used by the Language Selection activity.
 */
@Entity(tableName = "languages")
data class Language(
        @PrimaryKey
        var link: String = "",
        @ColumnInfo(name = "language_name")
        var languageName: String? = null,
        var updated: OffsetDateTime? = null,
        @ColumnInfo(name = "view_order")
        var viewOrder: Int? = null
)

@Dao
abstract class LanguageDao {
    @Insert
    abstract fun insert(languages: List<Language>)

    @Delete
    abstract fun delete(languages: List<Language>)

    @Update
    abstract fun update(language: Language)

    @Update
    abstract fun update(languages: List<Language>)

    @Transaction
    open fun updateLanguages(languagesToInsert: List<Language>, languagesToDelete: List<Language>, languagesToUpdate: List<Language>) {
        insert(languagesToInsert)
        delete(languagesToDelete)
        update(languagesToUpdate)
    }

    @Query("SELECT * FROM languages WHERE link = :link")
    abstract fun getLanguage(link: String): Language?

    @Query("SELECT * FROM languages ORDER BY view_order")
    abstract fun getLanguages(): List<Language>

    @Query("SELECT * FROM languages ORDER BY view_order")
    abstract fun getLiveLanguages(): LiveData<List<Language>>
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
@Database(entities = [Selection::class, Book::class, BookDownload::class, SelectionBook::class, Category::class, Language::class], version = 1)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun selectionDao(): SelectionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun selectionBooksDao(): SelectionBookDao
    abstract fun bookDownloadDao(): BookDownloadDao
    abstract fun languageDao(): LanguageDao
}