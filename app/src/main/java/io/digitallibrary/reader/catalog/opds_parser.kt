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
import org.simpleframework.xml.strategy.Strategy
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

const val OPDS_PARSE_DONE = "opds-fetch-done"

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
            val strategy: Strategy = AnnotationStrategy()
            val serializer: Serializer = Persister(strategy)

            val retrofit = Retrofit.Builder()
                    .baseUrl("https://opds.staging.digitallibrary.io/")
                    .addConverterFactory(SimpleXmlConverterFactory.create(serializer))
                    .build()
            return retrofit.create(OpdsParser::class.java)
        }
    }
}

fun updateCategories(categories: List<Feed.Entry>?, language: String): List<Category> {
    val dao = Gdl.getDatabase().categoryDao()
    val oldCategoryMap: MutableMap<String, Category> = HashMap()
    dao.getCategories(language).associateByTo(oldCategoryMap) { it.id }

    val categoriesToUpdate: MutableList<Category> = ArrayList(10)

    Log.i(TAG, "oldCatMap is " + oldCategoryMap)

    val categoriesList = categories.orEmpty().mapIndexed { i, it ->
        Category(
                id = it.id ?: "missing",
                title = it.title,
                link = it.links.orEmpty().firstOrNull { it.type.equals(ACQUISITION_TYPE_STRING) }?.href,
                language = language,
                viewOrder = i,
                updated = it.updated,
                description = it.summary
        )
    }

    categoriesList.forEach {
        val old = oldCategoryMap.get(it.id)
        if (old != null) {
            it.dbid = old.dbid
            dao.update(it)
            oldCategoryMap.remove(it.id)
            val oldTime = old.updated

            if (oldTime != null && it.updated?.isAfter(old.updated) == true) {
                categoriesToUpdate.add(it)
            }
        } else {
            categoriesToUpdate.add(it)
            dao.insert(it)
        }
    }

    oldCategoryMap.values.forEach {
        dao.delete(it)
    }

    return categoriesToUpdate
}

fun saveBooks(category: Category, books: List<Feed.Entry>?, language: String) {
    val dao = Gdl.getDatabase().bookDao()
    dao.insertList(
            books.orEmpty().map {
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
                        readingPosition = null,
                        image = it.links.orEmpty().firstOrNull { it.rel == AQ_IMAGE_LINK_REL }?.href,
                        thumb = it.links.orEmpty().firstOrNull { it.rel == AQ_IMAGE_THUMB_LINK_REL }?.href,
                        ePubLink = it.links.orEmpty().firstOrNull { it.type?.startsWith(EPUB_TYPE) == true }?.href,
                        description = it.summary,
                        updated = it.updated,
                        created = it.created,
                        published = it.published,
                        categoryId = category.id
                )
            }
    )
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

        val nav: Response<Feed> =
                try {
                    parser.getNavRoot(lang).execute()
                } catch (e: Exception) {
                    Log.e(TAG, "getNavRoot for $lang failed")
                    e.printStackTrace()
                    null
                } ?: return@launch

        val categories = updateCategories(nav.body()?.entries, lang)

        val jobs: MutableList<Deferred<Unit>> = ArrayList(10)


        if (Gdl.getDatabase().bookDao().haveLanguage(lang)) {
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

                    saveBooks(it, aq.body()?.entries, lang)
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
                        saveBooks(it.first, books.subList(0, 6), lang)
                    } else {
                        saveBooks(it.first, books, lang)
                    }
                }
            }

            todoLater.forEach {
                jobs.add(async {
                    saveBooks(it.first, it.second, lang)
                })
            }
        }

        jobs.forEach { it.await() }

        val intent = Intent(OPDS_PARSE_DONE)
        LocalBroadcastManager.getInstance(Gdl.getAppContext()).sendBroadcast(intent)
        fetchFeed(true)
    }
}