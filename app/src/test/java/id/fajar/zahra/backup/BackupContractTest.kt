package id.fajar.zahra.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupContractTest {
    @Test fun currentSchemaIsFive() = assertEquals(5, BackupContract.CURRENT_SCHEMA)
    @Test fun extensionIsStable() = assertEquals("zahra.backup", BackupContract.FILE_EXTENSION)
}
