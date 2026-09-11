/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.first
import uk.co.tacklebox.app.data.*
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.UUID

object PhotoBackup {
    private fun fields(vararg values: Pair<String,Any?>): Map<String,String> =
        values.mapNotNull { (key,value)->value?.let { key to it.toString() } }.toMap()
    /** What a backup was written from: the shareable file and how many photos had to be left out. */
    data class Written(val uri: Uri, val skippedPhotos: Int)

    /**
     * Builds the payload. A photo that can no longer be read — a library pick whose grant expired before the app
     * kept its own copies — is left out and counted in [skipped] rather than failing the whole backup, which used to
     * make one lost photo block the only feature that could have preserved the rest.
     */
    fun payload(context: Context, s: AppState, skipped: MutableList<String> = mutableListOf()): BackupPayload {
        val media=linkedMapOf<String,String>()
        fun photos(row: CatchRow): List<String> = row.allPhotoUris.mapNotNull { uri ->
            val bytes=runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use(PhotoBackupFormat::readBounded) }.getOrNull()
            if (bytes==null || bytes.isEmpty()) { skipped+=uri; return@mapNotNull null }
            val sha=PhotoBackupFormat.digest(bytes)
            media[sha]=Base64.getEncoder().encodeToString(bytes);sha
        }
        val fish=s.catches.map { row ->
            val item=row.item;val weather=row.conditions
            val data=fields("caughtAt" to item.caughtAt,"weightGrams" to item.weightGrams,"lengthCm" to item.lengthCm,
                "returned" to item.returned,"notes" to item.notes,"rig" to item.rig,"bait" to item.bait,
                "airTempC" to weather?.airTempC,"windDirection" to weather?.windDirection,
                "windSpeedKph" to weather?.windSpeedKph,"pressureHpa" to weather?.pressureHpa,
                "pressureTrend" to weather?.pressureTrend,"moonPhase" to weather?.moonPhase)
            BackupRecord(item.portableID,data,fields(
                "species" to s.species.firstOrNull { it.id==item.speciesId }?.portableID,
                "water" to s.waters.firstOrNull { it.id==item.waterId }?.portableID,
                "session" to s.sessions.firstOrNull { it.item.id==item.sessionId }?.item?.portableID),photos(row))
        }
        return BackupPayload(units=s.settings.unitSystem.name.lowercase(),disciplines=s.settings.activeDisciplines.map(String::lowercase),
            species=s.species.map { BackupRecord(it.portableID,fields("name" to it.name,"discipline" to it.discipline.name.lowercase(),
                "scientificName" to it.scientificName,"commonName" to it.commonName,"about" to it.about,
                "referencePhotoURL" to it.referencePhotoUrl,"photoAttribution" to it.photoAttribution)) },
            waters=s.waters.map { BackupRecord(it.portableID,fields("name" to it.name,
                "type" to if(it.type==WaterType.DAY_TICKET)"dayTicket" else it.type.name.lowercase(),
                "region" to it.region,"disciplines" to it.disciplines.joinToString(",") { d->d.lowercase() },"swimNotes" to it.swimNotes)) },
            sessions=s.sessions.map { BackupRecord(it.item.portableID,fields("startAt" to it.item.startAt,"endAt" to it.item.endAt,
                "notes" to it.item.notes),fields("water" to it.water?.portableID)) },
            catches=fish,gear=s.gear.map { BackupRecord(it.portableID,fields("name" to it.name,"category" to it.category.name.lowercase(),"notes" to it.notes)) },
            presets=s.presets.map { BackupRecord(it.portableID,fields("name" to it.name,"kind" to it.kind.name.lowercase())) },media=media)
    }
    fun write(context: Context, s: AppState): Written {
        val directory=File(context.cacheDir,"photo-backups").apply { mkdirs() }
        val file=File(directory,PhotoBackupFormat.FILE_NAME)
        val skipped=mutableListOf<String>()
        val bytes=PhotoBackupFormat.encode(payload(context,s,skipped))
        val temporary=File(directory,"pending-"+UUID.randomUUID())
        temporary.writeBytes(bytes)
        check(temporary.renameTo(file)) { "The photo backup could not be written." }
        return Written(FileProvider.getUriForFile(context,context.packageName+".fileprovider",file), skipped.size)
    }
    fun existing(s: AppState, p: BackupPayload): Map<String,Set<String>> {
        val speciesAliases=p.species.filter { record->s.species.any { it.name.equals(record.fields["name"],true) } }.map { it.id }
        val presetAliases=p.presets.filter { record->s.presets.any { it.name.equals(record.fields["name"],true) && it.kind.name.equals(record.fields["kind"],true) } }.map { it.id }
        val waterAliases=p.waters.filter { record->s.waters.any { it.name.equals(record.fields["name"],true) } }.map { it.id }
        return mapOf("species" to (s.species.map { it.portableID }+speciesAliases).toSet(),
            "waters" to (s.waters.map { it.portableID }+waterAliases).toSet(),"sessions" to s.sessions.map { it.item.portableID }.toSet(),
            "catches" to s.catches.map { it.item.portableID }.toSet(),"gear" to s.gear.map { it.portableID }.toSet(),
            "presets" to (s.presets.map { it.portableID }+presetAliases).toSet())
    }
    suspend fun restore(context: Context, repo: TackleboxRepository, p: BackupPayload, replace: Boolean): Int {
        PhotoBackupFormat.validate(p)
        val directory=File(context.filesDir,"backup-media/"+UUID.randomUUID()).apply { check(mkdirs()) }
        var committed=false
        val orphans=mutableSetOf<String>()
        try {
            // Staged once per distinct image, then copied per catch below: two catches must never share one
            // file, or deleting one catch deletes the other's photo.
            val staged=p.media.mapValues { (sha,encoded) -> File(directory,sha+".jpg").also { it.writeBytes(Base64.getDecoder().decode(encoded)) } }
            fun copyFor(sha:String):String { val copy=File(directory,sha+"-"+UUID.randomUUID()+".jpg"); staged.getValue(sha).copyTo(copy); return Uri.fromFile(copy).toString() }
            val result=repo.transaction {
                val dao=repo.dao
                val species=dao.speciesOnce().toMutableList();val waters=dao.watersOnce().toMutableList()
                val sessions=dao.sessions().first().map { it.item }.toMutableList()
                val catches=dao.catches().first().map { it.item }.toMutableList()
                val gear=dao.gearOnce().toMutableList();val presets=dao.presetsOnce().toMutableList()
                val speciesIds=mutableMapOf<String,Long>();val waterIds=mutableMapOf<String,Long>();val sessionIds=mutableMapOf<String,Long>()
                var writes=0
                p.species.forEach { r ->
                    val old=species.firstOrNull { it.portableID==r.id } ?: species.firstOrNull { it.name.equals(r.fields["name"],true) }
                    val item=Species(id=old?.id ?: 0,name=r.fields.getValue("name"),discipline=Discipline.valueOf(r.fields.getValue("discipline").uppercase()),
                        scientificName=r.fields["scientificName"],commonName=r.fields["commonName"],about=r.fields["about"],
                        referencePhotoUrl=r.fields["referencePhotoURL"],photoAttribution=r.fields["photoAttribution"],portableID=old?.portableID ?: r.id)
                    speciesIds[r.id]=if(old==null)dao.addSpecies(item).also { species.add(item.copy(id=it));writes++ }
                        else old.id.also { if(replace){dao.updateSpecies(item);writes++} }
                }
                p.waters.forEach { r ->
                    // Match by portable ID, then by name: a backup made on the other phone (or before IDs existed)
                    // carries IDs this journal has never seen, and "Alder Mere" must not come back twice.
                    val old=waters.firstOrNull { it.portableID==r.id } ?: waters.firstOrNull { it.name.equals(r.fields["name"],true) }
                    val item=Water(id=old?.id ?: 0,name=r.fields.getValue("name"),
                        type=if(r.fields["type"]=="dayTicket")WaterType.DAY_TICKET else WaterType.valueOf(r.fields.getValue("type").uppercase()),
                        region=r.fields["region"].orEmpty(),swimNotes=r.fields["swimNotes"].orEmpty(),
                        disciplines=r.fields["disciplines"].orEmpty().split(",").filter(String::isNotBlank).map(String::uppercase),portableID=old?.portableID ?: r.id)
                    waterIds[r.id]=if(old==null)dao.addWater(item).also { waters.add(item.copy(id=it));writes++ }
                        else old.id.also { if(replace){dao.updateWater(item);writes++} }
                }
                p.sessions.forEach { r ->
                    val old=sessions.firstOrNull { it.portableID==r.id }
                    val item=FishingSession(isTrialSession=old?.isTrialSession ?: false,id=old?.id ?: 0,waterId=r.references["water"]?.let(waterIds::get),
                        startAt=Instant.parse(r.fields.getValue("startAt")),endAt=r.fields["endAt"]?.let(Instant::parse),notes=r.fields["notes"].orEmpty(),portableID=r.id)
                    sessionIds[r.id]=if(old==null)dao.addSession(item).also { writes++ }
                        else old.id.also { if(replace){dao.updateSession(item);writes++} }
                }
                p.catches.forEach { r ->
                    val old=catches.firstOrNull { it.portableID==r.id }
                    if(old!=null && !replace)return@forEach
                    val uris=r.photos.map(::copyFor)
                    val item=Catch(id=old?.id ?: 0,speciesId=r.references["species"]?.let(speciesIds::get),
                        waterId=r.references["water"]?.let(waterIds::get),sessionId=r.references["session"]?.let(sessionIds::get),
                        weightGrams=r.fields["weightGrams"]?.toDouble(),lengthCm=r.fields["lengthCm"]?.toDouble(),
                        returned=r.fields.getValue("returned").toBooleanStrict(),notes=r.fields["notes"].orEmpty(),
                        caughtAt=Instant.parse(r.fields.getValue("caughtAt")),rig=r.fields["rig"],bait=r.fields["bait"],photoUri=uris.firstOrNull(),portableID=r.id)
                    // The old cover is read before the row is rewritten — afterwards it is already the new one.
                    val previousCover=old?.let { dao.coverPhotoFor(it.id) }
                    val id=if(old==null)dao.addCatch(item) else old.id.also { dao.updateCatch(item);dao.deleteConditionsFor(it) }
                    orphans+=repo.savePhotosInTransaction(id,uris)
                    if(previousCover!=null && previousCover !in uris)orphans+=previousCover
                    if(listOf("airTempC","windDirection","windSpeedKph","pressureHpa","pressureTrend","moonPhase").any { it in r.fields })
                        dao.addConditions(ConditionsSnapshot(catchId=id,airTempC=r.fields["airTempC"]?.toDouble(),
                            windDirection=r.fields["windDirection"],windSpeedKph=r.fields["windSpeedKph"]?.toDouble(),
                            pressureHpa=r.fields["pressureHpa"]?.toDouble(),pressureTrend=r.fields["pressureTrend"],moonPhase=r.fields["moonPhase"]))
                    writes++
                }
                p.gear.forEach { r ->
                    val old=gear.firstOrNull { it.portableID==r.id }
                    val item=GearItem(id=old?.id ?: 0,name=r.fields.getValue("name"),category=GearCategory.valueOf(r.fields.getValue("category").uppercase()),notes=r.fields["notes"].orEmpty(),portableID=r.id)
                    if(old==null){dao.addGear(item);writes++} else if(replace){dao.updateGear(item);writes++}
                }
                p.presets.forEach { r ->
                    val old=presets.firstOrNull { it.portableID==r.id } ?: presets.firstOrNull { it.name.equals(r.fields["name"],true) && it.kind.name.equals(r.fields["kind"],true) }
                    val item=TacklePreset(id=old?.id ?: 0,name=r.fields.getValue("name"),kind=PresetKind.valueOf(r.fields.getValue("kind").uppercase()),portableID=old?.portableID ?: r.id)
                    if(old==null){dao.addPreset(item);writes++} else if(replace){dao.updatePreset(item);writes++}
                }
                val settings=dao.settings().first() ?: AppSettings()
                // Unit preferences are explicit in preview; purchase state and provider credentials are never imported.
                dao.saveSettings(settings.copy(unitSystem=UnitSystem.valueOf(p.units.uppercase()),activeDisciplines=p.disciplines.map(String::uppercase),onboardingComplete=true))
                writes
            }
            committed=true
            staged.values.forEach { it.delete() }
            if(directory.listFiles().isNullOrEmpty())directory.delete()
            // Only now, with the rows committed, do the replaced files go — and only those nothing else names.
            repo.deletePhotoFiles(orphans)
            return result
        } finally {
            // Only remove the brand-new staging directory on failed transactions.
            if(!committed)directory.deleteRecursively()
        }
    }
}
