/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.Locale

class TackleboxRepository internal constructor(private val db: TackleboxDatabase, private val photoRoot: java.io.File? = null) {
    constructor(context: Context) : this(Room.databaseBuilder(context, TackleboxDatabase::class.java, "tacklebox.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build(), context.filesDir)
    internal val dao = db.dao()
    /** Removes the app's own photo files once their rows are gone. Only files under `filesDir` are ever touched. */
    private fun deletePhotoFiles(uris: Collection<String>) { val root = photoRoot ?: return; uris.forEach { uk.co.tacklebox.app.PhotoStore.delete(it, root) } }
    suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }
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
        // Insert any canonical species that are missing rather than only seeding an empty table — otherwise an
        // existing tester never gains one that is added later, which is how the two catalogues drifted. iOS has
        // always done this through SpeciesStore.existingOrInsert.
        val known = dao.speciesOnce().map { it.name.lowercase() }.toSet()
        seedSpecies.filterNot { it.name.lowercase() in known }.forEach { dao.addSpecies(it) }
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
    suspend fun repairSpeciesMetadata() = db.withTransaction {
        for (item in dao.speciesOnce()) {
            val expected = seedSpecies.firstOrNull { it.name.equals(item.name, ignoreCase=true) }?.scientificName ?: continue
            if (item.scientificName != expected) dao.updateSpecies(item.copy(scientificName=expected,
                commonName=null, about=null, referencePhotoUrl=null, photoAttribution=null))
        }
    }
    suspend fun saveSettings(v:AppSettings)=db.withTransaction {
        val used=dao.settings().first()?.freeSessionsStarted ?: 0
        dao.saveSettings(v.copy(freeSessionsStarted=maxOf(used,v.freeSessionsStarted)))
    }
    /**
     * Adds a species the angler typed, or returns the one that already exists under that name (case-insensitive) —
     * a second "Tench" split the PB board, the species filter and the year summary across two ids. New species are
     * filed under the first discipline the angler has active, so they appear in the chip row they were added from;
     * everything used to land on COARSE and vanished for anyone who had switched that discipline off.
     */
    suspend fun addSpecies(name:String, activeDisciplines:List<String> = emptyList(), discipline:Discipline? = null):Long = db.withTransaction {
        val trimmed=name.trim()
        require(trimmed.isNotEmpty()) { "Give this species a name." }
        dao.speciesOnce().firstOrNull { it.name.equals(trimmed, ignoreCase=true) }?.let { return@withTransaction it.id }
        // The Add Species sheet lets the angler pick the discipline, as iOS does; without a choice it lands on the
        // first active one.
        val chosen=discipline ?: activeDisciplines.firstNotNullOfOrNull { d -> Discipline.entries.firstOrNull { it.name.equals(d, ignoreCase=true) } } ?: Discipline.COARSE
        dao.addSpecies(Species(name=trimmed, discipline=chosen, scientificName=uk.co.tacklebox.app.services.SpeciesLookup.canonicalName(trimmed)))
    }
    /**
     * The species an iNaturalist suggestion resolves to: an existing one under either the common or the scientific
     * name, else a new record under the display name carrying both, filed under the first active discipline.
     */
    suspend fun speciesForSuggestion(displayName:String, scientificName:String, commonName:String?, activeDisciplines:List<String>):Long = db.withTransaction {
        val names=listOfNotNull(commonName, scientificName).map { it.trim() }.filter { it.isNotEmpty() }
        dao.speciesOnce().firstOrNull { sp -> names.any { it.equals(sp.name, ignoreCase=true) } }?.let { return@withTransaction it.id }
        val discipline=activeDisciplines.firstNotNullOfOrNull { d -> Discipline.entries.firstOrNull { it.name.equals(d, ignoreCase=true) } } ?: Discipline.COARSE
        dao.addSpecies(Species(name=displayName.trim(), discipline=discipline, scientificName=scientificName, commonName=commonName))
    }
    suspend fun addWater(v:Water)=dao.addWater(v)
    suspend fun addSession(v:FishingSession)=dao.addSession(v)
    suspend fun existingSpecies():List<Species> = dao.speciesOnce()
    suspend fun existingWaters():List<Water> = dao.watersOnce()
    suspend fun existingGear():List<GearItem> = dao.gearOnce()
    suspend fun existingPresets():List<TacklePreset> = dao.presetsOnce()
    suspend fun addCatch(v:Catch, conditions:ConditionsSnapshot?=null, photos:List<String> = emptyList()):Long = db.withTransaction {
        val id=dao.addCatch(v)
        conditions?.let { dao.addConditions(it.copy(catchId=id)) }
        savePhotos(id, photos)
        id
    }
    suspend fun startSession(waterId:Long?,unlimited:Boolean=false) = db.withTransaction {
        require(dao.openSession()==null) { "A session is already running." }
        val current=dao.settings().first() ?: defaults()
        require(uk.co.tacklebox.app.SessionAllowance.canStart(current.freeSessionsStarted,unlimited)) { "Your two free sessions are complete. Unlock Unlimited to start another." }
        if(!unlimited)dao.saveSettings(current.copy(freeSessionsStarted=current.freeSessionsStarted+1))
        dao.addSession(FishingSession(waterId=waterId,isTrialSession=!unlimited))
    }
    suspend fun saveSession(value:FishingSession) = db.withTransaction {
        val current=dao.sessions().first().firstOrNull { it.item.id==value.id }
            ?: error("This session is no longer in the journal.")
        val problem=uk.co.tacklebox.app.SessionRules.error(value.startAt,value.endAt,current.catches.map { it.caughtAt })
        require(problem==null) { problem.orEmpty() }
        dao.updateSession(value)
    }
    suspend fun saveGear(value:GearItem) {
        require(value.name.isNotBlank()) { "Give this gear a name." }
        if(value.id==0L)dao.addGear(value) else dao.updateGear(value)
    }
    suspend fun stopSession(id:Long)=dao.stopSession(id)
    /** Deleting a session keeps its catches, which simply lose the session. The free allowance is not returned. */
    suspend fun deleteSession(id:Long) = db.withTransaction { dao.detachCatchesFromSession(id); dao.deleteSession(id) }
    suspend fun addGear(v:GearItem)=dao.addGear(v)
    suspend fun deleteGear(v:GearItem)=dao.deleteGear(v)
    suspend fun addPreset(v:TacklePreset)=dao.addPreset(v)
    suspend fun deletePreset(v:TacklePreset)=dao.deletePreset(v)
    suspend fun saveSpecies(v:Species)=dao.updateSpecies(v)
    suspend fun saveWater(v:Water)=dao.updateWater(v)
    suspend fun saveCatch(v:Catch)=dao.updateCatch(v)
    /**
     * Replaces a catch's photos: the first is the cover on the row itself, the rest become CatchPhoto records.
     * Files the catch no longer references are deleted once the rows are written.
     */
    suspend fun savePhotos(catchId:Long, uris:List<String>) {
        val previous=(listOfNotNull(dao.coverPhotoFor(catchId))+dao.extraPhotosFor(catchId)).toSet()
        dao.clearPhotosFor(catchId)
        if (uris.size > 1) dao.addPhotos(uris.drop(1).mapIndexed { index, uri -> CatchPhoto(catchId=catchId, uri=uri, order=index) })
        deletePhotoFiles(previous - uris.toSet())
    }
    suspend fun openSession():FishingSession?=dao.openSession()
    suspend fun deleteCatch(id:Long) {
        val photos=db.withTransaction {
            val uris=listOfNotNull(dao.coverPhotoFor(id))+dao.extraPhotosFor(id)
            dao.deleteConditionsFor(id); dao.clearPhotosFor(id); dao.deleteCatch(id)
            uris
        }
        deletePhotoFiles(photos)
    }
    /** Deleting a water keeps its catches and sessions; there are no foreign keys, so detach them explicitly. */
    suspend fun deleteWater(id:Long) = db.withTransaction { dao.detachCatchesFromWater(id); dao.detachSessionsFromWater(id); dao.deleteWater(id) }
    fun species(id:Long)=dao.species(id); fun water(id:Long)=dao.water(id); fun catchById(id:Long)=dao.catchById(id)
    /**
     * Merges an import plan. Nothing existing is deleted or overwritten: waters, species, gear and presets are
     * matched by name and reused, and catches already present were flagged as duplicates during planning.
     */
    suspend fun applyImport(plan: uk.co.tacklebox.app.JournalImport.Plan): uk.co.tacklebox.app.JournalImport.Result = db.withTransaction {
        val result = uk.co.tacklebox.app.JournalImport.Result()

        val waters = existingWaters().associateBy { it.name.lowercase() }.toMutableMap()
        for (record in plan.waters) {
            if (waters.containsKey(record.name.lowercase())) continue
            val id = dao.addWater(Water(name=record.name, type=record.type, region=record.region, swimNotes=record.swimNotes))
            waters[record.name.lowercase()] = Water(id=id, name=record.name, type=record.type, region=record.region, swimNotes=record.swimNotes)
            result.waters++
        }

        val knownSessions = dao.sessions().first().associate {
            uk.co.tacklebox.app.JournalImport.sessionFingerprint(it.item.startAt, it.item.endAt, it.water?.name, it.item.notes) to it.item.id
        }.toMutableMap()
        val sessionsByFileId = mutableMapOf<Int, Long>()
        for (record in plan.sessions) {
            val key = uk.co.tacklebox.app.JournalImport.sessionFingerprint(record.startAt, record.endAt, record.water, record.notes)
            val id = knownSessions[key] ?: dao.addSession(FishingSession(
                waterId=record.water?.let { waters[it.lowercase()]?.id }, startAt=record.startAt, endAt=record.endAt, notes=record.notes
            )).also { knownSessions[key] = it; result.sessions++ }
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
        val knownCatches = dao.catches().first().map { uk.co.tacklebox.app.JournalImport.fingerprint(it.species?.name, it.item.caughtAt) }.toMutableSet()
        for (record in plan.catches) {
            if (!knownCatches.add(uk.co.tacklebox.app.JournalImport.fingerprint(record.species, record.caughtAt))) continue
            var speciesId: Long? = null
            record.species?.takeIf { it.isNotBlank() }?.let { name ->
                val found = species[name.lowercase()]
                speciesId = found?.id ?: dao.addSpecies(Species(name=name, discipline=Discipline.COARSE, scientificName=uk.co.tacklebox.app.services.SpeciesLookup.canonicalName(name) ?: record.scientificName)).also {
                    species[name.lowercase()] = Species(id=it, name=name, discipline=Discipline.COARSE, scientificName=uk.co.tacklebox.app.services.SpeciesLookup.canonicalName(name) ?: record.scientificName)
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
        result
    }

    /**
     * "Delete catches, waters & gear". The starter rig and bait presets are put back afterwards — they were only
     * ever seeded at onboarding, so a reset left the capture screen with nothing but "＋ Add" — and the app's own
     * photo files go with their rows, so a reset does not leave every picture on disk.
     */
    suspend fun deleteAllUserData() {
        val photos=db.withTransaction {
            val uris=dao.allCoverPhotos()+dao.allExtraPhotos()
            dao.clearConditions(); dao.clearPhotos(); dao.clearCatches(); dao.clearSessions(); dao.clearGear(); dao.clearPresets(); dao.clearWaters()
            seedPresets.forEach { dao.addPreset(it) }
            uris
        }
        deletePhotoFiles(photos)
    }
    /**
     * "Reset to a fresh vault", with the same meaning as `SeedData.reset` on iOS: every record and photo goes, the
     * species catalogue and the starter presets come back, units and disciplines return to their defaults, and
     * onboarding replays. The free-session count is the one thing kept, so a reset cannot mint new free sessions.
     */
    suspend fun resetVault() {
        val photos=db.withTransaction {
            val used=dao.settings().first()?.freeSessionsStarted ?: 0
            val uris=dao.allCoverPhotos()+dao.allExtraPhotos()
            dao.clearConditions(); dao.clearPhotos(); dao.clearCatches(); dao.clearSessions(); dao.clearGear(); dao.clearPresets(); dao.clearWaters(); dao.clearSpecies()
            seedSpecies.forEach { dao.addSpecies(it) }
            seedPresets.forEach { dao.addPreset(it) }
            dao.saveSettings(defaults().copy(freeSessionsStarted=used, onboardingComplete=false))
            uris
        }
        deletePhotoFiles(photos)
    }
    companion object {
        /**
         * The same two waters as `SeedData.swift`, so "Begin with sample waters" means the same thing on both
         * platforms. iOS's names win because they are the ones in the committed App Store screenshots.
         *
         */
        val sampleWaters = listOf(
            Water(name="Alder Mere", type=WaterType.SYNDICATE, region="Oxfordshire", disciplines=listOf("CARP","COARSE"), swimNotes="Reeds on the west bank fish well at dusk."),
            Water(name="River Lea", type=WaterType.RIVER, region="Hertfordshire", disciplines=listOf("COARSE"), swimNotes="Travel light; watch the level after rain."))
        val seedPresets = listOf("Ronnie rig","Hair rig","Method feeder","Waggler","Ledger").map { TacklePreset(name=it, kind=PresetKind.RIG) } +
            listOf("Boilie","Sweetcorn","Maggots","Pellets","Bread","Worm").map { TacklePreset(name=it, kind=PresetKind.BAIT) }
        /**
         * The species catalogue, identical to `SeedData.species` on iOS in names, order and discipline (TB-P-15).
         *
         * All-discipline, not coarse-first: the store listing promises "coarse or carp, river or sea", and the app
         * ships tides, sea state and species identification. iOS seeded eleven carp and coarse fish and Android
         * twelve spanning every discipline, with different names for the same animal — "Northern pike" against
         * "Pike", "Common carp" against "Common Carp".
         */
        val seedSpecies=listOf(
        Species(name="Common Carp",discipline=Discipline.CARP,scientificName="Cyprinus carpio",about="Powerful, adaptable and endlessly individual."),
        Species(name="Mirror Carp",discipline=Discipline.CARP,scientificName="Cyprinus carpio",about="A distinctive scaled form of common carp."),
        Species(name="Tench",discipline=Discipline.COARSE,scientificName="Tinca tinca",about="A dawn-loving fish of still and slow waters."),
        Species(name="Bream",discipline=Discipline.COARSE,scientificName="Abramis brama"),
        Species(name="Roach",discipline=Discipline.COARSE,scientificName="Rutilus rutilus"),
        Species(name="Rudd",discipline=Discipline.COARSE,scientificName="Scardinius erythrophthalmus"),
        Species(name="Perch",discipline=Discipline.PREDATOR,scientificName="Perca fluviatilis"),
        Species(name="Pike",discipline=Discipline.PREDATOR,scientificName="Esox lucius"),
        Species(name="Barbel",discipline=Discipline.COARSE,scientificName="Barbus barbus"),
        Species(name="Chub",discipline=Discipline.COARSE,scientificName="Squalius cephalus"),
        Species(name="Eel",discipline=Discipline.COARSE,scientificName="Anguilla anguilla"),
        Species(name="Brown Trout",discipline=Discipline.GAME,scientificName="Salmo trutta"),
        Species(name="Atlantic Salmon",discipline=Discipline.GAME,scientificName="Salmo salar"),
        Species(name="European Sea Bass",discipline=Discipline.SEA,scientificName="Dicentrarchus labrax"),
        Species(name="Atlantic Mackerel",discipline=Discipline.SEA,scientificName="Scomber scombrus"),
        Species(name="Cod",discipline=Discipline.SEA,scientificName="Gadus morhua")) }
}