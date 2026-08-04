package io.github.shmemcat.shmemplaylist.intake

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareIntentIntakeTest {
    private val intake = ShareIntentIntake()

    @Test
    fun launcherIntentIsNotTreatedAsShare() {
        val result = intake.receive(Intent(Intent.ACTION_MAIN))

        assertTrue(result is IntakeResult.NoShare)
    }

    @Test
    fun matchingExtraAndClipUriAreAcceptedWithBothSources() {
        val uri = Uri.parse("content://media/external/audio/media/42")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("track", uri)
        }

        val result = intake.receive(intent) as IntakeResult.Share

        assertEquals(uri, result.evidence.uri)
        assertEquals(setOf(UriSource.EXTRA_STREAM, UriSource.CLIP_DATA), result.evidence.uriSources)
        assertEquals(1, result.evidence.clipItemCount)
    }

    @Test
    fun inconsistentUrisAreRejectedWithoutSelectingEither() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://provider/first"))
            clipData = ClipData.newRawUri("track", Uri.parse("content://provider/second"))
        }

        val result = intake.receive(intent) as IntakeResult.InvalidShare

        assertEquals(null, result.evidence.uri)
        assertTrue(result.evidence.issue!!.contains("multiple"))
    }

    @Test
    fun supplementalTextStoresOnlyLength() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://provider/track"))
            putExtra(Intent.EXTRA_TEXT, "Secret Artist - Secret Title")
        }

        val result = intake.receive(intent) as IntakeResult.Share

        assertEquals(28, result.evidence.supplementalTextLength)
    }
}
