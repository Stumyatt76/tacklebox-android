/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

data class BackupRecord(
    val id: String, val fields: Map<String,String> = emptyMap(),
    val references: Map<String,String> = emptyMap(), val photos: List<String> = emptyList()
)
data class BackupPayload(
    val createdAt: String = Instant.now().toString(),
    val units: String = "metric", val disciplines: List<String> = emptyList(),
    val species: List<BackupRecord> = emptyList(), val waters: List<BackupRecord> = emptyList(),
    val sessions: List<BackupRecord> = emptyList(), val catches: List<BackupRecord> = emptyList(),
    val gear: List<BackupRecord> = emptyList(), val presets: List<BackupRecord> = emptyList(),
    val media: Map<String,String> = emptyMap()
) {
    fun groups(): List<Pair<String,List<BackupRecord>>> = listOf("species" to species,"waters" to waters,
        "sessions" to sessions,"catches" to catches,"gear" to gear,"presets" to presets)
    val photoCount: Int get() = catches.sumOf { it.photos.size }
}
data class BackupPreview(val additions: Int, val matching: Int, val photoCount: Int)

/** Portable, checksummed JSON envelope. Photo bytes are embedded; credentials never are. */
object PhotoBackupFormat {
    const val MAX_BYTES = 256 * 1024 * 1024
    const val FILE_NAME = "tacklebox-photo-backup.tacklebox"
    private val gson=Gson()
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun encode(payload: BackupPayload): ByteArray {
        validate(payload)
        val data=gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val result=gson.toJson(mapOf("app" to "Tacklebox","format" to "photo-backup","version" to 1,
            "sha256" to digest(data),"payload" to Base64.getEncoder().encodeToString(data))).toByteArray(Charsets.UTF_8)
        require(result.size<=MAX_BYTES) { "This photo backup exceeds the 256 MB limit." }
        return result
    }
    fun decode(bytes: ByteArray): BackupPayload {
        require(bytes.size<=MAX_BYTES) { "This photo backup exceeds the 256 MB limit." }
        try {
            val outer=JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            require(outer["app"].asString=="Tacklebox" && outer["format"].asString=="photo-backup") { "Choose a Tacklebox photo backup." }
            require(outer["version"].asString=="1") { "This backup needs a newer version of Tacklebox." }
            val data=Base64.getDecoder().decode(outer["payload"].asString)
            require(digest(data)==outer["sha256"].asString) { "The backup checksum does not match. The file may be damaged." }
            val p=JsonParser.parseString(data.toString(Charsets.UTF_8)).asJsonObject
            // Gson does not apply Kotlin defaults to absent fields: reject incomplete files before decoding.
            listOf("createdAt","units","disciplines","species","waters","sessions","catches","gear","presets","media").forEach {
                require(p.has(it) && !p[it].isJsonNull) { "The backup is incomplete." }
            }
            val payload=gson.fromJson(p,BackupPayload::class.java)
            validate(payload)
            return payload
        } catch(e: IllegalArgumentException) { throw e }
        catch(_: Exception) { throw IllegalArgumentException("The backup is not a valid Tacklebox photo backup.") }
    }
    fun validate(p: BackupPayload) {
        Instant.parse(p.createdAt)
        require(p.units in setOf("metric","imperial")) { "The backup uses unknown measurement units." }
        require(p.disciplines.isNotEmpty() && p.disciplines.all { d->uk.co.tacklebox.app.data.Discipline.entries.any { it.name.equals(d,true) } }) { "The backup uses unknown disciplines." }
        p.species.forEach { r->require(!r.fields["name"].isNullOrBlank() && uk.co.tacklebox.app.data.Discipline.entries.any { it.name.equals(r.fields["discipline"],true) }) { "A species record is invalid." } }
        p.waters.forEach { r->require(!r.fields["name"].isNullOrBlank() && (r.fields["type"]=="dayTicket" || uk.co.tacklebox.app.data.WaterType.entries.any { it.name.equals(r.fields["type"],true) })) { "A water record is invalid." } }
        p.gear.forEach { r->require(!r.fields["name"].isNullOrBlank() && uk.co.tacklebox.app.data.GearCategory.entries.any { it.name.equals(r.fields["category"],true) }) { "A gear record is invalid." } }
        p.presets.forEach { r->require(!r.fields["name"].isNullOrBlank() && uk.co.tacklebox.app.data.PresetKind.entries.any { it.name.equals(r.fields["kind"],true) }) { "A preset is invalid." } }
        val groups=p.groups()
        require(groups.sumOf { it.second.size } <= 50_000) { "The backup contains too many records." }
        val ids=groups.associate { (kind,rows) ->
            require(rows.map { it.id }.distinct().size==rows.size) { "The backup contains duplicate record IDs." }
            rows.forEach { r ->
                require(r.id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "The backup contains an invalid record ID." }
                require(r.fields.size<=40 && r.references.size<=5 && r.photos.size<=100) { "The backup record is too large." }
                require(r.fields.values.all { it.length<=100_000 }) { "A backup note is too long." }
            }
            kind to rows.map { it.id }.toSet()
        }
        fun reference(r: BackupRecord,key: String,kind: String) {
            r.references[key]?.let { require(it in ids.getValue(kind)) { "A backup relationship points to a missing record." } }
        }
        p.sessions.forEach { r ->
            Instant.parse(r.fields.getValue("startAt"))
            r.fields["endAt"]?.let { require(Instant.parse(it)>=Instant.parse(r.fields.getValue("startAt"))) { "A session ends before it starts." } }
            reference(r,"water","waters")
        }
        p.catches.forEach { r ->
            Instant.parse(r.fields.getValue("caughtAt"))
            listOf("weightGrams","lengthCm","airTempC","windSpeedKph","pressureHpa").forEach { key -> r.fields[key]?.let {
                val number=it.toDouble();require(number.isFinite() && kotlin.math.abs(number)<=100_000_000 && (key !in setOf("weightGrams","lengthCm","windSpeedKph") || number>=0)) { "A catch measurement is invalid." }
            } }
            require(r.fields["returned"] in setOf("true","false")) { "A catch return status is missing." }
            reference(r,"species","species");reference(r,"water","waters");reference(r,"session","sessions")
            r.photos.forEach { require(it in p.media) { "A catch photo is missing from the backup." } }
        }
        require(p.media.size<=20_000) { "The backup contains too many photos." }
        var size=0L
        p.media.forEach { (sha,encoded) ->
            require(sha.matches(Regex("[a-f0-9]{64}"))) { "A photo checksum is invalid." }
            val bytes=Base64.getDecoder().decode(encoded)
            size+=bytes.size
            require(bytes.isNotEmpty() && size<=MAX_BYTES && digest(bytes)==sha) { "A backup photo is damaged or too large." }
        }
    }
    fun preview(p: BackupPayload, existing: Map<String,Set<String>>): BackupPreview {
        var add=0;var match=0
        p.groups().forEach { (kind,rows)->rows.forEach { if(it.id in existing[kind].orEmpty())match++ else add++ } }
        return BackupPreview(add,match,p.photoCount)
    }
    fun readBounded(stream: java.io.InputStream): ByteArray {
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(16*1024)
        while(true) {
            val count=stream.read(buffer);if(count<0)break
            require(output.size().toLong()+count<=MAX_BYTES) { "This photo backup exceeds the 256 MB limit." }
            output.write(buffer,0,count)
        }
        return output.toByteArray()
    }
}
