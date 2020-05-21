package me.devsaki.hentoid.database;

import android.content.Context;
import android.util.SparseIntArray;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import io.objectbox.BoxStore;
import io.objectbox.android.ObjectBoxDataSource;
import io.objectbox.android.ObjectBoxLiveData;
import io.objectbox.query.Query;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

public class ObjectBoxDAO implements CollectionDAO {

    private final ObjectBoxDB db;


    @IntDef({Mode.SEARCH_CONTENT_MODULAR, Mode.COUNT_CONTENT_MODULAR, Mode.SEARCH_CONTENT_UNIVERSAL, Mode.COUNT_CONTENT_UNIVERSAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
        int SEARCH_CONTENT_MODULAR = 0;
        int COUNT_CONTENT_MODULAR = 1;
        int SEARCH_CONTENT_UNIVERSAL = 2;
        int COUNT_CONTENT_UNIVERSAL = 3;
    }

    public ObjectBoxDAO(Context ctx) {
        db = ObjectBoxDB.getInstance(ctx);
    }

    // Use for testing (store generated by the test framework)
    public ObjectBoxDAO(BoxStore store) {
        db = ObjectBoxDB.getInstance(store);
    }


    public void cleanup() {
        db.closeThreadResources();
    }

    @Override
    public Single<List<Long>> getStoredBookIds(boolean nonFavouritesOnly, boolean includeQueued) {
        return Single.fromCallable(() -> Helper.getListFromPrimitiveArray(db.selectStoredContentIds(nonFavouritesOnly, includeQueued)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Single<List<Long>> getRecentBookIds(int orderField, boolean orderDesc, boolean favouritesOnly) {
        return Single.fromCallable(() -> contentIdSearch(Mode.SEARCH_CONTENT_MODULAR, "", Collections.emptyList(), orderField, orderDesc, favouritesOnly))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Single<List<Long>> searchBookIds(String query, List<Attribute> metadata, int orderField, boolean orderDesc, boolean favouritesOnly) {
        return Single.fromCallable(() -> contentIdSearch(Mode.SEARCH_CONTENT_MODULAR, query, metadata, orderField, orderDesc, favouritesOnly))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Single<List<Long>> searchBookIdsUniversal(String query, int orderField, boolean orderDesc, boolean favouritesOnly) {
        return
                Single.fromCallable(() -> contentIdSearch(Mode.SEARCH_CONTENT_UNIVERSAL, query, Collections.emptyList(), orderField, orderDesc, favouritesOnly))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Single<AttributeQueryResult> getAttributeMasterDataPaged(
            @NonNull List<AttributeType> types,
            String filter,
            List<Attribute> attrs,
            boolean filterFavourites,
            int page,
            int booksPerPage,
            int orderStyle) {
        return Single
                .fromCallable(() -> pagedAttributeSearch(types, filter, attrs, filterFavourites, orderStyle, page, booksPerPage))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Single<SparseIntArray> countAttributesPerType(List<Attribute> filter) {
        return Single.fromCallable(() -> count(filter))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public LiveData<PagedList<Content>> getErrorContent() {
        Query<Content> query = db.selectErrorContentQ();

        PagedList.Config cfg = new PagedList.Config.Builder().setEnablePlaceholders(true).setInitialLoadSizeHint(40).setPageSize(20).build();

        return new LivePagedListBuilder<>(new ObjectBoxDataSource.Factory<>(query), cfg).build();
    }

    public LiveData<Integer> countAllBooks() {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        ObjectBoxLiveData<Content> livedata = new ObjectBoxLiveData<>(db.selectVisibleContentQ());

        MediatorLiveData<Integer> result = new MediatorLiveData<>();
        result.addSource(livedata, v -> result.setValue(v.size()));
        return result;
    }

    public LiveData<Integer> countBooks(String query, List<Attribute> metadata, boolean favouritesOnly) {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        ObjectBoxLiveData<Content> livedata = new ObjectBoxLiveData<>(db.queryContentSearchContent(query, metadata, favouritesOnly, Preferences.Constant.ORDER_FIELD_NONE, false));

        MediatorLiveData<Integer> result = new MediatorLiveData<>();
        result.addSource(livedata, v -> result.setValue(v.size()));
        return result;
    }

    public LiveData<PagedList<Content>> searchBooksUniversal(String query, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll) {
        return getPagedContent(Mode.SEARCH_CONTENT_UNIVERSAL, query, Collections.emptyList(), orderField, orderDesc, favouritesOnly, loadAll);
    }

    public LiveData<PagedList<Content>> searchBooks(String query, List<Attribute> metadata, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll) {
        return getPagedContent(Mode.SEARCH_CONTENT_MODULAR, query, metadata, orderField, orderDesc, favouritesOnly, loadAll);
    }

    public LiveData<PagedList<Content>> getRecentBooks(int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll) {
        return getPagedContent(Mode.SEARCH_CONTENT_MODULAR, "", Collections.emptyList(), orderField, orderDesc, favouritesOnly, loadAll);
    }

    private LiveData<PagedList<Content>> getPagedContent(
            int mode,
            String filter,
            List<Attribute> metadata,
            int orderField,
            boolean orderDesc,
            boolean favouritesOnly,
            boolean loadAll) {
        boolean isRandom = (orderField == Preferences.Constant.ORDER_FIELD_RANDOM);

        Query<Content> query;
        if (Mode.SEARCH_CONTENT_MODULAR == mode) {
            query = db.queryContentSearchContent(filter, metadata, favouritesOnly, orderField, orderDesc);
        } else { // Mode.SEARCH_CONTENT_UNIVERSAL
            query = db.queryContentUniversal(filter, favouritesOnly, orderField, orderDesc);
        }

        int nbPages = Preferences.getContentPageQuantity();
        int initialLoad = nbPages * 2;
        if (loadAll) {
            // Trump Android's algorithm by setting a number of pages higher that the actual number of results
            // to avoid having a truncated result set (see issue #501)
            initialLoad = (int) Math.ceil(query.count() * 1.0 / nbPages) * nbPages;
        }

        PagedList.Config cfg = new PagedList.Config.Builder().setEnablePlaceholders(!loadAll).setInitialLoadSizeHint(initialLoad).setPageSize(nbPages).build();

        return new LivePagedListBuilder<>(
                isRandom ? new ObjectBoxRandomDataSource.RandomDataSourceFactory<>(query) : new ObjectBoxDataSource.Factory<>(query),
                cfg
        ).build();
    }

    @Nullable
    public Content selectContent(long id) {
        return db.selectContentById(id);
    }

    @Nullable
    public Content selectContentBySourceAndUrl(@NonNull Site site, @NonNull String url) {
        return db.selectContentBySourceAndUrl(site, url);
    }

    public void insertContent(@NonNull final Content content) {
        db.insertContent(content);
    }

    public void updateContentStatus(@NonNull final StatusContent updateFrom, @NonNull final StatusContent updateTo) {
        db.updateContentStatus(updateFrom, updateTo);
    }

    public void deleteContent(@NonNull final Content content) {
        db.deleteContent(content);
    }

    public List<ErrorRecord> selectErrorRecordByContentId(long contentId) {
        return db.selectErrorRecordByContentId(contentId);
    }

    public void insertErrorRecord(@NonNull final ErrorRecord record) {
        db.insertErrorRecord(record);
    }

    public void deleteErrorRecords(long contentId) {
        db.deleteErrorRecords(contentId);
    }

    // Warning : no threads used there; this is a blocking operation
    public void deleteAllLibraryBooks() {
        Timber.i("Cleaning up library");
        db.deleteContentById(db.findAllLibraryBooksIds());

        // Switch status of all remaining images (i.e. from queued books) to SAVED, as we cannot guarantee the files are still there
        long[] remainingContentIds = db.findAllQueueBooksIds();
        for (long contentId : remainingContentIds)
            db.updateImageContentStatus(contentId, null, StatusContent.SAVED);
    }

    public void deleteAllQueuedBooks() {
        Timber.i("Cleaning up queue");
        db.deleteContentById(db.findAllQueueBooksIds());

        // Switch status of all remaining images (i.e. from queued books) to SAVED, as we cannot guarantee the files are still there
        long[] remainingContentIds = db.findAllLibraryBooksIds();
        for (long contentId : remainingContentIds)
            db.updateImageContentStatus(contentId, null, StatusContent.SAVED);
    }


    public void insertImageFile(@NonNull ImageFile img) {
        db.insertImageFile(img);
    }

    public void replaceImageList(long contentId, @NonNull final List<ImageFile> newList) {
        db.deleteImageFiles(contentId);
        for (ImageFile img : newList) img.setContentId(contentId);
        db.insertImageFiles(newList);
    }

    public void updateImageContentStatus(long contentId, StatusContent updateFrom, @NonNull StatusContent updateTo) {
        db.updateImageContentStatus(contentId, updateFrom, updateTo);
    }

    public void updateImageFileStatusParamsMimeTypeUri(@NonNull ImageFile image) {
        db.updateImageFileStatusParamsMimeTypeUri(image);
    }

    public void deleteImageFile(@NonNull ImageFile img) {
        db.deleteImageFile(img.getId());
    }

    @Nullable
    public ImageFile selectImageFile(long id) {
        return db.selectImageFile(id);
    }

    public LiveData<List<ImageFile>> getDownloadedImagesFromContent(long id) {
        return new ObjectBoxLiveData<>(db.selectDownloadedImagesFromContent(id));
    }

    public SparseIntArray countProcessedImagesById(long contentId) {
        return db.countProcessedImagesById(contentId);
    }


    public void addContentToQueue(@NonNull final Content content, StatusContent targetImageStatus) {
        if (targetImageStatus != null)
            db.updateImageContentStatus(content.getId(), null, targetImageStatus);

        content.setStatus(StatusContent.DOWNLOADING);
        db.insertContent(content);

        List<QueueRecord> queue = db.selectQueue();
        int lastIndex = 1;
        if (!queue.isEmpty())
            lastIndex = queue.get(queue.size() - 1).rank + 1;
        db.insertQueue(content.getId(), lastIndex);
    }

    private List<Long> contentIdSearch(@Mode int mode, String filter, List<Attribute> metadata, int orderField, boolean orderDesc, boolean favouritesOnly) {

        if (Mode.SEARCH_CONTENT_MODULAR == mode) {
            return Helper.getListFromPrimitiveArray(db.selectContentSearchId(filter, metadata, favouritesOnly, orderField, orderDesc));
        } else if (Mode.SEARCH_CONTENT_UNIVERSAL == mode) {
            return Helper.getListFromPrimitiveArray(db.selectContentUniversalId(filter, favouritesOnly, orderField, orderDesc));
        } else {
            return Collections.emptyList();
        }
    }

    private AttributeQueryResult pagedAttributeSearch(
            @NonNull List<AttributeType> attrTypes,
            String filter,
            List<Attribute> attrs,
            boolean filterFavourites,
            int sortOrder,
            int pageNum,
            int itemPerPage) {
        AttributeQueryResult result = new AttributeQueryResult();

        if (!attrTypes.isEmpty()) {
            if (attrTypes.get(0).equals(AttributeType.SOURCE)) {
                result.attributes.addAll(db.selectAvailableSources(attrs));
                result.totalSelectedAttributes = result.attributes.size();
            } else {
                for (AttributeType type : attrTypes) {
                    // TODO fix sorting when concatenating both lists
                    result.attributes.addAll(db.selectAvailableAttributes(type, attrs, filter, filterFavourites, sortOrder, pageNum, itemPerPage));
                    result.totalSelectedAttributes += db.countAvailableAttributes(type, attrs, filter, filterFavourites);
                }
            }
        }

        return result;
    }

    private SparseIntArray count(List<Attribute> filter) {
        SparseIntArray result;

        if (null == filter || filter.isEmpty()) {
            result = db.countAvailableAttributesPerType();
            result.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources().size());
        } else {
            result = db.countAvailableAttributesPerType(filter);
            result.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources(filter).size());
        }

        return result;
    }

    public LiveData<List<QueueRecord>> getQueueContent() {
        return new ObjectBoxLiveData<>(db.selectQueueContentsQ());
    }

    public List<QueueRecord> selectQueue() {
        return db.selectQueue();
    }

    public void updateQueue(@NonNull List<QueueRecord> queue) {
        db.updateQueue(queue);
    }

    public void deleteQueue(@NonNull Content content) {
        db.deleteQueue(content);
    }

    public void deleteQueue(int index) {
        db.deleteQueue(index);
    }

    public SiteHistory getHistory(@NonNull Site s) {
        return db.selectHistory(s);
    }

    public void insertSiteHistory(@NonNull Site site, @NonNull String url) {
        db.insertSiteHistory(site, url);
    }


    // ONE-TIME USE QUERIES (MIGRATION & CLEANUP)

    @Override
    public Single<List<Long>> getOldStoredBookIds() {
        return Single.fromCallable(() -> Helper.getListFromPrimitiveArray(db.selectOldStoredContentIds()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }
}
