package com.santoshdev.notnote;

import android.os.Build;
import android.util.Base64;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

public class EditorHelper {

    private final EditorActivity activity;
    private final WebView webView;

    public EditorHelper(EditorActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public void processAiResponse(String rawAiResponse) {
        try {
            String title = null;
            String content = rawAiResponse;

            // 1. SAFELY SEPARATE TITLE AND CONTENT
            if (content != null && content.contains("[TITLE]") && content.contains("[CONTENT]")) {
                int titleStart = content.indexOf("[TITLE]") + 7;
                int contentStart = content.indexOf("[CONTENT]");
                if (titleStart >= 7 && contentStart > titleStart) {
                    title = content.substring(titleStart, contentStart).trim();
                    content = content.substring(contentStart + 9).trim();
                }
            }

            // 2. SET TITLE SAFELY
            if (title != null && !title.isEmpty() && !title.equals("NO_CHANGE")) {
                final String finalTitle = title;
                activity.runOnUiThread(() -> {
                    EditText etTitle = activity.findViewById(R.id.et_title);
                    if (etTitle != null) {
                        etTitle.setText(finalTitle);
                    }
                    activity.markAsUnsaved(); // Mark as unsaved if title changed
                });
            }

            // 3. CLEAN UP CONTENT
            if (content == null) content = "";
            boolean replaceCurrent = content.startsWith("[REPLACE_CURRENT]");
            if (replaceCurrent) {
                content = content.replace("[REPLACE_CURRENT]", "").trim();
            }
            content = content.replace("[CONTAINER]", "").replace("[/CONTAINER]", "").trim();

            // 4. BULLETPROOF ENCODING (UTF-8 Bytes -> Base64)
            String encodedBase64 = Base64.encodeToString(content.getBytes("UTF-8"), Base64.NO_WRAP);

            // 5. INJECT INTO WEBVIEW & FORCE CLEAR "THINKING" STATUS
            activity.runOnUiThread(() -> {
                try {
                    String jsCommand = "javascript:receiveAiResponseBase64('" + encodedBase64 + "', " + replaceCurrent + ");";
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        webView.evaluateJavascript(jsCommand, null);
                    } else {
                        webView.loadUrl(jsCommand);
                    }
                    
                    // FORCE TRIGGER UNSAVED STATUS FOR ANDROID BACK BUTTON
                    activity.markAsUnsaved();

                    // GUARANTEED WAY TO REMOVE "THINKING" LOOP
                    TextView tvStatus = activity.findViewById(R.id.tv_editor_status);
                    if (tvStatus != null) {
                        tvStatus.setText("Editing Note");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
