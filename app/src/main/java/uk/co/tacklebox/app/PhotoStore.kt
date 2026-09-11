/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * The app's own copy of every catch photo.
 *
 * Library picks used to be stored as the picker's `content://` URI, whose read grant dies with the process, and
 * camera captures lived in the cache directory, which the system may purge. Both made photos vanish. Every photo
 * is now decoded once, oriented from EXIF, scaled to at most [MAX_EDGE] px on its longest side, re-encoded as JPEG
 * and written under `filesDir/photos`, and only that `file://` URI is stored. Older `content://` records are left
 * as they are and simply render as missing where they can no longer be read.
 */
object PhotoStore {
    const val MAX_EDGE = 2048
    const val QUALITY = 85
    private const val DIRECTORY = "photos"

    fun directory(context: Context): File = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /** True for a `file://` URI inside the app's private files directory — the only kind this store deletes. */
    fun isOwned(uri: String, root: File): Boolean {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file") return false
        val path = parsed.path ?: return false
        return runCatching { File(path).canonicalPath.startsWith(root.canonicalPath + File.separator) }.getOrDefault(false)
    }

    fun isReadable(context: Context, uri: String): Boolean =
        runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { true } ?: false }.getOrDefault(false)

    /** Copies a picked or captured image into the store. Returns the stored `file://` URI; throws when unreadable. */
    fun import(context: Context, source: Uri): String {
        val bitmap = decodeOriented(context, source.toString(), MAX_EDGE) ?: error("That photo could not be read.")
        val file = File(directory(context), UUID.randomUUID().toString() + ".jpg")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)) { "That photo could not be saved." } }
        } catch (e: Exception) { file.delete(); throw e } finally { bitmap.recycle() }
        return Uri.fromFile(file).toString()
    }

    /** Deletes a stored photo file. Anything that is not the store's own file is left untouched. */
    fun delete(uri: String, root: File): Boolean = isOwned(uri, root) && (Uri.parse(uri).path?.let { File(it).delete() } ?: false)

    /** The two places the app writes photos under `filesDir`: its own store and restored backup media. */
    fun isAppPhoto(uri: String, filesDir: File): Boolean = isOwned(uri, File(filesDir, DIRECTORY)) || isOwned(uri, File(filesDir, BACKUP_MEDIA))
    /** Deletes only a file inside `filesDir/photos` or `filesDir/backup-media` — never anything else under `files/`. */
    fun deleteOwned(uri: String, filesDir: File): Boolean = isAppPhoto(uri, filesDir) && (Uri.parse(uri).path?.let { File(it).delete() } ?: false)
    const val BACKUP_MEDIA = "backup-media"

    /**
     * Reclaims files in the store that no row names and that are older than [maxAgeMs]: an abandoned capture, a
     * photo removed from the strip before saving, an import whose result never reached a screen. Fresh files are
     * left alone because a capture in progress has not been saved yet.
     */
    fun sweep(context: Context, referenced: Set<String>, maxAgeMs: Long = 24L * 3600 * 1000, now: Long = System.currentTimeMillis()): Int {
        val keep = referenced.mapNotNull { Uri.parse(it).path }.toSet()
        return directory(context).listFiles().orEmpty().count { file ->
            file.isFile && file.path !in keep && now - file.lastModified() > maxAgeMs && file.delete()
        }
    }

    /**
     * Decodes an image at most `maxEdge` px on its longest side, with the EXIF orientation applied so a portrait
     * phone photo is upright wherever it is drawn.
     */
    fun decodeOriented(context: Context, uri: String, maxEdge: Int): Bitmap? = runCatching {
        val location = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(location)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@runCatching null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        val decoded = context.contentResolver.openInputStream(location)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val orientation = runCatching {
            context.contentResolver.openInputStream(location)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        }.getOrNull() ?: 1
        val transform = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        val decodedLongest = maxOf(decoded.width, decoded.height)
        if (decodedLongest > maxEdge) {
            val scale = maxEdge.toFloat() / decodedLongest
            transform.postScale(scale, scale)
        }
        if (transform.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true).also { if (it !== decoded) decoded.recycle() }
    }.getOrNull()
}
