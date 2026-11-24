package cn.wangyupwp.hyperospicker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

final class PickerLog {
    static final String PHOTOS = "com.android.photopicker";
    static final String XIAOMI_FILES = "com.android.fileexplorer";
    static final String RECORD = "record";
    static final String CALLER_PACKAGE = "caller_package";
    static final String CALLER_LABEL = "caller_label";
    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*");
    private static final Pattern LABEL_BREAKS = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]+");
    private static final Uri ENDPOINT = Uri.parse("content://cn.wangyupwp.hyperospicker.events");
    private static final AtomicBoolean WRITE_FAILED = new AtomicBoolean();
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(
            0, 1, 15, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), task -> {
                Thread thread = new Thread(task, "HyperOSPickerLog");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    enum Event {
        FORWARDED,
        SKIPPED_REQUEST,
        NO_DOCUMENTS_UI,
        RESOLVE_FAILED,
        INITIALIZE_FAILED,
        SOURCE_FINISHING,
        LAUNCH_FAILED,
        CLOSE_FAILED
    }

    private PickerLog() { }

    static String callerPackage(Activity activity, ComponentName resultReceiver) {
        if (resultReceiver != null) {
            return resultReceiver.getPackageName();
        }
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                var caller = activity.getInitialCaller();
                return caller == null ? null : caller.getPackage();
            } catch (RuntimeException | LinkageError unavailable) {
                // Android may restrict caller identity for this launch.
            }
        }
        return null;
    }

    static void record(Context source, String callerPackage, Event event) {
        try {
            Context application = source.getApplicationContext();
            if (application != null) {
                WRITER.execute(() -> {
                    try {
                        Bundle callerInfo = null;
                        if (isPackageName(callerPackage)) {
                            callerInfo = new Bundle(2);
                            callerInfo.putString(CALLER_PACKAGE, callerPackage);
                            callerInfo.putString(CALLER_LABEL, applicationLabel(application, callerPackage));
                        }
                        application.getContentResolver().call(ENDPOINT, RECORD, event.name(), callerInfo);
                    } catch (RuntimeException | LinkageError unavailable) {
                        warnOnce();
                    }
                });
            }
        } catch (RuntimeException | LinkageError unavailable) {
            warnOnce();
        }
    }

    static boolean isPackageName(String value) {
        return value != null && value.length() <= 255 && PACKAGE_NAME.matcher(value).matches();
    }

    static String cleanLabel(String value) {
        if (value == null) {
            return null;
        }
        String label = LABEL_BREAKS.matcher(value).replaceAll(" ").strip();
        int length = label.codePointCount(0, label.length());
        return length == 0 ? null : label.substring(0, label.offsetByCodePoints(0, Math.min(length, 80)));
    }

    private static String applicationLabel(Context context, String packageName) {
        try {
            PackageManager packages = context.getPackageManager();
            var app = packages.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0));
            return cleanLabel(packages.getApplicationLabel(app).toString());
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return null;
        }
    }

    private static void warnOnce() {
        if (WRITE_FAILED.compareAndSet(false, true)) {
            Log.w("HyperOSPicker", "Local event log unavailable");
        }
    }
}
