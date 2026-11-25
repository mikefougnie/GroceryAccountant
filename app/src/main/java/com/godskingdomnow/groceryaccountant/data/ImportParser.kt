package com.godskingdomnow.groceryaccountant.data

/**
 * Turns a casual text message ("Can you grab milk, 2 dozen eggs and bread? Thanks!")
 * or a pasted/shared list (one per line, bullets, checkboxes, numbers, prices) into items.
 * Pure Kotlin so it can be unit-tested without Android.
 */
object ImportParser {

    data class Parsed(
        val name: String,
        val qty: Int = 1,
        val size: String = "",
        val priceCents: Long? = null,
        /** false = probably chit-chat, not an item; shown unticked in the preview */
        val likely: Boolean = true,
    )

    private val MEASURE = listOf(
        "lb", "lbs", "pound", "pounds", "oz", "ounce", "ounces", "fl oz", "gal", "gallon", "gallons", "half gallon",
        "qt", "quart", "quarts", "pt", "pint", "pints", "kg", "g", "gram", "grams", "l", "liter", "liters", "litre",
        "ml", "ct", "count", "pk", "dozen", "doz",
    )
    private val CONTAINER = listOf(
        "bag", "bags", "box", "boxes", "can", "cans", "bottle", "bottles", "jar", "jars", "pack", "packs", "package",
        "packages", "loaf", "loaves", "bunch", "bunches", "head", "heads", "carton", "cartons", "container", "containers",
        "tub", "tubs", "case", "cases", "roll", "rolls", "pkg",
    )
    private val NUMBER_WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "couple" to 2, "a couple" to 2,
    )
    private val CHATTER = setOf(
        "thanks", "thank you", "thx", "ty", "ok", "okay", "k", "love you", "love u", "ily", "please", "pls", "plz",
        "yes", "no", "yep", "nope", "hi", "hey", "hello", "and", "also", "that's it", "thats it", "that's all",
        "thats all", "lol", "haha", "sure", "great", "cool", "xoxo", "<3", "babe", "honey", "hun",
    )

    private val bullet = Regex("""^\s*(?:[-*•·▪◦‣>+]+|\[\s?[xX✓✔]?\s?]|[☐☑✅✓✔□■●○]|\(?\d{1,2}[.)]|#)\s*""")
    private val askPrefix = Regex(
        """^(?:(?:hey|hi|babe|honey|hun)[,!]?\s+)?(?:can|could|would)\s+you\s+(?:also\s+|please\s+|pls\s+)*(?:get|grab|pick\s+up|buy|bring(?:\s+home)?)\s+(?:me\s+|us\s+|some\s+)?""" +
            """|^(?:please|pls|plz)\s+(?:get|grab|pick\s+up|buy)\s+(?:me\s+|us\s+|some\s+)?""" +
            """|^(?:we|i)(?:\s+(?:also\s+)?(?:need|are\s+out\s+of|ran\s+out\s+of|are\s+low\s+on)|['’]?re\s+(?:out\s+of|low\s+on)|['’]?m\s+out\s+of)\s+(?:some\s+|more\s+)?""" +
            """|^(?:also|and|plus)\s+(?:get\s+|grab\s+)?|^(?:get|grab|pick\s+up|buy)\s+(?:some\s+)?""",
        RegexOption.IGNORE_CASE,
    )
    private val tailNoise = Regex("""(?:[,\s]+(?:please|pls|plz|too|as\s+well|thanks|thank\s+you|thx|if\s+they\s+have\s+it|if\s+you\s+can))+\s*[.!?]*$""", RegexOption.IGNORE_CASE)
    private val priceRe = Regex("""(?:@\s*)?\$\s?(\d{1,4}(?:[.,]\d{2})?)\b|\b(\d{1,4}[.,]\d{2})\s*$""")
    private val qtyTail = Regex("""\s*(?:[xX×]\s?(\d{1,3})|\((\d{1,3})\)|[-,]?\s*qty:?\s*(\d{1,3}))\s*$""", RegexOption.IGNORE_CASE)
    private val qtyHead = Regex("""^(\d{1,3})\s*[xX×]\s+|^[xX×]\s?(\d{1,3})\s+|^qty:?\s*(\d{1,3})\s+""", RegexOption.IGNORE_CASE)
    private val emoji = Regex("""[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}\x{FE0F}\x{200D}]""")

    fun parse(text: String): List<Parsed> {
        val lines = text.replace("\r", "").replace(emoji, " ").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<Parsed>()
        for (raw in lines) {
            var line = raw
            if (line.endsWith(":") && line.length < 40) continue                 // "Walmart:" / "Produce:" headings
            line = bullet.replace(line, "")
            // a lone price line belongs to the item above ("$3.48")
            val lonePrice = Regex("""^\$?\s?(\d{1,4}[.,]\d{2})$""").find(line)
            if (lonePrice != null && out.isNotEmpty()) {
                out[out.size - 1] = out.last().copy(priceCents = parseCents(lonePrice.groupValues[1])); continue
            }
            // split a sentence into its parts
            val sentences = line.split(Regex("""(?<=[.!?])\s+""")).map { it.trim() }.filter { it.isNotEmpty() }
            for (sentence in sentences) {
                var s = sentence.trim().trimEnd('.', '!', '?', ';').trim()
                s = askPrefix.replace(s, "")
                s = tailNoise.replace(s, "").trim()
                if (s.isEmpty() || s.lowercase() in CHATTER) continue
                val pieces = splitList(s)
                for (p in pieces) {
                    val item = parseOne(p) ?: continue
                    // a piece that is only a size ("1 Gallon") belongs to the item before it
                    if (item.name.isEmpty()) {
                        if (out.isNotEmpty() && out.last().size.isEmpty()) out[out.size - 1] = out.last().copy(size = item.size)
                        continue
                    }
                    out.add(item)
                }
            }
        }
        return merge(out)
    }

    /** "milk, eggs, and bread" -> [milk, eggs, bread]; keeps "mac and cheese" whole when it stands alone. */
    private fun splitList(s: String): List<String> {
        val byComma = s.split(Regex("""\s*[,;]\s*|\s+&\s+(?=\S+\s*$)""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (byComma.size < 2) return byComma
        val last = byComma.last()
        val tail = Regex("""^(?:and|&)\s+""", RegexOption.IGNORE_CASE).replace(last, "")
        val lastParts = if (tail != last) listOf(tail)       // Oxford comma: "..., and bread"
        else tail.split(Regex("""\s+(?:and|&)\s+""", RegexOption.IGNORE_CASE), limit = 2)
        return byComma.dropLast(1).map { Regex("""^(?:and|&)\s+""", RegexOption.IGNORE_CASE).replace(it, "") } + lastParts
    }

    private fun parseOne(piece: String): Parsed? {
        var s = piece.trim().trim('"', '\'', '“', '”').trim()
        s = askPrefix.replace(s, "").trim()
        if (s.isEmpty() || s.lowercase() in CHATTER) return null

        var price: Long? = null
        priceRe.find(s)?.let { m ->
            price = parseCents(m.groupValues[1].ifEmpty { m.groupValues[2] })
            s = s.removeRange(m.range).trim().trimEnd('-', '–', ':', '@').trim()
        }

        var qty = 1
        qtyTail.find(s)?.let { m -> qty = m.groupValues.drop(1).first { it.isNotEmpty() }.toInt(); s = s.removeRange(m.range).trim() }
        qtyHead.find(s)?.let { m -> qty = m.groupValues.drop(1).first { it.isNotEmpty() }.toInt(); s = s.removeRange(m.range).trim() }

        var size = ""
        // leading amount: "2 lb ground beef", "2 cans of beans", "two bags chips", "a dozen eggs", "3 apples"
        val lead = Regex("""^(\d+(?:[./]\d+)?|a\s+couple(?:\s+of)?|[a-z]+)\s+(.*)$""", RegexOption.IGNORE_CASE).find(s)
        if (lead != null) {
            val numText = lead.groupValues[1].lowercase().removeSuffix(" of").trim()
            val num = numText.toDoubleOrNull() ?: fraction(numText) ?: NUMBER_WORDS[numText]?.toDouble()
            if (num != null) {
                var rest = lead.groupValues[2]
                val unit = unitAtStart(rest, MEASURE)
                val box = unitAtStart(rest, CONTAINER)
                when {
                    unit != null && (unit == "dozen" || unit == "doz") -> {
                        // "2 dozen eggs" = 2 of the 1-dozen packs
                        qty = num.toInt().coerceAtLeast(1) * qty; size = "1 dozen"; rest = rest.substring(unit.length)
                    }
                    unit != null -> { size = "${trimNumber(num)} $unit"; rest = rest.substring(unit.length) }
                    box != null -> { qty = num.toInt().coerceAtLeast(1) * qty; size = singular(box); rest = rest.substring(box.length) }
                    num >= 1 && num == Math.floor(num) && num <= 99 && (numText.first().isDigit() || numText in NUMBER_WORDS) &&
                        !(numText == "a" || numText == "an") -> qty = num.toInt() * qty
                    numText == "a" || numText == "an" -> {}
                    else -> rest = s   // "1.5 something" without a unit: leave as written
                }
                if (lead.groupValues[1].lowercase() in setOf("a", "an") && unit == null && box == null) rest = s.substring(lead.groupValues[1].length)
                s = Regex("""^\s*(?:of\s+)""", RegexOption.IGNORE_CASE).replace(rest, "").trim()
            }
        }
        // trailing size: "milk 1 gal", "Great Value Whole Milk, 1 Gallon" handled as a separate piece
        Regex("""\s+(\d+(?:\.\d+)?)\s*(${MEASURE.joinToString("|") { Regex.escape(it) }})\.?$""", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            if (size.isEmpty()) size = "${m.groupValues[1]} ${m.groupValues[2].lowercase()}"
            s = s.removeRange(m.range).trim()
        }
        s = s.replace(Regex("""\s{2,}"""), " ").trim().trim('-', '–', ':', '.', ',').trim()
        // "some", "more" at the start
        s = Regex("""^(?:some|more|a\s+few|a\s+bag\s+of)\s+""", RegexOption.IGNORE_CASE).replace(s, "")
        if (s.isEmpty()) return if (size.isNotEmpty()) Parsed("", qty, size, price) else null
        if (s.lowercase() in CHATTER) return null

        val words = s.split(' ').size
        val likely = words <= 7 && !s.contains('?') && !Regex("""^(?:i|i'm|i’m|im|we|we're|you|did|can|could|so|but|it|that|this|do|are|is|what|when|where|how|why|let|don't|dont)\b""", RegexOption.IGNORE_CASE).containsMatchIn(s)
        return Parsed(s.replaceFirstChar { it.uppercase() }.take(120), qty.coerceIn(1, 999), size, price, likely)
    }

    private fun unitAtStart(rest: String, units: List<String>): String? {
        val low = rest.lowercase()
        return units.sortedByDescending { it.length }.firstOrNull { u ->
            low.startsWith(u) && (low.length == u.length || !low[u.length].isLetter())
        }?.let { rest.substring(0, it.length).lowercase() }
    }

    private fun fraction(t: String): Double? {
        val m = Regex("""^(\d+)/(\d+)$""").find(t) ?: return null
        val d = m.groupValues[2].toDouble(); return if (d == 0.0) null else m.groupValues[1].toDouble() / d
    }

    private fun singular(w: String) = when {
        w == "loaves" -> "loaf"
        w.endsWith("ches") || w.endsWith("xes") || w.endsWith("ses") -> w.dropLast(2)
        w.endsWith("s") && w != "bunches" -> w.dropLast(1)
        else -> w
    }

    /** Same item twice in one message ("milk" and "Milk") becomes one line with the quantities added. */
    private fun merge(items: List<Parsed>): List<Parsed> {
        val seen = LinkedHashMap<String, Parsed>()
        for (i in items) {
            val k = i.name.lowercase() + "|" + i.size.lowercase()
            val prev = seen[k]
            seen[k] = if (prev == null) i else prev.copy(qty = (prev.qty + i.qty).coerceAtMost(999), priceCents = prev.priceCents ?: i.priceCents)
        }
        return seen.values.toList()
    }

    /* ---------------- export ---------------- */

    /** Plain-text list to text back, paste in notes, or search in a store app. */
    fun export(listName: String, items: List<ItemEntity>, includeChecked: Boolean, youPay: String?): String = buildString {
        append(listName).append('\n')
        items.filter { includeChecked || !it.checked }.forEach { i ->
            append(if (i.checked) "☑ " else "☐ ")
            if (i.qty > 1) append(i.qty).append(" × ")
            append(i.name)
            if (i.size.isNotBlank()) append(" (").append(i.size).append(')')
            i.priceCents?.let { append(" — ").append(money(it)) }
            append('\n')
        }
        if (youPay != null) append("Estimated total: ").append(youPay).append('\n')
    }.trimEnd()

    fun walmartSearch(name: String) = "https://www.walmart.com/search?q=" + java.net.URLEncoder.encode(name, "UTF-8")
    fun krogerSearch(name: String) = "https://www.kroger.com/search?query=" + java.net.URLEncoder.encode(name, "UTF-8")
}
