package org.greenstand.android.TreeTracker.viewmodels

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.UUID
import kotlin.math.roundToInt
import org.greenstand.android.TreeTracker.analytics.Analytics
import org.greenstand.android.TreeTracker.data.NewTree
import org.greenstand.android.TreeTracker.managers.FeatureFlags
import org.greenstand.android.TreeTracker.managers.LocationDataCapturer
import org.greenstand.android.TreeTracker.managers.LocationUpdateManager
import org.greenstand.android.TreeTracker.managers.User
import org.greenstand.android.TreeTracker.usecases.CreateTreeParams
import org.greenstand.android.TreeTracker.usecases.CreateTreeUseCase
import org.greenstand.android.TreeTracker.utilities.ValueHelper

class NewTreeViewModel(
    private val user: User,
    private val locationUpdateManager: LocationUpdateManager,
    private val locationDataCapturer: LocationDataCapturer,
    private val createTreeUseCase: CreateTreeUseCase,
    private val analytics: Analytics
) : ViewModel() {

    val onTreeSaved: MutableLiveData<Unit> = MutableLiveData()
    val onInsufficientGps: MutableLiveData<Unit> = MutableLiveData()
    val navigateToTreeHeight: MutableLiveData<NewTree> = MutableLiveData()
    val navigateBack: MutableLiveData<Unit> = MutableLiveData()
    val onTakePicture: MutableLiveData<Unit> = MutableLiveData()
    private var newTreeUuid: UUID? = null

    val noteEnabledLiveData: LiveData<Boolean> = MutableLiveData<Boolean>().apply {
        postValue(FeatureFlags.TREE_NOTE_FEATURE_ENABLED)
    }

    val accuracyLiveData: LiveData<Int> = MutableLiveData<Int>().apply {
        postValue(locationUpdateManager.currentLocation?.accuracy?.roundToInt() ?: 0)
    }

    var photoPath: String? = null

    init {
        if (locationUpdateManager.currentLocation == null) {
            onInsufficientGps.postValue(Unit)
            navigateBack.postValue(Unit)
        } else if (photoPath == null) {
            onTakePicture.postValue(Unit)
        }
    }

    suspend fun createTree(note: String) {

        val newTree = createNewTree(note, photoPath!!, newTreeUuid!!)

        if (newTree.content.isNotBlank()) {
            analytics.treeNoteAdded(newTree.content.length)
        }

        if (FeatureFlags.TREE_HEIGHT_FEATURE_ENABLED) {
            navigateToTreeHeight.postValue(newTree)
        } else {
            saveTree(newTree)
            onTreeSaved.postValue(Unit)
            navigateBack.postValue(Unit)
        }
    }

    private suspend fun saveTree(newTree: NewTree): Long {
        val createTreeParams = CreateTreeParams(
            planterCheckInId = newTree.planterCheckInId,
            photoPath = newTree.photoPath,
            content = newTree.content,
            treeUuid = newTree.treeUuid
        )

        return createTreeUseCase.execute(createTreeParams)
    }

    fun isImageBlurry(data: Intent): Boolean {
        val imageQuality = data.getDoubleExtra(ValueHelper.FOCUS_METRIC_VALUE, 0.0)
        return imageQuality < FOCUS_THRESHOLD
    }

    fun newTreePhotoCaptured() {
        newTreeUuid = locationDataCapturer.generatedTreeUuid
        locationDataCapturer.turnOffTreeCaptureMode()
    }

    fun newTreeCaptureCancelled() {
        newTreeUuid = null
        locationDataCapturer.turnOffTreeCaptureMode()
    }

    private fun createNewTree(note: String, photoPath: String, newTreeUuid: UUID): NewTree {
        return NewTree(
            photoPath,
            note,
            user.planterCheckinId ?: -1
            newTreeUuid
        )
    }

    companion object {
        const val FOCUS_THRESHOLD = 700.0
    }
}
