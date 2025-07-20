package alsaeeddev.filemanager.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FileViewModel extends ViewModel {
    private final MutableLiveData<List<FileItem>> files = new MutableLiveData<>();
    private boolean showHiddenFiles = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LiveData<List<FileItem>> getFiles() {
        return files;
    }

    public void setShowHiddenFiles(boolean show) {
        showHiddenFiles = show;
    }

    public boolean isShowingHiddenFiles() {
        return showHiddenFiles;
    }

    public void loadFiles(File dir) {
        executor.execute(() -> {
            File[] list = dir.listFiles(file -> {
                return showHiddenFiles || !file.getName().startsWith(".");
            });

            List<FileItem> items = new ArrayList<>();
            if (list != null) {
                for (File f : list) items.add(new FileItem(f));
                Collections.sort(items, Comparator.comparing(fi ->
                        fi.isDirectory() ? "0_" + fi.getName().toLowerCase()
                                : "1_" + fi.getName().toLowerCase()
                ));
            }

            // Safely update LiveData on the main thread
            files.postValue(items);
        });
    }


    private final Map<String, Integer> scrollPositionMap = new HashMap<>();

    public void saveScrollPosition(String path, int position) {
        scrollPositionMap.put(path, position);
    }

    public int getScrollPosition(String path) {
        Integer pos = scrollPositionMap.get(path);
        return pos != null ? pos : 0;
    }
}

