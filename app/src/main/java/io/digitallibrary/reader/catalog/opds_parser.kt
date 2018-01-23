package io.digitallibrary.reader.catalog

import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import io.digitallibrary.reader.Gdl
import io.digitallibrary.reader.utilities.LanguageUtil
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.simpleframework.xml.*
import org.simpleframework.xml.convert.AnnotationStrategy
import org.simpleframework.xml.convert.Convert
import org.simpleframework.xml.convert.Converter
import org.simpleframework.xml.core.Persister
import org.simpleframework.xml.stream.InputNode
import org.simpleframework.xml.stream.OutputNode
import org.threeten.bp.OffsetDateTime
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url
import java.util.*
import kotlin.collections.ArrayList

private const val TAG = "OpdsParser"

const val OPDS_PARSE_DONE = "opds-fetchOpdsFeed-done"

private const val ACQUISITION_TYPE_STRING = "application/atom+xml;profile=opds-catalog;kind=acquisition"
private const val AQ_IMAGE_LINK_REL = "http://opds-spec.org/image"
private const val AQ_IMAGE_THUMB_LINK_REL = "http://opds-spec.org/image/thumbnail"
private const val EPUB_TYPE = "application/epub"

object DateConverter : Converter<OffsetDateTime> {
    override fun read(node: InputNode): OffsetDateTime? {
        return TimeTypeConverters.toOffsetDateTime(node.value)
    }

    override fun write(node: OutputNode, date: OffsetDateTime) {
        node.value = TimeTypeConverters.fromOffsetDateTime(date)
    }
}

@Root(strict = false)
class Feed {
    @set:Element
    @get:Element
    var id: String? = null

    @set:Element
    @get:Element
    var title: String? = null

    @set:Element
    @get:Element
    @set:Convert(DateConverter::class)
    @get:Convert(DateConverter::class)
    var updated: OffsetDateTime? = null

    @set:ElementList(inline = true, entry = "link")
    @get:ElementList(inline = true, entry = "link")
    var links: List<Link>? = null

    @set:ElementList(inline = true, entry = "entry")
    @get:ElementList(inline = true, entry = "entry")
    var entries: List<Entry>? = null

    @Root(strict = false, name = "link")
    class Link {
        @set:Attribute
        @get:Attribute
        var href: String? = null

        @set:Attribute(required = false)
        @get:Attribute(required = false)
        var type: String? = null

        @set:Attribute(required = false)
        @get:Attribute(required = false)
        var rel: String? = null

        @set:Attribute(required = false)
        @get:Attribute(required = false)
        var title: String? = null
    }

    @Root(strict = false, name = "entry")
    class Entry {
        @set:Element
        @get:Element
        var id: String? = null

        @set:Element
        @get:Element
        var title: String? = null

        @set:ElementList(required = false)
        @get:ElementList(required = false)
        var author: List<String>? = null

        @Namespace(reference = "http://purl.org/dc/terms/")
        @set:Element(required = false)
        @get:Element(required = false)
        var license: String? = null

        @Namespace(reference = "http://purl.org/dc/terms/")
        @set:Element(required = false)
        @get:Element(required = false)
        var publisher: String? = null

        @Namespace(reference = "http://purl.org/dc/terms/")
        @set:Element(required = false)
        @get:Element(required = false)
        @set:Convert(DateConverter::class)
        @get:Convert(DateConverter::class)
        var created: OffsetDateTime? = null

        @set:Element
        @get:Element
        @set:Convert(DateConverter::class)
        @get:Convert(DateConverter::class)
        var updated: OffsetDateTime? = null

        @set:Element(required = false)
        @get:Element(required = false)
        @set:Convert(DateConverter::class)
        @get:Convert(DateConverter::class)
        var published: OffsetDateTime? = null

        @Namespace(reference = "http://purl.org/dcx/lrmi-terms/")
        @set:Element(required = false)
        @get:Element(required = false)
        var educationalAlignment: EducationalAlignment? = null

        @set:Element(required = false)
        @get:Element(required = false)
        var summary: String? = null

        @set:ElementList(inline = true, entry = "link")
        @get:ElementList(inline = true, entry = "link")
        var links: List<Link>? = null

        @Root(name = "educationalAlignment")
        class EducationalAlignment {
            @set:Attribute
            @get:Attribute
            var alignmentType: String? = null

            @set:Attribute
            @get:Attribute
            var targetName: String? = null
        }
    }
}

interface OpdsParser {
    @GET("{lang}/nav.xml")
    fun getNavRoot(@Path("lang") lang: String): Call<Feed>

    @GET
    fun getAcquisitionFeed(@Url url: String?): Call<Feed>

    companion object Factory {
        fun create(): OpdsParser {

            // This makes the Convert annotation work
            val strategy = AnnotationStrategy()
            val serializer = Persister(strategy)

            val retrofit = Retrofit.Builder()
                    .baseUrl("https://opds.staging.digitallibrary.io/")
                    .addConverterFactory(SimpleXmlConverterFactory.create(serializer))
                    .build()
            return retrofit.create(OpdsParser::class.java)
        }
    }
}

fun updateCategories(categories: List<Feed.Entry>?, language: String): List<Category> {
    val dao = Gdl.database.categoryDao()
    val oldCategoryMap: MutableMap<String, Category> = HashMap()
    dao.getCategories(language).associateByTo(oldCategoryMap) { it.id }

    val categoriesToUpdate: MutableList<Category> = ArrayList(10)

    categories.orEmpty().mapIndexed { index, entry ->
        Category(
                id = entry.id ?: "missing",
                title = entry.title,
                link = entry.links.orEmpty().firstOrNull { it.type.equals(ACQUISITION_TYPE_STRING) }?.href,
                language = language,
                viewOrder = index,
                updated = entry.updated,
                description = entry.summary
        )
    }.forEach {
        val old = oldCategoryMap[it.id]
        if (old != null) {
            it.dbid = old.dbid
            val oldTime = old.updated

            if (it != old) {
                dao.update(it)
                if (oldTime != null && it.updated?.isAfter(old.updated) == true) {
                    categoriesToUpdate.add(it)
                }
            }

            oldCategoryMap.remove(it.id)
        } else {
            categoriesToUpdate.add(it)
            dao.insert(it)
        }
    }

    oldCategoryMap.values.forEach {
        val booksFromDeletedCategory = Gdl.database.bookDao().getBooks(it.id)
        Gdl.database.bookDao().delete(booksFromDeletedCategory.filter { it.downloaded == null })
        dao.delete(it)
    }

    return categoriesToUpdate
}

fun updateBooks(category: Category, books: List<Feed.Entry>, language: String, checkDb: Boolean = false, startAtIndex: Int = 0) {
    if (checkDb) {
        val oldBooks: MutableMap<String, Book> = HashMap()
        Gdl.database.bookDao().getBooks(category.id).associateByTo(oldBooks) { it.id }

        val oldBookCategoryMap: MutableMap<String, BookCategoryMap> = HashMap()
        Gdl.database.bookCategoryMapDao().getBookCategoryMaps(category.id).associateByTo(oldBookCategoryMap) { it.bookId!! }

        val booksToInsert: MutableList<Book> = ArrayList(books.size)
        val bookCategoryMapsToInsert: MutableList<BookCategoryMap> = ArrayList(books.size)

        books.map {
            Book(
                    id = it.id ?: "missing",
                    title = it.title,
                    downloaded = null,
                    readingLevel = it.educationalAlignment?.targetName?.toInt(),
                    language = language,
                    license = it.license,

                    /*
                     * The simple XML parser parses:
                     *   <author>
                     *       <name/>
                     *   </author>
                     * to:
                     *   [null]
                     * which joinToString turned into the String "null" by default (bug?)
                     */
                    author = it.author?.joinToString(transform = { str: String? -> str ?: "" }),
                    publisher = it.publisher,
                    image = it.links.orEmpty().firstOrNull { it.rel == AQ_IMAGE_LINK_REL }?.href,
                    thumb = it.links.orEmpty().firstOrNull { it.rel == AQ_IMAGE_THUMB_LINK_REL }?.href,
                    ePubLink = it.links.orEmpty().firstOrNull { it.type?.startsWith(EPUB_TYPE) == true }?.href,
                    description = it.summary,
                    updated = it.updated,
                    created = it.created,
                    published = it.published
            )
        }.forEachIndexed { index, book ->
            val old = oldBooks[book.id]
            if (old != null) {
                book.dbid = old.dbid
                if (book != old) {
                    Gdl.database.bookDao().update(book)
                }
                val oldMapping = oldBookCategoryMap[book.id]

                if (oldMapping != null) {
                    if (oldMapping.viewOrder != index) {
                        oldMapping.viewOrder = index
                        Gdl.database.bookCategoryMapDao().update(oldMapping)
                    }
                } else {
                    Log.e(TAG, "Have old book, but no BookCategoryMap. This should be impossible.")
                    bookCategoryMapsToInsert.add(
                            BookCategoryMap(
                                    bookId = book.id,
                                    categoryId = category.id,
                                    viewOrder = index,
                                    language = language
                            )
                    )
                }
                oldBooks.remove(book.id)
                oldBookCategoryMap.remove(book.id)
            } else {
                booksToInsert.add(book)
                bookCategoryMapsToInsert.add(
                        BookCategoryMap(
                                bookId = book.id,
                                categoryId = category.id,
                                viewOrder = index,
                                language = language
                        )
                )
            }
        }

        Gdl.database.bookDao().insertList(booksToInsert)
        // We do not delete downloaded books
        Gdl.database.bookDao().delete(oldBooks.values.filter { it.downloaded == null }.toList())
        Gdl.database.bookCategoryMapDao().insert(bookCategoryMapsToInsert)
        Gdl.database.bookCategoryMapDao().delete(oldBookCategoryMap.values.toList())
    } else {
        Gdl.database.bookDao().insertList(
                books.map {
                    Book(
                            id = it.id ?: "missing",
                            title = it.title,
                            downloaded = null,
                            readingLevel = it.educationalAlignment?.targetName?.toInt(),
                            language = language,
                            license = it.license,

                            /*
                             * The simple XML parser parses:
                             *   <author>
                             *       <name/>
                             *   </author>
                             * to:
                             *   [null]
                             * which joinToString turned into the String "null" by default (bug?)
                             */
                            author = it.author?.joinToString(transform = { str: String? -> str ?: "" }),
                            publisher = it.publisher,
                            image = it.links.orEmpty().firstOrNull { it.rel == AQ_IMAGE_LINK_REL }?.href,
                            thumb = it.links.orEmpty().firstOrNull { it.rel == AQ_IMAGE_THUMB_LINK_REL }?.href,
                            ePubLink = it.links.orEmpty().firstOrNull { it.type?.startsWith(EPUB_TYPE) == true }?.href,
                            description = it.summary,
                            updated = it.updated,
                            created = it.created,
                            published = it.published
                    )
                })
        Gdl.database.bookCategoryMapDao().insert(
                books.mapIndexed { index, entry ->
                    BookCategoryMap(
                            bookId = entry.id,
                            categoryId = category.id,
                            viewOrder = index + startAtIndex,
                            language = language
                    )
                })
    }
}

val fetchJobLock = Any()
var currentLang: String? = null
val fetchJobs: Deque<String> = LinkedList()


fun fetchFeed(recursive: Boolean = false) {
    synchronized(fetchJobLock) {
        if (recursive) {
            if (fetchJobs.isEmpty()) {
                currentLang = null
                return
            } else {
                currentLang = fetchJobs.removeFirst()
                // This now starts
            }
        } else {
            val newLang = LanguageUtil.getCurrentLanguage()

            if (currentLang == null) {
                // Not running
                if (fetchJobs.isEmpty()) {
                    currentLang = newLang
                } else {
                    Log.e(TAG, "This shouldn't happen")
                    currentLang = fetchJobs.removeFirst()
                    fetchJobs.add(newLang)
                }
                // Start
            } else {
                // We are running
                if (newLang == currentLang || fetchJobs.contains(newLang)) {
                    // Language already added
                    return
                } else {
                    fetchJobs.add(newLang)
                }
                // Do nothing, next lang will be started by fetchFeed(true)
                return
            }
        }
    }


    launch(CommonPool) {
        val parser = OpdsParser.create()
        val lang = LanguageUtil.getCurrentLanguage()

        Log.i(TAG, "Fetching $lang")

        val nav: Response<Feed>? =
                try {
                    parser.getNavRoot(lang).execute()
                } catch (e: Exception) {
                    Log.e(TAG, "getNavRoot for $lang failed")
                    e.printStackTrace()
                    null
                }


        val categories: List<Category> = if (nav != null) { updateCategories(nav.body()?.entries, lang) } else { ArrayList() }

        val jobs: MutableList<Deferred<Unit>> = ArrayList(10)

        if (Gdl.database.bookDao().haveLanguage(lang)) {
            categories.forEach {
                jobs.add(async(CommonPool) {
                    val aq: Response<Feed> =
                            try {
                                parser.getAcquisitionFeed(it.link).execute()
                            } catch (e: Exception) {
                                Log.e(TAG, "getAcquisitionFeed for " + it.title + " failed")
                                e.printStackTrace()
                                null
                            } ?: return@async

                    updateBooks(it, aq.body()?.entries ?: emptyList(), lang, checkDb = true)
                })
            }
        } else {
            // Do the 6 first books in order from top category and down, then the rest
            // TODO: calculate visible books

            val aqs: MutableList<Pair<Category, Deferred<Response<Feed>?>>> = ArrayList(10)

            categories.forEach {
                aqs.add(Pair(it, async(CommonPool) {
                        try {
                            parser.getAcquisitionFeed(it.link).execute()
                        } catch (e: Exception) {
                            Log.e(TAG, "getAcquisitionFeed for " + it.title + " failed")
                            e.printStackTrace()
                            null
                        }
                }))
            }

            val todoLater: MutableList<Pair<Category, List<Feed.Entry>>> = ArrayList(10)

            aqs.forEach {
                val aq = it.second.await()
                val books: List<Feed.Entry>? = aq?.body()?.entries
                if (books != null) {
                    if (books.size > 6) {
                        todoLater.add(Pair(it.first, books.subList(6, books.size)))
                        updateBooks(it.first, books.subList(0, 6), lang)
                    } else {
                        updateBooks(it.first, books, lang)
                    }
                }
            }

            todoLater.forEach {
                jobs.add(async {
                    updateBooks(it.first, it.second, lang, startAtIndex = 6)
                })
            }
        }

        jobs.forEach { it.await() }

        val intent = Intent(OPDS_PARSE_DONE)
        LocalBroadcastManager.getInstance(Gdl.appContext).sendBroadcast(intent)
        fetchFeed(true)
    }
}