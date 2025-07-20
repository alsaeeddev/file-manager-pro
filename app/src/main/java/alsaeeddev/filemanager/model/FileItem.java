package alsaeeddev.filemanager.model;

import java.io.File;

public class FileItem {
    private final File file;
    private final boolean isDirectory;
    public FileItem(File file) {
        this.file = file;
        this.isDirectory = file.isDirectory();
    }
    public File getFile() { return file; }
    public boolean isDirectory() { return isDirectory; }
    public String getName() { return file.getName(); }
    public long getSize() { return file.length(); }
}
