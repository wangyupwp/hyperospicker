package cn.wangyupwp.hyperospicker;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

public final class LogProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!PickerLog.RECORD.equals(method) || arg == null || arg.length() > 32) {
            throw new IllegalArgumentException("Unsupported log event");
        }
        String source = getCallingPackage();
        Context context = requireContext();
        if (!isTrustedPicker(context.getPackageManager(), source, Binder.getCallingUid())) {
            throw new SecurityException("Only supported system pickers can record events");
        }
        PickerLog.Event event = PickerLog.Event.valueOf(arg);
        String caller = extras == null ? null : extras.getString(PickerLog.CALLER_PACKAGE);
        String label = null;
        if (PickerLog.isPackageName(caller)) {
            String rawLabel = extras.getString(PickerLog.CALLER_LABEL);
            if (rawLabel != null && rawLabel.length() <= 160) {
                label = PickerLog.cleanLabel(rawLabel);
            }
        } else {
            caller = null;
        }
        LogStore.get(context).record(source, event, caller, label);
        return Bundle.EMPTY;
    }

    private static boolean isTrustedPicker(PackageManager packages, String source, int uid) {
        if (!PickerLog.PHOTOS.equals(source) && !PickerLog.XIAOMI_FILES.equals(source)) {
            return false;
        }
        try {
            ApplicationInfo app = packages.getApplicationInfo(source, PackageManager.ApplicationInfoFlags.of(0));
            int systemFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            return app.uid == uid && (app.flags & systemFlags) != 0;
        } catch (PackageManager.NameNotFoundException unavailable) {
            return false;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        throw new UnsupportedOperationException("Logs are private to the module");
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Use the event protocol");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("Events cannot be updated");
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("Logs are private to the module");
    }
}
