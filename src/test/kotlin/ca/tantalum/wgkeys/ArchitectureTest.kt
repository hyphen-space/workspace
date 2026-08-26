package ca.tantalum.wgkeys

import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertFalse

class ArchitectureTest {
    @Test
    fun `domain sources do not depend on infrastructure`() {
        assertFalse(
            sourceFiles("src/main/kotlin/ca/tantalum/wgkeys/peer/domain")
                .any { it.readText().contains("infrastructure") },
        )
    }

    @Test
    fun `application sources do not depend on infrastructure`() {
        assertFalse(
            sourceFiles("src/main/kotlin/ca/tantalum/wgkeys/peer/application")
                .any { it.readText().contains("infrastructure") },
        )
    }

    @Test
    fun `http sources do not depend on domain`() {
        assertFalse(
            sourceFiles("src/main/kotlin/ca/tantalum/wgkeys/http")
                .any { it.readText().contains("peer.domain") },
        )
    }

    private fun sourceFiles(directory: String) = Path(directory).walk().filter { it.isRegularFile() && it.extension == "kt" }
}
