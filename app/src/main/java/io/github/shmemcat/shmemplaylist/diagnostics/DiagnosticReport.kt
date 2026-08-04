package io.github.shmemcat.shmemplaylist.diagnostics

import io.github.shmemcat.shmemplaylist.intake.DeliveryKind
import io.github.shmemcat.shmemplaylist.intake.IntentEvidence
import io.github.shmemcat.shmemplaylist.intake.UriEvidence

private const val MAX_REPORT_LENGTH = 16_384

data class DiagnosticReport(
    val text: String,
) {
    init {
        require(text.length <= MAX_REPORT_LENGTH)
    }
}

object DiagnosticReportFormatter {
    fun format(
        deliveryKind: DeliveryKind,
        intent: IntentEvidence,
        uri: UriEvidence,
    ): DiagnosticReport {
        val report = buildString {
            appendLine("Shmemplaylist GoneMAD intake diagnostic v1")
            appendLine("redaction: required")
            appendLine("delivery: ${deliveryKind.name.lowercase()}")
            appendLine("action: ${intent.action.safeValue()}")
            appendLine("declared MIME: ${intent.declaredMimeType.safeValue()}")
            appendLine("flags: 0x${intent.flags.toUInt().toString(16)}")
            appendLine("categories: ${intent.categories.joinToString().safeValue()}")
            appendLine("URI: ${uri.uriShape}")
            appendLine("URI sources: ${intent.uriSources.joinToString()}")
            appendLine("ClipData items: ${intent.clipItemCount}")
            appendLine("supplemental text: ${intent.supplementalTextLength.presence()}")
            appendLine("resolver MIME: ${uri.resolverMimeType.safeValue()}")
            appendLine(
                "display name: redacted; extension=${uri.displayName.extension.safeValue()}, " +
                    "length=${uri.displayName.length.safeValue()}",
            )
            appendLine("size bytes: ${uri.sizeBytes.safeValue()}")
            appendLine("stream accessible: ${uri.streamAccessible}")
            appendLine("descriptor accessible: ${uri.descriptorAccessible}")
            appendLine("descriptor length: ${uri.descriptorLength.safeValue()}")
            appendLine("seekable: ${uri.seekable.safeValue()}")
            appendLine("metadata duration ms: ${uri.metadata?.durationMs.safeValue()}")
            appendLine("metadata title present: ${uri.metadata?.titlePresent.safeValue()}")
            appendLine("metadata artist present: ${uri.metadata?.artistPresent.safeValue()}")
            appendLine("metadata album present: ${uri.metadata?.albumPresent.safeValue()}")
            appendLine("metadata track present: ${uri.metadata?.trackNumberPresent.safeValue()}")
            appendLine("metadata disc present: ${uri.metadata?.discNumberPresent.safeValue()}")
            appendLine("direct MediaStore URI: ${uri.directMediaStore.isMediaStoreUri}")
            appendLine("MediaStore volume: ${uri.directMediaStore.volume.safeValue()}")
            appendLine("MediaStore ID: ${uri.directMediaStore.id.safeValue()}")
            appendLine("relative path: redacted; depth=${uri.directMediaStore.relativePathDepth.safeValue()}")
            appendLine("direct MediaStore query: ${uri.directMediaStore.querySucceeded}")
            if (uri.errors.isEmpty()) {
                appendLine("probe errors: none")
            } else {
                appendLine("probe errors:")
                uri.errors.forEach { appendLine("- ${it.singleLine(256)}") }
            }
            appendLine("playlist access: not requested")
            appendLine("playlist mutation: disabled")
        }.take(MAX_REPORT_LENGTH)
        return DiagnosticReport(report)
    }
}

private fun Any?.safeValue(): String = this?.toString()?.singleLine(256) ?: "unknown"

private fun Int?.presence(): String = if (this == null) "absent" else "present; length=$this"

private fun String.singleLine(maxLength: Int): String =
    replace('\r', ' ').replace('\n', ' ').take(maxLength)
