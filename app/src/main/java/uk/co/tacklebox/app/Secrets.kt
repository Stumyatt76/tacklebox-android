/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.tacklebox.app.data.TackleboxRepository
import kotlinx.coroutines.flow.first

/** Where a secret lives. The app uses the Keystore-encrypted preferences; tests use memory. */
interface SecretVault { fun read(name:String):String?; fun write(name:String,value:String?) }
class MemorySecretVault : SecretVault { private val values=HashMap<String,String>(); override fun read(name:String)=values[name]; override fun write(name:String,value:String?){ if(value==null)values.remove(name) else values[name]=value } }

/** The outcome of tapping Test on a provider card — iOS `DataServiceTestResult`. */
sealed interface DataServiceTestResult { data object Connected:DataServiceTestResult; data object Invalid:DataServiceTestResult; data class Unreachable(val message:String):DataServiceTestResult }

/** What the status capsule says: connected, invalid, or saved-but-untested. */
enum class DataServiceStatus(val title:String){ CONNECTED("Connected ✓"), INVALID("Invalid — check the key"), SAVED("Saved · tap Test") }

data class SecretsState(val worldTidesKey:String="",val speciesIdToken:String="",val worldTidesStatus:DataServiceStatus?=null,val iNaturalistStatus:DataServiceStatus?=null)

/**
 * The WorldTides key and the iNaturalist token, kept in the same Android Keystore-encrypted store as the OAuth
 * grant — the Android counterpart of the iOS Keychain stores. They used to sit in plain Room columns.
 *
 * `status` records whether a stored secret has been validated, so the capsule reads "Saved · tap Test" until it
 * has, rather than "Connected ✓" the moment it is pasted (the iOS `worldTidesConnectionStatus` default).
 */
class Secrets(private val vault:SecretVault) {
    constructor(context:Context):this(KeystoreVault(context))
    private class KeystoreVault(context:Context):SecretVault { private val inner=SpeciesSecureVault(context); override fun read(name:String)=runCatching{inner.read(name)}.getOrNull(); override fun write(name:String,value:String?)=inner.write(name,value) }
    val state=MutableStateFlow(load())

    private fun load()=SecretsState(
        worldTidesKey=vault.read(WORLD_TIDES).orEmpty(), speciesIdToken=vault.read(SPECIES_ID).orEmpty(),
        worldTidesStatus=status(vault.read(WORLD_TIDES_STATUS), vault.read(WORLD_TIDES)),
        iNaturalistStatus=status(vault.read(SPECIES_ID_STATUS), vault.read(SPECIES_ID)))
    private fun status(raw:String?,secret:String?)=raw?.let{r->DataServiceStatus.entries.firstOrNull{it.name==r}} ?: if(secret.isNullOrBlank())null else DataServiceStatus.SAVED

    /** Stores a secret; an empty value clears it. Returns false when the store refuses, so the card can say so. */
    fun save(name:String,value:String):Boolean = runCatching { vault.write(name,value.trim().ifEmpty{null}); vault.write(statusKey(name),null); state.value=load() }.isSuccess
    fun setStatus(name:String,status:DataServiceStatus?){ runCatching { vault.write(statusKey(name),status?.name) }; state.value=load() }
    val worldTidesKey:String get()=state.value.worldTidesKey
    val speciesIdToken:String get()=state.value.speciesIdToken

    /**
     * Moves any secret still sitting in the Room columns into the vault, once, then blanks the columns. A value
     * already in the vault wins — the columns were only ever written before this store existed.
     */
    suspend fun migrateFrom(repo:TackleboxRepository) {
        val settings=repo.settings.first()
        if(settings.speciesIdToken.isBlank()&&settings.worldTidesKey.isBlank())return
        if(vault.read(SPECIES_ID).isNullOrBlank()&&settings.speciesIdToken.isNotBlank())save(SPECIES_ID,settings.speciesIdToken)
        if(vault.read(WORLD_TIDES).isNullOrBlank()&&settings.worldTidesKey.isNotBlank())save(WORLD_TIDES,settings.worldTidesKey)
        repo.saveSettings(settings.copy(speciesIdToken="",worldTidesKey=""))
    }

    companion object {
        const val WORLD_TIDES="worldTidesKey"; const val SPECIES_ID="speciesIdToken"
        private const val WORLD_TIDES_STATUS="worldTidesStatus"; private const val SPECIES_ID_STATUS="speciesIdStatus"
        fun statusKey(name:String)=if(name==WORLD_TIDES)WORLD_TIDES_STATUS else SPECIES_ID_STATUS
    }
}
