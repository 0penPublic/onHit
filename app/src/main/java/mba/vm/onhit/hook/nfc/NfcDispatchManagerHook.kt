package mba.vm.onhit.hook.nfc

import android.database.sqlite.SQLiteDatabase
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import io.github.kyuubiran.ezxhelper.core.misc.paramTypes
import io.github.kyuubiran.ezxhelper.core.misc.params
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.hook.BaseHook
import mba.vm.onhit.utils.LogUtils.logE
import mba.vm.onhit.utils.LogUtils.logI

/**
 * Hook handler for the Oplus (ColorOS) NFC Dispatch Manager.
 *
 * This hook serves two primary purposes:
 * 1. **Dispatch Interception**: Bypasses system-level foreground whitelist restrictions
 *    to prevent the dispatch manager from blocking NFC operations when this app is in focus.
 * 2. **Database & Cache Management**: Intercepts and holds a reference to the internal
 *    `DatabaseManager` instance, enabling direct SQLite database queries and cleanup operations
 *    for cached NFC tag data (e.g., UID cache).
 */
object NfcDispatchManagerHook : BaseHook() {

    /**
     * Holds a reference to the internal `mDatabaseManager` instance
     * inside `NfcDispatchManager` for direct SQLite database operations.
     */
    var databaseManager: Any? = null

    override fun init(classLoader: ClassLoader, packageName: String) {
        // Attempt to load the Oplus-specific NFC dispatch class.
        // Skip hooking if not found (for compatibility with non-ColorOS systems).
        val nfcDispatchManagerClazz = try {
            Class.forName("com.oplus.nfc.dispatch.NfcDispatchManager", false, classLoader)
        } catch (_: ClassNotFoundException) {
            logI("NfcDispatchManager not found, skip hook")
            return
        }

        logI("Found Oplus NFC Dispatch Manager, hooking")

        // Intercept initialization to capture the DatabaseManager reference
        MethodFinder.fromClass(nfcDispatchManagerClazz)
            .filterByName("init")
            .first()
            .createHook {
                after { param ->
                    databaseManager = param.thisObject.objectHelper().getObject("mDatabaseManager")
                    databaseManager?.let { logI("Oplus DatabaseManager found") }
                }
            }

        // Intercept foreground direct whitelist check
        MethodFinder.fromClass(nfcDispatchManagerClazz)
            .filterByName("checkForegroundDiretWhiteList")
            .first()
            .createHook {
                before { param ->
                    val pkg = param.args[0] as String
                    // Force return false if the target package is our application.
                    // Effect: Prevents the system from treating this app as whitelisted,
                    // bypassing default dispatch restrictions.
                    if (pkg == BuildConfig.APPLICATION_ID) param.result = false
                }
            }
    }

    /**
     * Clears local NFC tag caches maintained by the Oplus NFC service.
     *
     * The system retains UID cache records in SQLite after reading a card,
     * which can cause stale data or read errors on subsequent scans. This method
     * uses reflection on `DatabaseManager` to obtain a writable database and
     * purge the cached records for the specified UID.
     *
     * @param uid The card UID to clear from cache (as a byte array).
     */
    fun deleteTagCache(uid: ByteArray?) {
        databaseManager?.let { dm ->
            // Reflection call to acquire SQLiteDatabase instance
            (dm.objectHelper().invokeMethod(
                "getWritableDatabase",
                SQLiteDatabase::class.java,
                paramTypes(),
                params()
            ) as? SQLiteDatabase)?.let { db ->
                val hexUid = uid?.toHexString()?.lowercase()
                try {
                    // Remove cache entries from CardType and CommonDirectProperty tables
                    val cardTypeRows = db.delete("CardType", "uid=?", arrayOf(hexUid))
                    val propertyRows = db.delete("CommonDirectProperty", "uid=?", arrayOf(hexUid))
                    logI("Cleaned cache for UID $hexUid: CardType($cardTypeRows), Property($propertyRows)")
                } catch (e: Exception) {
                    logE("Failed to delete tag cache for UID $hexUid: ${e.message}", e)
                }
            } ?: logE("Failed to get writable database from DatabaseManager")
        } ?: logE("DatabaseManager is null, Ignore")
    }
}