package cn.wangyupwp.hyperospicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LogActivity extends Activity {
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private ModuleUi ui;
    private LogAdapter adapter;
    private TextView emptyMessage;
    private Button refreshButton;
    private Button clearButton;
    private boolean loading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new ModuleUi(this);
        var root = ui.screen();
        var header = ui.column();
        header.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(10));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        header.addView(ui.action(R.string.back, false, view -> finish()),
                new LinearLayout.LayoutParams(-2, -2));

        TextView title = ui.text(32, R.color.module_text, true);
        title.setText(R.string.logs_title);
        title.setAccessibilityHeading(true);
        ui.append(header, title, 14);
        TextView summary = ui.text(14, R.color.module_secondary, false);
        summary.setText(getString(R.string.logs_retention, LogStore.LIMIT));
        ui.append(header, summary, 6);

        var toolbar = new LinearLayout(this);
        refreshButton = ui.action(R.string.logs_refresh, true, view -> load(false));
        clearButton = ui.action(R.string.logs_clear, false, view -> confirmClear());
        toolbar.addView(refreshButton, new LinearLayout.LayoutParams(0, -2, 1));
        var clearParams = new LinearLayout.LayoutParams(0, -2, 1);
        clearParams.setMarginStart(ui.dp(8));
        toolbar.addView(clearButton, clearParams);
        ui.append(header, toolbar, 10);

        var listArea = new FrameLayout(this);
        root.addView(listArea, new LinearLayout.LayoutParams(-1, 0, 1));
        var list = new ListView(this);
        list.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(20));
        list.setClipToPadding(false);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(ui.dp(8));
        adapter = new LogAdapter();
        list.setAdapter(adapter);
        listArea.addView(list, new FrameLayout.LayoutParams(-1, -1));

        emptyMessage = ui.text(16, R.color.module_secondary, false);
        emptyMessage.setGravity(Gravity.CENTER);
        emptyMessage.setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16));
        listArea.addView(emptyMessage, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(emptyMessage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(false);
    }

    @Override
    protected void onDestroy() {
        disk.shutdown();
        super.onDestroy();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logs_clear)
                .setMessage(R.string.logs_clear_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.logs_clear, (dialog, button) -> load(true))
                .show();
    }

    private void load(boolean clear) {
        if (loading) {
            return;
        }
        loading = true;
        refreshButton.setEnabled(false);
        clearButton.setEnabled(false);
        if (adapter.getCount() == 0) {
            emptyMessage.setText(R.string.logs_loading);
        }
        disk.execute(() -> {
            List<LogStore.Entry> data;
            try {
                LogStore store = LogStore.get(getApplicationContext());
                if (clear) {
                    store.clear();
                }
                data = store.latest();
            } catch (RuntimeException unavailable) {
                data = null;
            }
            List<LogStore.Entry> result = data;
            runOnUiThread(() -> show(result));
        });
    }

    private void show(List<LogStore.Entry> result) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        loading = false;
        refreshButton.setEnabled(true);
        if (result == null) {
            emptyMessage.setText(R.string.logs_read_failed);
            if (adapter.getCount() > 0) {
                Toast.makeText(this, R.string.logs_read_failed, Toast.LENGTH_SHORT).show();
            }
        } else {
            emptyMessage.setText(R.string.logs_empty);
            adapter.entries = result;
            adapter.notifyDataSetChanged();
        }
        clearButton.setEnabled(adapter.getCount() > 0);
    }

    private final class LogAdapter extends BaseAdapter {
        private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
        private List<LogStore.Entry> entries = List.of();

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public LogStore.Entry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout existing) {
                row = existing;
            } else {
                row = ui.card();
                ui.append(row, ui.text(16, R.color.module_text, true), 0);
                ui.append(row, ui.text(14, R.color.module_text, false), 6);
                ui.append(row, ui.text(13, R.color.module_secondary, false), 6);
            }
            LogStore.Entry entry = getItem(position);
            ((TextView) row.getChildAt(0)).setText(eventLabel(entry.event()));
            ((TextView) row.getChildAt(1)).setText(callerText(entry));
            int source = PickerLog.PHOTOS.equals(entry.source()) ? R.string.logs_source_photo : R.string.logs_source_file;
            String date = dateFormat.format(new Date(entry.timestamp()));
            ((TextView) row.getChildAt(2)).setText(getString(R.string.logs_entry_meta, getString(source), date));
            return row;
        }
    }

    private String callerText(LogStore.Entry entry) {
        String packageName = entry.callerPackage();
        if (packageName == null) {
            return getString(R.string.logs_caller, getString(R.string.logs_unknown_app));
        }
        String label = entry.callerLabel();
        String app = label == null || label.equals(packageName)
                ? packageName : getString(R.string.logs_app_with_package, label, packageName);
        return getString(R.string.logs_caller, app);
    }

    private static int eventLabel(PickerLog.Event event) {
        return switch (event) {
            case FORWARDED -> R.string.logs_forwarded;
            case SKIPPED_REQUEST -> R.string.logs_skipped;
            case NO_DOCUMENTS_UI -> R.string.logs_documents_missing;
            case RESOLVE_FAILED -> R.string.logs_resolve_failed;
            case INITIALIZE_FAILED -> R.string.logs_initialize_failed;
            case SOURCE_FINISHING -> R.string.logs_source_finishing;
            case LAUNCH_FAILED -> R.string.logs_launch_failed;
            case CLOSE_FAILED -> R.string.logs_close_failed;
        };
    }
}
