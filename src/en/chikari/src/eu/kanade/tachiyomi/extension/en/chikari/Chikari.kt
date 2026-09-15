package eu.kanade.tachiyomi.extension.en.chikari

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Chikari : ParsedHttpSource() {

    override val name = "Chikari"
    override val baseUrl = "https://chikari.moe"
    override val lang = "en"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        )
        .add("Referer", "https://chikari.moe/")
        .add("Origin", "https://chikari.moe")

    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("en"))

    // ============================== Popular Manga ==============================
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga?page=$page", headers)
    }

    override fun popularMangaSelector(): String =
        "div.grid-comics > div.comic-card, div.manga-grid > div.manga-item, div.cards-container > a.card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleEl = element.selectFirst("h3.title, .comic-title, .title, span.name") ?: element
        title = titleEl.text().trim()
        val linkEl = element.selectFirst("a.card-link, a.comic-cover, a") ?: element
        setUrlWithoutBaseUrl(linkEl.attr("abs:href"))
        thumbnail_url = element.selectFirst("img.cover-image, img.poster, img")?.let {
            it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
        }
    }

    override fun popularMangaNextPageSelector(): String? =
        "a.pagination-next, a[rel=next], a.next-page"

    // ============================== Latest Updates ==============================
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/comics?sort=latest&page=$page", headers)
    }

    override fun latestUpdatesSelector(): String =
        "div.latest-updates > div.comic-card, div.updates-grid > div.item"

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = "a.pagination-next, a[rel=next]"

    // ============================== Search ==============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&page=$page", headers)
    }

    override fun searchMangaSelector(): String =
        "div.search-results > div.comic-card, div.search-grid > div.item"

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = "a.pagination-next, a[rel=next]"

    // ============================== Manga Details ==============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.comic-title, h1.title, h1.entry-title")?.text()?.trim() ?: ""
        author = document.selectFirst("span.author-name, div.author a, .meta-author span")?.text()?.trim()
        artist =
            document.selectFirst("span.artist-name, div.artist a, .meta-artist span")?.text()?.trim() ?: author
        description = document.select("div.comic-description, div.synopsis p, div.description")
            .joinToString("\n") { it.text().trim() }
        genre = document.select("div.genres-list a, .tag-item, a.genre").joinToString { it.text().trim() }

        val statusText = document.selectFirst("span.status-badge, .comic-status, .status")?.text()?.trim().orEmpty()
        status = parseStatus(statusText)

        thumbnail_url = document.selectFirst("div.comic-cover img, .poster-wrapper img, img.cover")?.let {
            it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        status.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        status.contains("Cancelled", ignoreCase = true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapter List ==============================
    override fun chapterListSelector(): String =
        "ul.chapter-list > li, div.chapters-container > div.chapter-row, div.chapter-item"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val linkEl = element.selectFirst("a.chapter-link, a") ?: element
        setUrlWithoutBaseUrl(linkEl.attr("abs:href"))
        name = element.selectFirst("a.chapter-title, span.chapter-num, a.chapter-name")?.text()?.trim() ?: linkEl.text().trim()
        scanlator = element.selectFirst("span.scanlator-name, .group-name")?.text()?.trim()
        date_upload = parseDate(element.selectFirst("span.chapter-date, time.published, span.date")?.text()?.trim())
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (_: ParseException) {
            0L
        }
    }

    // ============================== Page List ==============================
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-pages img, div.comic-pages img, div.reading-content img").mapIndexed { index, element ->
            val imageUrl = element.attr("abs:data-src").ifEmpty {
                element.attr("abs:data-lazy-src").ifEmpty {
                    element.attr("abs:src")
                }
            }
            Page(index, "", imageUrl)
        }
    }

    override fun imageUrlParse(document: Document): String = ""
}
