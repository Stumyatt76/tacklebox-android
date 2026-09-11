/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.services.*
import java.time.*

object SessionNotification {
    const val ID=7401
    const val CHANNEL="tacklebox-live-session"
    fun recordPlace(context:Context,lat:Double,lon:Double) {
        context.getSharedPreferences("tacklebox-live",Context.MODE_PRIVATE).edit().putString("lat",lat.toString()).putString("lon",lon.toString()).putLong("locationTime",System.currentTimeMillis()).apply()
    }
    private fun window(context:Context):String {
        val preferences=context.getSharedPreferences("tacklebox-live",Context.MODE_PRIVATE)
        if(System.currentTimeMillis()-preferences.getLong("locationTime",0)>7*24*3600*1000L)return "Open Tacklebox for bite windows"
        val lat=preferences.getString("lat",null)?.toDoubleOrNull() ?: return "Open Tacklebox for bite windows"
        val lon=preferences.getString("lon",null)?.toDoubleOrNull() ?: return "Open Tacklebox for bite windows"
        val now=LocalTime.now();val next=Astronomy.calculate(latitude=lat,longitude=lon).windows.firstOrNull { if(it.end<it.start)now>=it.start || now<=it.end else it.end>=now }
        return next?.let { "${LocalDate.now()}: ${it.start}–${it.end} bite window" } ?: "No more bite windows today"
    }
    fun enabled(context:Context)=context.getSharedPreferences("tacklebox-live",Context.MODE_PRIVATE).getBoolean("enabled",false)
    fun setEnabled(context:Context,value:Boolean) { context.getSharedPreferences("tacklebox-live",Context.MODE_PRIVATE).edit().putBoolean("enabled",value).apply() }
    fun update(context:Context,session:SessionRow?) {
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!enabled(context) || session==null){manager.cancel(ID);return}
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Fishing sessions",NotificationManager.IMPORTANCE_LOW).apply { description="Elapsed time and catch count for your active session";lockscreenVisibility=Notification.VISIBILITY_PRIVATE })
        val open=PendingIntent.getActivity(context,ID,Intent(context,MainActivity::class.java).putExtra("tacklebox.route","sessions"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end=PendingIntent.getBroadcast(context,ID,Intent(context,SessionNotificationReceiver::class.java).setAction("uk.co.tacklebox.END_SESSION").putExtra("sessionID",session.item.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text="${session.catches.size} ${if(session.catches.size==1)"catch" else "catches"} · "+window(context)
        val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_session_notification)
            .setContentTitle("Tacklebox · Fishing session").setContentText(text)
            .setWhen(session.item.startAt.toEpochMilli()).setUsesChronometer(true).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).addAction(0,"End session",end).build()
        manager.notify(ID,notification)
    }
}

class SessionNotificationReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val allowed=intent.action==Intent.ACTION_BOOT_COMPLETED || intent.action=="uk.co.tacklebox.END_SESSION"
        if(!allowed)return
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo=(context.applicationContext as TackleboxApp).repository
                if(intent.action=="uk.co.tacklebox.END_SESSION") {
                    val id=intent.getLongExtra("sessionID",-1)
                    if(repo.openSession()?.id==id)repo.stopSession(id)
                }
                SessionNotification.update(context,repo.sessions.first().firstOrNull { it.item.endAt==null })
            } catch(_:Exception) { /* Persisted session is unchanged; reopen the app to retry. */ } finally { pending.finish() }
        }
    }
}

@Composable fun SessionNotificationPreference(s:AppState) {
    val context=LocalContext.current
    var enabled by remember { mutableStateOf(SessionNotification.enabled(context)) }
    fun change(value:Boolean) { enabled=value;SessionNotification.setEnabled(context,value);SessionNotification.update(context,s.sessions.firstOrNull { it.item.endAt==null }) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { change(it) }
    androidx.compose.foundation.layout.Row {
        Switch(enabled,{ value->if(value && Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else change(value) },modifier=androidx.compose.ui.Modifier.semantics { contentDescription="Show session timer in notifications" })
        Text("Show session timer in notifications")
    }
}
