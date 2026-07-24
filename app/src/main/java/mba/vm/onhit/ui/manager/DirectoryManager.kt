package mba.vm.onhit.ui.manager

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import mba.vm.onhit.R
import mba.vm.onhit.ui.config.ConfigManager
import mba.vm.onhit.ui.model.FileData
import mba.vm.onhit.utils.FileUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DirectoryManager(
    private val activity: Activity,
    private val onDataChanged: (List<FileData>, String) -> Unit,
    private val onRefreshStateChanged: (Boolean) -> Unit
) {
    var currentDir: DocumentFile? = null
    var rootDir: DocumentFile? = null
    private var allFiles = listOf<FileData>()
    private var isRefreshing = false
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun restoreLastDirectory(): Boolean {
        val uri = ConfigManager.getRootUri(activity) ?: return false
        val hasPermission = activity.contentResolver.persistedUriPermissions.any { it.uri == uri }
        val df = if (hasPermission) DocumentFile.fromTreeUri(activity, uri) else null
        
        if (df != null && df.exists() && df.canRead()) {
            rootDir = df
            navigateTo(df)
            return true
        }
        return false
    }

    fun navigateTo(dir: DocumentFile) {
        currentDir = dir
        val simplifiedPath = FileUtils.getSimplifiedPath(activity, rootDir, dir)
        refreshCurrentDir(simplifiedPath)
    }

    fun navigateUp(): Boolean {
        if (currentDir?.uri != rootDir?.uri) {
            currentDir?.parentFile?.let {
                navigateTo(it)
                return true
            }
        }
        return false
    }

    fun refreshCurrentDir(pathDisplay: String? = null) {
        val dir = currentDir ?: return
        if (!dir.exists() || !dir.canRead()) {
            Toast.makeText(activity, R.string.toast_storage_unavailable, Toast.LENGTH_SHORT).show()
            if (dir.uri != rootDir?.uri) {
                rootDir?.let { navigateTo(it) } ?: requestSelectDirectory()
            } else {
                requestSelectDirectory()
            }
            return
        }
        
        if (isRefreshing) return
        isRefreshing = true
        onRefreshStateChanged(true)
        
        executor.execute {
            val newList = FileUtils.getFileDataList(activity, dir, rootDir)
            activity.runOnUiThread {
                allFiles = newList
                val currentPath = pathDisplay ?: FileUtils.getSimplifiedPath(activity, rootDir, dir)
                onDataChanged(allFiles, currentPath)
                isRefreshing = false
                onRefreshStateChanged(false)
            }
        }
    }

    fun filterFiles(query: String, pathDisplay: String) {
        val filtered = if (query.isEmpty()) {
            allFiles
        } else {
            allFiles.filter { it.isParent || it.name.contains(query, ignoreCase = true) }
        }
        onDataChanged(filtered, pathDisplay)
    }

    fun requestSelectDirectory() {
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1001)
    }

    fun handleDirectoryResult(data: Intent?) {
        data?.data?.let { uri ->
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            ConfigManager.setRootUri(activity, uri)
            val df = DocumentFile.fromTreeUri(activity, uri)
            if (df != null && df.exists() && df.canRead()) {
                rootDir = df
                navigateTo(df)
            } else {
                Toast.makeText(activity, R.string.toast_storage_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun destroy() {
        executor.shutdown()
    }
}