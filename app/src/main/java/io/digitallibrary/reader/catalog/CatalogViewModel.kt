package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import io.digitallibrary.reader.Gdl

class CatalogViewModel : ViewModel() {

    private var categories: LiveData<List<Category>>? = null
    private var books: MutableMap<String, LiveData<List<Book>>> = HashMap()

    fun getCategories(): LiveData<List<Category>> {
        if (categories == null) {
            categories = Gdl.getDatabase().categoryDao().getAllCategories()
        }
        return categories as LiveData<List<Category>>
    }

    fun getBooks(categoryId: String): LiveData<List<Book>> {
        if (!books.containsKey(categoryId)) {
            books[categoryId] = Gdl.getDatabase().bookDao().getBooks(categoryId)
        }
        return books[categoryId]!!
    }

    fun getCategory(categoryId: String): Category {
        return Gdl.getDatabase().categoryDao().getCategory(categoryId)
    }

    fun getBook(bookId: String): Book {
        return Gdl.getDatabase().bookDao().getBook(bookId)
    }
}