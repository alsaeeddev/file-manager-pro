package alsaeeddev.filemanager.utils;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.HashMap;
import java.util.Map;


public class ScrollManager {

    private final Map<String, Integer> scrollPositions = new HashMap<>();
    private final Map<String, Integer> scrollOffsets = new HashMap<>();

    // Save current scroll position
    public void saveScroll(File currentDir, RecyclerView recyclerView) {
        if (currentDir == null || recyclerView == null) return;

        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            int pos = layoutManager.findFirstVisibleItemPosition();
            View view = layoutManager.findViewByPosition(pos);
            int offset = (view != null) ? view.getTop() : 0;

            String path = currentDir.getAbsolutePath();
            scrollPositions.put(path, pos);
            scrollOffsets.put(path, offset);
        }
    }

    // Restore scroll (called after data is set in adapter)
    public void restoreScroll(File currentDir, RecyclerView recyclerView, boolean isBack, Runnable onRestored) {
        if (currentDir == null || recyclerView == null) {
            if (onRestored != null) onRestored.run();
            return;
        }

        String path = currentDir.getAbsolutePath();

        recyclerView.post(() -> {
            LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (lm != null) {
                if (isBack && scrollPositions.containsKey(path)) {
                    Integer posBoxed = scrollPositions.get(path);
                    Integer offsetBoxed = scrollOffsets.get(path);

                    int pos = posBoxed != null ? posBoxed : 0;
                    int offset = offsetBoxed != null ? offsetBoxed : 0;

                    lm.scrollToPositionWithOffset(pos, offset);
                } else {
                    lm.scrollToPositionWithOffset(0, 0);
                }
            }

            if (onRestored != null) onRestored.run(); //  Notify when done
        });


    }


    // Clear all saved scroll positions (e.g. on root exit)
    public void clearAll() {
        scrollPositions.clear();
        scrollOffsets.clear();
    }
}
