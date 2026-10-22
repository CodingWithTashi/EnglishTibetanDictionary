package com.kharagedition.tibetandictionary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Auto Backup stops at its per-app quota, so every pack database must stay out of every backup section. */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Section name to the database paths it excludes; the full-backup-content file is one section. */
    private fun excludedDatabases(resId: Int): Map<String, Set<String>> {
        val sections = mutableMapOf<String, MutableSet<String>>()
        val parser = context.resources.getXml(resId)
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "full-backup-content", "cloud-backup", "device-transfer" -> {
                    section = parser.name
                    sections.getOrPut(section) { mutableSetOf() }
                }
                "exclude" -> if (parser.getAttributeValue(null, "domain") == "database") {
                    sections.getValue(section) += parser.getAttributeValue(null, "path")
                }
            }
        }
        return sections
    }

    private fun assertPacksExcluded(resId: Int, expectedSections: Set<String>) {
        val sections = excludedDatabases(resId)
        assertThat(sections.keys).containsExactlyElementsIn(expectedSections)
        for ((section, paths) in sections) {
            for (pack in DictionaryPack.entries) {
                for (suffix in listOf("", "-journal", "-shm", "-wal", ".tmp")) {
                    assertThat(paths).contains(pack.dbName + suffix)
                }
            }
            assertThat(paths.filter { it.startsWith("user.db") }).isEmpty()
            assertThat(section).isNotEmpty()
        }
    }

    @Test
    fun backupRulesExcludeEveryPackDatabase() =
        assertPacksExcluded(R.xml.backup_rules, setOf("full-backup-content"))

    @Test
    fun dataExtractionRulesExcludeEveryPackDatabase() =
        assertPacksExcluded(R.xml.data_extraction_rules, setOf("cloud-backup", "device-transfer"))
}
