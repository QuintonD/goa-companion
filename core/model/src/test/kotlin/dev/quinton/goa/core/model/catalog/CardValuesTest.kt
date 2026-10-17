package dev.quinton.goa.core.model.catalog

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class CardValuesTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    @Test
    fun `plus is omitted when empty and older JSON still decodes`() {
        val plain = CardValues(attack = 4)
        assertThat(json.encodeToString(CardValues.serializer(), plain)).doesNotContain("plus")
        val old = """{"attack":4,"defense":null,"movement":null,"range":null,"radius":null}"""
        assertThat(json.decodeFromString(CardValues.serializer(), old)).isEqualTo(plain)
    }

    @Test
    fun `plus round-trips with stat names`() {
        val values = CardValues(attack = 3, range = 1, plus = listOf(Stat.RANGE))
        val text = json.encodeToString(CardValues.serializer(), values)
        assertThat(text).contains(""""plus":["range"]""")
        assertThat(json.decodeFromString(CardValues.serializer(), text)).isEqualTo(values)
        assertThat(values.get(Stat.RANGE)).isEqualTo(1)
        assertThat(values.get(Stat.INITIATIVE)).isNull()
    }
}
