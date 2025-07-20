package alsaeeddev.filemanager;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.transition.Slide;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import alsaeeddev.filemanager.adapter.FileAdapter;
import alsaeeddev.filemanager.model.FileViewModel;
import alsaeeddev.filemanager.utils.FileUtils;
import alsaeeddev.filemanager.utils.MenuState;
import alsaeeddev.filemanager.utils.PreferencesHelper;
import alsaeeddev.filemanager.utils.ScrollManager;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> storagePermissionLauncher;
    private FileViewModel vm;
    private FileAdapter adapter;
    private File currentDir;
    private boolean selecting = false;
    private File clipboardFile = null;
    private boolean isMoveOperation = false;
    LinearLayout bottomMenu;
    ConstraintLayout pasteMenu;
    private TextView tvItemCount;
    RecyclerView recyclerView;
    TextView tvCopyPaste;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<File> clipboardFiles = new ArrayList<>();
    private ConstraintLayout rootToolLayout;
    private TextView tvToolTitle;
    private CheckBox toolCbAll;
    private ProgressBar progressBar;

    boolean allDeleted;
    private final ScrollManager scrollManager = new ScrollManager();
    private boolean isBackNavigation = false;
    private View view;
    private TextView tvSize;
    private TextView tvCount;
    private Button btnOk;
    private TextView tvToolCount;
    private BottomSheetDialog bottomSheetDialog;
    private boolean isBackPressed = false;
    private MenuState currentMenuState = MenuState.DEFAULT;
    private List<File> selectedFiles;

    private final Map<String, Integer> scrollPositions = new HashMap<>();
    private final Map<String, Integer> scrollOffsets = new HashMap<>();


    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        toolbarSetup();
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        viewsInit();
        currentDir = Environment.getExternalStorageDirectory();
        vm = new ViewModelProvider(this).get(FileViewModel.class);

        permissionLauncherInit();
        checkAndRequestPermissions();

        bottomSheetItemDetailInit();

        setCheckListener();

        setupRecyclerView();
        // Load saved preference and apply
        boolean showHidden = PreferencesHelper.getShowHiddenFiles(this);
        vm.setShowHiddenFiles(showHidden);

        // Observe file data
        vm.getFiles().observe(this, data -> {
            // adapter.setData(data);
            progressBar.setVisibility(GONE);

            recyclerView.setItemAnimator(null); // Disable animation during scroll restore

            scrollManager.restoreScroll(currentDir, recyclerView, isBackNavigation, () -> {
                adapter.setData(data); //  Update after scroll
                recyclerView.setItemAnimator(new DefaultItemAnimator()); //  Restore animation
            });

        });


        handleClicks();


    }


    private void viewsInit() {
        recyclerView = findViewById(R.id.recyclerView);
        bottomMenu = findViewById(R.id.bottomMenu);
        pasteMenu = findViewById(R.id.clCopyCancel);
        tvItemCount = findViewById(R.id.tvItemSelected);
        tvCopyPaste = findViewById(R.id.tvCopyHere);
        rootToolLayout = findViewById(R.id.rootToolLayout);
        tvToolTitle = findViewById(R.id.tvToolTitle);
        tvToolCount = findViewById(R.id.tvToolCount);
        toolCbAll = findViewById(R.id.toolCbAll);
        progressBar = findViewById(R.id.roundProgressBar);
    }


    private void setupRecyclerView() {
        adapter = new FileAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setInitialPrefetchItemCount(20);
        recyclerView.setLayoutManager(layoutManager);
        //  Set RecyclerView performance options
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(40);

        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        // Add divider
        DividerItemDecoration divider = new DividerItemDecoration(
                recyclerView.getContext(), LinearLayoutManager.VERTICAL);
        recyclerView.addItemDecoration(divider);

        recyclerView.setAdapter(adapter);
    }


    private void permissionLauncherInit() {

        storagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (Environment.isExternalStorageManager()) {
                            loadFiles();
                        } else {
                            Toast.makeText(this, "All files access denied", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                }
        );


    }


    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                storagePermissionLauncher.launch(intent); // Use launcher instead of startActivityForResult
            } else {
                loadFiles(); // Permissions already granted
            }
        } else {
            // Android 6 ~ 10
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
            } else {
                loadFiles(); // Permissions already granted
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadFiles();
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    private void loadFiles() {
        // Load files only after permissions are granted
        progressBar.setVisibility(VISIBLE);
        vm.loadFiles(currentDir);
    }


    // to select and deselect all
    private void setCheckListener() {

        toolCbAll.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                adapter.selectAll();

            } else {
                adapter.clearSelection();

            }
        });

    }


    // handle files/folder clcks
    private void handleClicks() {

        adapter.setOnFileClickListener(file -> {
            Log.d("AlSaeed", "setOnFileClickListener: " + file.getName());

            if (file.isDirectory()) {
                scrollManager.saveScroll(currentDir, recyclerView); //  Save scroll
                isBackNavigation = false;

                currentDir = file;
                vm.loadFiles(currentDir);
            } else {
                openFile(file);
            }

        });


        adapter.setOnMultiSelectionChangeListener(multiFiles -> {
            Log.d("AlSaeed", "setOnMultiSelectionChangeListener: " + multiFiles.size());
            onLongClick(multiFiles);

        });

    }


    // toolbar setup
    private void toolbarSetup() {
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        // Remove default app title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

    }


    // open the file before check if not the .zip
    private void openFile(File f) {

        if (f.getName().toLowerCase().endsWith(".zip")) {
            extractZipToSameDir(f);


        } else if (f.isDirectory()) {
            currentDir = f;
            vm.loadFiles(currentDir);
        } else {
            Uri uri = FileProvider.getUriForFile(this,
                    "alsaeeddev.filemanager.provider", f);
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, FileUtils.getMimeType(f.getPath()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "No app to open " + f.getName(), Toast.LENGTH_SHORT).show();
            }
        }


    }


    private void updateMultiSelectMenu(List<File> selectedFiles) {
        boolean hasSelection = !selectedFiles.isEmpty();

        if (hasSelection) {
            if (selectedFiles.size() != adapter.getItemCount()) {
                // Remove listener temporarily
                toolCbAll.setOnCheckedChangeListener(null);
                // Set checked state without triggering the listener
                toolCbAll.setChecked(false); // or true
                setCheckListener();
            }

            tvToolTitle.setVisibility(GONE);
            rootToolLayout.setVisibility(VISIBLE);
            tvToolCount.setText(selectedFiles.size() + " Selected");
        } else {


            toolCbAll.setChecked(false);
            tvToolCount.setText("Select items");

        }


        toggleBottomMenu(hasSelection);

        if (!isBackPressed) {
            adapter.setMultiSelectMode(true);

        } else {
            isBackPressed = false;
        }

    }


    private void onLongClick(List<File> selectedFiles) {
        // selected file details here
        this.selectedFiles = selectedFiles;

        if (adapter.isInMultiSelect() && Objects.requireNonNull(selectedFiles).isEmpty()) {
            currentMenuState = MenuState.CLEAR;

        } else {

            if (selectedFiles == null || selectedFiles.isEmpty()) {
                currentMenuState = MenuState.DEFAULT;

            } else if (selectedFiles.size() == 1) {
                currentMenuState = MenuState.SINGLE_SELECTION;

            } else {
                currentMenuState = MenuState.MULTI_SELECTION;
            }
        }
        invalidateOptionsMenu(); // recreate the top menu

        // if all files selected by manually then checked the all checkbox
        assert selectedFiles != null;
        if (selectedFiles.size() == adapter.getItemCount()) {
            toolCbAll.setChecked(true);
        }


        selectedFileDetails(selectedFiles);

        updateMultiSelectMenu(selectedFiles);

        Log.d("AlSaeed", "onLongClick: long");


        // DELETE
        findViewById(R.id.btDelete).setOnClickListener(v2 -> {
            deleteFiles(selectedFiles);
        });

        // COPY
        findViewById(R.id.btCopy).setOnClickListener(v2 -> {
            copyFiles(selectedFiles);
        });


        // MOVE
        findViewById(R.id.btMove).setOnClickListener(v2 -> {
            moveFiles(selectedFiles);
        });

        // PASTE
        tvCopyPaste.setOnClickListener(v2 -> {
            copyPaste(clipboardFiles);
        });


        // selected item details
        findViewById(R.id.btDetails).setOnClickListener(view -> {
            selectedItemDetails();

        });


        findViewById(R.id.btShare).setOnClickListener(view -> {
            shareFilesIfAllAreFiles(this, selectedFiles);

        });


    }


    private void deleteFiles(List<File> selectedFiles) {

        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete the selected " + selectedFiles.size() + " file or folder?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Deleting files...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    bottomMenu.setVisibility(View.GONE);

                    executor.execute(() -> {
                        allDeleted = true;
                        for (File file : selectedFiles) {
                            if (!FileUtils.deleteRecursively(file)) {
                                allDeleted = false;
                            }
                        }

                        mainHandler.post(() -> {
                            progressDialog.dismiss();
                            vm.loadFiles(currentDir);
                            adapter.clearSelectedPositions();

                            terminateSelection();
                            Toast.makeText(this,
                                    allDeleted ? "Deleted successfully" : "Some deletions failed",
                                    Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();

    }


    private void copyFiles(List<File> selectedFiles) {
        terminateSelection();
        clipboardFiles = new ArrayList<>(selectedFiles); // clipboardFiles directly use
        isMoveOperation = false;
        manageCopyMenu();
        adapter.clearSelectedPositions();

    }

    private void moveFiles(List<File> selectedFiles) {
        terminateSelection();
        clipboardFiles = new ArrayList<>(selectedFiles);
        isMoveOperation = true;
        manageCopyMenu();
        adapter.clearSelectedPositions();
    }

    private void copyPaste(List<File> clipboardFiles) {
        terminateSelection();
        if (clipboardFiles == null || clipboardFiles.isEmpty()) {
            Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show();
            return;
        }

        tvCopyPaste.setText(isMoveOperation ? "Move Here" : "Copy Here");
        pasteMenu.setVisibility(View.GONE);

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(isMoveOperation ? "Moving files..." : "Copying files...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Track pending UI tasks for file-level conflict resolution
        List<File> normalFiles = new ArrayList<>();
        List<File> folders = new ArrayList<>();

        for (File file : clipboardFiles) {
            if (file.isDirectory()) folders.add(file);
            else normalFiles.add(file);
        }

        // Execute folders (no dialog)
        executor.execute(() -> {
            boolean success = true;

            try {
                for (File folder : folders) {
                    File target = new File(currentDir, folder.getName());
                    if (isMoveOperation) {
                        moveFolder(folder, target);
                    } else {
                        copyFolder(folder, target);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                success = false;
            }

            final boolean folderSuccess = success;

            mainHandler.post(() -> {
                if (!normalFiles.isEmpty()) {
                    // Process files one-by-one with conflict dialogs (must be on UI thread)
                    processNextFileWithDialog(normalFiles.iterator(), folderSuccess, progressDialog);
                } else {
                    // All done
                    progressDialog.dismiss();
                    vm.loadFiles(currentDir);
                    clipboardFiles.clear();
                    isMoveOperation = false;

                    Toast.makeText(this,
                            folderSuccess ? (isMoveOperation ? "Moved successfully" : "Copied successfully")
                                    : "Some operations failed",
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void selectedItemDetails() {
        // selected file/item details get in the onLongClick
        btnOk.setOnClickListener(v -> bottomSheetDialog.dismiss());
        bottomSheetDialog.show();
    }


    private void processNextFileWithDialog(Iterator<File> iterator, boolean prevSuccess, ProgressDialog progressDialog) {
        if (!iterator.hasNext()) {
            progressDialog.dismiss();
            vm.loadFiles(currentDir);
            clipboardFiles.clear();
            isMoveOperation = false;

            Toast.makeText(this, prevSuccess ? "Operation completed" : "Some operations failed", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = iterator.next();
        File target = new File(currentDir, file.getName());

        if (isMoveOperation) {
            moveFileWithConflictDialog(file, target, this,
                    () -> processNextFileWithDialog(iterator, prevSuccess, progressDialog), // onSuccess
                    () -> processNextFileWithDialog(iterator, false, progressDialog)        // onCancel/fail
            );
        } else {
            copyFileWithConflictDialog(file, target, this,
                    () -> processNextFileWithDialog(iterator, prevSuccess, progressDialog), // onSuccess
                    () -> processNextFileWithDialog(iterator, false, progressDialog)        // onCancel/fail
            );
        }
    }


    public void copyFile(File src, File dst) throws IOException {
        if (src == null || !src.exists() || !src.isFile()) {
            throw new FileNotFoundException("Source file not found: " + src);
        }

        if (!src.canRead()) {
            throw new IOException("Cannot read source file: " + src.getAbsolutePath());
        }

        File dstDir = dst.getParentFile();
        if (dstDir != null && !dstDir.exists() && !dstDir.mkdirs()) {
            throw new IOException("Failed to create destination folder: " + dstDir.getAbsolutePath());
        }

        if (dst.exists() && !dst.delete()) {
            throw new IOException("Failed to delete existing destination file: " + dst.getAbsolutePath());
        }

        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            out.flush();
        }
    }


    public void moveFile(File src, File dst) throws IOException {
        if (src == null || !src.exists() || !src.isFile()) {
            throw new FileNotFoundException("Source file is missing or invalid: " + src);
        }

        if (!src.canRead()) {
            throw new IOException("Source file is not readable: " + src.getAbsolutePath());
        }

        if (src.getAbsolutePath().equals(dst.getAbsolutePath())) {
            throw new IOException("Source and destination are the same file: " + src.getAbsolutePath());
        }

        File dstDir = dst.getParentFile();
        if (dstDir != null && !dstDir.exists() && !dstDir.mkdirs()) {
            throw new IOException("Failed to create destination directory: " + dstDir.getAbsolutePath());
        }

        if (dst.exists() && !dst.delete()) {
            throw new IOException("Failed to delete existing destination file: " + dst.getAbsolutePath());
        }

        copyFile(src, dst);

        if (!src.delete()) {
            dst.delete(); // Cleanup copied file
            throw new IOException("Failed to delete source file after copy: " + src.getAbsolutePath());
        }
    }


    private File resolveNameConflict(File destFile) {
        String name = destFile.getName();
        String baseName = name;
        String extension = "";

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex != -1) {
            baseName = name.substring(0, dotIndex);
            extension = name.substring(dotIndex);
        }

        int counter = 1;
        File newFile;
        do {
            newFile = new File(destFile.getParent(), baseName + "(" + counter + ")" + extension);
            counter++;
        } while (newFile.exists());

        return newFile;
    }


    public void moveFileWithConflictDialog(File src, File dst, Context context, Runnable onSuccess, Runnable onCancel) {
        if (dst.exists()) {
            new AlertDialog.Builder(context)
                    .setTitle("File Already Exists")
                    .setMessage("A file named \"" + dst.getName() + "\" already exists. Do you want to rename and move the file?")
                    .setPositiveButton("Rename and Move", (dialog, which) -> {
                        File renamedDst = resolveNameConflict(dst);
                        try {
                            moveFile(src, renamedDst);
                            if (onSuccess != null) onSuccess.run();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(context, "Move failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        if (onCancel != null) onCancel.run();
                        dialog.dismiss();
                    })
                    .setCancelable(true)
                    .show();
        } else {
            try {
                moveFile(src, dst);
                if (onSuccess != null) onSuccess.run();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(context, "Move failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }


    public void copyFileWithConflictDialog(File src, File dst, Context context, Runnable onSuccess, Runnable onCancel) {
        if (dst.exists()) {
            new AlertDialog.Builder(context)
                    .setTitle("File Already Exists")
                    .setMessage("A file named \"" + dst.getName() + "\" already exists. Do you want to rename and copy the file?")
                    .setPositiveButton("Rename and Copy", (dialog, which) -> {
                        File renamedDst = resolveNameConflict(dst);
                        try {
                            copyFile(src, renamedDst);
                            if (onSuccess != null) onSuccess.run();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(context, "Copy failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        if (onCancel != null) onCancel.run();
                        dialog.dismiss();
                    })
                    .setCancelable(true)
                    .show();
        } else {
            try {
                copyFile(src, dst);
                if (onSuccess != null) onSuccess.run();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(context, "Copy failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void copyFolder(File src, File dest) throws IOException {
        if (src == null || !src.exists() || !src.isDirectory()) {
            throw new FileNotFoundException("Source folder is invalid: " + src);
        }

        if (dest == null) {
            throw new IllegalArgumentException("Destination folder cannot be null");
        }

        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("Failed to create destination folder: " + dest.getAbsolutePath());
        }

        File[] files = src.listFiles();
        if (files == null) return;

        for (File srcFile : files) {
            if (srcFile.isHidden()) continue; // skip hidden/system files

            File destFile = new File(dest, srcFile.getName());
            destFile = resolveNameConflict(destFile);

            if (srcFile.isDirectory()) {
                copyFolder(srcFile, destFile);
            } else {
                copyFile(srcFile, destFile);
            }
        }
    }


    private void moveFolder(File src, File dest) throws IOException {
        if (src == null || !src.exists() || !src.isDirectory()) {
            throw new FileNotFoundException("Source folder is invalid: " + src);
        }

        if (dest == null) {
            throw new IllegalArgumentException("Destination folder cannot be null");
        }

        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("Failed to create destination folder: " + dest.getAbsolutePath());
        }

        File[] files = src.listFiles();
        if (files == null) return;

        for (File srcFile : files) {
            if (srcFile.isHidden()) continue;

            File destFile = new File(dest, srcFile.getName());
            destFile = resolveNameConflict(destFile);

            if (srcFile.isDirectory()) {
                moveFolder(srcFile, destFile);
            } else {
                moveFile(srcFile, destFile);
            }
        }

        if (!src.delete()) {
            throw new IOException("Failed to delete source folder: " + src.getAbsolutePath());
        }
    }


    private void manageCopyMenu() {

        pasteMenu.setVisibility(VISIBLE);
        bottomMenu.setVisibility(GONE);
        tvCopyPaste.setText(isMoveOperation ? "Move Here" : "Copy Here");
        tvItemCount.setText(adapter.getSelectedFiles().size() + " Items");

        findViewById(R.id.tvCancel).setOnClickListener(v -> {
            pasteMenu.setVisibility(GONE);
        });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.clear();

        switch (currentMenuState) {
            case SINGLE_SELECTION:
                getMenuInflater().inflate(R.menu.menu_single_selection, menu);
                break;

            case MULTI_SELECTION:
                getMenuInflater().inflate(R.menu.menu_multi_selection, menu);
                break;

            case CLEAR:
                menu.clear();
                break;

            case DEFAULT:
            default:
                getMenuInflater().inflate(R.menu.default_menu, menu);
                MenuItem toggleItem = menu.findItem(R.id.action_toggle_hidden);
                boolean isShowing = PreferencesHelper.getShowHiddenFiles(this);
                toggleItem.setTitle(isShowing ? "Hide Files" : "Show Hidden Files");

                break;
        }

        return true;


    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.new_folder) {
            checkVersionToPermission();
            return true;
        }

        if (id == R.id.refresh) {
            vm.loadFiles(currentDir);
            return true;
        }

        if (id == R.id.action_toggle_hidden) {
            boolean current = PreferencesHelper.getShowHiddenFiles(this);
            boolean newState = !current;
            PreferencesHelper.setShowHiddenFiles(this, newState);
            vm.setShowHiddenFiles(newState);
            vm.loadFiles(currentDir);
            item.setTitle(newState ? "Hide Files" : "Show Hidden Files");
            return true;
        }

        if (id == R.id.action_rename) {
            if (!selectedFiles.isEmpty()) {
                renameFile(selectedFiles.get(0));
                // resetMenu();
            }
            return true;
        }

        if (id == R.id.action_compress) {
            if (!selectedFiles.isEmpty()) {
                compressFiles(selectedFiles);
                //  resetMenu();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void resetMenu() {
        hideMultiSelection();
        currentMenuState = MenuState.DEFAULT;
        invalidateOptionsMenu(); // recreate the top menu

    }


    // rename the file
    private void renameFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Create a LinearLayout to hold title and EditText
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10); // add some padding

        TextView title = new TextView(this);
        title.setText("Rename File");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 10); // spacing below title
        layout.addView(title);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(file.getName());
        input.setSelectAllOnFocus(true);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();

            if (!newName.isEmpty() && !newName.equals(file.getName())) {
                File newFile = new File(file.getParent(), newName);
                if (file.renameTo(newFile)) {
                    resetMenu();
                    loadFiles(); // Refresh RecyclerView
                    Toast.makeText(this, "Renamed to " + newName, Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }


    // compress the files
    private void compressFiles(List<File> files) {
        if (files == null || files.isEmpty()) return;

        ProgressBar progressBar = findViewById(R.id.progressBar);
        TextView tvStatus = findViewById(R.id.tvStatus);
        LinearLayout statusBar = findViewById(R.id.statusBar);

        int totalFiles = files.size();
        runOnUiThread(() -> {
            statusBar.setVisibility(View.VISIBLE);
            progressBar.setMax(totalFiles);
            progressBar.setProgress(0);
            tvStatus.setText("0 / " + totalFiles + " files compressed");
        });

        executor.execute(() -> {
            try {
                File parentDir = files.get(0).getParentFile();
                File zipFile = new File(parentDir, "compressed_files.zip");
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));

                int currentFile = 0;

                for (File file : files) {
                    if (file.isFile()) {
                        FileInputStream fis = new FileInputStream(file);
                        ZipEntry zipEntry = new ZipEntry(file.getName());
                        zos.putNextEntry(zipEntry);

                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }

                        zos.closeEntry();
                        fis.close();

                        currentFile++;
                        int finalCurrent = currentFile;

                        runOnUiThread(() -> {
                            progressBar.setProgress(finalCurrent);
                            tvStatus.setText(finalCurrent + " / " + totalFiles + " files compressed");
                        });
                    }
                }

                zos.close();

                runOnUiThread(() -> {
                    statusBar.setVisibility(View.GONE);
                    loadFiles();
                    resetMenu();
                    Toast.makeText(this, "Compressed successfully!", Toast.LENGTH_LONG).show();
                });

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    statusBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Compression failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }


    // extract the file
    private void extractZipToSameDir(File zipFile) {
        if (!zipFile.getName().endsWith(".zip")) {
            Toast.makeText(this, "Selected file is not a ZIP archive", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> fileNames = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                fileNames.add(entry.getName());
                zis.closeEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to read ZIP: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        // Show dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Extract ZIP");
        builder.setItems(fileNames.toArray(new String[0]), null);
        builder.setPositiveButton("Extract All", (dialog, which) -> {
            File outputDir = zipFile.getParentFile(); // extract to same directory

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    File outFile = new File(outputDir, entry.getName());

                    if (entry.isDirectory()) {
                        outFile.mkdirs();
                    } else {
                        // Ensure parent directories exist
                        File parent = outFile.getParentFile();
                        if (!parent.exists()) parent.mkdirs();

                        FileOutputStream fos = new FileOutputStream(outFile);
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                    }
                    zis.closeEntry();
                }

                loadFiles();
                Toast.makeText(this, "Extracted to: " + outputDir.getAbsolutePath(), Toast.LENGTH_SHORT).show();

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Extraction failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }


    private void terminateSelection() {
        rootToolLayout.setVisibility(GONE);
        tvToolTitle.setVisibility(VISIBLE);
        toolCbAll.setChecked(false);
    }


    // to see the info
    private void selectedFileDetails(List<File> selectedFiles) {

        tvSize.setText("Processing...");
        tvCount.setText("Processing...");

        // Count and calculate size
        new Thread(() -> {
            long totalSize = 0;
            int fileCount = 0;
            int folderCount = 0;

            for (File file : selectedFiles) {
                CountResult result = countFilesAndFolders(file);
                totalSize += result.totalSize;
                fileCount += result.fileCount;
                folderCount += result.folderCount;
            }

            final long finalSize = totalSize;
            final int finalFiles = fileCount;
            final int finalFolders = folderCount;

            runOnUiThread(() -> {
                tvSize.setText("Total size (all folders and files)\n" + formatSize(finalSize));
                tvCount.setText(finalFiles + " files, " + finalFolders + " folders");
            });
        }).start();

    /*    btnOk.setOnClickListener(v -> bottomSheetDialog.dismiss());
        bottomSheetDialog.show();*/

    }


    private void bottomSheetItemDetailInit() {
        view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_file_details, null);
        tvSize = view.findViewById(R.id.tvSize);
        tvCount = view.findViewById(R.id.tvCount);
        btnOk = view.findViewById(R.id.btnOk);
        bottomSheetDialog = new BottomSheetDialog(this);
        bottomSheetDialog.setContentView(view);

    }


    private long getFolderSize(File folder) {
        long size = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += getFolderSize(file);
                }
            }
        }
        return size;
    }


    private String formatSize(long sizeInBytes) {
        float kb = sizeInBytes / 1024f;
        float mb = kb / 1024f;
        float gb = mb / 1024f;

        if (gb >= 1) return String.format(Locale.getDefault(), "%.2f GB", gb);
        else if (mb >= 1) return String.format(Locale.getDefault(), "%.2f MB", mb);
        else if (kb >= 1) return String.format(Locale.getDefault(), "%.2f KB", kb);
        else return sizeInBytes + " B";
    }


    public void toggleBottomMenu(boolean show) {
        ViewGroup parent = (ViewGroup) bottomMenu.getParent();
        Transition slide = new Slide(Gravity.BOTTOM);
        slide.setDuration(500); // smooth speed
        slide.setInterpolator(new AccelerateDecelerateInterpolator()); // optional for smoothness
        slide.addTarget(bottomMenu);

        TransitionManager.beginDelayedTransition(parent, slide);
        bottomMenu.setVisibility(show ? View.VISIBLE : View.GONE);
    }


    public void shareFilesIfAllAreFiles(Context context, List<File> fileList) {
        // Check if all are files
        for (File file : fileList) {
            if (!file.isFile()) {
                Toast.makeText(context, "Folders cannot be shared.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // All are files, proceed to share
        ArrayList<Uri> uriList = new ArrayList<>();
        for (File file : fileList) {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file
            );
            uriList.add(uri);
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.setType("*/*");
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(shareIntent, "Share files via"));
    }


    private void hideMultiSelection() {
        isBackPressed = true;
        adapter.setMultiSelectMode(false);
        bottomMenu.setVisibility(View.GONE);
        terminateSelection();

    }


    private void checkVersionToPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+
            if (!Environment.isExternalStorageManager()) {
                // Request MANAGE_EXTERNAL_STORAGE permission
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
        } else {
            // For Android 6 to 10
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
                return;
            }
        }

        // Permission is granted, show folder dialog
        showNewFolderDialog();
    }


    private void showNewFolderDialog() {
        // Create layout to hold title and EditText
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10); // Adjust padding as needed

        // Title
        TextView title = new TextView(this);
        title.setText("New Folder");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 10); // bottom spacing
        layout.addView(title);

        // EditText
        final EditText input = new EditText(this);
        input.setHint("Enter folder name");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(input);

        new AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton("Create", (dialog, which) -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        File newFolder = new File(currentDir, folderName);
                        if (!newFolder.exists()) {
                            if (newFolder.mkdir()) {
                                Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show();
                                vm.loadFiles(currentDir); // refresh list
                            } else {
                                Toast.makeText(this, "Failed to create folder", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Folder already exists", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Enter folder name", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }



     /*  @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {

        if (adapter.isInMultiSelect()) {
            isBackPressed = true;
            // adapter.clearSelectedPositions();
            adapter.setMultiSelectMode(false);
            bottomMenu.setVisibility(GONE);
            terminateSelection();


        } else {

            File parent = currentDir.getParentFile();

            // Define your logical root directory (e.g., internal storage root)
            File appRoot = Environment.getExternalStorageDirectory();  // /storage/emulated/0

            if (parent != null && currentDir.compareTo(appRoot) != 0) {
                currentDir = parent;
                vm.loadFiles(currentDir);
            } else {
                // You're at the root - exit or confirm
                super.onBackPressed();
            }
        }

    }*/

    // on back pressed handle here
    private final OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {

            // 1. If in multi-select mode, cancel it
            if (adapter.isInMultiSelect()) {
                hideMultiSelection();
                currentMenuState = MenuState.DEFAULT;
                invalidateOptionsMenu();
                return;
            }


            File parent = currentDir.getParentFile();
            File appRoot = Environment.getExternalStorageDirectory();


            if (parent != null && !currentDir.getAbsolutePath().equals(appRoot.getAbsolutePath())) {
                // Mark that we're navigating backward
                isBackNavigation = true;
                currentDir = parent;
                vm.loadFiles(currentDir);
            } else {

                scrollManager.clearAll(); //  Clear memory on root
                finish(); // or call finish()
            }
        }

    };


    // inner class used here
    private static class CountResult {
        long totalSize;
        int fileCount;
        int folderCount;

        CountResult(long size, int files, int folders) {
            totalSize = size;
            fileCount = files;
            folderCount = folders;
        }
    }

    private CountResult countFilesAndFolders(File file) {
        long size = 0;
        int files = 0;
        int folders = 0;

        if (file.isDirectory()) {
            folders++;
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    CountResult result = countFilesAndFolders(child);
                    size += result.totalSize;
                    files += result.fileCount;
                    folders += result.folderCount;
                }
            }
        } else {
            size += file.length();
            files++;
        }

        return new CountResult(size, files, folders);
    }


}

