package me.devsaki.hentoid.dirpicker.ui;

import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.IOException;

import io.fabric.sdk.android.InitializationException;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.dirpicker.events.CurrentRootDirChangedEvent;
import me.devsaki.hentoid.dirpicker.events.DataSetChangedEvent;
import me.devsaki.hentoid.dirpicker.events.OnDirCancelEvent;
import me.devsaki.hentoid.dirpicker.events.OnDirChosenEvent;
import me.devsaki.hentoid.dirpicker.events.OnMakeDirEvent;
import me.devsaki.hentoid.dirpicker.events.OnSAFRequestEvent;
import me.devsaki.hentoid.dirpicker.events.OnTextViewClickedEvent;
import me.devsaki.hentoid.dirpicker.events.OpFailedEvent;
import me.devsaki.hentoid.dirpicker.events.UpdateDirTreeEvent;
import me.devsaki.hentoid.dirpicker.exceptions.DirExistsException;
import me.devsaki.hentoid.dirpicker.exceptions.PermissionDeniedException;
import me.devsaki.hentoid.dirpicker.ops.DirListBuilder;
import me.devsaki.hentoid.dirpicker.ops.MakeDir;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

/**
 * Created by avluis on 06/12/2016.
 * Directory Chooser (Picker) Fragment Dialog
 */
public class DirChooserFragment extends DialogFragment implements
        View.OnClickListener, View.OnLongClickListener {
    private static final String CURRENT_ROOT_DIR = "currentRootDir";
    private static final String ROOT_DIR = "rootDir";

    private RecyclerView recyclerView;
    private TextView textView;
    private FloatingActionButton fabCreateDir,
            fabRequestSD;
    private Button selectDirBtn;
    private File currentRootDir;
    DirListBuilder dirListBuilder;

    public static DirChooserFragment newInstance(File rootDir) {
        DirChooserFragment dirChooserFragment = new DirChooserFragment();

        Bundle bundle = new Bundle();
        bundle.putSerializable(ROOT_DIR, rootDir);
        dirChooserFragment.setArguments(bundle);

        return dirChooserFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCurrentRootDir(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.ImportDialogTheme);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(CURRENT_ROOT_DIR, currentRootDir);
        super.onSaveInstanceState(outState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View rootView = inflater.inflate(R.layout.fragment_dir_picker, container, false);

        initUI(rootView);

        dirListBuilder = new DirListBuilder(requireActivity().getApplicationContext(), recyclerView);
        dirListBuilder.processListDirEvent(currentRootDir);

        return rootView;
    }

    private void initUI(View rootView) {
        recyclerView = rootView.findViewById(R.id.dir_list);
        textView = rootView.findViewById(R.id.current_dir);
        fabCreateDir = rootView.findViewById(R.id.create_dir);
        fabRequestSD = rootView.findViewById(R.id.request_sd);
        selectDirBtn = rootView.findViewById(R.id.select_dir);

        textView.setOnClickListener(this);
        textView.setOnLongClickListener(this);
        fabCreateDir.setOnClickListener(this);
        selectDirBtn.setOnClickListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && FileHelper.isSDPresent) {
            fabRequestSD.setOnClickListener(this);
            fabRequestSD.setVisibility(View.VISIBLE);
        }
    }

    @Subscribe
    public void onOpFailedEvent(OpFailedEvent event) {
        Timber.d(getString(R.string.op_not_allowed));
        Helper.toast(getActivity(), R.string.op_not_allowed);
    }

    @Subscribe
    public void onCurrentRootDirChangedEvent(CurrentRootDirChangedEvent event) {
        currentRootDir = event.getCurrentDirectory();
        textView.setText(event.getCurrentDirectory().toString());
    }

    @Subscribe
    public void onUpdateDirTreeEvent(UpdateDirTreeEvent event) {
        dirListBuilder.processListDirEvent(event.rootDir);
    }

    @Subscribe
    public void onMakeDirEvent(OnMakeDirEvent event) {
        try {
            MakeDir.TryMakeDir(event.root, event.dirName);
        } catch(DirExistsException dee) {
            Helper.toast(getActivity(), R.string.folder_already_exists);
        } catch(PermissionDeniedException dee) {
            Helper.toast(getActivity(), R.string.permission_denied);
        } catch (IOException e) {
            Helper.toast(getActivity(), R.string.op_not_allowed);
        }
        dirListBuilder.processListDirEvent(event.root);
    }

    @Subscribe
    public void onDataSetChangedEvent(DataSetChangedEvent event) {
        dirListBuilder.notifyDatasetChanged();
    }

    private void setCurrentRootDir(Bundle savedState) {
        if (savedState != null) {
            currentRootDir = (File) savedState.getSerializable(CURRENT_ROOT_DIR);
        } else {
            setCurrentDir();
        }
    }

    private void setCurrentDir() {
        if (null == getArguments())
            throw new InitializationException("Init failed : arguments have not been set");

        File rootDir = (File) getArguments().getSerializable(ROOT_DIR);
        if (rootDir == null) {
            currentRootDir = Environment.getExternalStorageDirectory();
        } else {
            currentRootDir = rootDir;
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        EventBus.getDefault().post(new OnDirCancelEvent());
        super.onCancel(dialog);
    }

    @Override
    public void onClick(View v) {
        if (v.equals(textView)) {
            onTextViewClicked(false);
        } else if (v.equals(fabCreateDir)) {
            createDirBtnClicked();
        } else if (v.equals(fabRequestSD)) {
            requestSDBtnClicked();
        } else if (v.equals(selectDirBtn)) {
            selectDirBtnClicked();
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v.equals(textView)) {
            onTextViewClicked(true);
            return true;
        }

        return false;
    }

    private void onTextViewClicked(boolean longClick) {
        Timber.d("On TextView Clicked Event");
        EventBus.getDefault().post(new OnTextViewClickedEvent(longClick));
    }

    private void createDirBtnClicked() {
        new CreateDirDialog(requireActivity(), Consts.DEFAULT_LOCAL_DIRECTORY).dialog(currentRootDir);
    }

    private void requestSDBtnClicked() {
        Timber.d("SAF Request Event");
        EventBus.getDefault().post(new OnSAFRequestEvent());
    }

    private void selectDirBtnClicked() {
        EventBus.getDefault().post(new OnDirChosenEvent(currentRootDir));
        dismiss();
    }
}
