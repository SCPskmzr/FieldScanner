package com.fieldscanner.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression barrier for the product rule "no visible text for children" (TASK-002, TASK-029).
 *
 * A release build must not render text anywhere. This test reads the sources directly so it
 * fails the task gate the moment a text widget attribute or a system text popup is reintroduced
 * on a live path. Debug-only diagnostics guarded by [BuildConfig.DEBUG] stay allowed.
 */
class ReleaseTextBarrierTest {

    private val appModuleRoot: File = run {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/main").isDirectory) {
            dir = if (File(dir, "app/src/main").isDirectory) File(dir, "app") else dir.parentFile
        }
        requireNotNull(dir) { "Could not locate the :app module root from ${File("").absolutePath}" }
    }

    private val mainKotlinSources: List<File> =
        File(appModuleRoot, "src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    private val layoutFiles: List<File> =
        File(appModuleRoot, "src/main/res/layout").walkTopDown().filter { it.extension == "xml" }.toList()

    @Test
    fun `no layout declares a visible text attribute`() {
        assertTrue("Expected layout resources", layoutFiles.isNotEmpty())
        val offenders = layoutFiles.filter { Regex("""\bandroid:text\s*=""").containsMatchIn(it.readText()) }
        assertTrue(
            "Child-facing layouts must not declare android:text: ${offenders.map { it.name }}",
            offenders.isEmpty()
        )
    }

    @Test
    fun `production code does not use text popup APIs outside a BuildConfig_DEBUG guard`() {
        assertTrue("Expected Android production sources", mainKotlinSources.isNotEmpty())
        val prohibited = Regex("""\bToast\b|\bAlertDialog\b""")
        val offenders = mainKotlinSources.filter { source ->
            val text = source.readText()
            prohibited.containsMatchIn(text) && !text.contains("BuildConfig.DEBUG")
        }
        assertTrue(
            "Child-facing production code must not use Toast/AlertDialog on a release path: " +
                "${offenders.map { it.name }}",
            offenders.isEmpty()
        )
    }
}
