package com.dorybrain.shared.refine

/**
 * The ways a dumped thought can be rewritten. Each carries the instruction
 * handed to the model, which is deliberately strict about not inventing
 * facts the note didn't contain.
 */
enum class RefineMode(val label: String, val instruction: String) {
    CLEAN_UP(
        label = "Clean up",
        instruction = "Fix spelling, grammar, punctuation, and capitalization. " +
            "Keep the original meaning, tone, and level of detail. Do not add new information."
    ),
    CHECKLIST(
        label = "Make a checklist",
        instruction = "Rewrite as a short checklist. Put each item on its own line prefixed with \"- \". " +
            "Only use items that are present or clearly implied in the note. Do not invent items."
    ),
    SHORTEN(
        label = "Shorten",
        instruction = "Rewrite more concisely while keeping every key detail. Drop filler words."
    ),
    EXPAND(
        label = "Add detail",
        instruction = "Rewrite as a clearer, more actionable note. You may make the phrasing " +
            "more specific, but do not invent facts, names, dates, or numbers that aren't in the note."
    );

    fun systemPrompt(): String =
        "You rewrite short personal notes. $instruction " +
            "Reply with only the rewritten note text — no preamble, no explanation, no quotes, " +
            "no markdown headings."
}
