package dev.quinton.goa.core.domain.catalog

import java.text.Normalizer

internal actual fun decomposeCanonical(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
