package alsaeeddev.filemanager.adapter;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import alsaeeddev.filemanager.R;
import alsaeeddev.filemanager.model.FileItem;
import alsaeeddev.filemanager.utils.FileUtils;

import com.bumptech.glide.Glide;


public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    public interface OnFileClickListener {
        void onClick(File file);
    }

    public interface OnMultiSelectionChangeListener {
        void onSelectionChanged(List<File> file);
    }


    private final Context ctx;
    private final List<FileItem> list;
    private final List<Integer> selectedPositions = new ArrayList<>();
    private boolean isMultiSelectMode = false;

    private OnFileClickListener clickListener;
    private OnMultiSelectionChangeListener selectionChangeListener;


    public FileAdapter(Context context) {
        this.ctx = context;
        this.list = new ArrayList<>();

        setHasStableIds(true);
    }


    public void setOnFileClickListener(OnFileClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnMultiSelectionChangeListener(OnMultiSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }


    public void clearSelectedPositions() {
        selectedPositions.clear();
        isMultiSelectMode = false;
        notifyDataSetChanged();
    }

    public void setData(List<FileItem> files) {
        list.clear();
        list.addAll(files);
        notifyDataSetChanged();
    }


    @Override
    public long getItemId(int position) {
        return list.get(position).hashCode(); // Or use a unique file ID
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(ctx).inflate(
                R.layout.item_file_list,
                parent, false
        );
        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
        FileItem fi = list.get(pos);
        File file = fi.getFile();

        holder.name.setText(file.getName());
        holder.size.setText(fi.isDirectory() ? "Folder" : FileUtils.formatSize(file.length()));

        // Set icon (same as before)
        if (fi.isDirectory()) {
            holder.icon.setImageResource(R.drawable.folder_ic);
        } else if (isImageFile(file)) {
            Glide.with(ctx).load(file)
                    .centerCrop()
                    .placeholder(R.drawable.image_ic)
                    .into(holder.icon);
        } else {
            String ext = getFileExtension(file);
            switch (ext) {
                case "pdf":
                    holder.icon.setImageResource(R.drawable.pdf_ic);
                    break;
                case "mp3":
                    holder.icon.setImageResource(R.drawable.mp3_ic);
                    break;
                case "wav":
                    holder.icon.setImageResource(R.drawable.wav_ic);
                    break;
                case "mp4":
                case "mkv":
                case "avi":
                    holder.icon.setImageResource(R.drawable.video_ic);
                    break;
                case "doc":
                case "docx":
                    holder.icon.setImageResource(R.drawable.word_ic);
                    break;
                case "xls":
                case "xlsx":
                    holder.icon.setImageResource(R.drawable.excel_ic);
                    break;
                case "apk":
                    holder.icon.setImageResource(R.drawable.apk_ic);
                    break;
                default:
                    holder.icon.setImageResource(R.drawable.file_ic);
                    break;

                // you can add for more files
            }
        }


        if (isMultiSelectMode) {
            if (holder.checkBox.getVisibility() != View.VISIBLE) {
                holder.checkBox.setVisibility(View.VISIBLE);
                holder.checkBox.setAlpha(0f);
                holder.checkBox.setTranslationX(-60f);
                holder.checkBox.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(300)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }

            holder.contentContainer.animate()
                    .translationX(0f)
                    .setDuration(100)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

        } else {
            if (holder.checkBox.getVisibility() == View.VISIBLE) {
                holder.checkBox.setVisibility(View.GONE);
                holder.checkBox.animate()
                        .alpha(0f)
                        .translationX(-60f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateInterpolator())
                        /* .withEndAction(() -> {
                             holder.checkBox.setVisibility(View.GONE); //
                         })*/
                        .start();
            }

            holder.contentContainer.animate()
                    .translationX(0f)
                    .setDuration(100)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }


        // Checkbox binding
        holder.checkBox.setOnCheckedChangeListener(null); // Prevents recycling issues
        holder.checkBox.setChecked(selectedPositions.contains(pos));

        // Sync checkbox click with itemView selection
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleSelection(pos);
        });


        holder.itemView.setOnClickListener(v -> {
            if (isMultiSelectMode) {
                toggleSelection(pos);
            } else {
                if (clickListener != null) {
                    clickListener.onClick(file);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isMultiSelectMode) {

                isMultiSelectMode = true;
                selectedPositions.clear();
                selectedPositions.add(pos);
                notifyDataSetChanged();

                if (selectionChangeListener != null) {
                    selectionChangeListener.onSelectionChanged(getSelectedFiles());
                }
            }
            return true;
        });


    }


    @Override
    public int getItemCount() {
        return list.size();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name, size;
        CheckBox checkBox;
        LinearLayout contentContainer;
        LinearLayout itemRoot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.ivFileIcon);
            name = itemView.findViewById(R.id.tvFileName);
            size = itemView.findViewById(R.id.tvFileSize);
            checkBox = itemView.findViewById(R.id.cbSelect);
            contentContainer = itemView.findViewById(R.id.contentContainer);
            itemRoot = itemView.findViewById(R.id.itemRoot);

        }
    }


    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp");
    }


    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf(".");
        if (lastDot == -1) return "";
        return name.substring(lastDot + 1).toLowerCase();
    }


    public void setMultiSelectMode(boolean enabled) {
        isMultiSelectMode = enabled;
        if (!enabled) selectedPositions.clear();
        notifyDataSetChanged();
    }

    public boolean isInMultiSelect() {
        return isMultiSelectMode;
    }

    public List<File> getSelectedFiles() {
        List<File> selected = new ArrayList<>();
        for (int i : selectedPositions) {
            selected.add(list.get(i).getFile());
        }
        return selected;
    }


    private void toggleSelection(int pos) {
        if (selectedPositions.contains(pos)) {
            selectedPositions.remove((Integer) pos);
        } else {
            selectedPositions.add(pos);
        }
        notifyItemChanged(pos);

        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(getSelectedFiles());
        }
    }


    public void selectAll() {
        isMultiSelectMode = true;
        selectedPositions.clear();

        for (int i = 0; i < list.size(); i++) {
            selectedPositions.add(i);
        }

        notifyDataSetChanged();

        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(getSelectedFiles());
        }
    }


    public void clearSelection() {
        // isMultiSelectMode = false;  // Exit multi-select mode
        selectedPositions.clear();  // Remove all selected positions
        notifyDataSetChanged();     // Refresh UI

        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(getSelectedFiles());
        }
    }


}
