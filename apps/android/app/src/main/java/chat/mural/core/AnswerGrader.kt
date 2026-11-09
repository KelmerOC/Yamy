package chat.mural.core

import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

object AnswerGrader {

    fun normalize(text: String, stripAccents: Boolean = false): String {
        var cleaned = text.trim().lowercase(Locale.ROOT)
        // Collapse multiple whitespace
        cleaned = cleaned.replace("\\s+".toRegex(), " ")
        // Strip trailing and leading punctuation commonly typed across all alphabets and scripts
        cleaned = cleaned.trim(
            '.', '!', '?', ',', ';', ':', '"', '\'', '¿', '¡',
            '。', '！', '？', '、', '，', '；', '：', '「', '」', '『', '』', '（', '）', '।', '॥', '‘', '’', '“', '”'
        )

        if (stripAccents) {
            val normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
            cleaned = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFC)
        }
        return cleaned.trim()
    }

    fun isCorrect(item: LearningTestItem, userAnswer: String): Boolean {
        if (userAnswer.isBlank()) return false

        val target = item.correctAnswer.ifBlank { item.targetSpeechText }
        if (target.isBlank()) return false

        val normUser = normalize(userAnswer)
        val normTarget = normalize(target)

        when (item.type) {
            TestItemType.MULTIPLE_CHOICE -> {
                if (normUser == normTarget) return true
                return userAnswer.trim().equals(target.trim(), ignoreCase = true)
            }
            TestItemType.ERROR_CORRECTION -> {
                if (item.options.isNotEmpty()) {
                    if (normUser == normTarget) return true
                    return userAnswer.trim().equals(target.trim(), ignoreCase = true)
                }
                if (normUser == normTarget) return true
                val userNoAccents = normalize(userAnswer, stripAccents = true)
                val targetNoAccents = normalize(target, stripAccents = true)
                if (userNoAccents == targetNoAccents) return true
                return false
            }
            TestItemType.READING_COMPREHENSION -> {
                if (normUser == normTarget) return true
                val userNoAccents = normalize(userAnswer, stripAccents = true)
                val targetNoAccents = normalize(target, stripAccents = true)
                if (userNoAccents == targetNoAccents) return true
                return userAnswer.trim().equals(target.trim(), ignoreCase = true)
            }
            TestItemType.FILL_BLANK, TestItemType.CLOZE_BANK -> {
                if (normUser == normTarget) return true
                // Tolerance for minor diacritic omission (e.g. 'está' vs 'esta')
                val userNoAccents = normalize(userAnswer, stripAccents = true)
                val targetNoAccents = normalize(target, stripAccents = true)
                if (userNoAccents == targetNoAccents) return true
                return false
            }
            TestItemType.SPEAKING -> {
                // If the user spoke, we compare speech transcript with targetSpeechText
                val speechTarget = item.targetSpeechText.ifBlank { item.context }.ifBlank { item.prompt }
                val sim = similarity(userAnswer, speechTarget)
                return sim >= 0.60f
            }
        }
    }

    fun formatTimeRemaining(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val mins = s / 60
        val secs = s % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun shuffleOptions(item: LearningTestItem, seed: Long = item.id.hashCode().toLong()): List<String> {
        val options = item.options.toMutableList()
        val correct = item.correctAnswer.trim()
        if (correct.isNotBlank() && !options.any { it.trim().equals(correct, ignoreCase = true) }) {
            options.add(correct)
        }
        val distinct = options.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return emptyList()

        val rng = Random(seed)
        return distinct.shuffled(rng)
    }

    fun similarity(s1: String, s2: String): Float {
        val n1 = normalize(s1, stripAccents = true)
        val n2 = normalize(s2, stripAccents = true)
        if (n1 == n2) return 1.0f
        if (n1.isEmpty() || n2.isEmpty()) return 0.0f

        val lev = levenshteinDistance(n1, n2)
        val maxLen = maxOf(n1.length, n2.length)
        val levSim = 1.0f - (lev.toFloat() / maxLen.toFloat())

        val words1 = n1.split(' ').filter { it.isNotBlank() }.toSet()
        val words2 = n2.split(' ').filter { it.isNotBlank() }.toSet()

        val isUnspaced = words1.size <= 1 || words2.size <= 1 || n1.any { it.isCjk() } || n2.any { it.isCjk() }
        if (isUnspaced) {
            val nGram1 = if (n1.length > 1) n1.windowed(2).toSet() else n1.map { it.toString() }.toSet()
            val nGram2 = if (n2.length > 1) n2.windowed(2).toSet() else n2.map { it.toString() }.toSet()
            val inter = nGram1.intersect(nGram2).size
            val uni = nGram1.union(nGram2).size
            val nGramSim = if (uni > 0) inter.toFloat() / uni.toFloat() else 0f
            return (nGramSim * 0.4f + levSim * 0.6f).coerceIn(0f, 1f)
        }

        if (words1.isEmpty() || words2.isEmpty()) return 0.0f

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        val jaccard = if (union > 0) intersection.toFloat() / union.toFloat() else 0f

        return (jaccard * 0.5f + levSim * 0.5f).coerceIn(0f, 1f)
    }

    private fun Char.isCjk(): Boolean {
        val code = this.code
        return (code in 0x4E00..0x9FFF) ||
               (code in 0x3040..0x309F) ||
               (code in 0x30A0..0x30FF) ||
               (code in 0xAC00..0xD7AF)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
