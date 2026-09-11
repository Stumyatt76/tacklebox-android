/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.data.UnitSystem
import uk.co.tacklebox.app.ui.fishGlyphPath
import java.io.File
import java.time.ZoneId

/**
 * The picture cards iOS shares — the catch card and the season card — drawn at the same 360×450 points, three
 * times over, with the same fonts, colours and layout as `CatchShareCard` and `SeasonShareCard` in the iOS app.
 *
 * Android used to share plain text here. The card is drawn straight onto a bitmap rather than through a
 * composable so it renders identically wherever it is asked for, then handed to the system share sheet as a JPEG,
 * with the old text kept as `EXTRA_TEXT` for targets that cannot take an image.
 */
object ShareCards {
    const val WIDTH = 360; const val HEIGHT = 450; const val SCALE = 3f

    private const val BG = 0xFF0E1A1E.toInt(); private const val INSET = 0xFF1C343B.toInt(); private const val INK = 0xFFF3EEE4.toInt()
    private const val MUTED = 0xFF9BB0B3.toInt(); private const val BRASS = 0xFFC9A24B.toInt(); private const val BRASS_SOFT = 0xFFE2C890.toInt(); private const val TEAL = 0xFF57B3A6.toInt()

    class Fonts(context:Context) {
        val serifBold: Typeface? = ResourcesCompat.getFont(context, R.font.spectral_bold)
        val serifSemibold: Typeface? = ResourcesCompat.getFont(context, R.font.spectral_semibold)
        val bodyBold: Typeface? = ResourcesCompat.getFont(context, R.font.figtree_bold)
        val bodySemibold: Typeface? = ResourcesCompat.getFont(context, R.font.figtree_semibold)
    }

    /** The text the catch card carries, and the fallback for share targets that cannot take an image. */
    fun catchText(row:CatchRow, water:String?, units:UnitSystem):String = listOfNotNull(
        row.species?.name ?: "Unknown species", row.item.weightGrams?.weight(units),
        water?.let { "at $it" }, row.item.caughtAt.atZone(ZoneId.systemDefault()).toLocalDate().toString(), "— logged with Tacklebox").joinToString("\n")

    /** "water · date", or "Water not recorded · date", as the iOS card prints its last line. */
    fun catchCaption(water:String?, date:java.time.LocalDate):String = "${water ?: "Water not recorded"} · ${date.pretty()}"

    /** Renders the catch card: artwork over the species, weight and "water · date". */
    fun catchCard(context:Context, row:CatchRow, water:String?, units:UnitSystem):Bitmap {
        val fonts = Fonts(context)
        val photo = row.item.photoUri?.let { PhotoStore.decodeOriented(context, it, 1080) }
        return card(fonts, photo, glyphColour = TEAL) { canvas, s ->
            val left = 22 * s; var y = 270 * s + 22 * s
            y += text(canvas, "TACKLEBOX", left, y, fonts.bodyBold, 12 * s, BRASS_SOFT, letterSpacing = 0.17f)
            y += 5 * s
            y += text(canvas, row.species?.name ?: "Unknown species", left, y, fonts.serifBold, 30 * s, INK, maxWidth = (WIDTH - 44) * s)
            y += 5 * s
            y += text(canvas, row.item.weightGrams?.weight(units) ?: "—", left, y, fonts.serifSemibold, 26 * s, BRASS_SOFT)
            y += 5 * s
            text(canvas, catchCaption(water, row.item.caughtAt.atZone(ZoneId.systemDefault()).toLocalDate()), left, y, fonts.bodySemibold, 12 * s, MUTED, maxWidth = (WIDTH - 44) * s)
        }.also { photo?.recycle() }
    }

    /** What the season card says: the year, the biggest fish and four numbers. */
    data class Season(val year:Int, val biggestSpecies:String?, val biggestWeight:String?, val fish:Int, val weight:String, val sessions:Int, val species:Int)

    fun seasonText(s:Season, hours:Long):String = listOfNotNull(
        "My ${s.year} on the water", "${s.fish} fish landed", "${s.species} species",
        s.biggestSpecies?.let { "Best · $it ${s.biggestWeight.orEmpty()}".trim() }, "$hours hours on the bank", "— logged with Tacklebox").joinToString("\n")

    /** Renders the season card: gradient ground, "TACKLEBOX" + "{year} Season", the biggest fish, then FISH / WEIGHT / SESSIONS / SPECIES. */
    fun seasonCard(context:Context, s:Season, photo:Bitmap?):Bitmap {
        val fonts = Fonts(context); val sc = SCALE
        val bitmap = Bitmap.createBitmap((WIDTH * sc).toInt(), (HEIGHT * sc).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, WIDTH * sc, HEIGHT * sc)
        canvas.clipPath(Path().apply { addRoundRect(bounds, 24 * sc, 24 * sc, Path.Direction.CW) })
        canvas.drawRect(bounds, Paint().apply { shader = LinearGradient(0f, 0f, bounds.right, bounds.bottom, BG, 0xFF174148.toInt(), Shader.TileMode.CLAMP) })
        canvas.drawCircle((WIDTH / 2f + 145) * sc, (HEIGHT / 2f - 190) * sc, 145 * sc, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(41, 0x57, 0xB3, 0xA6) })
        val left = 26 * sc; var y = 26 * sc
        y += text(canvas, "TACKLEBOX", left, y, fonts.bodyBold, 14 * sc, BRASS_SOFT, letterSpacing = 0.16f)
        val spark = fishGlyphPath(Size(24 * sc, 13 * sc)).asAndroidPath(); spark.offset((WIDTH - 26 - 24) * sc, 27 * sc)
        canvas.drawPath(spark, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRASS_SOFT })
        y += 12 * sc
        y += text(canvas, "${s.year} Season", left, y, fonts.serifBold, 39 * sc, INK)
        y += 14 * sc
        val art = RectF(left, y, (WIDTH - 26) * sc, y + 118 * sc)
        canvas.save(); canvas.clipPath(Path().apply { addRoundRect(art, 14 * sc, 14 * sc, Path.Direction.CW) })
        if (photo != null) {
            val scale = maxOf(art.width() / photo.width, art.height() / photo.height); val w = photo.width * scale; val h = photo.height * scale
            canvas.drawBitmap(photo, null, RectF(art.centerX() - w / 2, art.centerY() - h / 2, art.centerX() + w / 2, art.centerY() + h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawRect(art, Paint().apply { shader = LinearGradient(0f, art.centerY(), 0f, art.bottom, Color.TRANSPARENT, Color.argb(184, 0x0E, 0x1A, 0x1E), Shader.TileMode.CLAMP) })
        } else {
            canvas.drawRect(art, Paint().apply { color = INSET })
            val glyph = fishGlyphPath(Size(art.width() - 44 * sc, art.height() - 44 * sc)).asAndroidPath(); glyph.offset(art.left + 22 * sc, art.top + 22 * sc)
            canvas.drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEAL })
        }
        canvas.restore()
        y = art.bottom + 14 * sc
        y += text(canvas, "BIGGEST FISH", left, y, fonts.bodyBold, 9 * sc, MUTED, letterSpacing = 0.17f)
        y += text(canvas, s.biggestSpecies ?: "A season to remember", left, y, fonts.serifSemibold, 28 * sc, INK, maxWidth = (WIDTH - 52) * sc)
        text(canvas, s.biggestWeight ?: "—", left, y, fonts.serifSemibold, 24 * sc, BRASS_SOFT)
        val stats = listOf("${s.fish}" to "FISH", s.weight to "WEIGHT", "${s.sessions}" to "SESSIONS", "${s.species}" to "SPECIES")
        val column = ((WIDTH - 52) * sc - 3 * 8 * sc) / 4
        val statTop = (HEIGHT - 26) * sc - 30 * sc
        stats.forEachIndexed { i, (value, label) ->
            val x = left + (column + 8 * sc) * i
            val used = text(canvas, value, x, statTop, fonts.serifSemibold, (if (value.length > 8) 13 else 18) * sc, INK, maxWidth = column)
            text(canvas, label, x, statTop + used + 3 * sc, fonts.bodyBold, 7 * sc, MUTED, letterSpacing = 0.1f)
        }
        canvas.drawRoundRect(RectF(bounds).apply { inset(1.5f, 1.5f) }, 24 * sc, 24 * sc, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(128, 0xC9, 0xA2, 0x4B) })
        return bitmap
    }

    private fun card(fonts:Fonts, photo:Bitmap?, glyphColour:Int, artworkHeight:Int = 270, body:(Canvas, Float)->Unit):Bitmap {
        val s = SCALE
        val bitmap = Bitmap.createBitmap((WIDTH * s).toInt(), (HEIGHT * s).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, WIDTH * s, HEIGHT * s)
        val clip = Path().apply { addRoundRect(bounds, 24 * s, 24 * s, Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.drawColor(BG)
        val art = RectF(0f, 0f, WIDTH * s, artworkHeight * s)
        if (photo != null) {
            val scale = maxOf(art.width() / photo.width, art.height() / photo.height)
            val w = photo.width * scale; val h = photo.height * scale
            canvas.save(); canvas.clipRect(art)
            canvas.drawBitmap(photo, null, RectF(art.centerX() - w / 2, art.centerY() - h / 2, art.centerX() + w / 2, art.centerY() + h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawRect(art, Paint().apply { shader = LinearGradient(0f, art.centerY(), 0f, art.bottom, Color.TRANSPARENT, Color.argb(173, 0x0E, 0x1A, 0x1E), Shader.TileMode.CLAMP) })
            canvas.restore()
        } else {
            canvas.drawRect(art, Paint().apply { shader = LinearGradient(0f, 0f, art.right, art.bottom, INSET, 0xFF174148.toInt(), Shader.TileMode.CLAMP) })
            val glyph = fishGlyphPath(Size(230 * s, 125 * s)).asAndroidPath()
            glyph.offset(art.centerX() - 115 * s, art.centerY() - 62.5f * s)
            canvas.drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = glyphColour })
        }
        body(canvas, s)
        // The little fish in the corner, where iOS puts `fish.fill`.
        val mark = fishGlyphPath(Size(22 * s, 12 * s)).asAndroidPath()
        mark.offset((WIDTH - 22 - 22) * s, artworkHeight * s + 22 * s)
        canvas.drawPath(mark, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRASS_SOFT })
        canvas.drawRoundRect(bounds.apply { inset(1.5f, 1.5f) }, 24 * s, 24 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(128, 0xC9, 0xA2, 0x4B) })
        return bitmap
    }

    /** Draws one line at (x, top) and returns its height. Long lines are ellipsised to `maxWidth`, as `lineLimit(1)` does. */
    private fun text(canvas:Canvas, value:String, x:Float, top:Float, font:Typeface?, size:Float, colour:Int, maxWidth:Float = Float.MAX_VALUE, letterSpacing:Float = 0f):Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = size; color = colour; this.letterSpacing = letterSpacing }
        var line = value
        if (paint.measureText(line) > maxWidth) {
            while (line.isNotEmpty() && paint.measureText("$line…") > maxWidth) line = line.dropLast(1)
            line = "$line…"
        }
        val metrics = paint.fontMetrics
        canvas.drawText(line, x, top - metrics.ascent, paint)
        return metrics.descent - metrics.ascent
    }

    /** Writes the card to the share cache and opens the system share sheet with the image and its text fallback. */
    fun share(context:Context, bitmap:Bitmap, name:String, text:String, title:String) {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }
}
