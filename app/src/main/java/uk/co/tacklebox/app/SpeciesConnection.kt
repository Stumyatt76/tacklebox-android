/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SpeciesOAuth {
    const val REDIRECT="uk.co.tacklebox.app://oauth/inaturalist"
    fun random():String=base64(ByteArray(32).also(SecureRandom()::nextBytes))
    fun base64(bytes:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun challenge(verifier:String)=base64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun code(url:Uri,state:String):String {
        require(url.scheme=="uk.co.tacklebox.app" && url.host=="oauth" && url.path=="/inaturalist" && url.getQueryParameters("state").size==1 && url.getQueryParameter("state")==state && url.getQueryParameter("error")==null && url.getQueryParameters("code").size==1) { "The sign-in security check failed. Please reconnect." }
        return url.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: error("Sign-in did not return a code.")
    }
}

/** Only ciphertext is stored in preferences. The AES key stays in Android Keystore. */
class SpeciesSecureVault(context:Context) {
    private val preferences=context.getSharedPreferences("tacklebox-species-connection",Context.MODE_PRIVATE)
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias="uk.co.tacklebox.species.oauth"
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generateKey()
        }
    }
    @Synchronized fun read(name:String):String? {
        val text=preferences.getString(name,null) ?: return null
        val parts=text.split(".");require(parts.size==2)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])))
        return cipher.doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8)
    }
    @Synchronized fun write(name:String,value:String?) {
        if(value==null){check(preferences.edit().remove(name).commit());return}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val encrypted=Base64.getEncoder().encodeToString(cipher.iv)+"."+Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
        check(preferences.edit().putString(name,encrypted).commit()) { "The connection could not be stored securely." }
    }
}
data class SpeciesAuthorization(val access:String,val refresh:String?,val expires:Long?)
data class PendingSpeciesSignIn(val verifier:String,val state:String,val started:Long)
data class SpeciesConnectionState(val connected:Boolean=false,val busy:Boolean=false,val message:String?=null)

class SpeciesConnection(private val context:Context) {
    val state=MutableStateFlow(SpeciesConnectionState())
    private val vault=SpeciesSecureVault(context)
    private val client=OkHttpClient.Builder().followRedirects(false).callTimeout(20,TimeUnit.SECONDS).build()
    private val mutex=Mutex()
    @Volatile private var generation=0
    init { state.value=state.value.copy(connected=runCatching { vault.read("authorization")!=null }.getOrDefault(false)) }
    fun connect(activity:Activity) {
        if(BuildConfig.INATURALIST_CLIENT_ID.isBlank()){state.value=state.value.copy(message="iNaturalist sign-in is not available in this version yet. You can still select species by hand.");return}
        try {
            val pending=PendingSpeciesSignIn(SpeciesOAuth.random(),SpeciesOAuth.random(),System.currentTimeMillis())
            vault.write("pending",Gson().toJson(pending))
            val url="https://www.inaturalist.org/oauth/authorize".toHttpUrl().newBuilder().addQueryParameter("client_id",BuildConfig.INATURALIST_CLIENT_ID).addQueryParameter("redirect_uri",SpeciesOAuth.REDIRECT).addQueryParameter("response_type","code").addQueryParameter("state",pending.state).addQueryParameter("code_challenge",SpeciesOAuth.challenge(pending.verifier)).addQueryParameter("code_challenge_method","S256").build()
            activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url.toString())))
            state.value=state.value.copy(message="Finish signing in in your browser. You can reconnect if you cancel.")
        } catch(_:Exception){state.value=state.value.copy(message="The sign-in browser could not be opened.")}
    }
    suspend fun complete(url:Uri)=withContext(Dispatchers.IO) {
        state.value=state.value.copy(busy=true)
        val version=generation
        try {
            val pending=Gson().fromJson(vault.read("pending") ?: error("Sign-in expired."),PendingSpeciesSignIn::class.java)
            vault.write("pending",null)
            require(System.currentTimeMillis()-pending.started in 0..600_000)
            val code=SpeciesOAuth.code(url,pending.state)
            val authorization=exchange(mapOf("grant_type" to "authorization_code","code" to code,"code_verifier" to pending.verifier,"redirect_uri" to SpeciesOAuth.REDIRECT))
            check(version==generation)
            vault.write("authorization",Gson().toJson(authorization));state.value=state.value.copy(connected=true)
            apiToken("")
            state.value=state.value.copy(message="Connected to iNaturalist. Photo recognition also requires provider access.")
        } catch(_:Exception){state.value=state.value.copy(message="Could not complete sign-in. Please reconnect.")}
        finally { state.value=state.value.copy(busy=false) }
    }
    private fun exchange(fields:Map<String,String>):SpeciesAuthorization {
        val body=FormBody.Builder().add("client_id",BuildConfig.INATURALIST_CLIENT_ID).apply { fields.forEach { (key,value)->add(key,value) } }.build()
        val request=Request.Builder().url("https://www.inaturalist.org/oauth/token").post(body).build()
        return client.newCall(request).execute().use { response->
            check(response.code==200) { "Please reconnect iNaturalist." }
            val json=JsonParser.parseString(response.body?.string() ?: error("Empty response.")).asJsonObject
            val token=json["access_token"]?.asString?.takeIf(String::isNotBlank) ?: error("Missing access token.")
            val seconds=json["expires_in"]?.takeUnless { it.isJsonNull }?.asLong
            SpeciesAuthorization(token,json["refresh_token"]?.takeUnless { it.isJsonNull }?.asString,seconds?.takeIf { it>0 }?.let { System.currentTimeMillis()+it*1000 })
        }
    }
    suspend fun apiToken(manual:String):String=withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved=vault.read("authorization") ?: return@withLock manual
            var authorization=Gson().fromJson(saved,SpeciesAuthorization::class.java)
            val version=generation
            if(authorization.expires?.let { it<System.currentTimeMillis()+60_000 }==true) {
                val refresh=authorization.refresh ?: error("Please reconnect iNaturalist.")
                val renewed=exchange(mapOf("grant_type" to "refresh_token","refresh_token" to refresh))
                check(version==generation);authorization=renewed.copy(refresh=renewed.refresh ?: refresh)
                vault.write("authorization",Gson().toJson(authorization))
            }
            val request=Request.Builder().url("https://www.inaturalist.org/users/api_token").header("Authorization","Bearer "+authorization.access).header("Accept","application/json").build()
            client.newCall(request).execute().use { response->
                check(version==generation && response.code==200) { "Please reconnect iNaturalist." }
                JsonParser.parseString(response.body?.string() ?: error("Empty response.")).asJsonObject["api_token"]?.asString?.takeIf(String::isNotBlank) ?: error("Please reconnect iNaturalist.")
            }
        }
    }
    fun disconnect() {
        generation++
        try { vault.write("authorization",null);vault.write("pending",null);state.value=SpeciesConnectionState(message="Disconnected on this device. Manage app access in your iNaturalist account to revoke server access.") }
        catch(_:Exception){state.value=state.value.copy(message="The connection could not be removed. Please try again.")}
    }
}

class SpeciesOAuthCallbackActivity:Activity() {
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val url=intent.data
        if(url==null){finish();return}
        CoroutineScope(Dispatchers.Main).launch {
            (application as TackleboxApp).speciesConnection.complete(url)
            startActivity(Intent(this@SpeciesOAuthCallbackActivity,MainActivity::class.java).putExtra("tacklebox.route","data-services").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }
}
