package uk.co.tacklebox.app

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.tacklebox.app.data.*
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SeasonBookDeviceTest {
    @Test fun nativePdfPaginatesAndCreatesReviewArtifacts() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.filesDir,"Tacklebox-QA").apply { mkdirs() }
        val image=Bitmap.createBitmap(800,400,Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.rgb(0,128,128))
            drawText("TACKLEBOX QA PHOTO",90f,190f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=42f })
        }
        val photo=File(directory,"qa-season-photo.png")
        photo.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        val row=CatchRow(Catch(id=1,speciesId=1,weightGrams=2126.25,returned=true,photoUri=Uri.fromFile(photo).toString(),rig="Method feeder",bait="Sweetcorn",caughtAt=Instant.parse("2026-06-01T07:00:00Z"),notes="A quiet morning beside the reeds. Rain cleared before the first bite. ".repeat(160)+"END OF PRIVATE STORY"),Species(id=1,name="Tench",discipline=Discipline.COARSE),Water(id=1,name="Alder Mere",type=WaterType.LAKE,region="QA"),null)
        val file=SeasonBook.write(context,2026,listOf(row),UnitSystem.METRIC,true,true,personalBests=mapOf("Tench" to row))
        PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf->
            assertTrue(pdf.pageCount>3)
            for(index in 0 until pdf.pageCount)pdf.openPage(index).use { page->assertEquals(595,page.width);assertEquals(842,page.height) }
        }
        file.copyTo(File(directory,"qa-season-android.pdf"),overwrite=true)
        SeasonBook.write(context,2026,listOf(row),UnitSystem.METRIC,false,false).copyTo(File(directory,"qa-season-android-private.pdf"),overwrite=true)
    }
    @Test fun cameraOrientationIsAppliedBeforePdfRendering() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"qa-oriented.jpg")
        val original=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG,90,it) }
            android.media.ExifInterface(file.absolutePath).apply { setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");saveAttributes() }
            val restored=SeasonBook.readPhoto(context,Uri.fromFile(file).toString())!!
            try { assertEquals(40,restored.width);assertEquals(80,restored.height) } finally { restored.recycle() }
        } finally { original.recycle();file.delete() }
    }
    @Test fun oauthSecretsAreEncryptedAndRemovable() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vault=SpeciesSecureVault(context)
        try {
            vault.write("qa-test","TACKLEBOX-QA-SECRET")
            assertEquals("TACKLEBOX-QA-SECRET",vault.read("qa-test"))
            val stored=context.getSharedPreferences("tacklebox-species-connection",0).getString("qa-test","")!!
            assertFalse(stored.contains("TACKLEBOX-QA-SECRET"))
        } finally { vault.write("qa-test",null) }
        assertNull(vault.read("qa-test"))
    }
}
