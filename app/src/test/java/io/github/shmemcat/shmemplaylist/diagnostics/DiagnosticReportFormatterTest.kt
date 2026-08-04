package io.github.shmemcat.shmemplaylist.diagnostics

import android.net.Uri
import io.github.shmemcat.shmemplaylist.intake.DeliveryKind
import io.github.shmemcat.shmemplaylist.intake.DirectMediaStoreEvidence
import io.github.shmemcat.shmemplaylist.intake.DisplayNameEvidence
import io.github.shmemcat.shmemplaylist.intake.IntentEvidence
import io.github.shmemcat.shmemplaylist.intake.RedactedUriShape
import io.github.shmemcat.shmemplaylist.intake.UriEvidence
import io.github.shmemcat.shmemplaylist.intake.UriSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticReportFormatterTest {
    @Test
    fun reportNeverContainsRawUriNamesTagsOrShareText() {
        val sensitiveUri = "content://provider/private/Secret%20Artist/Secret%20Title.mp3?token=hunter2"
        val intent = IntentEvidence(
            action = "android.intent.action.SEND",
            declaredMimeType = "audio/mpeg",
            flags = 1,
            categories = emptyList(),
            clipItemCount = 1,
            supplementalTextLength = 28,
            uri = Uri.parse(sensitiveUri),
            uriSources = setOf(UriSource.EXTRA_STREAM),
            issue = null,
        )
        val evidence = UriEvidence(
            uriShape = RedactedUriShape("content", "provider", 3, true, false),
            resolverMimeType = "audio/mpeg",
            displayName = DisplayNameEvidence("Secret Title.mp3", "mp3", 22),
            sizeBytes = 123L,
            streamAccessible = true,
            descriptorAccessible = true,
            descriptorLength = 123L,
            seekable = true,
            metadata = null,
            directMediaStore = DirectMediaStoreEvidence(false, null, null, null, false),
            errors = listOf("Provider failed\r\nInjected line"),
        )

        val report = DiagnosticReportFormatter.format(DeliveryKind.COLD, intent, evidence).text

        assertFalse(report.contains("Secret", ignoreCase = true))
        assertFalse(report.contains("hunter2"))
        assertFalse(report.contains(sensitiveUri))
        assertTrue(report.contains("content://provider/<3 segments>?<redacted>"))
        assertFalse(report.contains("\r"))
        assertTrue(report.contains("Provider failed  Injected line"))
    }
}
