package app.exteraless.plugins.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import app.exteraless.plugins.PluginsController;

final class PluginSliderCell extends LinearLayout {
    private final TextView title;
    private final TextView subtitle;
    private final SeekBarView slider;
    private int steps;
    private JSONObject row;
    private double minimum, maximum, step;
    private boolean tracking;
    private String pendingValue;

    PluginSliderCell(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(21), AndroidUtilities.dp(6));
        title = new TextView(context);
        title.setTextSize(16);
        addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        subtitle = new TextView(context);
        subtitle.setTextSize(13);
        addView(subtitle, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        slider = new SeekBarView(context);
        addView(slider, new LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(40)));
        slider.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                if (row == null) return;
                if (!stop) {
                    double value = Math.min(maximum, minimum + Math.round(progress * steps) * step);
                    try {
                        row.put("value", value);
                    } catch (org.json.JSONException ignored) {
                    }
                    String json = row.optBoolean("integral") ? Long.toString(Math.round(value)) : Double.toString(value);
                    showValue(json);
                    pendingValue = json;
                    if (!tracking) {
                        flush();
                    }
                    return;
                }
                tracking = false;
                flush();
                PluginsController.getInstance().reloadSettingsScreen(row.optString("plugin_id"));
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
                if (pressed) {
                    tracking = true;
                    pendingValue = null;
                }
            }
        });
    }

    private void flush() {
        String value = pendingValue;
        pendingValue = null;
        if (value != null && row != null) {
            PluginsController.getInstance().notifySettingChanged(row.optString("plugin_id"), row.optString("key"), value);
        }
    }

    private void showValue(String value) {
        title.setText(row.optString("text") + " · " + value);
    }

    void bind(JSONObject data) {
        row = data;
        minimum = row.optDouble("min", 0);
        maximum = row.optDouble("max", 100);
        step = row.optDouble("step", 1);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setText(row.optString("subtext", ""));
        subtitle.setVisibility(subtitle.length() == 0 ? GONE : VISIBLE);
        boolean valid = Double.isFinite(minimum) && Double.isFinite(maximum) && Double.isFinite(step)
                && maximum > minimum && step > 0 && Math.ceil((maximum - minimum) / step) <= Integer.MAX_VALUE;
        slider.setEnabled(valid);
        if (!valid) return;
        double value = Math.max(minimum, Math.min(maximum, row.optDouble("value", minimum)));
        steps = (int) Math.ceil((maximum - minimum) / step);
        slider.setProgress((float) Math.round((value - minimum) / step) / steps);
        showValue(row.optBoolean("integral") ? Long.toString(Math.round(value)) : Double.toString(value));
    }

    public static class Factory extends UItem.UItemFactory<PluginSliderCell> {
        @Override
        public PluginSliderCell createView(Context context, RecyclerListView list, int account, int guid,
                                           Theme.ResourcesProvider provider) {
            return new PluginSliderCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView list) {
            ((PluginSliderCell) view).bind((JSONObject) item.object);
        }

        @Override
        public boolean isClickable() {
            return false;
        }

        @Override
        public boolean equals(UItem first, UItem second) {
            return first.id == second.id;
        }

        @Override
        public boolean contentsEquals(UItem first, UItem second) {
            if (first.id != second.id) {
                return false;
            }
            if (first.object == second.object) {
                return true;
            }
            if (!(first.object instanceof JSONObject) || !(second.object instanceof JSONObject)) {
                return false;
            }
            JSONObject a = (JSONObject) first.object;
            JSONObject b = (JSONObject) second.object;
            return a.optDouble("value", 0) == b.optDouble("value", 0)
                    && a.optDouble("min", 0) == b.optDouble("min", 0)
                    && a.optDouble("max", 100) == b.optDouble("max", 100)
                    && a.optDouble("step", 1) == b.optDouble("step", 1)
                    && a.optBoolean("integral") == b.optBoolean("integral")
                    && a.optString("text").equals(b.optString("text"))
                    && a.optString("subtext").equals(b.optString("subtext"))
                    && a.optString("key").equals(b.optString("key"));
        }
    }
}
