package uk.co.tacklebox.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.services.*
import java.time.Instant

data class AppState(val settings:AppSettings=AppSettings(),val species:List<Species> = emptyList(),val waters:List<Water> = emptyList(),val catches:List<CatchRow> = emptyList(),val sessions:List<SessionRow> = emptyList(),val gear:List<GearItem> = emptyList(),val presets:List<TacklePreset> = emptyList())
sealed interface LiveState<out T>{ data object Idle:LiveState<Nothing>; data object Loading:LiveState<Nothing>; data class Data<T>(val value:T):LiveState<T>; data class Error(val message:String):LiveState<Nothing> }
class MainViewModel(app:Application):AndroidViewModel(app){
    val repo=(app as TackleboxApp).repository
    val state=combine(repo.settings,repo.species,repo.waters,repo.catches,repo.sessions,repo.gear,repo.presets){ a:Array<Any?> ->
        @Suppress("UNCHECKED_CAST") AppState(a[0] as AppSettings,a[1] as List<Species>,a[2] as List<Water>,a[3] as List<CatchRow>,a[4] as List<SessionRow>,a[5] as List<GearItem>,a[6] as List<TacklePreset>)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),AppState())
    val marine=MutableStateFlow<LiveState<MarineResponse>>(LiveState.Idle); val river=MutableStateFlow<LiveState<RiverItems>>(LiveState.Idle)
    fun seed(samples:Boolean)=viewModelScope.launch{repo.seed(samples)}
    fun settings(v:AppSettings)=viewModelScope.launch{repo.saveSettings(v)}
    fun addCatch(speciesId:Long?,weight:Double?,length:Double?,rig:String?,bait:String?,returned:Boolean,waterId:Long?,photo:String?,onDone:(Long)->Unit)=viewModelScope.launch{ val moon=Astronomy.calculate().moonPhase; val id=repo.addCatch(Catch(speciesId=speciesId,weightGrams=weight,lengthCm=length,rig=rig,bait=bait,returned=returned,waterId=waterId,photoUri=photo,caughtAt=Instant.now()),ConditionsSnapshot(catchId=0,moonPhase=moon)); onDone(id) }
    fun startSession(water:Long?)=viewModelScope.launch{repo.startSession(water)}; fun stopSession(id:Long)=viewModelScope.launch{repo.stopSession(id)}
    fun addGear(name:String,category:GearCategory)=viewModelScope.launch{repo.addGear(GearItem(name=name,category=category))}; fun addPreset(name:String,kind:PresetKind)=viewModelScope.launch{repo.addPreset(TacklePreset(name=name,kind=kind))}
    fun marine(lat:Double=50.7,lon:Double=-1.9)=viewModelScope.launch{marine.value=LiveState.Loading;marine.value=runCatching{LiveState.Data(Services.marine.forecast(lat,lon))}.getOrElse{LiveState.Error("Couldn’t update the sea forecast. Try again.")}}
    fun river(lat:Double=52.5,lon:Double=-1.5)=viewModelScope.launch{river.value=LiveState.Loading;river.value=runCatching{LiveState.Data(Services.river.readings(lat,lon))}.getOrElse{LiveState.Error("Couldn’t update river gauges. Try again.")}}
    fun deleteData()=viewModelScope.launch{repo.deleteAllUserData()}
}
