package net.subsloth.backup

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BackupExclusionRulesTest {
    /**
     * Resolves the XML resource directory relative to the module root.
     * Falls back to a Gradle-safe path when the working directory isn't
     * the module root (e.g. IDE execution).
     */
    private fun resolveXmlDir(): File {
        val candidates = listOf(
            File("src/main/res/xml"),
            File("androidApp/src/main/res/xml"),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Cannot find res/xml directory. Tried: ${candidates.joinToString(", ")}")
    }

    @Test
    fun `backup rules XML exists and excludes credential shared preferences`() {
        val xml = readXmlFile("backup_rules.xml")
        assertTrue(xml.contains("""domain="sharedpref""""), "Should contain sharedpref domain")
        assertTrue(
            xml.contains("subsloth_encrypted_credentials.xml"),
            "Should exclude encrypted credential shared preferences",
        )
    }

    @Test
    fun `backup rules XML excludes datastore directory`() {
        val xml = readXmlFile("backup_rules.xml")
        assertTrue(
            xml.contains("""domain="file""""),
            "Should contain file domain",
        )
        assertTrue(
            xml.contains("""path="datastore/""""),
            "Should exclude datastore directory",
        )
    }

    @Test
    fun `backup rules XML excludes downloads directory`() {
        val xml = readXmlFile("backup_rules.xml")
        assertTrue(
            xml.contains("""path="downloads/""""),
            "Should exclude downloads directory",
        )
    }

    @Test
    fun `data extraction rules XML exists`() {
        val xml = readXmlFile("data_extraction_rules.xml")
        assertTrue(xml.contains("<data-extraction-rules>"), "Should be valid data extraction rules")
        assertTrue(xml.contains("<cloud-backup>"), "Should contain cloud-backup section")
        assertTrue(xml.contains("<device-transfer>"), "Should contain device-transfer section")
    }

    @Test
    fun `data extraction rules excludes credential shared preferences from backup and transfer`() {
        val xml = readXmlFile("data_extraction_rules.xml")
        val sharedprefCount = xml.split("subsloth_encrypted_credentials.xml").size - 1
        assertTrue(sharedprefCount >= 2, "Should exclude credentials from both cloud-backup and device-transfer")
        assertTrue(
            xml.contains("""domain="sharedpref""""),
            "Should contain sharedpref domain exclusion",
        )
    }

    @Test
    fun `data extraction rules excludes datastore and downloads`() {
        val xml = readXmlFile("data_extraction_rules.xml")
        val datastoreCount = xml.split("datastore/").size - 1
        val downloadsCount = xml.split("downloads/").size - 1
        assertTrue(
            datastoreCount >= 2,
            "Should exclude datastore from both cloud-backup and device-transfer",
        )
        assertTrue(
            downloadsCount >= 2,
            "Should exclude downloads from both cloud-backup and device-transfer",
        )
    }

    private fun readXmlFile(fileName: String): String {
        val dir = resolveXmlDir()
        val file = File(dir, fileName)
        assertTrue(file.exists(), "XML file $fileName should exist at ${file.absolutePath}")
        return file.readText()
    }
}
