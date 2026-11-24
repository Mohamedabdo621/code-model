package com.alex.nexacode;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * NEXA CODE 2100 — تطبيق مصمم الأكواد: بيكلم نموذج Alex المدرّب،
 * يسأله عن أكواد، ويبني له مشاريع ويب كاملة على الجهاز مع معاينة فورية.
 * الواجهة كلها مرسومة برمجياً: زجاج نيون + خلفية سائلة متحركة.
 */
public class MainActivity extends Activity {

    // ------------------------------------------------------------ الثوابت
    private static final String PREFS = "nexa_prefs";
    private static final String KEY_URL = "api_url";
    private static final String DEFAULT_URL = "https://5e5982073a5ebbb186.gradio.live";

    private static final int C_TEXT = Color.rgb(234, 246, 255);
    private static final int C_MUTED = Color.rgb(159, 179, 200);
    private static final int C_CYAN = Color.rgb(0, 229, 255);
    private static final int C_VIOLET = Color.rgb(139, 92, 255);
    private static final int C_MAGENTA = Color.rgb(255, 62, 165);
    private static final int C_GLASS = Color.argb(30, 255, 255, 255);
    private static final int C_GLASS_STROKE = Color.argb(90, 0, 229, 255);

    // ------------------------------------------------------------ الحالة
    private FrameLayout screenHost;
    private LinearLayout chatMessages, projectList, buildLog;
    private ScrollView chatScroll, buildLogScroll;
    private EditText chatInput, buildInput, urlInput;
    private View chatScreen, buildScreen, projectsScreen, settingsScreen;
    private TextView[] navItems;
    private File lastBuiltDir;
    private Button previewBtn;

    // ------------------------------------------------------------ أدوات واجهة
    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable glass(float radiusDp, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(radiusDp));
        g.setColor(fill);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private GradientDrawable neonGradient(float radiusDp) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{C_CYAN, C_VIOLET, C_MAGENTA});
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button neonButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(neonGradient(16));
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int m) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(m), dp(m), dp(m), dp(m));
        return p;
    }

    private String apiUrl() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_URL, DEFAULT_URL).trim();
    }

    private File projectsRoot() {
        File f = new File(getExternalFilesDir(null), "projects");
        f.mkdirs();
        return f;
    }

    // ------------------------------------------------------------ البداية
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.addView(new FluidBackgroundView(this),
                new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        column.addView(buildHeader(), lp(-1, -2, 0));
        screenHost = new FrameLayout(this);
        column.addView(screenHost, new LinearLayout.LayoutParams(-1, 0, 1f));
        column.addView(buildNav(), lp(-1, -2, 0));

        chatScreen = buildChatScreen();
        buildScreen = buildBuildScreen();
        projectsScreen = buildProjectsScreen();
        settingsScreen = buildSettingsScreen();
        screenHost.addView(chatScreen);
        screenHost.addView(buildScreen);
        screenHost.addView(projectsScreen);
        screenHost.addView(settingsScreen);
        showScreen(0);

        setContentView(root);
        addBubble("أهلاً بيك في 2100 ✦ أنا نموذج الكود بتاعك. اسألني عن أي كود، أو روح لتبويب «ابنِ» واوصف مشروع وأنا أبنيهولك ملفات جاهزة على موبايلك.", false);
    }

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(18), dp(16), dp(18), dp(8));

        TextView logo = text("✦", 30, C_CYAN, true);
        h.addView(logo);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        TextView name = text("NEXA CODE", 21, C_TEXT, true);
        name.setLetterSpacing(0.18f);
        titles.addView(name);
        titles.addView(text("مصمم الأكواد الذكي · إصدار 2100", 12, C_MUTED, false));
        h.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView dot = text("●", 14, Color.rgb(0, 255, 170), false);
        h.addView(dot);
        return h;
    }

    private View buildNav() {
        String[][] items = {
                {"💬", "اسأل"}, {"🛠️", "ابنِ"}, {"📁", "مشاريعي"}, {"⚙️", "الرابط"}};
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackground(glass(0, Color.argb(46, 10, 10, 34), 0));
        nav.setPadding(dp(8), dp(8), dp(8), dp(12));
        navItems = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = text(items[i][0] + "\n" + items[i][1], 12, C_MUTED, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(6), dp(8), dp(6), dp(8));
            t.setOnClickListener(v -> showScreen(idx));
            navItems[i] = t;
            nav.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return nav;
    }

    private void showScreen(int idx) {
        View[] screens = {chatScreen, buildScreen, projectsScreen, settingsScreen};
        for (int i = 0; i < screens.length; i++) {
            screens[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            navItems[i].setTextColor(i == idx ? C_CYAN : C_MUTED);
            navItems[i].setBackground(i == idx ? glass(14, C_GLASS, C_GLASS_STROKE) : null);
        }
        if (idx == 2) refreshProjects();
    }

    // ------------------------------------------------------------ شاشة الشات
    private View buildChatScreen() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(6), dp(12), dp(10));

        chatScroll = new ScrollView(this);
        chatMessages = new LinearLayout(this);
        chatMessages.setOrientation(LinearLayout.VERTICAL);
        chatScroll.addView(chatMessages);
        col.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(glass(20, C_GLASS, C_GLASS_STROKE));
        bar.setPadding(dp(8), dp(6), dp(6), dp(6));

        chatInput = new EditText(this);
        chatInput.setHint("اكتب طلب الكود هنا...");
        chatInput.setHintTextColor(C_MUTED);
        chatInput.setTextColor(C_TEXT);
        chatInput.setTextSize(15);
        chatInput.setBackground(null);
        chatInput.setMaxLines(3);
        bar.addView(chatInput, new LinearLayout.LayoutParams(0, -2, 1f));

        Button send = neonButton("➤");
        send.setOnClickListener(v -> sendChat());
        bar.addView(send);
        col.addView(bar, lp(-1, -2, 4));
        return col;
    }

    private TextView addBubble(String msg, boolean user) {
        TextView b = text(msg, 14.5f, C_TEXT, false);
        b.setLineSpacing(0, 1.25f);
        if (!user) {
            b.setTypeface(Typeface.MONOSPACE);
            b.setTextSize(13.5f);
        }
        b.setPadding(dp(14), dp(11), dp(14), dp(11));
        b.setBackground(user ? neonGradient(18) : glass(18, C_GLASS, C_GLASS_STROKE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(dp(6), dp(5), dp(6), dp(5));
        p.gravity = user ? Gravity.END : Gravity.START;
        p.width = -2;
        chatMessages.addView(b, p);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        b.setAlpha(0f);
        b.animate().alpha(1f).setDuration(350).start();
        return b;
    }

    private void sendChat() {
        String prompt = chatInput.getText().toString().trim();
        if (prompt.isEmpty()) return;
        chatInput.setText("");
        addBubble(prompt, true);
        TextView typing = addBubble("⚡ النموذج بيصمم الكود...", false);
        ObjectAnimator pulse = ObjectAnimator.ofFloat(typing, "alpha", 0.35f, 1f);
        pulse.setDuration(650);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.start();
        ApiClient.ask(apiUrl(), prompt, 400, new ApiClient.Callback() {
            @Override
            public void onSuccess(String result) {
                pulse.cancel();
                typing.setAlpha(1f);
                typing.setText(result);
                chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
            }

            @Override
            public void onError(String message) {
                pulse.cancel();
                typing.setAlpha(1f);
                typing.setText("❌ " + message);
                typing.setTextColor(Color.rgb(255, 140, 170));
            }
        });
    }

    // ------------------------------------------------------------ شاشة البناء
    private View buildBuildScreen() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(10), dp(14), dp(12));

        col.addView(text("🛠️ ابنِ مشروع كامل", 19, C_TEXT, true));
        TextView sub = text("اوصف اللي عايزه والنموذج هيخطط الملفات ويبنيها على جهازك: HTML + CSS + JavaScript وأي لغات تانية المشروع محتاجها.", 13, C_MUTED, false);
        sub.setPadding(0, dp(4), 0, dp(10));
        col.addView(sub);

        buildInput = new EditText(this);
        buildInput.setHint("مثال: صفحة هبوط لمحل قهوة فيها منيو وزر طلب...");
        buildInput.setHintTextColor(C_MUTED);
        buildInput.setTextColor(C_TEXT);
        buildInput.setTextSize(15);
        buildInput.setMinLines(3);
        buildInput.setGravity(Gravity.TOP);
        buildInput.setBackground(glass(18, C_GLASS, C_GLASS_STROKE));
        buildInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        col.addView(buildInput, lp(-1, -2, 2));

        Button buildBtn = neonButton("🚀 ابنِ المشروع");
        buildBtn.setOnClickListener(v -> startBuild());
        col.addView(buildBtn, lp(-1, -2, 4));

        previewBtn = neonButton("👁️ معاينة آخر مشروع");
        previewBtn.setVisibility(View.GONE);
        previewBtn.setOnClickListener(v -> {
            if (lastBuiltDir != null) openPreview(lastBuiltDir);
        });
        col.addView(previewBtn, lp(-1, -2, 2));

        buildLogScroll = new ScrollView(this);
        buildLogScroll.setBackground(glass(18, Color.argb(24, 0, 0, 0), C_GLASS_STROKE));
        buildLog = new LinearLayout(this);
        buildLog.setOrientation(LinearLayout.VERTICAL);
        buildLog.setPadding(dp(12), dp(10), dp(12), dp(10));
        buildLogScroll.addView(buildLog);
        col.addView(buildLogScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return col;
    }

    private void logLine(String line) {
        runOnUiThread(() -> {
            TextView t = text(line, 13.5f, C_TEXT, false);
            t.setTypeface(Typeface.MONOSPACE);
            t.setPadding(0, dp(2), 0, dp(2));
            buildLog.addView(t);
            buildLogScroll.post(() -> buildLogScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void startBuild() {
        String desc = buildInput.getText().toString().trim();
        if (desc.isEmpty()) {
            Toast.makeText(this, "اوصف المشروع الأول", Toast.LENGTH_SHORT).show();
            return;
        }
        buildLog.removeAllViews();
        String slug = desc.replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 30) slug = slug.substring(0, 30);
        if (slug.isEmpty()) slug = "project-" + new SimpleDateFormat("HHmmss", Locale.US).format(new Date());
        File dir = new File(projectsRoot(), slug);
        int n = 2;
        while (dir.exists()) dir = new File(projectsRoot(), slug + "-" + (n++));
        final File outDir = dir;
        logLine("🎯 المشروع: " + desc);
        ApiClient.buildProject(apiUrl(), desc, outDir,
                line -> logLine(line),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(String path) {
                        lastBuiltDir = new File(path);
                        logLine("✅ اتبنى المشروع في: " + lastBuiltDir.getName());
                        previewBtn.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, "المشروع جاهز 🎉", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        logLine("❌ " + message);
                    }
                });
    }

    // ------------------------------------------------------------ شاشة المشاريع
    private View buildProjectsScreen() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(10), dp(14), dp(12));
        col.addView(text("📁 مشاريعي المبنية", 19, C_TEXT, true));
        ScrollView scroll = new ScrollView(this);
        projectList = new LinearLayout(this);
        projectList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(projectList);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return col;
    }

    private void refreshProjects() {
        projectList.removeAllViews();
        File[] dirs = projectsRoot().listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            TextView empty = text("لسه مفيش مشاريع — روح تبويب «ابنِ» وابنِ أول مشروع 🛠️", 14, C_MUTED, false);
            empty.setPadding(dp(6), dp(16), dp(6), 0);
            projectList.addView(empty);
            return;
        }
        java.util.Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US);
        for (File d : dirs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(glass(16, C_GLASS, C_GLASS_STROKE));
            row.setPadding(dp(14), dp(12), dp(10), dp(12));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text("🌐 " + d.getName(), 15.5f, C_TEXT, true));
            info.addView(text(fmt.format(new Date(d.lastModified())), 12, C_MUTED, false));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView del = text("🗑", 17, C_MAGENTA, false);
            del.setPadding(dp(10), dp(6), dp(10), dp(6));
            del.setOnClickListener(v -> {
                deleteRecursive(d);
                refreshProjects();
            });
            row.addView(del);
            row.setOnClickListener(v -> openPreview(d));
            projectList.addView(row, lp(-1, -2, 4));
        }
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    // ------------------------------------------------------------ المعاينة
    private void openPreview(File projectDir) {
        File index = new File(projectDir, "index.html");
        if (!index.exists()) {
            Toast.makeText(this, "المشروع ده مفيهوش index.html", Toast.LENGTH_SHORT).show();
            return;
        }
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.rgb(5, 5, 20));

        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setDomStorageEnabled(true);
        web.loadUrl("file://" + index.getAbsolutePath());
        overlay.addView(web, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackground(glass(0, Color.argb(200, 8, 8, 26), 0));
        topBar.setPadding(dp(14), dp(10), dp(10), dp(10));
        TextView title = text("👁️ " + projectDir.getName(), 15, C_TEXT, true);
        topBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = text("✕ إغلاق", 15, C_CYAN, true);
        close.setPadding(dp(10), dp(4), dp(10), dp(4));
        close.setOnClickListener(v -> ((ViewGroup) overlay.getParent()).removeView(overlay));
        topBar.addView(close);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2);
        overlay.addView(topBar, tp);

        ((ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content)).addView(overlay);
    }

    // ------------------------------------------------------------ شاشة الرابط
    private View buildSettingsScreen() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(10), dp(14), dp(12));

        col.addView(text("⚙️ رابط النموذج (API)", 19, C_TEXT, true));
        TextView hint = text("الرابط ده بييجي من نوتبوك التشغيل في Colab (gradio.live). لو قفلت الجلسة وفتحتها تاني، انسخ الرابط الجديد هنا.", 13, C_MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(10));
        col.addView(hint);

        urlInput = new EditText(this);
        urlInput.setText(apiUrl());
        urlInput.setTextColor(C_TEXT);
        urlInput.setHintTextColor(C_MUTED);
        urlInput.setTextSize(13.5f);
        urlInput.setTypeface(Typeface.MONOSPACE);
        urlInput.setBackground(glass(16, C_GLASS, C_GLASS_STROKE));
        urlInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        urlInput.setSingleLine(false);
        urlInput.setMaxLines(3);
        col.addView(urlInput, lp(-1, -2, 2));

        Button save = neonButton("💾 احفظ الرابط");
        save.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_URL, urlInput.getText().toString().trim()).apply();
            Toast.makeText(this, "اتحفظ ✓", Toast.LENGTH_SHORT).show();
        });
        col.addView(save, lp(-1, -2, 4));

        TextView about = text("NEXA CODE 2100 ✦ نموذج Qwen2.5-Coder-1.5B مدرّب بـ LoRA خصيصاً لـ Alex — darklink56/qwen-coder-1.5b-lora-out", 12.5f, C_MUTED, false);
        about.setPadding(dp(4), dp(18), dp(4), 0);
        col.addView(about);
        return col;
    }
}
