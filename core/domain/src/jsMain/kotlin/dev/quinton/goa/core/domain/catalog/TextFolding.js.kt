package dev.quinton.goa.core.domain.catalog

internal actual fun decomposeCanonical(text: String): String = text.asDynamic().normalize("NFD") as String
