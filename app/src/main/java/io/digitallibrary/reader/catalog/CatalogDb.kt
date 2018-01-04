package io.digitallibrary.reader.catalog

import android.arch.persistence.room.*
import java.time.OffsetDateTime

@Entity(tableName = "books",
        foreignKeys = [(ForeignKey(entity = Category::class, parentColumns = arrayOf("id"), childColumns = arrayOf("category_id")))],
        indices= [(Index(value = ["category_id"], name = "category_id_index", unique = true))])
data class Book(
    @PrimaryKey
    var dbid: Long? = null,
    var id: String = "",
    var title: String = "",
    var downloaded: Boolean = false,
    var readingLevel: Int? = null,
    var license: String = "",
    var author: String = "",
    var publisher: String = "",
    var readingPosition: String = "",
    var cover: String = "",
    @ColumnInfo(name = "category_id")
    var categoryId: Long? = null
)

@Dao
interface BookDao {
    @Insert
    fun insert(book: Book)

    @Update
    fun update(book: Book)

    @Delete
    fun delete(book: Book)

    @Query("SELECT * FROM books")
    fun getAllBooks(): List<Book>
}


@Entity(tableName = "categories", indices= [(Index(value = ["id"], name = "id_index", unique = true))])
data class Category(
    @PrimaryKey
    var dbid: Long? = null,
    var id: String = "",
    var title: String = "",
    var link: String = "",
    var updated: String = "",
    var description: String = ""
)

@Dao
interface CategoryDao {
    @Insert
    fun insert(category: Category)

    @Update
    fun update(category: Category)

    @Delete
    fun delete(category: Category)

    @Query("SELECT * FROM categories")
    fun getAllCategories(): List<Category>
}

@Database(entities = arrayOf(Category::class, Book::class), version = 1)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun categoryDao(): CategoryDao
}