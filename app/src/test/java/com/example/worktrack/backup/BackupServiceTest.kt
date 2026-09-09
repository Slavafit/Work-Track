package com.example.worktrack.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.example.worktrack.data.*
import com.example.worktrack.license.licenseDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupServiceTest {
    private lateinit var context: Context
    private lateinit var db: WorkTrackDatabase
    private lateinit var repo: WorkTrackRepository
    private lateinit var service: BackupService
    // AndroidX FileProvider uses Android's '/' paths, which cannot resolve Windows JVM files.
    // Use real files through an injected content-URI adapter; archive/Room code runs unchanged.
    private class TestMedia : BackupMedia {
        private val files = mutableMapOf<Uri, File>()
        override fun uriFor(file: File): Uri = Uri.parse("content://test.photos/${java.util.UUID.randomUUID()}").also { files[it] = file }
        override fun open(uri: Uri): InputStream? = files[uri]?.inputStream()
        override fun extension(uri: Uri) = "png"
    }
    private lateinit var media: TestMedia
    private var objectId = 0L
    private var dayId = 0L
    private var photoId = 0L
    private lateinit var originalPhoto: File
    private val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 1, 2, 3, 4)

    @Before fun setup() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, WorkTrackDatabase::class.java).allowMainThreadQueries().build()
        repo = WorkTrackRepository(db)
        media = TestMedia()
        service = BackupService(context, db, media)
        objectId = repo.createObject("Address", null, "Customer", "+123456789")
        val worker = repo.addWorker("Worker", null)
        val type = repo.addWorkType("Service")
        val material = repo.addMaterial("Material")
        dayId = repo.createDay(objectId, 1000, setOf(worker), "Day note")
        repo.addEntry(dayId, worker, type, 100, "Entry note")
        repo.addMaterialEntry(dayId, worker, material, 50, null)
        repo.saveProposal(null, objectId,
            listOf(ProposalItem(proposalId = 0, workTypeId = type, amount = 200)),
            listOf(ProposalMaterialItem(proposalId = 0, materialId = material, amount = 75)))
        originalPhoto = File(context.filesDir, "restored_photos/source/test.png")
        originalPhoto.parentFile!!.mkdirs()
        originalPhoto.writeBytes(image)
        photoId = repo.addDayPhoto(dayId, media.uriFor(originalPhoto).toString())
    }

    @After fun cleanup() { db.close() }

    private suspend fun archive(): ByteArray = ByteArrayOutputStream().also { service.export(it) }.toByteArray()

    private fun entries(bytes: ByteArray): MutableMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; result[entry.name] = zip.readBytes(); zip.closeEntry() }
        }
        return result
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()

    private fun tamper(bytes: ByteArray, change: (JSONObject) -> Unit): ByteArray {
        val contents = entries(bytes)
        val json = JSONObject(String(contents.getValue("manifest.json"), Charsets.UTF_8))
        change(json)
        contents["manifest.json"] = json.toString().toByteArray()
        return zip(contents)
    }

    @Test fun `round trip restores saved work proposals and independent photo files`() = runBlocking {
        val archive = archive()
        originalPhoto.delete()
        repo.createObject("Later object", null, "Later customer", null)
        val prepared = service.prepare(archive.inputStream())
        assertEquals(1, prepared.summary.objects)
        assertEquals(1, prepared.summary.photos)
        assertEquals(0, prepared.summary.missingPhotos)
        service.restore(prepared)
        val objects = db.dao().objectSummaries().first()
        assertEquals(1, objects.size)
        assertEquals("Address", objects.single().address)
        assertEquals(150L, objects.single().totalAmount)
        assertEquals("Day note", db.dao().dayById(dayId)!!.notes)
        assertEquals("Entry note", db.dao().entries(dayId).first().single().notes)
        assertEquals(275L, db.dao().proposals().first().single().totalAmount)
        val restoredUri = db.dao().photoById(photoId)!!.uri
        assertNotEquals(originalPhoto.toURI().toString(), restoredUri)
        media.open(Uri.parse(restoredUri))!!.use { assertArrayEquals(image, it.readBytes()) }
        assertFalse(prepared.directory.exists())
        // IDs remain usable for subsequent inserts after replacement.
        assertTrue(repo.createObject("New", null, "New", null) > objectId)
    }

    @Test fun `unavailable photos are counted and source URIs cannot be reused on import`() = runBlocking {
        originalPhoto.delete()
        val contents = archive()
        val prepared = service.prepare(contents.inputStream())
        assertEquals(0, prepared.summary.photos)
        assertEquals(1, prepared.summary.missingPhotos)
        service.restore(prepared)
        assertEquals("", db.dao().photoById(photoId)!!.uri)
    }

    @Test fun `inspection and cancellation leave existing records untouched`() = runBlocking {
        val bytes = archive()
        val added = repo.createObject("Keep me", null, "Keep", null)
        val prepared = service.prepare(bytes.inputStream())
        prepared.close()
        assertNotNull(repo.objectById(added))
        assertEquals(2, db.dao().objectSummaries().first().size)
        assertTrue(originalPhoto.exists())
    }

    @Test fun `broken foreign key is rejected before live data changes`() = runBlocking {
        val corrupt = tamper(archive()) { json ->
            val table = json.getJSONObject("tables").getJSONObject("WorkObject")
            table.getJSONArray("rows").getJSONArray(0).put(1, 9999)
        }
        try { service.prepare(corrupt.inputStream()); fail("Invalid relation accepted") } catch (_: InvalidBackupException) { }
        assertEquals("Address", repo.objectById(objectId)!!.address)
        assertTrue(originalPhoto.exists())
    }

    @Test fun `unknown archive version is rejected`() = runBlocking {
        val corrupt = tamper(archive()) { it.put("version", 99) }
        try { service.prepare(corrupt.inputStream()); fail("Unknown version accepted") } catch (_: InvalidBackupException) { }
        assertNotNull(repo.objectById(objectId))
    }

    @Test fun `oversized manifest is rejected before allocating an unbounded document`() = runBlocking {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            val chunk = ByteArray(64 * 1024) { 32 }
            repeat(257) { zip.write(chunk) }
            zip.closeEntry()
        }
        try { service.prepare(bytes.toByteArray().inputStream()); fail("Oversized metadata accepted") } catch (_: BackupLimitException) { }
        assertNotNull(repo.objectById(objectId))
    }

    @Test fun `missing day worker link is rejected`() = runBlocking {
        val corrupt = tamper(archive()) { it.getJSONObject("tables").getJSONObject("WorkDayWorker").put("rows", JSONArray()) }
        try { service.prepare(corrupt.inputStream()); fail("Orphan worker entry accepted") } catch (_: InvalidBackupException) { }
        assertEquals(150L, db.dao().objectSummaries().first().single().totalAmount)
    }

    @Test fun `changed photo checksum is rejected`() = runBlocking {
        val corrupt = entries(archive())
        val name = corrupt.keys.first { it.startsWith("photos/") }
        corrupt[name] = byteArrayOf(1, 2)
        try { service.prepare(zip(corrupt).inputStream()); fail("Changed photo accepted") } catch (_: InvalidBackupException) { }
        assertTrue(originalPhoto.exists())
    }

    @Test fun `archive paths cannot escape staging`() = runBlocking {
        val corrupt = entries(archive())
        corrupt["../escape.txt"] = byteArrayOf(1)
        try { service.prepare(zip(corrupt).inputStream()); fail("Traversal accepted") } catch (_: InvalidBackupException) { }
        assertFalse(File(context.cacheDir, "escape.txt").exists())
        assertNotNull(repo.objectById(objectId))
    }

    @Test fun `restore rolls back deletion when row insertion fails`() = runBlocking {
        val prepared = service.prepare(archive().inputStream())
        val added = repo.createObject("Keep me", null, "Keep", null)
        val objects = prepared.manifest.getJSONObject("tables").getJSONObject("WorkObject").getJSONArray("rows")
        objects.getJSONArray(0).put(1, 99999)
        try { service.restore(prepared); fail("Invalid replacement committed") } catch (_: Exception) { }
        assertNotNull(repo.objectById(added))
        assertNotNull(repo.objectById(objectId))
        assertArrayEquals(image, originalPhoto.readBytes())
        assertFalse(prepared.directory.exists())
    }

    @Test fun `decimal amounts are not silently converted to integers`() = runBlocking {
        val corrupt = tamper(archive()) { json ->
            val table = json.getJSONObject("tables").getJSONObject("WorkEntry")
            val cols = table.getJSONArray("columns")
            val index = (0 until cols.length()).first { cols.getString(it) == "amount" }
            table.getJSONArray("rows").getJSONArray(0).put(index, 1.5)
        }
        try { service.prepare(corrupt.inputStream()); fail("Fractional money accepted") } catch (_: InvalidBackupException) { }
        assertEquals(150L, db.dao().objectSummaries().first().single().totalAmount)
    }

    @Test fun `licensing and settings are absent from manual archives and unchanged by restore`() = runBlocking {
        val tokenKey = stringPreferencesKey("license_token")
        context.licenseDataStore.edit { it[tokenKey] = "private" }
        val settings = SettingsStore(context)
        settings.setCompanyName("Company")
        val bytes = archive()
        val manifest = JSONObject(String(entries(bytes).getValue("manifest.json")))
        assertFalse(manifest.has("license"))
        assertFalse(manifest.has("settings"))
        service.restore(service.prepare(bytes.inputStream()))
        assertEquals("Company", settings.settings.first().companyName)
        assertEquals("private", context.licenseDataStore.data.first()[tokenKey])
    }
}
