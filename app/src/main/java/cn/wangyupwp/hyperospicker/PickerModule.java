package cn.wangyupwp.hyperospicker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class PickerModule extends XposedModule {
    private static final String TAG = "HyperOSPicker";
    private static final String PHOTOS = "com.android.photopicker";
    private static final String XIAOMI_FILES = "com.android.fileexplorer";
    private static final String[] DOCUMENTS_PACKAGES = {
            "com.android.documentsui", "com.google.android.documentsui"
    };

    private String processName;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String host = param.getPackageName();
        if (!host.equals(processName) && (processName == null || !processName.startsWith(host + ":"))) {
            return;
        }

        switch (host) {
            case PHOTOS -> register(param, "com.android.photopicker.hyper.HyperMainActivity", true);
            case XIAOMI_FILES -> register(param, "com.android.fileexplorer.picker.PickMainNavigatorActivity", false);
            default -> { }
        }
    }

    private void register(PackageReadyParam param, String className, boolean photo) {
        try {
            Class<? extends Activity> picker = Class.forName(className, false, param.getClassLoader())
                    .asSubclass(Activity.class);
            Method onCreate = findOnCreate(picker);
            // An inherited onCreate already is the lifecycle implementation. Otherwise run
            // the superclass method so ComponentActivity also restores its AndroidX state.
            Method lifecycleOnCreate = onCreate.getDeclaringClass() == picker
                    ? findOnCreate(picker.getSuperclass()) : onCreate;
            var lifecycleInvoker = getInvoker(lifecycleOnCreate).setType(Invoker.Type.ORIGIN);
            var finishInvoker = getInvoker(Activity.class.getDeclaredMethod("finish"))
                    .setType(Invoker.Type.ORIGIN);
            hook(onCreate).intercept(chain -> {
                if (chain.getArg(0) != null || !(chain.getThisObject() instanceof Activity activity)
                        || activity.getClass() != picker) {
                    return chain.proceed();
                }
                List<Intent> candidates = resolveRequests(activity, photo);
                if (candidates.isEmpty()) {
                    return chain.proceed();
                }

                // Complete the framework lifecycle before transferring the original result target.
                try {
                    lifecycleInvoker.invokeSpecial(activity, (Bundle) null);
                } catch (Exception | LinkageError failed) {
                    log(Log.WARN, TAG, "Could not initialize the forwarding activity", failed);
                    return chain.proceed();
                }

                // After superclass onCreate, never reenter the vendor picker.
                if (activity.isFinishing()) {
                    return null;
                }
                try {
                    if (!launchDocuments(activity, candidates)) {
                        log(Log.WARN, TAG, "DocumentsUI rejected the forwarded request");
                    }
                } finally {
                    try {
                        finishInvoker.invokeSpecial(activity);
                    } catch (Exception | LinkageError failed) {
                        log(Log.WARN, TAG, "Could not close the forwarding activity", failed);
                        try {
                            activity.finish();
                        } catch (RuntimeException closeFailed) {
                            log(Log.WARN, TAG, "Picker finish failed", closeFailed);
                        }
                    }
                }
                return null;
            });
        } catch (ReflectiveOperationException | LinkageError | SecurityException | ClassCastException incompatible) {
            log(Log.WARN, TAG, "Picker unavailable: " + param.getPackageName());
        }
    }

    private static Method findOnCreate(Class<?> start) throws NoSuchMethodException {
        for (Class<?> type = start; type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredMethod("onCreate", Bundle.class);
            } catch (NoSuchMethodException inherited) {
                // The picker may inherit its implementation on some HyperOS builds.
            }
        }
        throw new NoSuchMethodException(start.getName() + ".onCreate(Bundle)");
    }

    private List<Intent> resolveRequests(Activity activity, boolean photo) {
        Intent original = activity.getIntent();
        if (activity.isFinishing() || activity.getCallingActivity() == null
                || original == null || original.getSelector() != null) {
            return List.of();
        }

        try {
            Intent forwarded = photo ? photoRequest(original) : fileRequest(original);
            if (forwarded == null) {
                return List.of();
            }
            forwarded.setComponent(null);

            var candidates = new ArrayList<Intent>(DOCUMENTS_PACKAGES.length);
            for (String packageName : DOCUMENTS_PACKAGES) {
                forwarded.setPackage(packageName);
                ResolveInfo resolved = activity.getPackageManager().resolveActivity(
                        forwarded, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY));
                ActivityInfo target = resolved == null ? null : resolved.activityInfo;
                if (!isSystemPicker(target, packageName)) {
                    continue;
                }
                candidates.add(new Intent(forwarded)
                        .setComponent(new ComponentName(target.packageName, target.name)));
            }
            return candidates;
        } catch (SecurityException | IllegalArgumentException | BadParcelableException unavailable) {
            log(Log.WARN, TAG, "Could not resolve DocumentsUI");
            return List.of();
        }
    }

    private static boolean launchDocuments(Activity activity, List<Intent> candidates) {
        for (Intent request : candidates) {
            try {
                activity.startActivity(request);
                return true;
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                // The resolved component can disappear before launch. Try the other system package.
            } catch (RuntimeException rejected) {
                // The framework may have accepted the launch before reporting an error.
                return false;
            }
        }
        return false;
    }

    private static boolean isSystemPicker(ActivityInfo target, String packageName) {
        int systemFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        return target != null && packageName.equals(target.packageName) && target.enabled
                && target.exported && (target.applicationInfo.flags & systemFlags) != 0;
    }

    private static Intent photoRequest(Intent original) {
        if ((original.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
            return null;
        }
        Intent forwarded = new Intent(original);
        forwarded.setAction(Intent.ACTION_OPEN_DOCUMENT);
        forwarded.addCategory(Intent.CATEGORY_OPENABLE);
        String mimeType = original.getType();
        if (mimeType == null || (!mimeType.startsWith("image/") && !mimeType.startsWith("video/"))) {
            forwarded.setDataAndType(original.getData(), "*/*");
            forwarded.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        }
        if (original.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 1) > 1) {
            forwarded.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return forwarded.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
    }

    private static Intent fileRequest(Intent original) {
        String action = original.getAction();
        if (!Intent.ACTION_GET_CONTENT.equals(action) && !Intent.ACTION_OPEN_DOCUMENT.equals(action)
                && !Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action)) {
            return null;
        }
        return new Intent(original).setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
    }
}
