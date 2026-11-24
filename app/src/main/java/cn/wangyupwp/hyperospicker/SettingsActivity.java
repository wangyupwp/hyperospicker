package cn.wangyupwp.hyperospicker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private static final String MANAGE_SPECIAL_ACCESS =
            "android.settings.MANAGE_SPECIAL_APP_ACCESSES";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(24);
        content.addView(title);

        TextView instructions = new TextView(this);
        instructions.setText(R.string.scope_help);
        instructions.setPadding(0, padding, 0, padding);
        content.addView(instructions);

        Button specialAccess = new Button(this);
        specialAccess.setText(R.string.open_special_access);
        specialAccess.setOnClickListener(view -> openSpecialAccess());
        content.addView(specialAccess, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        setContentView(content);
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
