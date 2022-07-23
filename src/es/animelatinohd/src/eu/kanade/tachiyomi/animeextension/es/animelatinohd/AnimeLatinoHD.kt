package eu.kanade.tachiyomi.animeextension.es.animelatinohd

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.es.animelatinohd.extractors.DoodExtractor
import eu.kanade.tachiyomi.animeextension.es.animelatinohd.extractors.FembedExtractor
import eu.kanade.tachiyomi.animeextension.es.animelatinohd.extractors.OkruExtractor
import eu.kanade.tachiyomi.animeextension.es.animelatinohd.extractors.SolidFilesExtractor
import eu.kanade.tachiyomi.animeextension.es.animelatinohd.extractors.StreamSBExtractor
import eu.kanade.tachiyomi.animeextension.es.animelatinohd.extractors.StreamTapeExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class AnimeLatinoHD : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "AnimeLatinoHD"

    override val baseUrl = "https://www.animelatinohd.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularAnimeSelector(): String = "#__next main[class*='Animes_container'] div[class*='ListAnimes_box'] div[class*='ListAnimes'] div[class*='AnimeCard_anime']"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/animes/populares")

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animeList = mutableListOf<SAnime>()
        val hasNextPage = document.select("#__next > main > div > div[class*=\"Animes_paginate\"] a:last-child svg").any()
        document.select("script").forEach { script ->
            if (script.data().contains("{\"props\":{\"pageProps\":")) {
                val jObject = json.decodeFromString<JsonObject>(script.data())
                val props = jObject["props"]!!.jsonObject
                val pageProps = props["pageProps"]!!.jsonObject
                val data = pageProps["data"]!!.jsonObject
                val popularToday = data["popular_today"]!!.jsonArray
                popularToday.forEach { item ->
                    val animeItem = item!!.jsonObject
                    val anime = SAnime.create()
                    anime.setUrlWithoutDomain(externalOrInternalImg("anime/${animeItem["slug"]!!.jsonPrimitive!!.content}"))
                    anime.thumbnail_url = "https://image.tmdb.org/t/p/w200${animeItem["poster"]!!.jsonPrimitive!!.content}"
                    anime.title = animeItem["name"]!!.jsonPrimitive!!.content
                    animeList.add(anime)
                }
            }
        }
        return AnimesPage(animeList, hasNextPage)
    }

    override fun popularAnimeFromElement(element: Element) = throw Exception("not used")

    override fun popularAnimeNextPageSelector(): String = "uwu"

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        val newAnime = SAnime.create()
        document.select("script").forEach { script ->
            if (script.data().contains("{\"props\":{\"pageProps\":")) {
                val jObject = json.decodeFromString<JsonObject>(script.data())
                val props = jObject["props"]!!.jsonObject
                val pageProps = props["pageProps"]!!.jsonObject
                val data = pageProps["data"]!!.jsonObject

                newAnime.title = data["name"]!!.jsonPrimitive!!.content
                newAnime.genre = data["genres"]!!.jsonPrimitive!!.content.split(",").joinToString()
                newAnime.description = data["overview"]!!.jsonPrimitive!!.content
                newAnime.status = parseStatus(data["status"]!!.jsonPrimitive!!.content)
                newAnime.thumbnail_url = "https://image.tmdb.org/t/p/w600_and_h900_bestv2${data["poster"]!!.jsonPrimitive!!.content}"
                newAnime.setUrlWithoutDomain(externalOrInternalImg("anime/${data["slug"]!!.jsonPrimitive!!.content}"))
            }
        }
        return newAnime
    }

    override fun animeDetailsParse(document: Document) = throw Exception("not used")

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodeList = mutableListOf<SEpisode>()
        document.select("script").forEach { script ->
            if (script.data().contains("{\"props\":{\"pageProps\":")) {
                val jObject = json.decodeFromString<JsonObject>(script.data())
                val props = jObject["props"]!!.jsonObject
                val pageProps = props["pageProps"]!!.jsonObject
                val data = pageProps["data"]!!.jsonObject
                val arrEpisode = data["episodes"]!!.jsonArray
                arrEpisode.forEach { item ->
                    val animeItem = item!!.jsonObject
                    val episode = SEpisode.create()
                    episode.setUrlWithoutDomain(externalOrInternalImg("ver/${data["slug"]!!.jsonPrimitive!!.content}/${animeItem["number"]!!.jsonPrimitive!!.content!!.toFloat()}"))
                    episode.episode_number = animeItem["number"]!!.jsonPrimitive!!.content!!.toFloat()
                    episode.name = "Episodio ${animeItem["number"]!!.jsonPrimitive!!.content!!.toFloat()}"
                    episodeList.add(episode)
                }
            }
        }
        return episodeList
    }

    override fun episodeListSelector() = "uwu"

    override fun episodeFromElement(element: Element) = throw Exception("not used")

    private fun parseJsonArray(json: JsonElement?): List<JsonElement> {
        var list = mutableListOf<JsonElement>()
        json!!.jsonObject!!.entries!!.forEach { list.add(it.value) }
        return list
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()
        document.select("script").forEach { script ->
            if (script.data().contains("{\"props\":{\"pageProps\":")) {
                val jObject = json.decodeFromString<JsonObject>(script.data())
                val props = jObject["props"]!!.jsonObject
                val pageProps = props["pageProps"]!!.jsonObject
                val data = pageProps["data"]!!.jsonObject
                val playersElement = data["players"]
                val players = if (playersElement !is JsonArray) JsonArray(parseJsonArray(playersElement)) else playersElement!!.jsonArray
                players.forEach { player ->
                    val servers = player!!.jsonArray
                    servers.forEach { server ->
                        val item = server!!.jsonObject
                        val url = item["code"]!!.jsonPrimitive!!.content
                        val language = if (item["languaje"]!!.jsonPrimitive!!.content == "1") "[Lat] " else "[Sub] "
                        if (url.lowercase().contains("streamsb")) {
                            val headers = headers.newBuilder()
                                .set("Referer", url)
                                .set(
                                    "User-Agent",
                                    "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:96.0) Gecko/20100101 Firefox/96.0"
                                )
                                .set("Accept-Language", "en-US,en;q=0.5")
                                .set("watchsb", "streamsb")
                                .build()
                            val videos = StreamSBExtractor(client).videosFromUrl(url, headers, language)
                            videoList.addAll(videos)
                        }
                        if (url.lowercase().contains("www.fembed.com")) {
                            val videos = FembedExtractor().videosFromUrl(url, language)
                            videoList.addAll(videos)
                        }
                        if (url.lowercase().contains("streamtape")) {
                            val video = StreamTapeExtractor(client).videoFromUrl(url, language + "Streamtape")
                            if (video != null) {
                                videoList.add(video)
                            }
                        }
                        if (url.lowercase().contains("doodstream")) {
                            val video = try {
                                DoodExtractor(client).videoFromUrl(url, language + "DoodStream")
                            } catch (e: Exception) {
                                null
                            }
                            if (video != null) {
                                videoList.add(video)
                            }
                        }
                        if (url.lowercase().contains("okru")) {
                            val videos = OkruExtractor(client).videosFromUrl(url, language)
                            videoList.addAll(videos)
                        }
                        if (url.lowercase().contains("www.solidfiles.com")) {
                            val videos = SolidFilesExtractor(client).videosFromUrl(url, language)
                            videoList.addAll(videos)
                        }
                        if (url.lowercase().contains("od.lk")) {
                            videoList.add(Video(url, language + "Od.lk", url, null))
                        }
                        if (url.lowercase().contains("cldup.com")) {
                            videoList.add(Video(url, language + "CldUp", url, null))
                        }
                    }
                }
            }
        }
        return videoList
    }

    override fun videoListSelector() = throw Exception("not used")

    override fun videoUrlParse(document: Document) = throw Exception("not used")

    override fun videoFromElement(element: Element) = throw Exception("not used")

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", "[Sub] Fembed:720p")
        if (quality != null) {
            val newList = mutableListOf<Video>()
            var preferred = 0
            for (video in this) {
                if (video.quality == quality) {
                    newList.add(preferred, video)
                    preferred++
                } else {
                    newList.add(video)
                }
            }
            return newList
        }
        return this
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val genreFilter = filterList.find { it is GenreFilter } as GenreFilter
        return when {
            query.isNotBlank() -> GET("$baseUrl/animes?page=$page&search=$query")
            genreFilter.state != 0 -> GET("$baseUrl/animes?page=$page&genre=${genreFilter.toUriPart()}")
            else -> GET("$baseUrl/animes?page=$page")
        }
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("La busqueda por texto ignora el filtro"),
        GenreFilter()
    )

    override fun searchAnimeFromElement(element: Element): SAnime {
        return popularAnimeFromElement(element)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animeList = mutableListOf<SAnime>()
        val hasNextPage = document.select("#__next > main > div > div[class*=\"Animes_paginate\"] a:last-child svg").any()
        document.select("script").forEach { script ->
            if (script.data().contains("{\"props\":{\"pageProps\":")) {
                val jObject = json.decodeFromString<JsonObject>(script.data())
                val props = jObject["props"]!!.jsonObject
                val pageProps = props["pageProps"]!!.jsonObject
                val data = pageProps["data"]!!.jsonObject
                val arrData = data["data"]!!.jsonArray
                arrData.forEach { item ->
                    val animeItem = item!!.jsonObject
                    val anime = SAnime.create()
                    anime.setUrlWithoutDomain(externalOrInternalImg("anime/${animeItem["slug"]!!.jsonPrimitive!!.content}"))
                    anime.thumbnail_url = "https://image.tmdb.org/t/p/w200${animeItem["poster"]!!.jsonPrimitive!!.content}"
                    anime.title = animeItem["name"]!!.jsonPrimitive!!.content
                    animeList.add(anime)
                }
            }
        }
        return AnimesPage(animeList, hasNextPage)
    }

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    private class GenreFilter : UriPartFilter(
        "Géneros",
        arrayOf(
            Pair("<Selecionar>", ""),
            Pair("Acción", "accion"),
            Pair("Aliens", "aliens"),
            Pair("Artes Marciales", "artes-marciales"),
            Pair("Aventura", "aventura"),
            Pair("Ciencia Ficción", "ciencia-ficcion"),
            Pair("Comedia", "comedia"),
            Pair("Cyberpunk", "cyberpunk"),
            Pair("Demonios", "demonios"),
            Pair("Deportes", "deportes"),
            Pair("Detectives", "detectives"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Escolar", "escolar"),
            Pair("Espacio", "espacio"),
            Pair("Fantasía", "fantasia"),
            Pair("Gore", "gore"),
            Pair("Harem", "harem"),
            Pair("Histórico", "historico"),
            Pair("Horror", "horror"),
            Pair("Josei", "josei"),
            Pair("Juegos", "juegos"),
            Pair("Kodomo", "kodomo"),
            Pair("Magia", "magia"),
            Pair("Maho Shoujo", "maho-shoujo"),
            Pair("Mecha", "mecha"),
            Pair("Militar", "militar"),
            Pair("Misterio", "misterio"),
            Pair("Musica", "musica"),
            Pair("Parodia", "parodia"),
            Pair("Policial", "policial"),
            Pair("Psicológico", "psicologico"),
            Pair("Recuentos De La Vida", "recuentos-de-la-vida"),
            Pair("Romance", "romance"),
            Pair("Samurais", "samurais"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Sobrenatural", "sobrenatural"),
            Pair("Soft Hentai", "soft-hentai"),
            Pair("Super Poderes", "super-poderes"),
            Pair("Suspenso", "suspenso"),
            Pair("Terror", "terror"),
            Pair("Vampiros", "vampiros"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun externalOrInternalImg(url: String) = if (url.contains("https")) url else "$baseUrl/$url"

    private fun parseStatus(statusString: String): Int {
        return when {
            statusString.contains("1") -> SAnime.ONGOING
            statusString.contains("0") -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }

    override fun latestUpdatesSelector() = popularAnimeSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/animes?page=$page&status=1")

    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)

    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("[Sub] Od.lk", "[Sub] CldUp", "[Sub] SolidFiles", "[Sub] Fembed:720p", "[Sub] Fembed:480p", "[Lat] Od.lk", "[Lat] CldUp", "[Lat] SolidFiles", "[Lat] Fembed:720p", "[Lat] Fembed:480p")
            entryValues = arrayOf("[Sub] Od.lk", "[Sub] CldUp", "[Sub] SolidFiles", "[Sub] Fembed:720p", "[Sub] Fembed:480p", "[Lat] Od.lk", "[Lat] CldUp", "[Lat] SolidFiles", "[Lat] Fembed:720p", "[Lat] Fembed:480p")
            setDefaultValue("[Sub] Fembed:720p")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        screen.addPreference(videoQualityPref)
    }
}