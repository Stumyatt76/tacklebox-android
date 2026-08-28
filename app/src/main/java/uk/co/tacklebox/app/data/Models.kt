/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

enum class UnitSystem { METRIC, IMPERIAL }
enum class Discipline { COARSE, GAME, SEA, PREDATOR }
enum class WaterType { LAKE, RIVER, CANAL, RESERVOIR, SEA }
enum class GearCategory { ROD, REEL, LINE, HOOK, LURE, NET, CLOTHING, OTHER }
enum class PresetKind { RIG, BAIT }

class Converters {
    @TypeConverter fun instant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)
    @TypeConverter fun instant(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun strings(v: String): List<String> = v.split('|').filter(String::isNotBlank)
    @TypeConverter fun strings(v: List<String>): String = v.joinToString("|")
}

@Entity data class AppSettings(@PrimaryKey val id: Int = 1, val unitSystem: UnitSystem = UnitSystem.METRIC, val activeDisciplines: List<String> = listOf("COARSE", "GAME", "SEA"), val onboardingComplete: Boolean = false, val backupEnabled: Boolean = false, val speciesIdToken: String = "")
@Entity data class Species(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val discipline: Discipline, val scientificName: String? = null, val commonName: String? = null, val about: String? = null, val referencePhotoUrl: String? = null, val photoAttribution: String? = null)
@Entity data class Water(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val type: WaterType, val region: String, val disciplines: List<String> = emptyList(), val swimNotes: String = "")
@Entity(indices = [Index("waterId")]) data class FishingSession(@PrimaryKey(autoGenerate = true) val id: Long = 0, val waterId: Long? = null, val startAt: Instant = Instant.now(), val endAt: Instant? = null, val notes: String = "")
@Entity(indices = [Index("speciesId"), Index("sessionId"), Index("waterId")]) data class Catch(@PrimaryKey(autoGenerate = true) val id: Long = 0, val speciesId: Long? = null, val weightGrams: Double? = null, val lengthCm: Double? = null, val returned: Boolean = true, val photoUri: String? = null, val rig: String? = null, val bait: String? = null, val caughtAt: Instant = Instant.now(), val sessionId: Long? = null, val waterId: Long? = null)
@Entity(indices = [Index(value=["catchId"], unique=true)]) data class ConditionsSnapshot(@PrimaryKey(autoGenerate = true) val id: Long = 0, val catchId: Long, val airTempC: Double? = null, val windDirection: String? = null, val windSpeedKph: Double? = null, val pressureHpa: Double? = null, val pressureTrend: String? = null, val moonPhase: String? = null)
@Entity data class GearItem(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val category: GearCategory, val notes: String = "")
@Entity data class TacklePreset(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val kind: PresetKind)

data class CatchRow(@Embedded val item: Catch, @Relation(parentColumn="speciesId", entityColumn="id") val species: Species?, @Relation(parentColumn="waterId", entityColumn="id") val water: Water?, @Relation(parentColumn="id", entityColumn="catchId") val conditions: ConditionsSnapshot?)
data class SessionRow(@Embedded val item: FishingSession, @Relation(parentColumn="waterId", entityColumn="id") val water: Water?, @Relation(parentColumn="id", entityColumn="sessionId", entity=Catch::class) val catches: List<Catch>)

@Dao interface TackleboxDao {
    @Query("SELECT * FROM AppSettings WHERE id=1") fun settings(): Flow<AppSettings?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSettings(value: AppSettings)
    @Query("SELECT * FROM Species ORDER BY name") fun species(): Flow<List<Species>>
    @Query("SELECT * FROM Species WHERE id=:id") fun species(id:Long): Flow<Species?>
    @Insert suspend fun addSpecies(value: Species): Long
    @Insert suspend fun addSpecies(values: List<Species>)
    @Query("SELECT COUNT(*) FROM Species") suspend fun speciesCount(): Int
    @Query("SELECT * FROM Water ORDER BY name") fun waters(): Flow<List<Water>>
    @Query("SELECT * FROM Water WHERE id=:id") fun water(id:Long): Flow<Water?>
    @Insert suspend fun addWater(value: Water): Long
    @Insert suspend fun addWaters(values: List<Water>)
    @Query("SELECT COUNT(*) FROM Water") suspend fun waterCount(): Int
    @Transaction @Query("SELECT * FROM Catch ORDER BY caughtAt DESC") fun catches(): Flow<List<CatchRow>>
    @Transaction @Query("SELECT * FROM Catch WHERE id=:id") fun catchById(id:Long): Flow<CatchRow?>
    @Insert suspend fun addCatch(value: Catch): Long
    @Insert suspend fun addConditions(value: ConditionsSnapshot)
    @Transaction @Query("SELECT * FROM FishingSession ORDER BY startAt DESC") fun sessions(): Flow<List<SessionRow>>
    @Insert suspend fun addSession(value: FishingSession): Long
    @Query("UPDATE FishingSession SET endAt=:at WHERE id=:id") suspend fun stopSession(id:Long, at:Instant=Instant.now())
    @Query("SELECT * FROM GearItem ORDER BY category,name") fun gear(): Flow<List<GearItem>>
    @Insert suspend fun addGear(value:GearItem)
    @Delete suspend fun deleteGear(value:GearItem)
    @Query("SELECT * FROM TacklePreset ORDER BY kind,name") fun presets(): Flow<List<TacklePreset>>
    @Insert suspend fun addPreset(value:TacklePreset)
    @Query("DELETE FROM Catch") suspend fun clearCatches()
    @Query("DELETE FROM FishingSession") suspend fun clearSessions()
    @Query("DELETE FROM Water") suspend fun clearWaters()
    @Query("DELETE FROM GearItem") suspend fun clearGear()
    @Query("DELETE FROM TacklePreset") suspend fun clearPresets()
}

@Database(entities=[AppSettings::class,Species::class,Water::class,FishingSession::class,Catch::class,ConditionsSnapshot::class,GearItem::class,TacklePreset::class], version=1, exportSchema=false)
@TypeConverters(Converters::class)
abstract class TackleboxDatabase: RoomDatabase() { abstract fun dao(): TackleboxDao }