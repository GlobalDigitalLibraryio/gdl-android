package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.content.SharedPreferences
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.utilities.LanguageUtil

class CatalogViewModel : ViewModel() {

    private var categories: MediatorLiveData<List<Category>> = MediatorLiveData()
    private var dbCategories: LiveData<List<Category>>? = null
    private var books: MutableMap<String, LiveData<List<Book>>> = HashMap()
    private var book: LiveData<Book>? = null

    private var downloadedBooks: LiveData<List<Book>>? = null

    private var langListener: SharedPreferences.OnSharedPreferenceChangeListener

    init {
        langListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key === LanguageUtil.getLangPrefKey()) {
                dbCategories?.let {
                    categories.removeSource(it)
                }
                dbCategories = Gdl.getDatabase().categoryDao().getLiveCategories(LanguageUtil.getCurrentLanguage())
                dbCategories?.let {
                    categories.addSource(it, { categories.postValue(it) })
                }            }
        }
        Gdl.getSharedPrefs().registerListener(langListener)
    }

    override fun onCleared() {
        super.onCleared()
        Gdl.getSharedPrefs().unregisterListener(langListener)
    }

    fun getCategories(): LiveData<List<Category>> {
        if (dbCategories == null) {
            dbCategories = Gdl.getDatabase().categoryDao().getLiveCategories(LanguageUtil.getCurrentLanguage())
            dbCategories?.let {
                categories.addSource(it, { categories.postValue(it) })
            }
        }
        return categories
    }

    fun getBooks(categoryId: String): LiveData<List<Book>> {
        if (!books.containsKey(categoryId)) {
            books[categoryId] = Gdl.getDatabase().bookDao().getLiveBooks(categoryId)
        }
        return books[categoryId]!!
    }

    fun getCategory(categoryId: String): Category {
        return Gdl.getDatabase().categoryDao().getCategory(categoryId)
    }

    fun getBook(bookId: String): LiveData<Book> {
        if (book?.value?.id != bookId) {
            book = Gdl.getDatabase().bookDao().getLiveBook(bookId)
        }
        return book!!
    }

    fun getDownloadedBooks(): LiveData<List<Book>> {
        if (downloadedBooks == null) {
            downloadedBooks = Gdl.getDatabase().bookDao().getLiveDownloadedBooks()
        }
        return downloadedBooks!!
    }
}