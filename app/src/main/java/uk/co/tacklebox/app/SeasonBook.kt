package uk.co.tacklebox.app

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import androidx.core.content.FileProvider
import uk.co.tacklebox.app.data.*
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SeasonBook {
    fun write(context:Context,year:Int,entries:List<CatchRow>,units:UnitSystem,notes:Boolean,photos:Boolean,sessions:List<SessionRow> = emptyList(),personalBests:Map<String,CatchRow> = emptyMap()):File {
        require(entries.isNotEmpty() && entries.size<=500) { "Choose between 1 and 500 catches for a Season Book." }
        val directory=File(context.cacheDir,"season-books").apply { mkdirs() }
        val file=File(directory,"tacklebox-season-$year.pdf");val temporary=File(directory,"pending-${java.util.UUID.randomUUID()}.pdf")
        try {
            val document=PdfDocument()
            try {
                var page:PdfDocument.Page?=null;var number=0;var y=0f
                fun face(heading:Boolean)=androidx.core.content.res.ResourcesCompat.getFont(context,if(heading)R.font.spectral_semibold else R.font.figtree_regular) ?: Typeface.DEFAULT
                fun paint(size:Float,heading:Boolean=false)=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize=size;typeface=face(heading);color=Color.rgb(20,48,43) }
                fun newPage() {
                    page?.let(document::finishPage);number++
                    page=document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create())
                    page!!.canvas.drawColor(Color.rgb(250,247,240))
                    page!!.canvas.drawText("TACKLEBOX / SEASON BOOK / $year",44f,38f,paint(10f).apply { color=Color.rgb(140,107,54) })
                    page!!.canvas.drawText(number.toString(),535f,816f,paint(10f));y=68f
                }
                fun text(value:String,size:Float=13f,heading:Boolean=false) {
                    val layout=StaticLayout.Builder.obtain(value,0,value.length,paint(size,heading),507).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3f,1f).build()
                    for(line in 0 until layout.lineCount) {
                        val top=layout.getLineTop(line);val height=layout.getLineBottom(line)-top
                        val lineHeight=kotlin.math.ceil(size*1.2f)+3
                        if(y+lineHeight>778)newPage()
                        page!!.canvas.run { save();translate(44f,y-top);clipRect(0,top,507,top+height);layout.draw(this);restore() }
                        y+=lineHeight
                    }
                    y+=9
                }
                fun photo(uri:String) {
                    val bitmap=readPhoto(context,uri)
                    if(bitmap==null){text("Photo unavailable");return}
                    try {
                        if(y+290>778)newPage()
                        val scale=minOf(507f/bitmap.width,280f/bitmap.height)
                        val w=bitmap.width*scale;val h=bitmap.height*scale
                        page!!.canvas.drawBitmap(bitmap,null,RectF(44+(507-w)/2,y,44+(507+w)/2,y+h),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG));y+=h+15
                    } finally { bitmap.recycle() }
                }
                newPage();y=160f
                text("Your $year\non the water",42f,true)
                text("${entries.size} ${if(entries.size==1)"catch" else "catches"} · ${entries.map { it.species?.name ?: "Unidentified catch" }.distinct().size} species",20f,true)
                text("Selected moments from your fishing journal.")
                text(if(notes)"Personal notes included." else "Personal notes omitted.")
                text(if(photos)"Catch photos included where available." else "Catch photos omitted.")
                entries.sortedBy { it.item.caughtAt }.forEach { row->
                    newPage();text(row.species?.name ?: "Unidentified catch",30f,true)
                    text(row.item.caughtAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm"))+" · "+(row.water?.name ?: "Water not recorded"))
                    text(row.item.weightGrams?.weight(units) ?: "Weight not recorded",23f,true)
                    text(listOfNotNull(row.item.rig,row.item.bait,if(row.item.returned)"Returned safely" else "Not returned",if(personalBests[row.species?.name]?.item?.id==row.item.id)"Personal best" else null).joinToString(" · "))
                    if(photos)row.allPhotoUris.forEach(::photo)
                    if(notes && row.item.notes.isNotBlank()){text("Bank notes",18f,true);text(row.item.notes)}
                    val story=sessions.firstOrNull { it.item.id==row.item.sessionId }?.item?.notes
                    if(notes && !story.isNullOrBlank()){text("Session story",18f,true);text(story)}
                }
                page?.let(document::finishPage)
                temporary.outputStream().use(document::writeTo)
            } finally { document.close() }
            check(temporary.renameTo(file)) { "The book could not be saved." };return file
        } finally { temporary.delete() }
    }
    internal fun readPhoto(context:Context,uri:String):Bitmap? = runCatching {
        val location=Uri.parse(uri)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(location)?.use { BitmapFactory.decodeStream(it,null,bounds) }
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1600)sample*=2
        val bitmap=context.contentResolver.openInputStream(location)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: return@runCatching null
        val orientation=runCatching { context.contentResolver.openInputStream(location)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1) } }.getOrNull() ?: 1
        val transform=Matrix().apply {
            when(orientation) {
                2->setScale(-1f,1f)
                3->setRotate(180f)
                4->setScale(1f,-1f)
                5->{setRotate(90f);postScale(-1f,1f)}
                6->setRotate(90f)
                7->{setRotate(-90f);postScale(-1f,1f)}
                8->setRotate(-90f)
            }
        }
        if(transform.isIdentity)bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,transform,true).also { if(it!==bitmap)bitmap.recycle() }
    }.getOrNull()
    fun shareUri(context:Context,file:File):Uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
}
