package cn.wangyupwp.hyperospicker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private static final String MANAGE_SPECIAL_ACCESS =
            "android.settings.MANAGE_SPECIAL_APP_ACCESSES";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        var ui = new ModuleUi(this);
        var content = ui.scrollBody(ui.screen());

        var title = ui.text(32, R.color.module_text, true);
        title.setText(R.string.app_name);
        title.setAccessibilityHeading(true);
        ui.append(content, title, 16);

        var instructions = ui.text(15, R.color.module_secondary, false);
        instructions.setText(R.string.scope_help);
        ui.append(content, instructions, 10);

        var logCard = ui.card();
        var logTitle = ui.text(18, R.color.module_text, true);
        logTitle.setText(R.string.logs_title);
        ui.append(logCard, logTitle, 0);
        var logSummary = ui.text(14, R.color.module_secondary, false);
        logSummary.setText(R.string.logs_summary);
        ui.append(logCard, logSummary, 5);
        ui.append(logCard, ui.action(R.string.open_logs, true,
                view -> startActivity(new Intent(this, LogActivity.class))), 12);
        ui.append(content, logCard, 24);
        ui.append(content, ui.action(R.string.open_special_access, false, view -> openSpecialAccess()), 12);
    }

    private void openSpecialAccess() {
        try {
            startActivity(new Intent(MANAGE_SPECIAL_ACCESS));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
                Toast.makeText(this, R.string.open_settings, Toast.LENGTH_SHORT).show();
            } catch (ActivityNotFoundException | SecurityException noSettings) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
