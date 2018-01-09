package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*

@Entity(tableName = "books")
data class Book(
        @PrimaryKey
        var dbid: Long? = null,
        var id: String = "",
        var title: String? = null,
        var downloaded: Boolean = false,
        var readingLevel: Int? = null,
        var license: String? = null,
        var author: String? = null,
        var publisher: String? = null,
        var readingPosition: String? = null,
        var cover: String? = null,
        var description: String? = null,
        @ColumnInfo(name = "category_id")
        var categoryId: String? = null
)

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

    @Query("SELECT * FROM books")
    fun getAllBooks(): List<Book>

    @Query("SELECT * FROM books WHERE category_id = :arg0")
    fun getBooks(categoryId: String): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE id = :arg0")
    fun getBook(bookId: String): Book
}


@Entity(tableName = "categories")
data class Category(
    @PrimaryKey
    var dbid: Long? = null,
    var id: String = "",
    var title: String? = "",
    var link: String? = "",
    var updated: String? = "",
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

    @Query("DELETE FROM categories")
    fun deleteAll()

    @Query("SELECT * FROM categories")
    fun getAllCategories(): LiveData<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :arg0")
    fun getCategory(categoryId: String): Category
}

@Database(entities = arrayOf(Category::class, Book::class), version = 1)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun categoryDao(): CategoryDao
}