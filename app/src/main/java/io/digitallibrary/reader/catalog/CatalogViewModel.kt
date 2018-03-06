package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import android.content.SharedPreferences
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.utilities.LanguageUtil

class CatalogViewModel : ViewModel() {

    private var selections: MediatorLiveData<List<Selection>> = MediatorLiveData()
    private var dbSelections: LiveData<List<Selection>>? = null

    private var books: MutableMap<String, LiveData<PagedList<Book>>> = HashMap()
    private var book: LiveData<Book>? = null

    val downloadedBooks: LiveData<PagedList<Book>> by lazy {
        LivePagedListBuilder(Gdl.database.bookDao().getLivePagedDownloadedBooks(), 40).build()
    }

    private var langListener: SharedPreferences.OnSharedPreferenceChangeListener

    init {
        langListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key === LanguageUtil.getLangPrefKey()) {
                dbSelections?.let {
                    selections.removeSource(it)
                }
                dbSelections = Gdl.database.selectionDao().getLiveSelections(LanguageUtil.getCurrentLanguage())
                dbSelections?.let {
                    selections.addSource(it, { selections.postValue(it) })
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
                selections.addSource(it, { selections.postValue(it) })
            }
        }
        return selections
    }

    fun getBooks(selectionLink: String): LiveData<PagedList<Book>> {
        if (!books.containsKey(selectionLink)) {
            books[selectionLink] = LivePagedListBuilder(Gdl.database.bookDao().getLivePagedBooks(selectionLink), 20).build()
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
}