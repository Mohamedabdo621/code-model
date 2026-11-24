package com.alex.nexacode;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * عميل الـ API: بيكلم نموذج الكود (Gradio على Colab) بنفس منطق أداة التيرمنال —
 * سؤال ورد، أو بناء مشروع كامل ملف ملف ويتكتب على الجهاز.
 */
public class ApiClient {

    public interface Callback {
        void onSuccess(String result);
        void onError(String message);
    }

    public interface Progress {
        void onProgress(String line);
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int BUILD_TOKENS = 500;

    // ------------------------------------------------------------------ HTTP

    private static String httpPostJson(String urlStr, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readAll(conn);
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(300000);
        return readAll(conn);
    }

    private static String readAll(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        if (code >= 400) throw new Exception("HTTP " + code + ": " + sb);
        return sb.toString();
    }

    /** خطوتين Gradio: POST يرجع event_id وبعده GET يجيب الرد. */
    private static String askSync(String baseUrl, String prompt, int tokens) throws Exception {
        String base = baseUrl.replaceAll("/+$", "");
        JSONArray data = new JSONArray();
        data.put(prompt);
        data.put(tokens);
        JSONObject body = new JSONObject();
        body.put("data", data);
        String postResp = httpPostJson(base + "/gradio_api/call/generate", body);
        String eventId = new JSONObject(postResp).getString("event_id");
        String getResp = httpGet(base + "/gradio_api/call/generate/" + eventId);
        for (String line : getResp.split("\n")) {
            if (line.startsWith("data:")) {
                JSONArray arr = new JSONArray(line.substring(5).trim());
                return arr.length() > 0 ? arr.getString(0) : "";
            }
        }
        throw new Exception("رد غير متوقع من الـ API");
    }

    public static void ask(String baseUrl, String prompt, int tokens, Callback cb) {
        EXEC.execute(() -> {
            try {
                String r = askSync(baseUrl, prompt, tokens);
                MAIN.post(() -> cb.onSuccess(r));
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(friendlyError(e)));
            }
        });
    }

    private static String friendlyError(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (m.contains("Unable to resolve host") || m.contains("Failed to connect")
                || m.contains("timeout") || m.contains("timed out")) {
            return "مش قادر أوصل للـ API — اتأكد إن جلسة Colab مفتوحة وإن الرابط في الإعدادات هو الأحدث.";
        }
        return m.length() > 220 ? m.substring(0, 220) : m;
    }

    // ------------------------------------------------------------------ البناء

    private static final String[] DEFAULT_FILES = {"index.html", "style.css", "script.js"};
    private static final Pattern FILE_LINE = Pattern.compile(
            "^[\\w./-]+\\.(html|css|js|json|txt|md|svg|xml)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_BLOCK = Pattern.compile(
            "```(\\w+)?\\n(.*?)```", Pattern.DOTALL);

    private static List<String> planFiles(String baseUrl, String description) {
        String prompt = "You are building a small, complete web project.\n"
                + "Project description: " + description + "\n\n"
                + "List the files needed to build it. Rules:\n"
                + "- One filename per line, filenames only, no explanations.\n"
                + "- At most 5 files. Always include index.html.\n"
                + "- Example:\nindex.html\nstyle.css\nscript.js";
        List<String> files = new ArrayList<>();
        try {
            String reply = askSync(baseUrl, prompt, 120);
            for (String line : reply.split("\n")) {
                String name = line.trim().replaceAll("^[`\\-*•0-9. ]+", "").trim();
                if (FILE_LINE.matcher(name).matches() && !files.contains(name)) files.add(name);
            }
        } catch (Exception ignored) {
            // هنرجع للافتراضي تحت
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
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** يبني المشروع كاملاً في outDir ويرجع مساره عند النجاح. */
    public static void buildProject(String baseUrl, String description, File outDir,
                                    Progress progress, Callback cb) {
        EXEC.execute(() -> {
            try {
                progress.onProgress("🧠 النموذج بيخطط ملفات المشروع...");
                List<String> files = planFiles(baseUrl, description);
                StringBuilder list = new StringBuilder();
                for (String f : files) { if (list.length() > 0) list.append(", "); list.append(f); }
                progress.onProgress("📋 الملفات: " + list);
                outDir.mkdirs();
                String htmlContext = "";
                for (String fname : files) {
                    progress.onProgress("🔨 بيولّد " + fname + " ...");
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
                    String reply = askSync(baseUrl, prompt, BUILD_TOKENS);
                    String content = extractFileContent(reply, fname).trim() + "\n";
                    writeFile(outDir, fname, content);
                    if (fname.toLowerCase().endsWith(".html") && htmlContext.isEmpty()) {
                        htmlContext = content;
                    }
                    progress.onProgress("💾 اتكتب " + fname);
                }
                MAIN.post(() -> cb.onSuccess(outDir.getAbsolutePath()));
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(friendlyError(e)));
            }
        });
    }
}
