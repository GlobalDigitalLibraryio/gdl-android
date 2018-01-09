package io.digitallibrary.reader.catalog

import com.ethlo.time.FastInternetDateTimeUtil
import io.digitallibrary.reader.Gdl
import kotlinx.coroutines.experimental.launch
import org.simpleframework.xml.*
import org.simpleframework.xml.convert.AnnotationStrategy
import org.simpleframework.xml.convert.Convert
import org.simpleframework.xml.convert.Converter
import org.simpleframework.xml.core.Persister
import org.simpleframework.xml.strategy.Strategy
import org.simpleframework.xml.stream.InputNode
import org.simpleframework.xml.stream.OutputNode
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url
import ru.gildor.coroutines.retrofit.await
import java.time.OffsetDateTime

const val TAG = "KotCoRutTest"

const val ACQUISITION_TYPE_STRING = "application/atom+xml;profile=opds-catalog;kind=acquisition"
const val AQ_IMAGE_LINK_REL = "http://opds-spec.org/image"
const val AQ_IMAGE_THUMB_REL = "http://opds-spec.org/image/thumbnail"

class DateConverter : Converter<OffsetDateTime> {
    companion object {
        val itu: FastInternetDateTimeUtil = FastInternetDateTimeUtil()
    }

    override fun read(node: InputNode): OffsetDateTime {
        val dateAsString = node.value
        return itu.parse(dateAsString)
    }

    override fun write(node: OutputNode, date: OffsetDateTime) {
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
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build()
            return retrofit.create(OpdsParser::class.java)
        }
    }
}

fun updateCategories(categories: List<Feed.Entry>?): List<Category> {
    val db = Gdl.getDatabase()
    db.categoryDao().deleteAll()
    val categoriesList = categories.orEmpty().map {
        Category(
                null,
                it.id ?: "missing",
                it.title,
                it.links.orEmpty().firstOrNull { it.type.equals(ACQUISITION_TYPE_STRING) }?.href,
                it.updated.toString(),
                it.summary
        )
    }
    categoriesList.forEach { db.categoryDao().insert(it) }
    return categoriesList
}

fun saveBooks(category: Category, books: List<Feed.Entry>?) {
    val db = Gdl.getDatabase()
    books.orEmpty().map {
        Book(
                null,
                it.id ?: "missing",
                it.title,
                false,
                it.educationalAlignment?.targetName?.toInt(),
                it.license,
                it.author?.joinToString(),
                it.publisher,
                null,
                it.links.orEmpty().firstOrNull { it.rel.equals(AQ_IMAGE_LINK_REL) }?.href,
                it.summary,
                category.id
        )
    }.forEach { db.bookDao().insert(it) }
}

fun fetchFeed() {
    launch {
        val parser = OpdsParser.create()
        val nav = parser.getNavRoot("eng").await()

        Gdl.getDatabase().bookDao().deleteAll()

        val categories = updateCategories(nav.entries)

        categories.forEach {
            launch {
                val aq = parser.getAcquisitionFeed(it.link).await()
                saveBooks(it, aq.entries)
            }
        }
    }
}