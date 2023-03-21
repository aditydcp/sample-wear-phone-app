package com.example.samplewearmobileapp.utils

import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import java.util.*

object UriUtils {
    private const val TAG = "URI Utilities"

    /**
     * Checks if a file exists for the given document Uri.
     *
     * @param context The context.
     * @param uri     The document Uri.
     * @return Whether it exists.
     */
    fun exists(context: Context, uri: Uri?): Boolean {
        // !!!!!!!!!!!!!!!!!!!!! A kludge. Needs to be tested.
//        Log.d(TAG, "exists: uri=" + uri.getLastPathSegment());
        try {
            context.contentResolver.query(
                uri!!,
                null, null, null, null
            ).use { cursor -> return cursor != null && cursor.moveToFirst() }
        } catch (ex: Exception) {
            return false
        }
    }

    /***
     * Gets the file name from the given Uri,
     * @param uri The Uri.
     * @return The file name or null if not determined.
     */
    fun getFileNameFromUri(uri: Uri?): String? {
        if (uri == null) return null
        val lastSeg = uri.lastPathSegment
        val tokens = lastSeg!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        val len = tokens.size
        return if (tokens == null || len == 0) null else tokens[len - 1]
    }

    /**
     * Gets the display name for a given documentUri.
     *
     * @param context The context.
     * @param uri     The document Uri.
     * @return The name.
     */
    fun getDisplayName(context: Context, uri: Uri?): String? {
        var displayName: String? = null
        try {
            context.contentResolver.query(
                uri!!, null, null,
                null, null
            ).use { cursor ->
                cursor!!.moveToFirst()
                val colIndex =
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                displayName = if (colIndex < 0) {
                    "NA"
                } else {
                    cursor.getString(colIndex)
                }
            }
        } catch (ex: Exception) {
            AppUtils.excMsg(context, "Error getting display name", ex)
        }
        return displayName
    }

    /**
     * Check if the mime type of a given document Uri represents a
     * directory.
     *
     * @param context The context.
     * @param uri     The document Uri.
     * @return Whether the Uri represents a directory.
     */
    fun isDirectory(context: Context, uri: Uri?): Boolean {
        if (!DocumentsContract.isDocumentUri(context, uri)) return false
        val contentResolver = context.contentResolver
        var mimeType = "NA"
        contentResolver.query(
            uri!!, arrayOf(
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        ).use { cursor ->
            if (cursor != null && cursor.count > 0) {
                cursor.moveToFirst()
                mimeType = cursor.getString(0)
            }
        }
        return DocumentsContract.Document.MIME_TYPE_DIR == mimeType
    }

    /**
     * Gets a List of the children of the given document Uri that match the
     * given extension.
     *
     * @param uri A document Uri.
     * @param ext The extension.
     * @return The list.
     */
    fun getChildren(
        context: Context, uri: Uri?,
        ext: String?
    ): List<UriData>? {
        val contentResolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getTreeDocumentId(uri)
        )
        val children: MutableList<UriData> = ArrayList()
        contentResolver.query(
            childrenUri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        ).use { cursor ->
            var documentId: String?
            var documentUri: Uri?
            var modifiedTime: Long
            var displayName: String?
            while (cursor!!.moveToNext()) {
                documentId = cursor.getString(0)
                documentUri = DocumentsContract.buildDocumentUriUsingTree(
                    uri,
                    documentId
                )
                if (documentUri == null) continue
                modifiedTime = cursor.getLong(1)
                displayName = cursor.getString(2)
                val name = documentUri.lastPathSegment
                if (name != null) {
                    if (name.lowercase(Locale.getDefault()).endsWith(ext!!)) {
                        children.add(
                            UriData(
                                documentUri, modifiedTime,
                                displayName
                            )
                        )
                    }
                }
            }
        }
        // Do nothing
        return children
    }

    /**
     * Releases all permissions for the given Context.
     *
     * @param context The context.
     */
    fun releaseAllPermissions(context: Context) {
        val resolver = context.contentResolver
        val permissionList = resolver.persistedUriPermissions
        val nPermissions = permissionList.size
        if (nPermissions == 0) {
//            Utils.warnMsg(this, "There are no persisted permissions");
            return
        }
        var uri: Uri
        for (permission in permissionList) {
            uri = permission.uri
            resolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /**
     * Removes all but the most recent nToKeep permissions.
     *
     * @param context The context.
     */
    fun trimPermissions(context: Context, nToKeep: Int) {
        val resolver = context.contentResolver
        val permissionList = resolver.persistedUriPermissions
        val nPermissions = permissionList.size
        if (nPermissions <= nToKeep) return
        // Add everything in permissionList to sortedList
        val sortedList: List<UriPermission> = ArrayList(permissionList)
        // Sort with newest first
        Collections.sort(
            sortedList
        ) { p1: UriPermission, p2: UriPermission ->
            java.lang.Long.compare(
                p2.persistedTime,
                p1.persistedTime
            )
        }
        for (i in nToKeep until nPermissions) {
            val permission = sortedList[i]
            resolver.releasePersistableUriPermission(
                permission.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /**
     * Returns the number of persisted permissions.
     *
     * @param context The context.
     * @return The number of persisted permissions or -1 on error.
     */
    fun getNPersistedPermissions(context: Context): Int {
        val resolver = context.contentResolver
        val permissionList = resolver.persistedUriPermissions
        return permissionList.size
    }

    /**
     * Returns information about the persisted permissions.
     *
     * @param context The context.
     * @return The information as a formatted string.
     */
    fun showPermissions(context: Context): String? {
        val resolver = context.contentResolver
        val permissionList = resolver.persistedUriPermissions
        val sb = StringBuilder()
        sb.append("Persistent Permissions").append("\n")
        for (permission in permissionList) {
            sb.append(permission.uri).append("\n")
            sb.append("    time=").append(Date(permission.persistedTime)).append(
                "\n"
            )
            sb.append("    access=").append(if (permission.isReadPermission) "R" else "")
                .append(if (permission.isWritePermission) "W" else "").append("\n")
            sb.append("    special objects flag=").append(permission.describeContents())
                .append("\n")
        }
        return sb.toString()
    }

    /**
     * Gets the application UID.  This is a unique user ID (UID) to each
     * Android application when it is installed.
     *
     * @param context The Context.
     * @return The UID or -1 on failure.
     */
    fun getApplicationUid(context: Context): Int {
        var uid = -1
        try {
            val info: ApplicationInfo
            info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager
                    .getApplicationInfo(
                        context.packageName,
                        PackageManager.ApplicationInfoFlags.of(0)
                    )
            } else {
                context.packageManager
                    .getApplicationInfo(context.packageName, 0)
            }
            if (info != null) {
                uid = info.uid
            }
        } catch (ex: Exception) {
            Log.e(TAG, "getApplicationUid: Failed to get UID", ex)
        }
        return uid
    }

    /**
     * Gets a String with the requested permissions. The ones granted will
     * be preceded by a Y and the ones not granted, by an N.
     *
     * @param ctx The Context.
     * @return A String with the info.
     */
    fun getRequestedPermissionsInfo(ctx: Context): String? {
        val sb = StringBuilder()
        sb.append("Permissions Granted").append("\n")
        return try {
            val pi = ctx.packageManager.getPackageInfo(
                ctx.packageName,
                PackageManager.GET_PERMISSIONS
            )
            val permissions = pi.requestedPermissions
            // Note: permissions seems to be  null rather than a
            // zero-length  array if there are no permissions
            if (permissions != null) {
                var granted: Boolean
                var shortName: String
                for (i in permissions.indices) {
                    granted = pi.requestedPermissionsFlags[i] and
                            PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                    shortName = permissions[i]
                        .substring("android.Permission.".length)
                    sb.append("  ").append(if (granted) "Y " else "N ").append(shortName).append(
                        "\n"
                    )
                }
            } else {
                sb.append("    None").append("\n")
            }
            sb.toString()
        } catch (ex: PackageManager.NameNotFoundException) {
            sb.append("   Error finding permissions for ")
                .append(ctx.packageName).append("\n")
            sb.toString()
        }
    }

    /**
     * Convenience class for managing Uri information.
     */
    class UriData internal constructor(
        val uri: Uri,
        val modifiedTime: Long,
        val displayName: String?
    )
}