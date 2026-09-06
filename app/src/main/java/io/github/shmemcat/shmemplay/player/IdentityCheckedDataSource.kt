package io.github.shmemcat.shmemplay.player

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/** MediaStore can reuse IDs after a database rebuild. Do not play a different file under a saved URI. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class IdentityCheckedDataSource(private val context: Context, private val expected: QueueTrack, private val delegate: DataSource) : DataSource by delegate {
    override fun open(dataSpec: DataSpec): Long {
        val columns = if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.RELATIVE_PATH) else arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val matches = context.contentResolver.query(android.net.Uri.parse(expected.uri),columns,null,null,null)?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == expected.filename &&
                (Build.VERSION.SDK_INT < 29 || expected.relativePath == null || cursor.getString(1)?.trim('/') == expected.relativePath?.trim('/'))
        } ?: false
        if(!matches) throw IOException("The saved audio file is missing or its MediaStore identity changed.")
        return delegate.open(dataSpec)
    }
}

