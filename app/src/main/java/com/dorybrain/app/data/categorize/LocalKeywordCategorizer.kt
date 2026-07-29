package com.dorybrain.app.data.categorize

import com.dorybrain.app.data.Category

/**
 * Offline fallback used when no NVIDIA API key is configured, or when the
 * cloud call fails. Scores the note text against a keyword list per
 * category and picks the best match.
 */
class LocalKeywordCategorizer : Categorizer {

    private val keywordsByCategory: Map<Category, List<String>> = mapOf(
        Category.WORK to listOf(
            "meeting", "email", "deadline", "project", "boss", "client", "report",
            "presentation", "coworker", "colleague", "office", "interview", "resume",
            "invoice", "task", "standup", "sprint", "call with", "follow up"
        ),
        Category.SHOPPING to listOf(
            "buy", "purchase", "order", "cart", "amazon", "grocery", "groceries",
            "milk", "eggs", "store", "shop", "shopping list", "restock", "pick up"
        ),
        Category.HEALTH to listOf(
            "doctor", "dentist", "appointment", "medicine", "pills", "workout",
            "gym", "exercise", "diet", "sleep", "therapy", "checkup", "vitamins",
            "headache", "sick", "symptom"
        ),
        Category.FINANCE to listOf(
            "pay", "bill", "rent", "mortgage", "bank", "budget", "tax", "taxes",
            "invest", "savings", "loan", "salary", "expense", "credit card",
            "subscription", "insurance"
        ),
        Category.IDEAS to listOf(
            "idea", "what if", "maybe build", "concept", "brainstorm", "invention",
            "app idea", "startup", "someday", "wouldn't it be cool", "i wonder"
        ),
        Category.REMINDERS to listOf(
            "remind me", "don't forget", "remember to", "tomorrow", "tonight",
            "later today", "next week", "due", "expires", "deadline for"
        ),
        Category.PERSONAL to listOf(
            "family", "friend", "birthday", "anniversary", "call mom", "call dad",
            "vacation", "trip", "home", "kids", "partner", "relationship"
        )
    )

    override suspend fun categorize(text: String): Category {
        val normalized = text.lowercase()
        var best = Category.OTHER
        var bestScore = 0

        for ((category, keywords) in keywordsByCategory) {
            val score = keywords.count { normalized.contains(it) }
            if (score > bestScore) {
                bestScore = score
                best = category
            }
        }

        return best
    }
}
