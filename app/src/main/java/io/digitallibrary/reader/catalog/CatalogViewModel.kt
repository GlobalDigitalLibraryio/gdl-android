package io.digitallibrary.reader.catalog

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import android.content.SharedPreferences
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.utilities.SelectionsUtil

class CatalogViewModel : ViewModel() {

    private val currentCategorySelections: MediatorLiveData<List<Selection>> = MediatorLiveData()
    private var dbCurrentCategorySelections: LiveData<List<Selection>>? = null
    private var emptySelections: MutableLiveData<List<Selection>> = MutableLiveData()

    private val categories: MediatorLiveData<List<Category>> = MediatorLiveData()
    private var dbCategories: LiveData<List<Category>>? = null



    private val books: MutableMap<String, LiveData<PagedList<Book>>> = HashMap()
    private var book: LiveData<Book>? = null
    private var bookContributors: LiveData<List<Contributor>>? = null

    val downloadedBooks: LiveData<PagedList<Book>> by lazy {
        LivePagedListBuilder(Gdl.database.bookDao().getLivePagedDownloadedBooks(), 40).build()
    }

    private fun updateCategorySelections() {
        dbCurrentCategorySelections?.let { currentCategorySelections.removeSource(it) }
        val cat = SelectionsUtil.getCurrentCategoryLink()
        dbCurrentCategorySelections = if (cat != null) {
            Gdl.database.selectionDao().getLiveSelections(cat)
        } else {
            null
        }
        dbCurrentCategorySelections?.let { currentCategorySelections.addSource(it, { currentCategorySelections.postValue(it) }) }
        if (dbCurrentCategorySelections == null) {
            // removeSource does not update the observers
            emptySelections.value = emptyList()
        }
    }

    private fun updateCategories() {
        dbCategories?.let { categories.removeSource(it) }
        val lang = SelectionsUtil.getCurrentLanguageLink()
        dbCategories = if (lang != null) {
            Gdl.database.categoryDao().getLiveCategories(lang)
        } else {
            null
        }
        dbCategories?.let { categories.addSource(it, { categories.postValue(it) }) }
    }

    private var langAndCategoryChangeListener: SharedPreferences.OnSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key === SelectionsUtil.getCategoryPrefKey()) {
            updateCategorySelections()
        } else if (key === SelectionsUtil.getLangPrefKey()) {
            updateCategories()
        }
    }

    init {
        emptySelections.value = emptyList()
        currentCategorySelections.addSource(emptySelections, { currentCategorySelections.postValue(emptySelections.value) })
        updateCategorySelections()
        updateCategories()
        Gdl.sharedPrefs.registerListener(langAndCategoryChangeListener)
    }

    override fun onCleared() {
        super.onCleared()
        Gdl.sharedPrefs.unregisterListener(langAndCategoryChangeListener)
    }

    fun getCurrentCategorySelections(): LiveData<List<Selection>> {
        return currentCategorySelections
    }

    fun getCategories(): LiveData<List<Category>> {
        return categories
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

    fun getBookContributors(bookId: String): LiveData<List<Contributor>> {
        if (bookContributors?.value?.get(0)?.bookId != bookId) {
            bookContributors = Gdl.database.bookDao().getLiveContributors(bookId)
        }
        return bookContributors!!
    }
}
