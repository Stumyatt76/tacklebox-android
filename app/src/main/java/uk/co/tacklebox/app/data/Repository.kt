package uk.co.tacklebox.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.Locale

class TackleboxRepository(context: Context) {
    private val db = Room.databaseBuilder(context, TackleboxDatabase::class.java, "tacklebox.db").build()
    private val dao = db.dao()
    val settings = dao.settings().map { it ?: defaults() }.distinctUntilChanged()
    val species = dao.species()
    val waters = dao.waters()
    val catches = dao.catches()
    val sessions = dao.sessions()
    val gear = dao.gear()
    val presets = dao.presets()
    private fun defaults() = AppSettings(unitSystem=if(Locale.getDefault().country in listOf("US","GB")) UnitSystem.IMPERIAL else UnitSystem.METRIC)
    suspend fun seed(samples:Boolean) {
        if (dao.speciesCount()==0) dao.addSpecies(seedSpecies)
        if (samples && dao.waterCount()==0) dao.addWaters(listOf(Water(name="Willow Mere",type=WaterType.LAKE,region="Norfolk",disciplines=listOf("COARSE"),swimNotes="Reeds on the west bank fish well at dusk."), Water(name="Upper Avon",type=WaterType.RIVER,region="Wiltshire",disciplines=listOf("GAME","COARSE"),swimNotes="Travel light; watch the level after rain.")))
        dao.saveSettings(defaults().copy(onboardingComplete=true))
    }
    suspend fun saveSettings(v:AppSettings)=dao.saveSettings(v)
    suspend fun addSpecies(name:String):Long=dao.addSpecies(Species(name=name, discipline=Discipline.COARSE))
    suspend fun addWater(v:Water)=dao.addWater(v)
    suspend fun addCatch(v:Catch, conditions:ConditionsSnapshot?=null):Long { val id=dao.addCatch(v); conditions?.let { dao.addConditions(it.copy(catchId=id)) }; return id }
    suspend fun startSession(waterId:Long?)=dao.addSession(FishingSession(waterId=waterId))
    suspend fun stopSession(id:Long)=dao.stopSession(id)
    suspend fun addGear(v:GearItem)=dao.addGear(v)
    suspend fun addPreset(v:TacklePreset)=dao.addPreset(v)
    fun species(id:Long)=dao.species(id); fun water(id:Long)=dao.water(id); fun catchById(id:Long)=dao.catchById(id)
    suspend fun deleteAllUserData() { dao.clearCatches(); dao.clearSessions(); dao.clearGear(); dao.clearPresets(); dao.clearWaters() }
    companion object { val seedSpecies=listOf(
        Species(name="Common carp",discipline=Discipline.COARSE,scientificName="Cyprinus carpio",about="Powerful, adaptable and endlessly individual."),
        Species(name="Mirror carp",discipline=Discipline.COARSE,scientificName="Cyprinus carpio",about="A distinctive scaled form of common carp."),
        Species(name="Tench",discipline=Discipline.COARSE,scientificName="Tinca tinca",about="A dawn-loving fish of still and slow waters."),
        Species(name="Roach",discipline=Discipline.COARSE,scientificName="Rutilus rutilus"), Species(name="Perch",discipline=Discipline.PREDATOR,scientificName="Perca fluviatilis"),
        Species(name="Northern pike",discipline=Discipline.PREDATOR,scientificName="Esox lucius"), Species(name="Barbel",discipline=Discipline.COARSE,scientificName="Barbus barbus"),
        Species(name="Brown trout",discipline=Discipline.GAME,scientificName="Salmo trutta"), Species(name="Atlantic salmon",discipline=Discipline.GAME,scientificName="Salmo salar"),
        Species(name="European sea bass",discipline=Discipline.SEA,scientificName="Dicentrarchus labrax"), Species(name="Atlantic mackerel",discipline=Discipline.SEA,scientificName="Scomber scombrus"), Species(name="Cod",discipline=Discipline.SEA,scientificName="Gadus morhua")) }
}
