package cn.wangyupwp.hyperospicker;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ModuleUi {
    private final Activity activity;

    ModuleUi(Activity activity) {
        this.activity = activity;
    }

    LinearLayout screen() {
        LinearLayout root = column();
        root.setBackgroundColor(activity.getColor(R.color.module_background));
        activity.getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            var edges = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(edges.left, edges.top, edges.right, edges.bottom);
            return insets;
        });
        activity.setContentView(root);
        root.requestApplyInsets();
        return root;
    }

    LinearLayout scrollBody(LinearLayout root) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = column();
        body.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return body;
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout card() {
        LinearLayout card = column();
        card.setBackground(surface(R.color.module_surface));
        card.setPadding(dp(18), dp(15), dp(18), dp(15));
        return card;
    }

    TextView text(int size, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setTextSize(size);
        text.setTextColor(activity.getColor(color));
        if (bold) {
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return text;
    }

    Button action(int label, boolean emphasized, View.OnClickListener listener) {
        Button button = new Button(activity);
        int color = emphasized ? R.color.module_accent_surface : R.color.module_surface;
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(activity.getColor(R.color.module_ripple)),
                surface(color), null));
        button.setText(label);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        button.setTextSize(15);
        button.setTextColor(activity.getColor(R.color.module_accent));
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinHeight(dp(48));
        button.setPadding(dp(14), dp(9), dp(14), dp(9));
        button.setOnClickListener(listener);
        return button;
    }

    void append(LinearLayout parent, View child, int gap) {
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(gap);
        parent.addView(child, params);
    }

    int dp(int units) {
        return Math.round(units * activity.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable surface(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(20));
        shape.setColor(activity.getColor(color));
        return shape;
    }
}
