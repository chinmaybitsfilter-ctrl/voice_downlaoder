package com.chinmay.voicefiles

import com.chinmay.voicefiles.TextNorm.isDigits
import java.util.Calendar
import java.util.Locale

enum class Action { OPEN, SHARE }
enum class Day { TODAY, YESTERDAY }

/** What the user asked for, pulled out of one spoken sentence. */
data class Query(
    val action: Action,
    /** Words that should appear in the file name. */
    val terms: List<String>,
    /** "pdf", "photo"… → the kind of file. Kept with the spoken word in case it is really part of the name. */
    val typeWords: List<Pair<String, FileKind>>,
    val latest: Boolean,
    val day: Day?,
    val dayWord: String?,
) {
    val kinds: Set<FileKind> get() = typeWords.map { it.second }.toSet()
}

sealed class Match {
    data class Open(val item: FileItem) : Match()
    data class Choose(val items: List<FileItem>) : Match()
    /** Nothing fits. */
    object None : Match()
    /** The user only said "open" — no file name at all. */
    object Empty : Match()
}

data class Resolution(val query: Query, val match: Match, val heard: String)

object VoiceCommand {

    private const val MIN_SCORE = 0.6
    private const val SURE_SCORE = 0.8
    private const val CLEAR_GAP = 0.1

    private val SHARE_WORDS = setOf("share", "send", "forward")

    private val RECENT_WORDS = setOf("latest", "last", "newest", "recent", "recently", "lately")

    private val DAY_WORDS = mapOf(
        "today" to Day.TODAY, "todays" to Day.TODAY,
        "yesterday" to Day.YESTERDAY, "yesterdays" to Day.YESTERDAY,
    )

    private val TYPE_WORDS: Map<String, FileKind> = buildMap {
        listOf("pdf", "pdfs").forEach { put(it, FileKind.PDF) }
        listOf("photo", "photos", "image", "images", "picture", "pictures", "pic", "pics",
            "jpg", "jpeg", "png", "wallpaper").forEach { put(it, FileKind.IMAGE) }
        listOf("video", "videos", "movie", "movies", "clip", "clips", "mp4").forEach { put(it, FileKind.VIDEO) }
        listOf("song", "songs", "audio", "music", "recording", "recordings", "mp3").forEach { put(it, FileKind.AUDIO) }
        listOf("word", "doc", "docs", "docx", "txt").forEach { put(it, FileKind.DOC) }
        listOf("excel", "sheet", "sheets", "spreadsheet", "spreadsheets", "xlsx", "xls", "csv")
            .forEach { put(it, FileKind.SHEET) }
        listOf("ppt", "pptx", "powerpoint", "presentation", "presentations", "slide", "slides")
            .forEach { put(it, FileKind.SLIDES) }
        listOf("apk", "apks").forEach { put(it, FileKind.APK) }
        listOf("zip", "rar", "archive").forEach { put(it, FileKind.ARCHIVE) }
    }

    private val FILLER = setOf(
        "open", "opening", "show", "view", "play", "launch", "start", "display", "read", "find", "get",
        "bring", "pull", "up", "please", "can", "could", "would", "will", "you", "i", "want", "wanna",
        "need", "to", "the", "a", "an", "my", "me", "mine", "our", "that", "this", "those", "these",
        "file", "files", "document", "documents", "called", "named", "name", "titled", "with", "which",
        "is", "was", "it", "of", "for", "from", "in", "on", "at", "folder", "download", "downloads",
        "downloaded", "just", "most", "and", "hey", "ok", "okay", "now", "some", "one", "dot", "s",
    )

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    /** After these words, "one" means "the file" (e.g. "the latest one"), not the number 1. */
    private val ONE_AS_PRONOUN_AFTER = setOf("the", "that", "this", "latest", "last", "newest", "recent", "new", "other") +
        TYPE_WORDS.keys

    // ---------------- parsing ----------------

    fun parse(spoken: String): Query {
        var text = spoken.lowercase(Locale.ROOT)
            .replace(Regex("\\bp\\s*\\.?\\s*d\\s*\\.?\\s*f\\b"), "pdf")   // "p d f", "P.D.F."
            .replace(Regex("\\ba\\s*\\.?\\s*p\\s*\\.?\\s*k\\b"), "apk")
            .replace(Regex("\\bp\\s*p\\s*t\\b"), "ppt")
            .replace("power point", "powerpoint")
            .replace(Regex("['’]s\\b"), "s")                                  // "today's" → "todays"
        text = text.replace(Regex("\\bdot\\s+(?=\\w)"), "")                 // "notes dot pdf" → "notes pdf"

        val raw = TextNorm.tokenize(text)
        val words = numbersToDigits(raw)

        var action = Action.OPEN
        var latest = false
        var day: Day? = null
        var dayWord: String? = null
        val types = mutableListOf<Pair<String, FileKind>>()
        val terms = mutableListOf<String>()

        words.forEachIndexed { i, w ->
            when {
                i <= 2 && w in SHARE_WORDS && action == Action.OPEN && terms.isEmpty() -> action = Action.SHARE
                w in RECENT_WORDS -> latest = true
                w in DAY_WORDS -> { day = DAY_WORDS[w]; dayWord = w }
                w in TYPE_WORDS -> types += w to TYPE_WORDS.getValue(w)
                w in FILLER -> Unit
                else -> terms += w
            }
        }
        return Query(action, terms, types, latest, day, dayWord)
    }

    /** "chapter three" → "chapter 3", "twenty one" → "21"; "the latest one" keeps "one" as a filler word. */
    private fun numbersToDigits(words: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val prev = out.lastOrNull()
            when {
                w == "one" && (prev == null || prev in ONE_AS_PRONOUN_AFTER) -> out += w   // filler
                w in TENS -> {
                    val next = words.getOrNull(i + 1)
                    val unit = next?.let { UNITS[it] }
                    if (unit != null && unit in 1..9) {
                        out += (TENS.getValue(w) + unit).toString(); i++
                    } else out += TENS.getValue(w).toString()
                }
                w in UNITS -> out += UNITS.getValue(w).toString()
                else -> out += w
            }
            i++
        }
        return out
    }

    // ---------------- matching ----------------

    /**
     * Tries every alternative the speech recogniser offered and keeps the best one,
     * with a small preference for the recogniser's first guess.
     */
    fun resolve(alternatives: List<String>, files: List<FileItem>, now: Long = System.currentTimeMillis()): Resolution {
        var best: Resolution? = null
        var bestRank = Double.NEGATIVE_INFINITY
        alternatives.take(5).forEachIndexed { idx, alt ->
            val q = parse(alt)
            val (score, match) = evaluate(q, files, now)
            val rank = when (match) {
                is Match.Open -> 2.0 + score
                is Match.Choose -> 1.0 + score
                Match.Empty -> 0.5
                Match.None -> 0.0
            } - idx * 0.03
            if (rank > bestRank) {
                bestRank = rank
                best = Resolution(q, match, alt)
            }
        }
        return best ?: Resolution(parse(""), Match.Empty, "")
    }

    fun evaluate(q: Query, files: List<FileItem>, now: Long): Pair<Double, Match> {
        val strict = attempt(q.terms, q.kinds, q.day, q.latest, files, now)
        if (strict.second != Match.None) return strict
        // "pdf"/"today"… might actually be part of the file's name — try again without treating them as filters.
        if (q.typeWords.isNotEmpty() || q.day != null) {
            val extra = q.typeWords.map { it.first } + listOfNotNull(q.dayWord)
            val loose = attempt(q.terms + extra, emptySet(), null, q.latest, files, now)
            if (loose.second != Match.None) return loose
        }
        return 0.0 to Match.None
    }

    private fun attempt(
        terms: List<String>, kinds: Set<FileKind>, day: Day?, latest: Boolean,
        files: List<FileItem>, now: Long,
    ): Pair<Double, Match> {
        var pool = files
        if (kinds.isNotEmpty()) pool = pool.filter { it.kind in kinds }
        if (day != null) pool = pool.filter { isOnDay(it.modified, day, now) }

        if (terms.isEmpty()) {
            if (kinds.isEmpty() && day == null && !latest) return 0.0 to Match.Empty
            val newest = pool.maxByOrNull { it.modified } ?: return 0.0 to Match.None
            return 1.0 to Match.Open(newest)
        }

        val scored = pool.map { it to score(terms, it) }
            .filter { it.second >= MIN_SCORE }
            .sortedWith(compareByDescending<Pair<FileItem, Double>> { it.second }.thenByDescending { it.first.modified })
        if (scored.isEmpty()) return 0.0 to Match.None

        val top = scored[0].second
        if (latest) {
            // "open the last physics pdf" → newest of the good matches
            val pick = scored.filter { it.second >= top - 0.15 }.maxByOrNull { it.first.modified }!!
            return pick.second to Match.Open(pick.first)
        }
        val second = scored.getOrNull(1)?.second
        if (top >= SURE_SCORE && (second == null || top - second >= CLEAR_GAP)) {
            return top to Match.Open(scored[0].first)
        }
        val options = scored.filter { it.second >= top - 0.25 }.take(6).map { it.first }
        return top to if (options.size == 1) Match.Open(options[0]) else Match.Choose(options)
    }

    /** How well the spoken words fit this file's name (≈0 … 1.2). */
    fun score(terms: List<String>, item: FileItem): Double {
        if (terms.isEmpty()) return 0.0
        val matchedName = HashSet<Int>()
        val perTerm = DoubleArray(terms.size)
        for ((t, q) in terms.withIndex()) {
            var best = 0.0
            var bestIdx = -1
            item.tokens.forEachIndexed { i, n ->
                val s = TextNorm.tokenSim(q, n)
                if (s > best) { best = s; bestIdx = i }
            }
            // "labreport" file vs spoken "lab" "report", or spoken word glued inside a longer name word
            if (best < 0.7 && q.length >= 3 && !q.isDigits() && item.joined.contains(q)) best = 0.7
            if (bestIdx >= 0 && best > 0) matchedName += bestIdx
            perTerm[t] = best
        }
        // One extra word the name doesn't have ("the calculus BOOK") shouldn't sink a strong match.
        val anyStrong = perTerm.any { it >= 0.85 }
        val sum = perTerm.sumOf { if (it == 0.0 && anyStrong) 0.3 else it }
        var s = sum / terms.size
        val qJoined = terms.joinToString("")
        if (terms.size > 1 && qJoined.length >= 4 && item.joined.contains(qJoined)) s += 0.1
        // prefer "notes.pdf" over "notes for exam revision final.pdf" when the user said just "notes"
        s += 0.08 * matchedName.size / maxOf(1, item.tokens.size)
        return s
    }

    private fun isOnDay(time: Long, day: Day, now: Long): Boolean {
        val target = Calendar.getInstance().apply {
            timeInMillis = now
            if (day == Day.YESTERDAY) add(Calendar.DAY_OF_YEAR, -1)
        }
        val c = Calendar.getInstance().apply { timeInMillis = time }
        return c.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            c.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }
}
