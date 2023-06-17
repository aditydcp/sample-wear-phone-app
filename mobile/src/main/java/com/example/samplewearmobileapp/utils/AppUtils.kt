package com.example.samplewearmobileapp.utils

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.ContextThemeWrapper
import com.example.samplewearmobileapp.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.*

object AppUtils {
    private const val TAG = "App Utilities"

    /**
     * General alert dialog.
     *
     * @param context The context.
     * @param title   The dialog title.
     * @param msg     The dialog message.
     */
    private fun alert(context: Context, title: String, msg: String) {
        try {
            val alertDialog = AlertDialog.Builder(
                ContextThemeWrapper(
                    context,
                    R.style.InverseTheme
                )
            )
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(
                    context.getText(R.string.ok)
                ) { dialog, _ -> dialog.cancel() }.create()
            alertDialog.show()
        } catch (t: Throwable) {
            Log.e(
                getContextTag(context), """
     Error using ${title}AlertDialog
     $t
     ${t.message}
     """.trimIndent()
            )
        }
    }

    /**
     * Error message dialog.
     *
     * @param context The context.
     * @param msg     The dialog message.
     */
    fun errMsg(context: Context, msg: String) {
        Log.e(TAG, getContextTag(context) + msg)
        alert(context, context.getText(R.string.error).toString(), msg)
    }

    /**
     * Error message dialog.
     *
     * @param context The context.
     * @param msg     The dialog message.
     */
    fun warnMsg(context: Context, msg: String) {
        Log.w(TAG, getContextTag(context) + msg)
        alert(context, context.getText(R.string.warning).toString(), msg)
    }

    /**
     * Info message dialog.
     *
     * @param context The context.
     * @param msg     The dialog message.
     */
    fun infoMsg(context: Context, msg: String) {
        Log.i(TAG, getContextTag(context) + msg)
        alert(context, context.getText(R.string.info).toString(), msg)
    }

    /**
     * Exception message dialog. Displays message plus the exception and
     * exception message.
     *
     * @param context The context.
     * @param msg     The dialog message.
     * @param t       The throwable.
     */
    fun excMsg(context: Context, msg: String, t: Throwable) {
        var msg = msg
        msg += """
             
             ${context.getText(R.string.exception)}: $t
             ${t.message}
             """.trimIndent()
        val fullMsg = msg
        Log.e(TAG, getContextTag(context) + msg)
        alert(context, context.getText(R.string.exception).toString(), fullMsg)
    }

    /**
     * Utility method to get a tag representing the Context to associate with a
     * log message.
     *
     * @param context The context.
     * @return The context tag.
     */
    private fun getContextTag(context: Context?): String {
        return if (context == null) {
            "<???>: "
        } else "Utils: " + context.javaClass.simpleName + ": "
    }

    /**
     * Get the stack trace for a throwable as a String.
     *
     * @param t The throwable.
     * @return The stack trace as a String.
     */
    fun getStackTraceString(t: Throwable): String? {
        val baos = ByteArrayOutputStream()
        val ps = PrintStream(baos)
        t.printStackTrace(ps)
        ps.close()
        return baos.toString()
    }

    /**
     * Get the extension of a file.
     *
     * @param file The file.
     * @return The extension without the dot.
     */
    fun getExtension(file: File): String? {
        var ext: String? = null
        val s = file.name
        val i = s.lastIndexOf('.')
        if (i > 0 && i < s.length - 1) {
            ext = s.substring(i + 1).lowercase(Locale.getDefault())
        }
        return ext
    }

    /**
     * Utility method for printing a hash code in hex.
     *
     * @param obj The object whose hash code is desired.
     * @return The hex-formatted hash code.
     */
    fun getHashCode(obj: Any?): String? {
        return if (obj == null) {
            "null"
        } else String.format("%08X", obj.hashCode())
    }

    /**
     * Get the version name for the application with the specified context.
     *
     * @param context The context.
     * @return The package name.
     */
    fun getVersion(context: Context): String? {
        var versionName: String? = "NA"
        try {
            versionName = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager
                    .getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    ).versionName
            } else {
                context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName
            }
        } catch (ex: Exception) {
            // Do nothing
        }
        return versionName
    }

    /**
     * Get the orientation of the device.
     *
     * @param ctx The Context.
     * @return Either "Portrait" or "Landscape".
     */
    fun getOrientation(ctx: Context): String? {
        val orientation = ctx.resources.configuration.orientation
        return if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            "Portrait"
        } else {
            "Landscape"
        }
    }

//    /**
//     * Utility method to get an info string listing all the keys,value pairs
//     * in the given SharedPreferences.
//     *
//     * @param prefix String with text to prepend to each line, e.g. "    ".
//     * @param prefs  The given Preferences.
//     * @return The info/
//     */
//    fun getSharedPreferencesInfo(
//        prefix: String?,
//        prefs: SharedPreferences
//    ): String? {
//        val map = prefs.all
//        val sb = StringBuilder()
//        for ((key, value1): Map.Entry<String, *> in map) {
//            val value = value1!!
//            sb.append(prefix).append("key=").append(key)
//                .append(" value=").append(value).append("\n")
//        }
//        return sb.toString()
//    }
}