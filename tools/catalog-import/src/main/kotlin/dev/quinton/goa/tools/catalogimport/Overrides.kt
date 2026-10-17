package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Hero
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Hand-maintained corrections (`tools/catalog-import/overrides.json`), applied last.
 * Every entry needs a `reason` and the `evidence` it rests on (card image, owner photo, rulebook).
 * Hero and card entries are JSON patches: each key replaces that field (`values` and `stats`
 * merge one level deep). A key also marks the matching disagreement as resolved.
 */
@Serializable
data class Overrides(
    val comment: String? = null,
    val heroes: Map<String, JsonObject> = emptyMap(),
    val cards: Map<String, JsonObject> = emptyMap(),
    /** Card ID → source scan path in dmrediger/goa2, for cards the matcher cannot identify. */
    val images: Map<String, ImageOverride> = emptyMap(),
    /** Hero ID → record that every card's numbers were compared with the card images. */
    val imageChecks: Map<String, ImageCheck> = emptyMap(),
) {
    /**
     * Fields an entry patches or explicitly confirms. `confirm` lists fields where source 1 is
     * right and a cross-check source is wrong (the evidence says so); nothing is changed.
     */
    fun coveredFields(entity: String): Set<String> {
        val obj = heroes[entity] ?: cards[entity] ?: return emptySet()
        val out = mutableSetOf<String>()
        obj["confirm"]?.jsonArray?.forEach { out += it.jsonPrimitive.content }
        for ((key, value) in obj) {
            if (key in META) continue
            out += key
            if (value is JsonObject) value.keys.forEach { out += "$key.$it" }
        }
        return out
    }

    /** True when an override patches or confirms [field] of [entity]. */
    fun resolves(entity: String, field: String): Boolean {
        val covered = coveredFields(entity)
        return field in covered ||
            (field == "values.range/radius" && ("values.range" in covered || "values.radius" in covered))
    }

    fun validate() {
        (heroes + cards).forEach { (id, obj) ->
            require(obj["reason"]?.jsonPrimitive?.content?.isNotBlank() == true) {
                "overrides.json: $id needs a reason"
            }
            require(obj["evidence"]?.jsonPrimitive?.content?.isNotBlank() == true) {
                "overrides.json: $id needs evidence"
            }
            require("id" !in obj && "heroId" !in obj) { "overrides.json: $id must not change IDs" }
        }
    }

    companion object {
        val META = setOf("reason", "evidence", "confirm")
    }
}

@Serializable
data class ImageOverride(val path: String, val reason: String, val evidence: String)

@Serializable
data class ImageCheck(val evidence: String, val checked: String)

object OverrideApplier {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = true
    }

    private fun merge(base: JsonObject, patch: JsonObject): JsonObject {
        val out = base.toMutableMap()
        for ((key, value) in patch) {
            if (key in Overrides.META) continue
            val current = out[key]
            out[key] = if (value is JsonObject && current is JsonObject && key in setOf("values", "stats")) {
                JsonObject(current + value)
            } else {
                value
            }
        }
        return JsonObject(out)
    }

    fun card(card: Card, patch: JsonObject?): Card {
        if (patch == null) return card
        val merged: JsonElement = merge(json.encodeToJsonElement(Card.serializer(), card).jsonObject, patch)
        return json.decodeFromJsonElement(Card.serializer(), merged)
    }

    fun hero(hero: Hero, patch: JsonObject?): Hero {
        if (patch == null) return hero
        require("cards" !in patch && "handicap" !in patch && "extras" !in patch) { "Patch cards via overrides.cards" }
        val merged = merge(json.encodeToJsonElement(Hero.serializer(), hero).jsonObject, patch)
        return json.decodeFromJsonElement(Hero.serializer(), merged)
    }
}
