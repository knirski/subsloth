package net.subsloth.backup

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BackupExclusionRulesTest {
    private val moduleDir = File("src/main/res/xml")

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
        val sharedprefCount = xml.windowed("subsloth_encrypted_credentials.xml".length).count {
            it == "subsloth_encrypted_credentials.xml"
        }
        assertTrue(sharedprefCount >= 2, "Should exclude credentials from both cloud-backup and device-transfer")
        assertTrue(
            xml.contains("""domain="sharedpref""""),
            "Should contain sharedpref domain exclusion",
        )
    }

    @Test
    fun `data extraction rules excludes datastore and downloads`() {
        val xml = readXmlFile("data_extraction_rules.xml")
        val datastoreCount = xml.windowed("datastore/".length).count { it == "datastore/" }
        val downloadsCount = xml.windowed("downloads/".length).count { it == "downloads/" }
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
        val file = File(moduleDir, fileName)
        assertTrue(file.exists(), "XML file $fileName should exist at ${file.absolutePath}")
        return file.readText()
    }
}
