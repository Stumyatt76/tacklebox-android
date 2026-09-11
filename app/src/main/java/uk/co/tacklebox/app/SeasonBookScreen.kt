/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.tacklebox.app.ui.*
import java.io.File
import java.time.Year
import java.time.ZoneId

@Composable fun SeasonBookScreen(s:AppState,nav:androidx.navigation.NavHostController) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var year by rememberSaveable { mutableIntStateOf(Year.now().value) }
    var notes by rememberSaveable { mutableStateOf(false) };var photos by rememberSaveable { mutableStateOf(true) }
    var omitted by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var busy by remember { mutableStateOf(false) };var message by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<File?>(null) };var page by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(0) };var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val years=(s.catches.map { it.item.caughtAt.atZone(ZoneId.systemDefault()).year }+Year.now().value).distinct().sortedDescending()
    val season=s.catches.filter { it.item.caughtAt.atZone(ZoneId.systemDefault()).year==year }
    LaunchedEffect(file,page) {
        val pdf=file ?: return@LaunchedEffect
        try {
            val result=withContext(Dispatchers.IO) {
                PdfRenderer(ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer->
                    renderer.openPage(page.coerceIn(0,renderer.pageCount-1)).use { source->
                        val image=Bitmap.createBitmap(800,800*source.height/source.width,Bitmap.Config.ARGB_8888)
                        source.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);image to renderer.pageCount
                    }
                }
            }
            bitmap=result.first;pages=result.second
        } catch(_:Exception){message="The PDF preview could not be opened."}
    }
    PushedScreen(if(file==null)"" else "PDF preview",onBack={if(file==null)nav.popBackStack() else {file=null;bitmap=null}},eyebrow=if(file==null)"Made to keep" else null,heading=if(file==null)"Season Book" else null,
        actions={if(file!=null)TextButton({file?.let { ShareSheet.file(context,SeasonBook.shareUri(context,it),"application/pdf","Share Season Book") }}) { Text("Share PDF",color=BrassSoft) }}) {
        if(file==null) {
            item { HeritageCard { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { SectionLabel("Season");Spacer(Modifier.weight(1f))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { years.forEach { value->FilterChip(year==value,{year=value},{Text(value.toString())},colors=brassChipColours(),shape=androidx.compose.foundation.shape.CircleShape) } } } } }
            item { Row { Checkbox(photos,{photos=it},modifier=Modifier.semantics { contentDescription="Include catch photos" });Text("Include catch photos") };Row { Checkbox(notes,{notes=it},modifier=Modifier.semantics { contentDescription="Include personal notes" });Text("Include personal notes") } }
            item { Text("Choose your catches, then preview the PDF before sharing. Up to 500 catches per book.",color=Muted) }
            if(season.isEmpty())item { Text("No catches recorded in this year.") }
            items(season,key={it.item.id}) { fish->Row { Checkbox(fish.item.id !in omitted,{ checked->omitted=if(checked)omitted-fish.item.id else omitted+fish.item.id },modifier=Modifier.semantics { contentDescription="Include "+(fish.species?.name ?: "Unidentified catch")+" from "+fish.item.caughtAt.pretty() });Text((fish.species?.name ?: "Unidentified catch")+" · "+fish.item.caughtAt.pretty()) } }
            if(busy)item { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp),color=Brass,strokeWidth=2.dp);Spacer(Modifier.width(10.dp));Text("Creating your book…",color=Muted) } }
            item { Button({
                val selected=season.filter { it.item.id !in omitted };busy=true;message=null
                scope.launch {
                    try { file=withContext(Dispatchers.IO){SeasonBook.write(context,year,selected,s.settings.unitSystem,notes,photos,s.sessions,CatchFilter.personalBests(s.catches))};page=0 }
                    catch(e:Exception){message="The book could not be created. "+e.message.orEmpty()}
                    finally { busy=false }
                }
            },enabled=!busy && season.any { it.item.id !in omitted }) { Text("Create PDF preview") } }
        } else {
            item { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { TextButton({page--},enabled=page>0) { Text("Previous") };Text("Page ${page+1} of $pages",color=Muted);TextButton({page++},enabled=page+1<pages) { Text("Next") };Spacer(Modifier.weight(1f));TextButton({file=null;bitmap=null}) { Text("Close",color=BrassSoft) } } }
            item { bitmap?.let { Image(it.asImageBitmap(),"Season Book page ${page+1}",Modifier.fillMaxWidth()) } ?: CircularProgressIndicator() }
        }
        message?.let { item { Text(it,color=Muted) } }
    }
}
