package io.digitallibrary.reader.catalog

import android.util.Log
import com.ethlo.time.FastInternetDateTimeUtil
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

val TAG = "KotCoRutTest"

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

        @Namespace(reference="http://purl.org/dc/terms/")
        @set:Element(required = false)
        @get:Element(required = false)
        var license: String? = null

        @Namespace(reference="http://purl.org/dc/terms/")
        @set:Element(required = false)
        @get:Element(required = false)
        var publisher: String? = null

        @Namespace(reference="http://purl.org/dc/terms/")
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

        @Namespace(reference="http://purl.org/dcx/lrmi-terms/")
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

suspend fun updateCategories(categories: List<Feed.Entry>) {

}

fun fetchRoot() {
    launch {
        Log.i(TAG, "Starting ...")
        val parser = OpdsParser.create()
        val test = parser.getNavRoot("eng").await()
        // Update categories

        for (item in test.entries!!) {
            for (link in item.links.orEmpty()) {
                Log.i(TAG, "VVV Fetching from " + item.title)
                launch {
                    Log.i(TAG, "XXX Fetching from " + item.title)
                    val aq = parser.getAcquisitionFeed(link.href).await()
                    Log.i(TAG, "XXX Got result from " + item.title)
                    for (e in aq.entries.orEmpty()) {
                        launch {
                            // save book
                        }
                    }
                    Log.i(TAG, item.title + " done")
                }
            }
        }
        Log.i(TAG, "Done ...")
    }
}