package io.github.shmemcat.shmemplaylist.intake

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

enum class DeliveryKind {
    COLD,
    WARM,
}

enum class UriSource {
    EXTRA_STREAM,
    CLIP_DATA,
}

data class IntentEvidence(
    val action: String?,
    val declaredMimeType: String?,
    val flags: Int,
    val categories: List<String>,
    val clipItemCount: Int,
    val supplementalTextLength: Int?,
    val uri: Uri?,
    val uriSources: Set<UriSource>,
    val issue: String?,
)

sealed interface IntakeResult {
    data class Share(val evidence: IntentEvidence) : IntakeResult

    data class NoShare(val action: String?) : IntakeResult

    data class InvalidShare(val evidence: IntentEvidence) : IntakeResult
}

class ShareIntentIntake {
    fun receive(intent: Intent): IntakeResult {
        if (intent.action != Intent.ACTION_SEND) {
            return IntakeResult.NoShare(intent.action)
        }

        val extraUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val clipUris = buildList {
            val clipData = intent.clipData
            if (clipData != null) {
                for (index in 0 until clipData.itemCount) {
                    clipData.getItemAt(index).uri?.let(::add)
                }
            }
        }
        val distinctUris = (listOfNotNull(extraUri) + clipUris).distinct()
        val uri = distinctUris.singleOrNull()
        val issue = when {
            distinctUris.isEmpty() -> "No shared stream URI was supplied."
            distinctUris.size > 1 -> "The share contains multiple or inconsistent stream URIs."
            uri?.scheme != "content" && uri?.scheme != "file" ->
                "The shared URI scheme is unsupported."
            else -> null
        }
        val sources = buildSet {
            if (extraUri == uri) add(UriSource.EXTRA_STREAM)
            if (clipUris.contains(uri)) add(UriSource.CLIP_DATA)
        }
        val evidence = IntentEvidence(
            action = intent.action,
            declaredMimeType = intent.type,
            flags = intent.flags,
            categories = intent.categories?.sorted().orEmpty(),
            clipItemCount = intent.clipData?.itemCount ?: 0,
            supplementalTextLength = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.length,
            uri = uri,
            uriSources = sources,
            issue = issue,
        )
        return if (issue == null) IntakeResult.Share(evidence) else IntakeResult.InvalidShare(evidence)
    }
}
