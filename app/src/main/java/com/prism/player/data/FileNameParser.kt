package com.prism.player.data

import androidx.media3.common.MimeTypes

/** Turns names like "The.Movie.2019.1080p.BluRay.x264.mkv" or "Show.S02E05.mkv" into titles. */
object FileNameParser {
    val VIDEO_EXT = setOf("mkv", "mp4", "m4v", "avi", "mov", "wmv", "ts", "m2ts", "mts", "webm", "mpg", "mpeg", "flv", "3gp", "ogm", "ogv")
    val SUBTITLE_MIME = mapOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "vtt" to MimeTypes.TEXT_VTT,
        "ass" to MimeTypes.TEXT_SSA,
        "ssa" to MimeTypes.TEXT_SSA,
        "ttml" to MimeTypes.APPLICATION_TTML,
    )

    data class Parsed(val title: String, val year: Int?, val season: Int?, val episode: Int?)

    private val episodeA = Regex("""(?i)(?:^|[^a-z0-9])s(\d{1,2})[ ._-]?e(\d{1,3})""")
    private val episodeB = Regex("""(?i)(?:^|[^a-z0-9])(\d{1,2})x(\d{2,3})(?:[^a-z0-9]|$)""")
    private val year = Regex("""(?:^|[ .(\[_-])((?:19|20)\d{2})(?=$|[ .)\]_-])""")
    private val seasonFolder = Regex("""(?i)^(season|series|staffel|saison|s)[ ._-]*\d+$""")
    private val junk = Regex(
        """(?i)[ .\[(_-](2160p|1080p|1080i|720p|576p|480p|4k|uhd|hdr10\+?|hdr|dv|dovi|bluray|blu-ray|bdrip|brrip|""" +
            """web-?dl|webrip|web|hdtv|dvdrip|dvd|remux|x264|x265|h[ .]?264|h[ .]?265|hevc|avc|xvid|aac|ac3|eac3|dts|""" +
            """atmos|truehd|ddp?5[ .]1|10bit|proper|repack|extended|unrated|imax|multi|subbed|dubbed)(?=[ .\])_-]|$).*"""
    )

    fun parse(fileName: String, folders: List<String> = emptyList()): Parsed {
        val base = fileName.substringBeforeLast('.')
        val ep = episodeA.find(base) ?: episodeB.find(base)
        if (ep != null) {
            var show = clean(base.substring(0, ep.range.first))
            if (show.isBlank()) {
                show = folders.firstOrNull { !seasonFolder.matches(it.trim()) }?.let { clean(it) }.orEmpty()
            }
            val y = year.findAll(show).lastOrNull()
            if (y != null && y.range.first > 0) show = show.substring(0, y.range.first).trim()
            return Parsed(show.ifBlank { base }, null, ep.groupValues[1].toInt(), ep.groupValues[2].toInt())
        }
        val y = year.findAll(base).lastOrNull { it.range.first > 0 }
        val titlePart = if (y != null) base.substring(0, y.range.first) else base
        val title = clean(titlePart).ifBlank { clean(base) }
        return Parsed(title, y?.groupValues?.get(1)?.toInt(), null, null)
    }

    fun clean(raw: String): String {
        var s = raw.replace(Regex("""\[[^]]*]"""), " ")
        s = s.replace('.', ' ').replace('_', ' ')
        s = junk.replace(s, "")
        return s.replace(Regex("""\s+"""), " ").trim(' ', '-', '(', ')', '[', ']')
    }

    fun subtitleLanguage(subName: String): String? {
        val parts = subName.substringBeforeLast('.').split('.', '_', '-', ' ')
        val tag = parts.lastOrNull()?.lowercase() ?: return null
        return tag.takeIf { it.length in 2..3 && it.all(Char::isLetter) }
    }
}
