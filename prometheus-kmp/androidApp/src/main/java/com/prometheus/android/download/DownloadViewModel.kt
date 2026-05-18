package com.prometheus.android.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.prometheus.android.inference.ModelManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadUiState(
    val isDownloading: Boolean = false,
    val progress: Int = -1,
    val statusText: String = "",
    val isComplete: Boolean = false,
    val isFailed: Boolean = false
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    val future = workManager.getWorkInfosByTag("model_download")
                    val infos = future.get()
                    val info = infos.lastOrNull()
                    _uiState.value = when (info?.state) {
                        WorkInfo.State.ENQUEUED -> DownloadUiState(
                            isDownloading = true,
                            statusText = "Waiting..."
                        )
                        WorkInfo.State.RUNNING -> {
                            val progress = info.progress.getInt("progress", -1)
                            DownloadUiState(
                                isDownloading = true,
                                progress = progress,
                                statusText = if (progress >= 0) "Downloading $progress%" else "Downloading..."
                            )
                        }
                        WorkInfo.State.SUCCEEDED -> DownloadUiState(
                            isComplete = true,
                            statusText = "Download complete!"
                        )
                        WorkInfo.State.FAILED -> DownloadUiState(
                            isFailed = true,
                            statusText = "Download failed"
                        )
                        WorkInfo.State.CANCELLED -> DownloadUiState(
                            statusText = "Download cancelled"
                        )
                        else -> DownloadUiState()
                    }
                    if (info?.state?.isFinished == true) break
                } catch (_: Exception) {}
                delay(1000)
            }
        }
    }

    fun startDownload(allowCellular: Boolean = false) {
        ModelManager.enqueueDownload(getApplication(), allowCellular)
    }

    fun cancelDownload() {
        ModelManager.cancelDownload(getApplication())
    }
}
