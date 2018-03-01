package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
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
        @ColumnInfo(name = "reading_level")
        var readingLevel: Int? = null,
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
            "FROM books JOIN book_selections_map " +
            "ON books.id = book_selections_map.book_id " +
            "WHERE book_selections_map.selection_link = :selectionLink " +
            "ORDER BY book_selections_map.view_order")
    abstract fun getBooks(selectionLink: String): List<Book>

    @Query(value = "SELECT id, title, downloaded, reading_level, books.language_link, license, author, " +
            "publisher, reading_position, image, thumb, epub_link, pdf_link, description, updated, " +
            "created, published, books.version, state " +
            "FROM books JOIN book_selections_map " +
            "ON books.id = book_selections_map.book_id " +
            "WHERE book_selections_map.selection_link = :selectionLink " +
            "ORDER BY book_selections_map.view_order")
    abstract fun getLiveBooks(selectionLink: String): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE downloaded IS NOT NULL")
    abstract fun getLiveDownloadedBooks(): LiveData<List<Book>>

    @Query("SELECT COUNT(id) FROM books WHERE language_link = :languageLink LIMIT 1")
    abstract fun haveLanguage(languageLink: String): Boolean

    @Query("SELECT MAX(version) FROM books WHERE language_link = :languageLink")
    abstract fun maxVersion(languageLink: String): Long
}

/**
 * Table with the available selections (Level 1-5, New Arrivals, etc.) for each language.
 */
@Entity(tableName = "selections")
data class Selection(
        @PrimaryKey
        @ColumnInfo(name = "root_link")
        var rootLink: String = "",
        var title: String? = "",
        @ColumnInfo(name = "language_link")
        var languageLink: String? = null,
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

    @Query("SELECT * FROM selections WHERE root_link = :rootLink")
    abstract fun getSelection(rootLink: String): Selection

    @Query("SELECT * FROM selections WHERE language_link = :languageLink ORDER BY view_order")
    abstract fun getSelections(languageLink: String): List<Selection>

    @Query("SELECT * FROM selections WHERE language_link = :languageLink ORDER BY view_order")
    abstract fun getLiveSelections(languageLink: String): LiveData<List<Selection>>
}

/**
 * Table that keep tack of which selections a book belongs to. This also keeps track of the
 * order books are shown in the UI.
 */
@Entity(tableName = "book_selections_map",
        primaryKeys = ["book_id", "selection_link"],
        foreignKeys = [
            ForeignKey(entity = Book::class,
                    parentColumns = ["id"],
                    childColumns = ["book_id"],
                    onDelete = ForeignKey.CASCADE),
            ForeignKey(entity = Selection::class,
                    parentColumns = ["root_link"],
                    childColumns = ["selection_link"],
                    onDelete = ForeignKey.CASCADE)
        ],
        indices = [
            Index(value = ["selection_link", "view_order"],
                    name = "selection_link_view_order_index",
                    unique = false)
        ])
data class BookSelectionMap(
        @ColumnInfo(name = "book_id")
        var bookId: String = "",
        @ColumnInfo(name = "selection_link")
        var selectionLink: String = "",
        @ColumnInfo(name = "view_order")
        var viewOrder: Int? = null,
        @ColumnInfo(name = "language_link")
        var languageLink: String? = null,
        var version: Long? = null
)

@Dao
abstract class BookSelectionMapDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(bookSelectionMap: BookSelectionMap): Long

    @Update
    abstract fun update(bookSelectionMap: BookSelectionMap)

    @Transaction
    open fun insertOrUpdate(books: List<BookSelectionMap>) {
        books.forEach {
            val id = insert(it)
            if (id == -1L) {
                update(it)
            }
        }
    }

    @Delete
    abstract fun delete(bookSelectionMap: List<BookSelectionMap>)

    @Query("DELETE FROM book_selections_map WHERE language_link = :languageLink AND version < :currentVersion")
    abstract fun deleteOld(languageLink: String, currentVersion: Long)
}


/**
 * Table that maps books to downlands in the DownloadManager.
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

    @Query("DELETE FROM book_downloads WHERE book_id in (:bookId)")
    fun delete(bookId: List<String>)

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
@Database(entities = [Selection::class, Book::class, BookDownload::class, BookSelectionMap::class, Language::class], version = 1)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun selectionDao(): SelectionDao
    abstract fun bookSelectionMapDao(): BookSelectionMapDao
    abstract fun bookDownloadDao(): BookDownloadDao
    abstract fun languageDao(): LanguageDao
}