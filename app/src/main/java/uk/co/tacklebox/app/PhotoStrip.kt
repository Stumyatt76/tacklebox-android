/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import uk.co.tacklebox.app.ui.*

// --- Multiple photos per catch ----------------------------------------------------------------------------------
/**
 * The photo editor used by both the capture and edit screens. One photo per catch with no gallery was the biggest
 * thing the app under-used — anglers take three or four shots of a good fish. The first image is the cover, which
 * is what the Vault hero and list thumbnails show, so the first slot is labelled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PhotoStrip(photos:List<String>,onChange:(List<String>)->Unit){
    val limit=8
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val current by rememberUpdatedState(photos)
    var importing by remember{mutableStateOf(false)}
    var failed by remember{mutableIntStateOf(0)}
    // Every picked or captured image is copied into the app's own photo store (oriented, ≤2048 px, JPEG) and only
    // that file's URI is kept. The picker's content:// grant dies with the process and the camera's cache file with
    // the next cache trim — both used to make photos vanish. `cleanup` removes the camera's cache copy afterwards.
    fun add(sources:List<Uri>,cleanup:()->Unit={}){
        if(sources.isEmpty())return
        scope.launch{
            importing=true
            val stored=withContext(Dispatchers.IO){sources.mapNotNull{source->runCatching{PhotoStore.import(context,source)}.getOrNull()}}
            cleanup();failed=sources.size-stored.size;importing=false
            if(stored.isNotEmpty())onChange((current+stored).take(limit))}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){uris->add(uris)}
    // rememberSaveable: the camera app is exactly when Android kills the caller, and a `remember` here forgot the
    // destination on the way back, so the photo just taken was silently dropped.
    var pending by rememberSaveable{mutableStateOf<String?>(null)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->
        val capture=pending;pending=null
        if(ok&&capture!=null)add(listOf(Uri.parse(capture))){CapturePhoto.discard(context,Uri.parse(capture))}}
    fun capture(){val destination=CapturePhoto.destination(context);pending=destination.toString();camera.launch(destination)}
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)capture()}

    val largeText=LocalDensity.current.fontScale>=1.5f
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        if(photos.isEmpty()){
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp)).background(Inset).clickable{picker.launch("image/*")}.semantics{contentDescription="No photos yet"},contentAlignment=Alignment.Center){
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){FishGlyphOutline(Teal,Modifier.size(90.dp,45.dp));Text("Add the moment",color=Muted,style=MaterialTheme.typography.bodyMedium)}}
        } else {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                itemsIndexed(photos){index,uri->
                    Box(Modifier.size(118.dp)){
                        AsyncImage(uri,if(index==0)"Cover photo" else "Photo ${index+1}",
                            Modifier.fillMaxSize().clip(RoundedRectangle14)
                                .border(if(index==0)2.dp else 1.dp,if(index==0)Brass else Muted.copy(alpha=.4f),RoundedRectangle14),
                            contentScale=ContentScale.Crop)
                        IconButton({onChange(photos.filterIndexed{i,_->i!=index})},Modifier.align(Alignment.TopEnd)){
                            Icon(Icons.Default.Cancel,"Remove photo ${index+1}",tint=Ink)}
                        if(index==0)Text("COVER",color=Background,style=MaterialTheme.typography.bodyMedium,
                            modifier=Modifier.align(Alignment.BottomStart).padding(4.dp).background(Brass,RoundedCornerShape(6.dp)).padding(horizontal=5.dp))}}}
        }
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),maxItemsInEachRow=if(largeText)1 else 2){
            FilledTonalButton({picker.launch("image/*")},Modifier.weight(1f),enabled=photos.size<limit&&!importing,shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=Inset,contentColor=BrassSoft)){
                Icon(Icons.Default.PhotoLibrary,null);Spacer(Modifier.width(8.dp));Text(if(photos.isEmpty())"Library" else "Add more")}
            FilledTonalButton({if(CapturePhoto.permitted(context))capture() else cameraPermission.launch(Manifest.permission.CAMERA)},Modifier.weight(1f),enabled=photos.size<limit&&!importing,shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=Inset,contentColor=BrassSoft)){
                Icon(Icons.Default.PhotoCamera,null);Spacer(Modifier.width(8.dp));Text("Camera")}}
        if(importing)Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(16.dp),color=BrassSoft,strokeWidth=2.dp);Spacer(Modifier.width(9.dp));Text("Saving photo…",color=Muted,style=MaterialTheme.typography.bodyMedium)}
        else if(failed>0)Text(if(failed==1)"One photo could not be read and was not added." else "$failed photos could not be read and were not added.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        if(photos.size>=limit)Text("That's the limit of $limit photos for one catch.",color=Muted,style=MaterialTheme.typography.bodyMedium)
        else if(photos.size>1)Text("The first photo is the one that appears on your board and shared cards.",color=Dim,style=MaterialTheme.typography.bodyMedium)
    }
}

/** A swipeable gallery for the catch detail. A single photo looks exactly as it did before. */
@Composable fun PhotoGallery(photos:List<String>){
    val pager=rememberPagerState(pageCount={photos.size})
    Column(horizontalAlignment=Alignment.CenterHorizontally){
        HorizontalPager(pager,Modifier.fillMaxWidth().height(300.dp)){page->
            AsyncImage(photos[page],"Photo ${page+1} of ${photos.size}",
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),contentScale=ContentScale.Crop)}
        if(photos.size>1)Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            repeat(photos.size){i->Box(Modifier.size(7.dp).clip(CircleShape).background(if(i==pager.currentPage)Brass else Muted.copy(alpha=.35f)))}}
    }
}

val RoundedRectangle14=RoundedCornerShape(14.dp)
