package com.alex.nexacode;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * النموذج المحلي: بيشتغل جوه التطبيق نفسه بمحرك llama.cpp — من غير سيرفر
 * ولا رابط ولا إنترنت (بعد تنزيل ملف النموذج مرة واحدة من Hugging Face).
 */
public class LocalModel {

    public interface Callback {
        void onSuccess(String result);
        void onError(String message);
    }

    public interface Progress {
        void onProgress(String line);
    }

    public interface DownloadProgress {
        void onProgress(int percent, long doneMb, long totalMb);
    }

    static {
        System.loadLibrary("nexa_llama");
    }

    private static native boolean nativeInit(String modelPath, int nCtx, int nThreads);
    private static native String nativeGenerate(String prompt, int maxTokens);
    private static native void nativeFree();

    public static final String MODEL_URL =
            "https://huggingface.co/darklink56/qwen-coder-1.5b-gguf/resolve/main/qwen-coder-1.5b-q4_k_m.gguf";
    public static final String MODEL_FILENAME = "qwen-coder-1.5b-q4_k_m.gguf";
    private static final long MIN_VALID_SIZE = 500L * 1024 * 1024; // أقل حجم مقبول ~500MB
    private static final int BUILD_TOKENS = 500;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean ready = false;
    private static volatile boolean loading = false;

    // ------------------------------------------------------------ إدارة الملف
    public static File modelFile(Context ctx) {
        return new File(ctx.getFilesDir(), MODEL_FILENAME);
    }

    public static boolean isDownloaded(Context ctx) {
        File f = modelFile(ctx);
        return f.exists() && f.length() > MIN_VALID_SIZE;
    }

    public static boolean isReady() {
        return ready;
    }

    public static void initAsync(Context ctx, Callback cb) {
        if (ready) {
            cb.onSuccess("ready");
            return;
        }
        if (loading) {
            cb.onError("النموذج بيتحمّل دلوقتي...");
            return;
        }
        loading = true;
        EXEC.execute(() -> {
            boolean ok = false;
            String err = "ملف النموذج مش موجود — نزّله الأول من تبويب «النموذج».";
            try {
                File f = modelFile(ctx);
                if (f.exists() && f.length() > MIN_VALID_SIZE) {
                    ok = nativeInit(f.getAbsolutePath(), 4096, 4);
                    if (!ok) err = "فشل تحميل النموذج في الذاكرة — ممكن الذاكرة مش كفاية، اقفل تطبيقات تانية وحاول تاني.";
                }
            } catch (Throwable t) {
                err = "خطأ أثناء تحميل النموذج: " + t.getMessage();
            }
            ready = ok;
            loading = false;
            final String e = err;
            MAIN.post(() -> {
                if (ready) cb.onSuccess("ready");
                else cb.onError(e);
            });
        });
    }

    public static void download(Context ctx, DownloadProgress dp, Callback cb) {
        EXEC.execute(() -> {
            File tmp = new File(ctx.getFilesDir(), MODEL_FILENAME + ".tmp");
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(MODEL_URL).openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.connect();
                if (conn.getResponseCode() / 100 != 2) {
                    throw new Exception("HTTP " + conn.getResponseCode());
                }
                long total = conn.getContentLengthLong();
                long done = 0;
                int lastPct = -1;
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[256 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int pct = (int) (done * 100 / total);
                            if (pct != lastPct) {
                                lastPct = pct;
                                final int p = pct;
                                final long dMb = done / (1024 * 1024);
                                final long tMb = total / (1024 * 1024);
                                MAIN.post(() -> dp.onProgress(p, dMb, tMb));
                            }
                        }
                    }
                }
                File dest = modelFile(ctx);
                if (dest.exists()) dest.delete();
                if (!tmp.renameTo(dest)) throw new Exception("فشل حفظ ملف النموذج");
                if (dest.length() <= MIN_VALID_SIZE) {
                    dest.delete();
                    throw new Exception("الملف اللي نزل ناقص — اتأكد من النت وحاول تاني");
                }
                MAIN.post(() -> cb.onSuccess("downloaded"));
            } catch (Exception e) {
                tmp.delete();
                MAIN.post(() -> cb.onError("فشل تنزيل النموذج: " + e.getMessage()));
            }
        });
    }

    public static void deleteModel(Context ctx) {
        EXEC.execute(() -> {
            try {
                nativeFree();
            } catch (Throwable ignored) {
            }
            ready = false;
            File f = modelFile(ctx);
            if (f.exists()) f.delete();
        });
    }

    // ------------------------------------------------------------ التوليد
    private static String generateSync(String userPrompt, int maxTokens) {
        String full = "<|im_start|>user\n" + userPrompt + "<|im_end|>\n<|im_start|>assistant\n";
        String out = nativeGenerate(full, maxTokens);
        return out == null ? "" : out.trim();
    }

    public static void ask(String prompt, int tokens, Callback cb) {
        if (!ready) {
            cb.onError("النموذج مش جاهز — افتح تبويب «النموذج» ونزّله/حمّله الأول.");
            return;
        }
        EXEC.execute(() -> {
            try {
                String r = generateSync(prompt, tokens);
                MAIN.post(() -> cb.onSuccess(r));
            } catch (Throwable t) {
                MAIN.post(() -> cb.onError("خطأ أثناء التوليد: " + t.getMessage()));
            }
        });
    }

    // ------------------------------------------------------------ البناء
    private static final String[] DEFAULT_FILES = {"index.html", "style.css", "script.js"};
    private static final Pattern FILE_LINE = Pattern.compile(
            "^[\\w./-]+\\.(html|css|js|json|txt|md|svg|xml)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_BLOCK = Pattern.compile(
            "```(\\w+)?\\n(.*?)```", Pattern.DOTALL);

    private static List<String> planFiles(String description) {
        String prompt = "You are building a small, complete web project.\n"
                + "Project description: " + description + "\n\n"
                + "List the files needed to build it. Rules:\n"
                + "- One filename per line, filenames only, no explanations.\n"
                + "- At most 5 files. Always include index.html.\n"
                + "- Example:\nindex.html\nstyle.css\nscript.js";
        List<String> files = new ArrayList<>();
        try {
            String reply = generateSync(prompt, 120);
            for (String line : reply.split("\n")) {
                String name = line.trim().replaceAll("^[`\\-*•0-9. ]+", "").trim();
                if (FILE_LINE.matcher(name).matches() && !files.contains(name)) files.add(name);
            }
        } catch (Throwable ignored) {
        }
        if (files.isEmpty()) {
            for (String f : DEFAULT_FILES) files.add(f);
            return files;
        }
        boolean hasHtml = false;
        for (String f : files) if (f.toLowerCase().endsWith(".html")) hasHtml = true;
        if (!hasHtml) files.add(0, "index.html");
        return files.size() > 5 ? files.subList(0, 5) : files;
    }

    private static String extractFileContent(String text, String filename) {
        Matcher m = CODE_BLOCK.matcher(text);
        List<String[]> blocks = new ArrayList<>();
        while (m.find()) blocks.add(new String[]{m.group(1) == null ? "" : m.group(1), m.group(2)});
        if (blocks.isEmpty()) return text;
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.')).toLowerCase() : "";
        List<String> wanted = new ArrayList<>();
        if (ext.equals(".html")) wanted.add("html");
        else if (ext.equals(".css")) wanted.add("css");
        else if (ext.equals(".js")) { wanted.add("javascript"); wanted.add("js"); }
        else if (ext.equals(".json")) wanted.add("json");
        for (String[] b : blocks) if (wanted.contains(b[0].toLowerCase())) return b[1];
        return ext.equals(".html") ? blocks.get(0)[1] : blocks.get(blocks.size() - 1)[1];
    }

    private static void writeFile(File dir, String name, String content) throws Exception {
        File f = new File(dir, name);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes("UTF-8"));
        }
    }

    public static void buildProject(String description, File outDir,
                                    Progress progress, Callback cb) {
        if (!ready) {
            cb.onError("النموذج مش جاهز — افتح تبويب «النموذج» ونزّله/حمّله الأول.");
            return;
        }
        EXEC.execute(() -> {
            try {
                progress.onProgress("🧠 النموذج بيخطط ملفات المشروع...");
                List<String> files = planFiles(description);
                StringBuilder list = new StringBuilder();
                for (String f : files) { if (list.length() > 0) list.append(", "); list.append(f); }
                progress.onProgress("📋 الملفات: " + list);
                outDir.mkdirs();
                String htmlContext = "";
                for (String fname : files) {
                    progress.onProgress("🔨 بيولّد " + fname + " ... (على الموبايل بياخد شوية وقت)");
                    String prompt = "Write the complete file `" + fname + "` for this web project.\n"
                            + "Project description: " + description + "\n"
                            + "Project files: " + list + "\n"
                            + "Requirements:\n"
                            + "- The files must work together: index.html links style.css and "
                            + "script.js with relative paths.\n"
                            + "- Complete and working, but compact — no placeholder comments "
                            + "like 'your code here'.\n"
                            + "- Output ONLY the content of `" + fname + "` inside one code "
                            + "block. Do NOT output any other file. No explanations.";
                    if (!htmlContext.isEmpty() && !fname.toLowerCase().endsWith(".html")) {
                        prompt += "\n\nThe index.html file is ALREADY created — here it is. "
                                + "You MUST use the exact same element ids, class names and "
                                + "function names that appear in it:\n```html\n"
                                + htmlContext.substring(0, Math.min(1500, htmlContext.length()))
                                + "\n```";
                    }
                    String reply = generateSync(prompt, BUILD_TOKENS);
                    String content = extractFileContent(reply, fname).trim() + "\n";
                    writeFile(outDir, fname, content);
                    if (fname.toLowerCase().endsWith(".html") && htmlContext.isEmpty()) {
                        htmlContext = content;
                    }
                    progress.onProgress("💾 اتكتب " + fname);
                }
                MAIN.post(() -> cb.onSuccess(outDir.getAbsolutePath()));
            } catch (Throwable t) {
                MAIN.post(() -> cb.onError("خطأ أثناء البناء: " + t.getMessage()));
            }
        });
    }
}
