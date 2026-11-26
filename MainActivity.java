package ru.aurora.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static class App { String name, id; Drawable icon; ComponentName cn; }

    final List<App> all = new ArrayList<>(), shown = new ArrayList<>();
    SharedPreferences p;
    BaseAdapter adapter;
    LinearLayout dock;
    EditText search;

    final String[] themes = {"Тёмная", "Светлая", "Чёрная (AMOLED)"};
    final String[] accents = {"Бирюзовый", "Фиолетовый", "Оранжевый", "Розовый"};
    final int[] accentColors = {0xFF18B7A6, 0xFF8B6CFF, 0xFFFF8A34, 0xFFF45B93};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("s", 0);
    }

    @Override public void onResume() {
        super.onResume();
        load();
        build();
    }

    @Override public void onBackPressed() {
        if (search.length() > 0) search.setText("");
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (search != null) search.setText("");
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    boolean light() { return p.getInt("theme", 0) == 1; }
    int text() { return light() ? 0xFF14181F : Color.WHITE; }
    int accent() { return accentColors[p.getInt("accent", 0)]; }

    GradientDrawable panel() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(p.getInt("alpha", 40), light() ? 255 : 160, light() ? 255 : 170, light() ? 255 : 190));
        g.setCornerRadius(dp(26));
        return g;
    }

    void load() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        all.clear();
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            App a = new App();
            a.name = r.loadLabel(pm).toString();
            a.icon = r.loadIcon(pm);
            a.cn = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
            a.id = a.cn.flattenToString();
            all.add(a);
        }
        all.sort((x, y) -> x.name.compareToIgnoreCase(y.name));
    }

    View cell(App a, boolean label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(2), dp(8), dp(2), dp(8));
        ImageView im = new ImageView(this);
        im.setImageDrawable(a.icon);
        int s = dp(p.getInt("icon", 52));
        c.addView(im, new LinearLayout.LayoutParams(s, s));
        if (label) {
            TextView t = new TextView(this);
            t.setText(a.name);
            t.setTextColor(text());
            t.setTextSize(12);
            t.setSingleLine();
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            c.addView(t);
        }
        return c;
    }

    void open(App a, View v) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions o = p.getBoolean("anim", true)
                ? ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight())
                : ActivityOptions.makeCustomAnimation(this, 0, 0);
            startActivity(i, o.toBundle());
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть", Toast.LENGTH_SHORT).show();
        }
    }

    List<String> pinned() {
        List<String> l = new ArrayList<>();
        for (String s : p.getString("dock", "").split("\n")) if (!s.isEmpty()) l.add(s);
        return l;
    }

    void togglePin(App a) {
        List<String> l = pinned();
        l.removeIf(id -> all.stream().noneMatch(x -> x.id.equals(id)));
        if (!l.remove(a.id)) {
            if (l.size() >= 5) {
                Toast.makeText(this, "В доке максимум 5 приложений", Toast.LENGTH_SHORT).show();
                return;
            }
            l.add(a.id);
        }
        p.edit().putString("dock", String.join("\n", l)).apply();
        fillDock();
    }

    void fillDock() {
        dock.removeAllViews();
        for (String id : pinned())
            for (App a : all)
                if (a.id.equals(id)) {
                    View v = cell(a, false);
                    v.setOnClickListener(x -> open(a, x));
                    v.setOnLongClickListener(x -> { togglePin(a); return true; });
                    dock.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
                }
        dock.setVisibility(dock.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    void filter(String q) {
        shown.clear();
        for (App a : all)
            if (a.name.toLowerCase(Locale.getDefault()).contains(q.toLowerCase(Locale.getDefault())))
                shown.add(a);
        adapter.notifyDataSetChanged();
    }

    void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(12));
        root.setFitsSystemWindows(true);
        int th = p.getInt("theme", 0);
        int top = th == 2 ? Color.BLACK : th == 1 ? 0xFFF4F6FB : 0xFF121826;
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{top, th == 2 ? top : blend(top, accent())});
        getWindow().setBackgroundDrawable(bg);
        getWindow().setStatusBarColor(top);
        getWindow().setNavigationBarColor(th == 2 ? Color.BLACK : blend(top, accent()));
        getWindow().getDecorView().setSystemUiVisibility(light() ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackground(panel());
        head.setPadding(dp(22), dp(14), dp(22), dp(14));
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextSize(58);
        clock.setTextColor(text());
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextSize(15);
        date.setTextColor(accent());
        head.addView(clock);
        head.addView(date);
        head.setOnLongClickListener(v -> { settings(); return true; });
        root.addView(head);

        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(panel());
        bar.setPadding(dp(16), 0, dp(4), 0);
        search = new EditText(this);
        search.setHint("Поиск приложений");
        search.setSingleLine();
        search.setBackground(null);
        search.setTextColor(text());
        search.setHintTextColor((text() & 0x00FFFFFF) | 0x88000000);
        bar.addView(search, new LinearLayout.LayoutParams(0, dp(50), 1));
        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(24);
        gear.setTextColor(accent());
        gear.setGravity(Gravity.CENTER);
        gear.setOnClickListener(v -> settings());
        bar.addView(gear, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, dp(6));
        root.addView(bar, lp);

        GridView grid = new GridView(this);
        grid.setNumColumns(p.getInt("cols", 4));
        grid.setVerticalScrollBarEnabled(false);
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View old, ViewGroup g) { return cell(shown.get(i), true); }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((g, v, i, id) -> open(shown.get(i), v));
        grid.setOnItemLongClickListener((g, v, i, id) -> { togglePin(shown.get(i)); return true; });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        dock = new LinearLayout(this);
        dock.setBackground(panel());
        root.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        fillDock();

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            public void afterTextChanged(Editable e) {}
        });
        filter("");
        setContentView(root);
    }

    int blend(int a, int b) {
        return Color.rgb((Color.red(a) * 3 + Color.red(b)) / 4,
            (Color.green(a) * 3 + Color.green(b)) / 4,
            (Color.blue(a) * 3 + Color.blue(b)) / 4);
    }

    void settings() {
        String[] items = {"Тема", "Цвет акцента", "Колонки", "Размер значков", "Прозрачность панелей", "Анимация запуска"};
        new AlertDialog.Builder(this).setTitle("Настройки").setItems(items, (d, w) -> {
            if (w == 0) choose("theme", themes, new int[]{0, 1, 2}, 0);
            if (w == 1) choose("accent", accents, new int[]{0, 1, 2, 3}, 0);
            if (w == 2) choose("cols", new String[]{"3", "4", "5"}, new int[]{3, 4, 5}, 4);
            if (w == 3) choose("icon", new String[]{"Маленькие", "Средние", "Крупные"}, new int[]{44, 52, 60}, 52);
            if (w == 4) choose("alpha", new String[]{"Прозрачные", "Средние", "Плотные"}, new int[]{20, 40, 90}, 40);
            if (w == 5) {
                boolean on = !p.getBoolean("anim", true);
                p.edit().putBoolean("anim", on).apply();
                Toast.makeText(this, on ? "Включена" : "Выключена", Toast.LENGTH_SHORT).show();
            }
        }).show();
    }

    void choose(String key, String[] names, int[] values, int def) {
        int sel = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == p.getInt(key, def)) sel = i;
        new AlertDialog.Builder(this).setSingleChoiceItems(names, sel, (d, i) -> {
            p.edit().putInt(key, values[i]).apply();
            d.dismiss();
            build();
        }).show();
    }
}
