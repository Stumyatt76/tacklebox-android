/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.services.*
import java.time.Instant
import kotlin.math.roundToInt

/**
 * `loaded` distinguishes "nothing read yet" from "nothing there".
 *
 * Without it the placeholder handed to `stateIn` was a default AppState whose onboardingComplete is false, so an
 * existing angler saw the onboarding flow for as long as the seven flows took to emit — normally a blink, but I
 * watched it hold for six seconds after an install, when the Room migration ran first. Tapping Continue through
 * it would have set onboardingComplete and could have seeded sample waters into a vault that already had fish.
 */
data class AppState(val loaded:Boolean=false,val settings:AppSettings=AppSettings(),val species:List<Species> = emptyList(),val waters:List<Water> = emptyList(),val catches:List<CatchRow> = emptyList(),val sessions:List<SessionRow> = emptyList(),val gear:List<GearItem> = emptyList(),val presets:List<TacklePreset> = emptyList())
sealed interface LiveState<out T>{ data object Idle:LiveState<Nothing>; data object Loading:LiveState<Nothing>; data class Data<T>(val value:T):LiveState<T>; data class Error(val message:String):LiveState<Nothing> }

class MainViewModel(app:Application):AndroidViewModel(app){
    val connection=(app as TackleboxApp).speciesConnection
    val store=(app as TackleboxApp).unlimitedStore
    val showAccess=MutableStateFlow(false)
    val repo=(app as TackleboxApp).repository
    init { viewModelScope.launch { repo.repairSpeciesMetadata() } }
    val state=combine(repo.settings,repo.species,repo.waters,repo.catches,repo.sessions,repo.gear,repo.presets){ a:Array<Any?> ->
        @Suppress("UNCHECKED_CAST") AppState(true,a[0] as AppSettings,a[1] as List<Species>,a[2] as List<Water>,a[3] as List<CatchRow>,a[4] as List<SessionRow>,a[5] as List<GearItem>,a[6] as List<TacklePreset>)
    }.onEach { SessionNotification.update(app,it.sessions.firstOrNull { row->row.item.endAt==null }) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),AppState())
    val marine=MutableStateFlow<LiveState<MarineResponse>>(LiveState.Idle); val river=MutableStateFlow<LiveState<RiverResult>>(LiveState.Idle)
    val marinePlace=MutableStateFlow(""); val riverPlace=MutableStateFlow("")
    val tides=MutableStateFlow<LiveState<TideResult>>(LiveState.Idle)
    val suggestions=MutableStateFlow<LiveState<List<SpeciesSuggestion>>>(LiveState.Idle)
    val exported=MutableStateFlow<Uri?>(null)
    /**
     * One message for the angler, under a title that says what it is about — "Couldn't save this catch", "Session
     * unavailable", "Photo backup" — as iOS's alerts do. Every notice used to be titled "Journal update".
     */
    data class Notice(val title:String,val message:String)
    val notice=MutableStateFlow<Notice?>(null)
    private fun fail(title:String,message:String){notice.value=Notice(title,message)}
    private fun tell(title:String,message:String){notice.value=Notice(title,message)}
    val photoBackupPlan=MutableStateFlow<BackupPayload?>(null)
    val photoBackupBusy=MutableStateFlow(false)
    val photoBackupExport=MutableStateFlow<Uri?>(null)
    /** Backups hold every photo in memory, so an OutOfMemoryError is a real outcome — an Error, which `catch (e: Exception)` let crash the app. */
    private fun backupFailure(e:Throwable,fallback:String):String = when(e){
        is OutOfMemoryError -> "There is not enough memory to process this photo backup. Free some memory and try again."
        else -> e.message?.takeIf { it.isNotBlank() } ?: fallback
    }
    fun createPhotoBackup()=viewModelScope.launch {
        if(photoBackupBusy.value)return@launch
        photoBackupBusy.value=true
        try {
            val written=withContext(Dispatchers.IO){PhotoBackup.write(getApplication(),state.value)}
            photoBackupExport.value=written.uri
            if(written.skippedPhotos>0)tell("Photo backup","Backup created. ${written.skippedPhotos} ${if(written.skippedPhotos==1)"photo" else "photos"} could no longer be read on this device and were left out.")
        }
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Throwable){fail("Photo backup",backupFailure(e,"Could not create this photo backup."))}
        finally { photoBackupBusy.value=false }
    }
    fun previewPhotoBackup(uri:Uri)=viewModelScope.launch {
        if(photoBackupBusy.value)return@launch
        photoBackupPlan.value=null
        photoBackupBusy.value=true
        try {
            photoBackupPlan.value=withContext(Dispatchers.IO){
                val data=getApplication<Application>().contentResolver.openInputStream(uri)?.use(PhotoBackupFormat::readBounded)
                    ?: error("The selected backup cannot be opened.")
                PhotoBackupFormat.decode(data)
            }
        }
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Throwable){fail("Photo backup",backupFailure(e,"The selected backup is invalid."))}
        finally { photoBackupBusy.value=false }
    }
    fun restorePhotoBackup(replace:Boolean)=viewModelScope.launch {
        if(photoBackupBusy.value)return@launch
        val payload=photoBackupPlan.value ?: return@launch
        photoBackupBusy.value=true
        try {
            val restored=withContext(Dispatchers.IO){PhotoBackup.restore(getApplication(),repo,payload,replace)}
            photoBackupPlan.value=null;tell("Photo backup","Restored "+restored+" records. Catch photos are included.")
        }
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Throwable){fail("Photo backup","The backup could not be restored. No imported records were kept. "+backupFailure(e,""))}
        finally { photoBackupBusy.value=false }
    }
    val importPlan=MutableStateFlow<JournalImport.Plan?>(null)
    val importResult=MutableStateFlow<JournalImport.Result?>(null)

    fun seed(samples:Boolean)=viewModelScope.launch{repo.seed(samples)}
    fun settings(v:AppSettings)=viewModelScope.launch{repo.saveSettings(v)}

    /**
     * A catch is stamped with the open session and the chosen water, and with the real weather at the moment it was
     * logged. Previously sessionId was never set at all and waterId was hardcoded null at the call site, so water
     * passports and session catch counts were permanently zero (TB-A-02, TB-A-03); and only the moon phase was
     * recorded, while the detail screen blamed the network for the rest (TB-A-09).
     */
    fun addCatch(speciesId:Long?,weight:Double?,length:Double?,rig:String?,bait:String?,returned:Boolean,waterId:Long?,photos:List<String> = emptyList(),notes:String="",caughtAt:Instant=Instant.now(),stamped:ConditionsSnapshot?=null,onDone:(Long)->Unit)=viewModelScope.launch{
        // Free logging needs a session to be under way — any open session, whether it began as a trial, was
        // imported or was restored. The catch joins it only if it was caught after the session started.
        val running=repo.openSession()
        if(!store.state.value.unlimited && running==null) { showAccess.value=true;return@launch }
        val openSession=running?.takeIf { !caughtAt.isBefore(it.startAt) }
        // The capture screen reads the conditions when it opens and shows them, so the angler can see what is being
        // stamped and retry a failed reading before saving. Falling back to a fresh capture keeps any other caller
        // working, and keeps a save honest if the screen never managed one.
        val conditions=if (CapturePolicy.canStampCurrentWeather(caughtAt)) stamped else null
        try {
        val id=repo.addCatch(
            Catch(speciesId=speciesId,weightGrams=weight,lengthCm=length,rig=rig?.ifBlank{null},bait=bait?.ifBlank{null},returned=returned,
                  waterId=waterId ?: openSession?.waterId, sessionId=openSession?.id, photoUri=photos.firstOrNull(), caughtAt=caughtAt, notes=notes.trim()),
            conditions, photos)
        onDone(id)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { fail("Couldn't save this catch","Your entries are still here. Please try saving again.") }
    }

    /** Best-effort: a failed or slow weather call must never stop a catch being saved, so the moon phase is the floor. */
    suspend fun captureConditions():ConditionsSnapshot? {
        val (lat,lon)=DeviceLocation.current(getApplication()) ?: return null
        val moon=Astronomy.calculate(latitude=lat,longitude=lon).moonPhase
        val weather=runCatching{Services.weather.current(lat,lon)}.getOrNull()
        val current=weather?.current
        return ConditionsSnapshot(catchId=0,airTempC=current?.temperature,windSpeedKph=current?.wind,
            windDirection=current?.windDirection?.let(::compass),pressureHpa=current?.pressure,
            // The trend needs the hourly series, which the weather call now asks for. Android had the column and
            // the importer but nothing that ever computed one (feature parity, 2026-09-08).
            pressureTrend=current?.pressure?.let{PressureTrend.of(it,weather.hourly,current.time)},
            moonPhase=moon)
    }

    private fun compass(degrees:Double):String {
        val points=listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
        return points[(((degrees % 360) / 22.5).roundToInt()) % 16]
    }

    /** Sun times and bite windows follow the device, falling back to central England when location is unavailable. */
    suspend fun solunarPlace():Pair<Double,Double> = DeviceLocation.current(getApplication()) ?: DeviceLocation.FALLBACK_INLAND

    /** A new water takes the active disciplines, as the iOS sheet's "Disciplines use your active app settings." says. */
    fun addWater(name:String,type:WaterType,region:String,onDone:(Long)->Unit={})=viewModelScope.launch{
        runCatching { repo.addWater(Water(name=name,type=type,region=region,disciplines=state.value.settings.activeDisciplines)) }
            .onSuccess(onDone).onFailure { fail("Couldn't save this water","Please try again.") }
    }
    fun updateWater(v:Water,onDone:()->Unit={})=viewModelScope.launch {
        runCatching { repo.saveWater(v) }.onSuccess { onDone() }.onFailure { fail("Couldn't save this water","Please try again.") }
    }
    fun updateCatch(v:Catch,photos:List<String>?=null)=viewModelScope.launch{repo.saveCatch(v);photos?.let{repo.savePhotos(v.id,it)}}
    /** Adds or reuses a species by name and hands back its id, so the capture screen can select it straight away. */
    fun addSpecies(name:String,discipline:Discipline?=null,onDone:(Long)->Unit={})=viewModelScope.launch{
        runCatching { repo.addSpecies(name,state.value.settings.activeDisciplines,discipline) }.onSuccess(onDone).onFailure { fail("Couldn't add this species",it.message ?: "Please try again.") }
    }
    fun startSession(water:Long?,onDone:()->Unit={})=viewModelScope.launch {
        runCatching { repo.startSession(water,store.state.value.unlimited) }.onSuccess { onDone() }.onFailure { fail("Session unavailable",it.message ?: "Could not start this session.") }
    }
    fun saveSession(value:FishingSession,onDone:()->Unit={})=viewModelScope.launch {
        runCatching { repo.saveSession(value) }.onSuccess { onDone() }.onFailure { fail("Could not save session",it.message ?: "Could not save this session. Your entries are still here; try again.") }
    }
    /** Assigns a session a water from the "Choose Water" sheet. */
    fun assignSessionWater(session:FishingSession,waterId:Long?)=viewModelScope.launch {
        runCatching { repo.saveSession(session.copy(waterId=waterId)) }.onFailure { fail("Could not save session","Please try again. Your last saved session is kept.") }
    }
    fun saveGear(value:GearItem,onDone:()->Unit={})=viewModelScope.launch {
        runCatching { repo.saveGear(value) }.onSuccess { onDone() }.onFailure { fail("Couldn't save this gear",it.message ?: "Please try again.") }
    }
    fun deleteSession(id:Long)=viewModelScope.launch {
        runCatching { repo.deleteSession(id) }.onFailure { fail("Could not save session","Please try again. Your last saved session is kept.") }
    }
    fun stopSession(id:Long)=viewModelScope.launch {
        runCatching { repo.stopSession(id) }.onFailure { fail("Could not save session","Your session is still open. Please try again.") }
    }
    fun addGear(name:String,category:GearCategory)=viewModelScope.launch{repo.addGear(GearItem(name=name,category=category))}
    fun deleteGear(v:GearItem)=viewModelScope.launch{repo.deleteGear(v)}
    fun addPreset(name:String,kind:PresetKind)=viewModelScope.launch{repo.addPreset(TacklePreset(name=name,kind=kind))}
    fun deletePreset(v:TacklePreset)=viewModelScope.launch{repo.deletePreset(v)}
    fun deleteCatch(id:Long)=viewModelScope.launch{repo.deleteCatch(id)}
    fun deleteWater(id:Long)=viewModelScope.launch{repo.deleteWater(id)}

    /**
     * Fills in a species' reference photo and description from iNaturalist, once (feature parity, 2026-09-08).
     *
     * Only ever adds: a name or note the angler has already got stays as it is, and a failed lookup leaves the
     * record exactly as it was rather than blanking it. Skipped entirely when the photo is already there, so
     * opening a species record repeatedly does not re-fetch.
     */
    fun enrichSpecies(species:Species)=viewModelScope.launch{
        if(!species.referencePhotoUrl.isNullOrBlank()) return@launch
        val info=SpeciesLookup.enrich(species.name, species.scientificName) ?: return@launch
        repo.saveSpecies(species.copy(
            scientificName=species.scientificName ?: info.scientificName,
            commonName=species.commonName ?: info.commonName,
            about=species.about ?: info.about,
            referencePhotoUrl=info.referencePhotoUrl,
            photoAttribution=info.photoAttribution))
    }

    fun marine()=viewModelScope.launch{
        marine.value=LiveState.Loading
        val place=DeviceLocation.current(getApplication())
        marinePlace.value=if(place==null) "Reference location: the Solent — your location is unavailable" else "Near your approximate location"
        val (lat,lon)=place ?: DeviceLocation.FALLBACK_COASTAL
        marine.value=runCatching{LiveState.Data(Services.marine.forecast(lat,lon))}.getOrElse{LiveState.Error("Couldn’t update the sea forecast. Try again.")}
    }
    /**
     * Tide predictions (feature parity with iOS, 2026-09-08).
     *
     * The error carries the service's own message rather than a generic one, because the two failures need
     * different things from the angler: "not available for your area" is answered by adding a WorldTides key,
     * "couldn't be updated" by finding a signal.
     */
    fun tides()=viewModelScope.launch{
        tides.value=LiveState.Loading
        val place=DeviceLocation.current(getApplication())
        marinePlace.value=if(place==null) "Reference location: the Solent — your location is unavailable" else "Near your approximate location"
        val (lat,lon)=place ?: DeviceLocation.FALLBACK_COASTAL
        val key=repo.settings.first().worldTidesKey
        tides.value=runCatching{LiveState.Data(Tides.tides(lat,lon,key))}
            .getOrElse{LiveState.Error(it.message ?: "Couldn’t update tide predictions. Try again.")}
    }
    fun river()=viewModelScope.launch{
        river.value=LiveState.Loading
        val place=DeviceLocation.current(getApplication())
        riverPlace.value=if(place==null) "Reference location: central England — your location is unavailable" else "Near your approximate location"
        val (lat,lon)=place ?: DeviceLocation.FALLBACK_INLAND
        river.value=runCatching{LiveState.Data(Rivers.gauges(lat,lon))}
            .getOrElse{LiveState.Error(it.message ?: "Couldn’t update river gauges. Try again.")}
    }

    /** Sends the cover photo and the rounded position to iNaturalist, as iOS does, and reports in its words. */
    fun identify(photoUri:String?,token:String)=viewModelScope.launch{
        if(photoUri==null){suggestions.value=LiveState.Error(SpeciesId.NO_RESULTS);return@launch}
        suggestions.value=LiveState.Loading
        suggestions.value=try{
            val bytes=withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openInputStream(Uri.parse(photoUri))?.use{it.readBytes()}}
                ?: throw SpeciesIdException(SpeciesId.NO_RESULTS)
            val place=DeviceLocation.current(getApplication())
            LiveState.Data(SpeciesId.identify(bytes,connection.apiToken(token),place?.first,place?.second))
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:SpeciesIdException){LiveState.Error(e.message ?: SpeciesId.UNREACHABLE)}
        catch(_:Exception){LiveState.Error(SpeciesId.UNAUTHORIZED)}
    }
    fun clearSuggestions(){suggestions.value=LiveState.Idle}
    /** Accepts a suggestion: reuses a species under either name, else adds one, and hands back the id to select. */
    fun pickSuggestion(s:SpeciesSuggestion,onDone:(Long)->Unit)=viewModelScope.launch{
        runCatching { repo.speciesForSuggestion(s.displayName,s.scientificName,s.commonName,state.value.settings.activeDisciplines) }
            .onSuccess(onDone).onFailure { fail("Couldn't add this species",it.message ?: "Please try again.") }
    }

    /** Writes the whole journal to a shareable JSON file. The button existed but did nothing at all (TB-A-05). */
    fun exportJson()=viewModelScope.launch{
        exported.value=runCatching{withContext(Dispatchers.IO){JournalExport.write(getApplication(),state.value)}}.getOrElse{fail("Couldn't create the export","Your journal is unchanged. Try again in a moment.");null}
    }
    fun clearExport(){exported.value=null}
    fun clearNotice(){notice.value=null}

    /** Parses a picked file and works out what importing it would do, writing nothing until the user confirms. */
    fun planImport(uri:Uri)=viewModelScope.launch{
        runCatching{
            val json=withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openInputStream(uri)?.use{it.readBytes().decodeToString()}}
                ?: throw JournalImport.Failure("That file could not be opened.")
            JournalImport.plan(json, JournalImport.ExistingVault.from(state.value))
        }.onSuccess{plan->
            if(plan.isEmpty)fail("Couldn't import that file","That journal has nothing in it to import.") else importPlan.value=plan
        }.onFailure{fail("Couldn't import that file",it.message ?: "That file could not be read.")}
    }
    fun cancelImport(){importPlan.value=null}
    fun clearImportResult(){importResult.value=null}

    /** Merges the plan into the vault. Existing records are matched by name and reused, never replaced. */
    fun confirmImport()=viewModelScope.launch{
        val plan=importPlan.value ?: return@launch
        importPlan.value=null
        runCatching { repo.applyImport(plan) }
            .onSuccess { importResult.value=it }
            .onFailure { fail("Couldn't import that file","The import could not be saved. No imported records were kept. Try again.") }
    }

    /** "Reset to a fresh vault". Onboarding replays on its own once `onboardingComplete` drops to false. */
    fun resetVault(onDone:()->Unit={})=viewModelScope.launch{
        runCatching { repo.resetVault() }.onSuccess { onDone() }.onFailure { fail("Couldn't reset your vault","The reset was not completed. Please try again.") }
    }
}
