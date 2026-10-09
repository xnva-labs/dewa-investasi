package id.fajar.zahra.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupContractTest {
    @Test fun currentSchemaIsSix() = assertEquals(6, BackupContract.CURRENT_SCHEMA)
    @Test fun extensionIsStable() = assertEquals("zahra.backup", BackupContract.FILE_EXTENSION)
}
