package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.annimon.stream.Stream
import io.reactivex.disposables.CompositeDisposable
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content


class MetadataEditViewModel(
    application: Application,
    private val dao: CollectionDAO
) : AndroidViewModel(application) {

    // Cleanup for all RxJava calls
    private val compositeDisposable = CompositeDisposable()

    // LiveData for the UI
    val contentList = MutableLiveData<List<Content>>()


    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
        compositeDisposable.clear()
    }

    fun getContent(): LiveData<List<Content>> {
        return contentList
    }

    /**
     * Load the given list of Content
     *
     * @param contentId  IDs of the Contents to load
     */
    fun loadContent(contentId: LongArray) {
        contentList.postValue(dao.selectContent(contentId.filter { id -> id > 0 }.toLongArray()))
    }

    fun setCover(order: Int) {
        val content = contentList.value?.get(0)
        if (content != null) {
            content.imageFiles?.forEach {
                if (it.order == order) {
                    it.setIsCover(true)
                    content.coverImageUrl = it.url
                } else {
                    it.setIsCover(false)
                }
            }
            contentList.postValue(Stream.of(content).toList())
        }
    }
}