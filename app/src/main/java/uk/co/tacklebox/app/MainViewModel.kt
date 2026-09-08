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
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.services.*
import java.time.Instant
import kotlin.math.roundToInt

data class AppState(val settings:AppSettings=AppSettings(),val species:List<Species> = emptyList(),val waters:List<Water> = emptyList(),val catches:List<CatchRow> = emptyList(),val sessions:List<SessionRow> = emptyList(),val gear:List<GearItem> = emptyList(),val presets:List<TacklePreset> = emptyList())
sealed interface LiveState<out T>{ data object Idle:LiveState<Nothing>; data object Loading:LiveState<Nothing>; data class Data<T>(val value:T):LiveState<T>; data class Error(val message:String):LiveState<Nothing> }

class MainViewModel(app:Application):AndroidViewModel(app){
    val repo=(app as TackleboxApp).repository
    val state=combine(repo.settings,repo.species,repo.waters,repo.catches,repo.sessions,repo.gear,repo.presets){ a:Array<Any?> ->
        @Suppress("UNCHECKED_CAST") AppState(a[0] as AppSettings,a[1] as List<Species>,a[2] as List<Water>,a[3] as List<CatchRow>,a[4] as List<SessionRow>,a[5] as List<GearItem>,a[6] as List<TacklePreset>)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),AppState())
    val marine=MutableStateFlow<LiveState<MarineResponse>>(LiveState.Idle); val river=MutableStateFlow<LiveState<RiverItems>>(LiveState.Idle)
    val suggestions=MutableStateFlow<LiveState<List<SpeciesSuggestion>>>(LiveState.Idle)
    val exported=MutableStateFlow<Uri?>(null)
    val notice=MutableStateFlow<String?>(null)
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
        val openSession=repo.openSession()
        // The capture screen reads the conditions when it opens and shows them, so the angler can see what is being
        // stamped and retry a failed reading before saving. Falling back to a fresh capture keeps any other caller
        // working, and keeps a save honest if the screen never managed one.
        val conditions=stamped ?: captureConditions()
        val id=repo.addCatch(
            Catch(speciesId=speciesId,weightGrams=weight,lengthCm=length,rig=rig?.ifBlank{null},bait=bait?.ifBlank{null},returned=returned,
                  waterId=waterId ?: openSession?.waterId, sessionId=openSession?.id, photoUri=photos.firstOrNull(), caughtAt=caughtAt, notes=notes.trim()),
            conditions)
        repo.savePhotos(id, photos)
        onDone(id)
    }

    /** Best-effort: a failed or slow weather call must never stop a catch being saved, so the moon phase is the floor. */
    suspend fun captureConditions():ConditionsSnapshot {
        val (lat,lon)=DeviceLocation.current(getApplication()) ?: DeviceLocation.FALLBACK_INLAND
        val moon=Astronomy.calculate(latitude=lat,longitude=lon).moonPhase
        val current=runCatching{Services.weather.current(lat,lon).current}.getOrNull()
        return ConditionsSnapshot(catchId=0,airTempC=current?.temperature,windSpeedKph=current?.wind,
            windDirection=current?.windDirection?.let(::compass),pressureHpa=current?.pressure,moonPhase=moon)
    }

    private fun compass(degrees:Double):String {
        val points=listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
        return points[(((degrees % 360) / 22.5).roundToInt()) % 16]
    }

    /** Sun times and bite windows follow the device, falling back to central England when location is unavailable. */
    suspend fun solunarPlace():Pair<Double,Double> = DeviceLocation.current(getApplication()) ?: DeviceLocation.FALLBACK_INLAND

    fun addWater(name:String,type:WaterType,region:String)=viewModelScope.launch{repo.addWater(Water(name=name,type=type,region=region))}
    fun updateWater(v:Water)=viewModelScope.launch{repo.saveWater(v)}
    fun updateCatch(v:Catch,photos:List<String>?=null)=viewModelScope.launch{repo.saveCatch(v);photos?.let{repo.savePhotos(v.id,it)}}
    fun addSpecies(name:String)=viewModelScope.launch{repo.addSpecies(name)}
    fun startSession(water:Long?)=viewModelScope.launch{repo.startSession(water)}
    fun stopSession(id:Long)=viewModelScope.launch{repo.stopSession(id)}
    fun addGear(name:String,category:GearCategory)=viewModelScope.launch{repo.addGear(GearItem(name=name,category=category))}
    fun deleteGear(v:GearItem)=viewModelScope.launch{repo.deleteGear(v)}
    fun addPreset(name:String,kind:PresetKind)=viewModelScope.launch{repo.addPreset(TacklePreset(name=name,kind=kind))}
    fun deletePreset(v:TacklePreset)=viewModelScope.launch{repo.deletePreset(v)}
    fun deleteCatch(id:Long)=viewModelScope.launch{repo.deleteCatch(id)}
    fun deleteWater(id:Long)=viewModelScope.launch{repo.deleteWater(id)}

    fun marine()=viewModelScope.launch{
        marine.value=LiveState.Loading
        val (lat,lon)=DeviceLocation.current(getApplication()) ?: DeviceLocation.FALLBACK_COASTAL
        marine.value=runCatching{LiveState.Data(Services.marine.forecast(lat,lon))}.getOrElse{LiveState.Error("Couldn’t update the sea forecast. Try again.")}
    }
    fun river()=viewModelScope.launch{
        river.value=LiveState.Loading
        val (lat,lon)=DeviceLocation.current(getApplication()) ?: DeviceLocation.FALLBACK_INLAND
        river.value=runCatching{LiveState.Data(Services.river.readings(lat,lon))}.getOrElse{LiveState.Error("Couldn’t update river gauges. Try again.")}
    }

    fun identify(photoUri:String?,token:String)=viewModelScope.launch{
        if(photoUri==null){suggestions.value=LiveState.Error("Add a photo first.");return@launch}
        suggestions.value=LiveState.Loading
        suggestions.value=runCatching{
            val bytes=getApplication<Application>().contentResolver.openInputStream(Uri.parse(photoUri))?.use{it.readBytes()}
                ?: throw SpeciesIdException("That photo could not be read.")
            LiveState.Data(SpeciesId.identify(bytes,token))
        }.getOrElse{LiveState.Error(it.message ?: "Identification failed.")}
    }
    fun clearSuggestions(){suggestions.value=LiveState.Idle}

    /** Writes the whole journal to a shareable JSON file. The button existed but did nothing at all (TB-A-05). */
    fun exportJson()=viewModelScope.launch{
        exported.value=runCatching{JournalExport.write(getApplication(),state.value)}.getOrElse{notice.value="Couldn’t create the export file.";null}
    }
    fun clearExport(){exported.value=null}
    fun clearNotice(){notice.value=null}

    /** Parses a picked file and works out what importing it would do, writing nothing until the user confirms. */
    fun planImport(uri:Uri)=viewModelScope.launch{
        runCatching{
            val json=getApplication<Application>().contentResolver.openInputStream(uri)?.use{it.readBytes().decodeToString()}
                ?: throw JournalImport.Failure("That file could not be opened.")
            JournalImport.plan(json, JournalImport.ExistingVault.from(state.value))
        }.onSuccess{plan->
            if(plan.isEmpty)notice.value="That journal has nothing in it to import." else importPlan.value=plan
        }.onFailure{notice.value=it.message ?: "That file could not be read."}
    }
    fun cancelImport(){importPlan.value=null}
    fun clearImportResult(){importResult.value=null}

    /** Merges the plan into the vault. Existing records are matched by name and reused, never replaced. */
    fun confirmImport()=viewModelScope.launch{
        val plan=importPlan.value ?: return@launch
        importPlan.value=null
        importResult.value=repo.applyImport(plan)
    }

    fun deleteData()=viewModelScope.launch{repo.deleteAllUserData()}
}
