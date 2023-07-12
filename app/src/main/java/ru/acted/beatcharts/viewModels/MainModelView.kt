package ru.acted.beatcharts.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import ru.acted.beatcharts.types.Song

class MainViewModel(application: Application): AndroidViewModel(application) {

    //General states
    val offlineMode = MutableLiveData<Boolean>()
    val username = MutableLiveData<String>()

    //Global communications
    val notifIndicator = MutableLiveData<Int>()
    val notifText = MutableLiveData<String>()

    //Main page
    val songData = MutableLiveData<Song>()
    val currentDialog = MutableLiveData<Int>()
    val songsList = MutableLiveData<List<Song>>()

    //Chart publication stuff
    val pubContentInteraction = MutableLiveData<Int>()
    val itemInteractionId = MutableLiveData<Int>()
    val pubProgressInteraction = MutableLiveData<Int>()

    //Temp stuff TODO remove it
    val songIdForPreview = MutableLiveData<Int>()

    init {
        notifIndicator.value = 0 //0 - undefined, 1 - show, 2 - hide
        username.value = "unknown"
        currentDialog.value = -1
        pubContentInteraction.value = 0
        itemInteractionId.value = 0
        pubProgressInteraction.value = 0
        offlineMode.value = false
        notifText.value = ""
        songIdForPreview.value = 0
    }

    fun showNotif(text: String) {notifText.value = text; notifIndicator.value = 1}
    fun setSongsList(newList: List<Song>) {songsList.value = newList}
    fun setSong(newData: Song) {songData.value = newData}
    fun changeDialogTo(dialogId: Int) {currentDialog.value = dialogId} //1 - delete chart

}