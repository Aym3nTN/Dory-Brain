package com.dorybrain.app.data

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
    }
}
