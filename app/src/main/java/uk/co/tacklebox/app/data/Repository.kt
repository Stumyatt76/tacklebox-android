/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.Locale

class TackleboxRepository(context: Context) {
    private val db = Room.databaseBuilder(context, TackleboxDatabase::class.java, "tacklebox.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    private val dao = db.dao()
    val settings = dao.settings().map { it ?: defaults() }.distinctUntilChanged()
    val species = dao.species()
    val waters = dao.waters()
    val catches = dao.catches()
    val sessions = dao.sessions()
    val gear = dao.gear()
    val presets = dao.presets()
    // Only the US is an imperial-first market, and the AppSettings default is METRIC. Including "GB" here made a UK
    // device disagree with its own model default and with iOS, which keys off Locale.measurementSystem (TB-A-14).
    internal fun defaults() = AppSettings(unitSystem=if(Locale.getDefault().country=="US") UnitSystem.IMPERIAL else UnitSystem.METRIC)
    suspend fun seed(samples:Boolean) {
        if (dao.speciesCount()==0) dao.addSpecies(seedSpecies)
        // Presets were never seeded, so a fresh install had none — and PresetField renders its chips only when
        // presets exist, leaving Android with bare Rig and Bait text fields where iOS offers a chip grid (TB-P-02).
        // The same eleven as SeedData.swift, in the same order, so the two apps open identically.
        if (dao.presetsOnce().isEmpty()) seedPresets.forEach { dao.addPreset(it) }
        // The same two waters as SeedData.swift, so "Begin with sample waters" means the same thing on both
        // platforms. iOS's names win because they are the ones in the committed App Store screenshots.
        if (samples && dao.waterCount()==0) dao.addWaters(sampleWaters)
        // Merge onto what is already stored. saveSettings is REPLACE on id=1, so writing defaults() wholesale
        // discarded any unit choice, the species-ID token and the backup flag (TB-A-14).
        dao.saveSettings((dao.settings().first() ?: defaults()).copy(onboardingComplete=true))
    }
    suspend fun saveSettings(v:AppSettings)=dao.saveSettings(v)
    suspend fun addSpecies(name:String):Long=dao.addSpecies(Species(name=name, discipline=Discipline.COARSE))
    suspend fun addWater(v:Water)=dao.addWater(v)
    suspend fun addSession(v:FishingSession)=dao.addSession(v)
    suspend fun existingSpecies():List<Species> = dao.speciesOnce()
    suspend fun existingWaters():List<Water> = dao.watersOnce()
    suspend fun existingGear():List<GearItem> = dao.gearOnce()
    suspend fun existingPresets():List<TacklePreset> = dao.presetsOnce()
    suspend fun addCatch(v:Catch, conditions:ConditionsSnapshot?=null):Long { val id=dao.addCatch(v); conditions?.let { dao.addConditions(it.copy(catchId=id)) }; return id }
    suspend fun startSession(waterId:Long?)=dao.addSession(FishingSession(waterId=waterId))
    suspend fun stopSession(id:Long)=dao.stopSession(id)
    suspend fun addGear(v:GearItem)=dao.addGear(v)
    suspend fun deleteGear(v:GearItem)=dao.deleteGear(v)
    suspend fun addPreset(v:TacklePreset)=dao.addPreset(v)
    suspend fun deletePreset(v:TacklePreset)=dao.deletePreset(v)
    suspend fun saveWater(v:Water)=dao.updateWater(v)
    suspend fun saveCatch(v:Catch)=dao.updateCatch(v)
    /** Replaces a catch's photos: the first is the cover on the row itself, the rest become CatchPhoto records. */
    suspend fun savePhotos(catchId:Long, uris:List<String>) {
        dao.clearPhotosFor(catchId)
        if (uris.size > 1) dao.addPhotos(uris.drop(1).mapIndexed { index, uri -> CatchPhoto(catchId=catchId, uri=uri, order=index) })
    }
    suspend fun openSession():FishingSession?=dao.openSession()
    suspend fun deleteCatch(id:Long){ dao.deleteConditionsFor(id); dao.clearPhotosFor(id); dao.deleteCatch(id) }
    /** Deleting a water keeps its catches and sessions; there are no foreign keys, so detach them explicitly. */
    suspend fun deleteWater(id:Long){ dao.detachCatchesFromWater(id); dao.detachSessionsFromWater(id); dao.deleteWater(id) }
    fun species(id:Long)=dao.species(id); fun water(id:Long)=dao.water(id); fun catchById(id:Long)=dao.catchById(id)
    /**
     * Merges an import plan. Nothing existing is deleted or overwritten: waters, species, gear and presets are
     * matched by name and reused, and catches already present were flagged as duplicates during planning.
     */
    suspend fun applyImport(plan: uk.co.tacklebox.app.JournalImport.Plan): uk.co.tacklebox.app.JournalImport.Result {
        val result = uk.co.tacklebox.app.JournalImport.Result()

        val waters = existingWaters().associateBy { it.name.lowercase() }.toMutableMap()
        for (record in plan.waters) {
            if (waters.containsKey(record.name.lowercase())) continue
            val id = dao.addWater(Water(name=record.name, type=record.type, region=record.region, swimNotes=record.swimNotes))
            waters[record.name.lowercase()] = Water(id=id, name=record.name, type=record.type, region=record.region, swimNotes=record.swimNotes)
            result.waters++
        }

        // Session ids in a file are local to that file, so they are recreated and remapped.
        val sessionsByFileId = mutableMapOf<Int, Long>()
        for (record in plan.sessions) {
            val waterId = record.water?.let { waters[it.lowercase()]?.id }
            val id = dao.addSession(FishingSession(waterId=waterId, startAt=record.startAt, endAt=record.endAt, notes=record.notes))
            result.sessions++
            record.id?.let { sessionsByFileId[it] = id }
        }

        val gear = existingGear().map { it.name.lowercase() }.toMutableSet()
        for (record in plan.gear) {
            if (!gear.add(record.name.lowercase())) continue
            dao.addGear(GearItem(name=record.name, category=record.category, notes=record.notes))
            result.gear++
        }

        val presets = existingPresets().map { "${it.kind}:${it.name.lowercase()}" }.toMutableSet()
        for (record in plan.presets) {
            if (!presets.add("${record.kind}:${record.name.lowercase()}")) continue
            dao.addPreset(TacklePreset(name=record.name, kind=record.kind))
            result.presets++
        }

        val species = existingSpecies().associateBy { it.name.lowercase() }.toMutableMap()
        for (record in plan.catches.filter { !it.isDuplicate }) {
            var speciesId: Long? = null
            record.species?.takeIf { it.isNotBlank() }?.let { name ->
                val found = species[name.lowercase()]
                speciesId = found?.id ?: dao.addSpecies(Species(name=name, discipline=Discipline.COARSE, scientificName=record.scientificName)).also {
                    species[name.lowercase()] = Species(id=it, name=name, discipline=Discipline.COARSE, scientificName=record.scientificName)
                    result.species++
                }
            }
            val catchId = dao.addCatch(Catch(
                speciesId=speciesId, weightGrams=record.weightGrams, lengthCm=record.lengthCm,
                returned=record.returned, rig=record.rig, bait=record.bait, caughtAt=record.caughtAt,
                notes=record.notes, sessionId=record.sessionId?.let { sessionsByFileId[it] },
                waterId=record.water?.let { waters[it.lowercase()]?.id }))
            record.conditions?.let {
                dao.addConditions(ConditionsSnapshot(catchId=catchId, airTempC=it.airTempC, windDirection=it.windDirection,
                    windSpeedKph=it.windSpeedKph, pressureHpa=it.pressureHpa, pressureTrend=it.pressureTrend, moonPhase=it.moonPhase))
            }
            result.catches++
        }
        return result
    }

    suspend fun deleteAllUserData() { dao.clearPhotos(); dao.clearCatches(); dao.clearSessions(); dao.clearGear(); dao.clearPresets(); dao.clearWaters() }
    companion object {
        /**
         * The same two waters as `SeedData.swift`, so "Begin with sample waters" means the same thing on both
         * platforms. iOS's names win because they are the ones in the committed App Store screenshots.
         *
         * Alder Mere is a *syndicate* on iOS and a lake here, because Android's `WaterType` has five values to
         * iOS's ten and no syndicate among them. That enum divergence is real but is a model problem, not an
         * onboarding one; it is recorded separately as TB-P-14.
         */
        val sampleWaters = listOf(
            Water(name="Alder Mere", type=WaterType.LAKE, region="Oxfordshire", disciplines=listOf("CARP","COARSE"), swimNotes="Reeds on the west bank fish well at dusk."),
            Water(name="River Lea", type=WaterType.RIVER, region="Hertfordshire", disciplines=listOf("COARSE"), swimNotes="Travel light; watch the level after rain."))
        val seedPresets = listOf("Ronnie rig","Hair rig","Method feeder","Waggler","Ledger").map { TacklePreset(name=it, kind=PresetKind.RIG) } +
            listOf("Boilie","Sweetcorn","Maggots","Pellets","Bread","Worm").map { TacklePreset(name=it, kind=PresetKind.BAIT) }
        val seedSpecies=listOf(
        Species(name="Common carp",discipline=Discipline.COARSE,scientificName="Cyprinus carpio",about="Powerful, adaptable and endlessly individual."),
        Species(name="Mirror carp",discipline=Discipline.COARSE,scientificName="Cyprinus carpio",about="A distinctive scaled form of common carp."),
        Species(name="Tench",discipline=Discipline.COARSE,scientificName="Tinca tinca",about="A dawn-loving fish of still and slow waters."),
        Species(name="Roach",discipline=Discipline.COARSE,scientificName="Rutilus rutilus"), Species(name="Perch",discipline=Discipline.PREDATOR,scientificName="Perca fluviatilis"),
        Species(name="Northern pike",discipline=Discipline.PREDATOR,scientificName="Esox lucius"), Species(name="Barbel",discipline=Discipline.COARSE,scientificName="Barbus barbus"),
        Species(name="Brown trout",discipline=Discipline.GAME,scientificName="Salmo trutta"), Species(name="Atlantic salmon",discipline=Discipline.GAME,scientificName="Salmo salar"),
        Species(name="European sea bass",discipline=Discipline.SEA,scientificName="Dicentrarchus labrax"), Species(name="Atlantic mackerel",discipline=Discipline.SEA,scientificName="Scomber scombrus"), Species(name="Cod",discipline=Discipline.SEA,scientificName="Gadus morhua")) }
}