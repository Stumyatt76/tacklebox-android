/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.services

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class SpeciesInfo(
    val scientificName: String?,
    val commonName: String?,
    val about: String?,
    val referencePhotoUrl: String?,
    val photoAttribution: String?,
)

interface TaxaApi {
    @GET("v1/taxa") suspend fun search(
        @Query("q") query: String,
        @Query("rank") rank: String = "species",
        @Query("per_page") perPage: Int = 30,
        @Query("locale") locale: String = "en",
    ): TaxaResponse
}

data class TaxaResponse(val results: List<Taxon> = emptyList())
data class Taxon(
    val name: String?,
    @SerializedName("iconic_taxon_name") val iconicTaxonName: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("preferred_common_name") val preferredCommonName: String?,
    @SerializedName("wikipedia_summary") val wikipediaSummary: String?,
    @SerializedName("default_photo") val defaultPhoto: TaxonPhoto?,
)
data class TaxonPhoto(@SerializedName("medium_url") val mediumUrl: String?, val attribution: String?)

/**
 * Reference photo and description for a species, from iNaturalist (feature parity with iOS, 2026-09-08).
 *
 * `Species` has carried `about`, `referencePhotoUrl` and `photoAttribution` since the schema was written, and the
 * importer fills them, but nothing on Android ever fetched any — so a species record showed a flat icon where iOS
 * showed a photograph and a summary. This is the same public taxa endpoint iOS uses, and it needs no key: the
 * token in Settings is for photo identification, which is a different endpoint.
 */
object SpeciesLookup {
    private val api: TaxaApi = Services.taxa

    fun canonicalName(name: String): String? = uk.co.tacklebox.app.data.TackleboxRepository.seedSpecies
        .firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.scientificName

    suspend fun enrich(name: String, scientificName: String? = null): SpeciesInfo? = runCatching {
        validatedInfo(api.search(canonicalName(name) ?: scientificName ?: name), name, scientificName)
    }.getOrNull()

    fun validatedInfo(response: TaxaResponse, name: String, scientificName: String? = null): SpeciesInfo? {
        val expected = canonicalName(name) ?: scientificName
        val matches = response.results.filter {
            it.isActive != false && it.iconicTaxonName in setOf("Actinopterygii", "Elasmobranchii", "Sarcopterygii", "Myxini", "Cephalaspidomorphi") &&
                if (expected != null) it.name.equals(expected, ignoreCase = true)
                else it.preferredCommonName.equals(name, ignoreCase = true) || it.name.equals(name, ignoreCase = true)
        }
        val taxon = matches.singleOrNull() ?: return null
        return SpeciesInfo(
            scientificName = taxon.name?.ifBlank { null },
            commonName = taxon.preferredCommonName?.ifBlank { null },
            about = taxon.wikipediaSummary?.stripHtml()?.ifBlank { null },
            referencePhotoUrl = taxon.defaultPhoto?.mediumUrl?.ifBlank { null },
            photoAttribution = taxon.defaultPhoto?.attribution?.ifBlank { null },
        )
    }

    /** The summary comes back as HTML. Tags out, entities decoded, whitespace collapsed — as iOS does. */
    private fun String.stripHtml(): String =
        replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
