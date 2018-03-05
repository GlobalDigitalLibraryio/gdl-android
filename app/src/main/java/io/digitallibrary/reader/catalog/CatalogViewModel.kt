package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.content.SharedPreferences
import android.util.Log
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.utilities.LanguageUtil

class CatalogViewModel : ViewModel() {

    private var categories: MediatorLiveData<List<Selection>> = MediatorLiveData()
    private var dbSelections: LiveData<List<Selection>>? = null
    private var books: MutableMap<String, LiveData<List<Book>>> = HashMap()
    private var book: LiveData<Book>? = null

    private var downloadedBooks: LiveData<List<Book>>? = null

    private var langListener: SharedPreferences.OnSharedPreferenceChangeListener

    init {
        langListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key === LanguageUtil.getLangPrefKey()) {
                dbSelections?.let {
                    categories.removeSource(it)
                }
                dbSelections = Gdl.database.selectionDao().getLiveSelections(LanguageUtil.getCurrentLanguage())
                dbSelections?.let {
                    categories.addSource(it, { categories.postValue(it) })
                }
            }
        }
        Gdl.sharedPrefs.registerListener(langListener)
    }

    override fun onCleared() {
        super.onCleared()
        Gdl.sharedPrefs.unregisterListener(langListener)
    }

    fun getSelections(): LiveData<List<Selection>> {
        if (dbSelections == null) {
            dbSelections = Gdl.database.selectionDao().getLiveSelections(LanguageUtil.getCurrentLanguage())
            dbSelections?.let {
                categories.addSource(it, { categories.postValue(it) })
            }
        }
        return categories
    }

    fun getBooks(selectionLink: String): LiveData<List<Book>> {
        if (!books.containsKey(selectionLink)) {
            books[selectionLink] = Gdl.database.bookDao().getLiveBooks(selectionLink)
        }
        return books[selectionLink]!!
    }

    fun getSelection(selectionLink: String): Selection {
        return Gdl.database.selectionDao().getSelection(selectionLink)
    }

    fun getBook(bookId: String): LiveData<Book> {
        if (book?.value?.id != bookId) {
            book = Gdl.database.bookDao().getLiveBook(bookId)
        }
        return book!!
    }

    fun getDownloadedBooks(): LiveData<List<Book>> {
        if (downloadedBooks == null) {
            downloadedBooks = Gdl.database.bookDao().getLiveDownloadedBooks()
        }
        return downloadedBooks!!
    }
}