package com.dorybrain.shared.model

/**
 * Fixed set of buckets a dumped thought can land in. Kept small and general
 * so both the on-device classifier and a cloud model can reliably pick one.
 */
enum class Category(val label: String) {
    WORK("Work"),
    PERSONAL("Personal"),
    SHOPPING("Shopping"),
    IDEAS("Ideas"),
    HEALTH("Health"),
    FINANCE("Finance"),
    REMINDERS("Reminders"),
    OTHER("Other");

    companion object {
        fun fromLabel(label: String): Category? =
            entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }

        /** Parses a stored name, falling back to [OTHER] for anything unknown. */
        fun fromName(name: String): Category =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
