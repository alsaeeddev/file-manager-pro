package alsaeeddev.filemanager.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesHelper {
    private static final String PREF_NAME = "file_manager_prefs";
    private static final String KEY_SHOW_HIDDEN = "show_hidden_files";

    public static boolean getShowHiddenFiles(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SHOW_HIDDEN, false); // default: false
    }

    public static void setShowHiddenFiles(Context context, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply();
    }
}
