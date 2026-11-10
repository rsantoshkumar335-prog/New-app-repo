package com.santoshdev.notnote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EditorActivity extends Activity {
    private EditText etTitle;
    private WebView webViewEditor;
    private TextView tvWordCount, tvEditorStatus, tvFocusTimer, tvCategoryChip;
    private LinearLayout colorPaletteContainer, layoutAiToolsContainer, layoutFindReplace, aiQuickChipsContainer;
    private View bottomToolsContainer, sidebarColorMenu, btnFocusTimer, btnCategoryChip, btnQuickDateChip;
    private DatabaseHelper dbHelper;
    private int noteId = -1;
    private static final int PICK_IMAGE_REQUEST = 1001;
    private static final int VOICE_INPUT_REQUEST = 1002;

    private String currentEditorHtml = "";
    private String currentPlainText = "";
    private String currentPageColor = "#FFFFFF";
    private boolean currentIsPinned = false;
    private String currentCategory = "General";
    private boolean hasUnsavedChanges = false;
    private boolean isWebViewLoaded = false;
    private String pendingHtmlToLoad = "";

    private ImageView btnAiAction, btnCloseAi, btnAiSend, btnFillColor, btnPinEditor, btnFindInNote;
    private EditText etAiInput, etFindQuery, etReplaceQuery;

    private EditorHelper editorHelper;
    private SharedPreferences aiPrefs;
    private List<ColorOption> colorOptionsList;

    private int sidebarWidthPx;
    private boolean wasKeyboardOpen = false;

    // Built-in Pomodoro Focus Timer State
    private CountDownTimer focusCountDownTimer;
    private boolean isFocusTimerRunning = false;
    private long focusTimeRemainingMs = 25 * 60 * 1000L;
    private long focusInitialDurationMs = 25 * 60 * 1000L;

    private static final String[] CATEGORIES = {"General", "Work", "Personal", "Ideas", "Study"};

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        dbHelper = new DatabaseHelper(this);
        aiPrefs = getSharedPreferences("NotNoteAiPrefs", MODE_PRIVATE);

        etTitle = findViewById(R.id.et_title);
        webViewEditor = findViewById(R.id.editor_webview);
        tvWordCount = findViewById(R.id.tv_word_count);
        bottomToolsContainer = findViewById(R.id.bottom_tools_container);
        colorPaletteContainer = findViewById(R.id.color_palette_container);
        layoutAiToolsContainer = findViewById(R.id.layout_ai_tools_container);
        aiQuickChipsContainer = findViewById(R.id.ai_quick_chips_container);
        sidebarColorMenu = findViewById(R.id.sidebar_color_menu);
        layoutFindReplace = findViewById(R.id.layout_find_replace);

        btnFocusTimer = findViewById(R.id.btn_focus_timer);
        tvFocusTimer = findViewById(R.id.tv_focus_timer);
        btnPinEditor = findViewById(R.id.btn_pin_editor);
        btnFindInNote = findViewById(R.id.btn_find_in_note);
        btnCategoryChip = findViewById(R.id.btn_category_chip);
        tvCategoryChip = findViewById(R.id.tv_category_chip);
        btnQuickDateChip = findViewById(R.id.btn_quick_date_chip);

        etFindQuery = findViewById(R.id.et_find_query);
        etReplaceQuery = findViewById(R.id.et_replace_query);

        sidebarWidthPx = dp(310);

        try { tvEditorStatus = findViewById(R.id.tv_editor_status); } catch (Exception e) { tvEditorStatus = null; }

        editorHelper = new EditorHelper(this, webViewEditor);

        setupWebView();
        setupToolbar();
        setupProductivityControls();
        setupAiToolbar();
        setupKeyboardListener();
        setupColorSidebar();

        loadExistingNote();

        etTitle.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                markAsUnsaved();
            }
        });
    }

    private void setupProductivityControls() {
        // 1. Built-in Pomodoro Focus Timer (Tap to start/pause, Long-press to cycle 25m / 15m / 5m & reset)
        if (btnFocusTimer != null) {
            btnFocusTimer.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (isFocusTimerRunning) {
                    pauseFocusTimer();
                    Toast.makeText(this, "Focus timer paused", Toast.LENGTH_SHORT).show();
                } else {
                    startFocusTimer();
                    Toast.makeText(this, "Focus sprint started! Stay in flow.", Toast.LENGTH_SHORT).show();
                }
            });

            btnFocusTimer.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                pauseFocusTimer();
                if (focusInitialDurationMs == 25 * 60 * 1000L) {
                    focusInitialDurationMs = 15 * 60 * 1000L;
                } else if (focusInitialDurationMs == 15 * 60 * 1000L) {
                    focusInitialDurationMs = 5 * 60 * 1000L;
                } else {
                    focusInitialDurationMs = 25 * 60 * 1000L;
                }
                focusTimeRemainingMs = focusInitialDurationMs;
                updateFocusTimerText();
                Toast.makeText(this, "Timer set to " + (focusInitialDurationMs / 60000) + " min", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // 2. Pin Toggle in Editor
        if (btnPinEditor != null) {
            btnPinEditor.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                currentIsPinned = !currentIsPinned;
                updatePinIconState();
                markAsUnsaved();
                Toast.makeText(this, currentIsPinned ? "📌 Note pinned to top" : "Note unpinned", Toast.LENGTH_SHORT).show();
            });
        }

        // 3. Category Selector Chip
        if (btnCategoryChip != null) {
            btnCategoryChip.setOnClickListener(v -> {
                int idx = 0;
                for (int i = 0; i < CATEGORIES.length; i++) {
                    if (CATEGORIES[i].equalsIgnoreCase(currentCategory)) {
                        idx = i;
                        break;
                    }
                }
                currentCategory = CATEGORIES[(idx + 1) % CATEGORIES.length];
                if (tvCategoryChip != null) tvCategoryChip.setText(currentCategory);
                markAsUnsaved();
            });
        }

        // 4. Quick Date Stamp Chip
        if (btnQuickDateChip != null) {
            btnQuickDateChip.setOnClickListener(v -> {
                insertCurrentTimestamp();
            });
        }

        // 5. Find & Replace Bar
        if (btnFindInNote != null && layoutFindReplace != null) {
            btnFindInNote.setOnClickListener(v -> {
                if (layoutFindReplace.getVisibility() == View.VISIBLE) {
                    layoutFindReplace.setVisibility(View.GONE);
                } else {
                    layoutFindReplace.setVisibility(View.VISIBLE);
                    if (etFindQuery != null) etFindQuery.requestFocus();
                }
            });
        }

        View btnFindNext = findViewById(R.id.btn_find_next);
        if (btnFindNext != null) {
            btnFindNext.setOnClickListener(v -> {
                if (etFindQuery == null) return;
                String q = etFindQuery.getText().toString().trim();
                if (!q.isEmpty()) {
                    String safeQ = q.replace("'", "\\'");
                    execJs("findInNote('" + safeQ + "');");
                }
            });
        }

        View btnReplaceAll = findViewById(R.id.btn_replace_all);
        if (btnReplaceAll != null) {
            btnReplaceAll.setOnClickListener(v -> {
                if (etFindQuery == null || etReplaceQuery == null) return;
                String findStr = etFindQuery.getText().toString();
                String repStr = etReplaceQuery.getText().toString();
                if (!findStr.isEmpty()) {
                    String findB64 = Base64.encodeToString(findStr.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    String repB64 = Base64.encodeToString(repStr.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    execJs("replaceAllInNote('" + findB64 + "', '" + repB64 + "');");
                    markAsUnsaved();
                    Toast.makeText(this, "Replaced occurrences of \"" + findStr + "\"", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnCloseFind = findViewById(R.id.btn_close_find);
        if (btnCloseFind != null && layoutFindReplace != null) {
            btnCloseFind.setOnClickListener(v -> layoutFindReplace.setVisibility(View.GONE));
        }

        // 6. Detailed Analytics Dialog on tapping word count
        View layoutStatusAnalytics = findViewById(R.id.layout_status_analytics);
        if (layoutStatusAnalytics != null) {
            layoutStatusAnalytics.setOnClickListener(v -> showNoteAnalyticsDialog());
        }
    }

    private void startFocusTimer() {
        if (focusCountDownTimer != null) focusCountDownTimer.cancel();
        isFocusTimerRunning = true;
        focusCountDownTimer = new CountDownTimer(focusTimeRemainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                focusTimeRemainingMs = millisUntilFinished;
                updateFocusTimerText();
            }

            @Override
            public void onFinish() {
                isFocusTimerRunning = false;
                focusTimeRemainingMs = focusInitialDurationMs;
                updateFocusTimerText();
                if (btnFocusTimer != null) {
                    btnFocusTimer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                Toast.makeText(EditorActivity.this, "🎉 Focus session complete! Great work.", Toast.LENGTH_LONG).show();
            }
        }.start();
    }

    private void pauseFocusTimer() {
        if (focusCountDownTimer != null) {
            focusCountDownTimer.cancel();
        }
        isFocusTimerRunning = false;
    }

    private void updateFocusTimerText() {
        if (tvFocusTimer == null) return;
        long totalSec = focusTimeRemainingMs / 1000;
        long mins = totalSec / 60;
        long secs = totalSec % 60;
        tvFocusTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
    }

    private void updatePinIconState() {
        if (btnPinEditor == null) return;
        btnPinEditor.setColorFilter(Color.parseColor(currentIsPinned ? "#0F172A" : "#94A3B8"));
    }

    private void showNoteAnalyticsDialog() {
        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        CardView card = new CardView(this);
        card.setRadius(dp(24));
        card.setCardBackgroundColor(Color.WHITE);
        card.setCardElevation(dp(8));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(22), dp(20), dp(22), dp(22));

        TextView title = new TextView(this);
        title.setText("📊 Note Insights & Quick Actions");
        title.setTextSize(19f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        inner.addView(title);

        String text = currentPlainText != null ? currentPlainText.trim() : "";
        int words = text.isEmpty() ? 0 : text.split("\\s+").length;
        int chars = text.length();
        int lines = text.isEmpty() ? 0 : text.split("\n").length;
        int readMins = Math.max(1, (int) Math.ceil(words / 200.0));

        TextView statsView = new TextView(this);
        statsView.setText(
                "• Words: " + words + "\n" +
                "• Characters: " + chars + "\n" +
                "• Lines / Paragraphs: " + lines + "\n" +
                "• Est. Reading Time: ~" + readMins + " min\n" +
                "• Category: " + currentCategory
        );
        statsView.setTextSize(14.5f);
        statsView.setLineSpacing(0, 1.35f);
        statsView.setTextColor(Color.parseColor("#334155"));
        statsView.setPadding(0, dp(12), 0, dp(18));
        inner.addView(statsView);

        TextView btnCopyAll = new TextView(this);
        btnCopyAll.setText("📋 Copy Note to Clipboard");
        btnCopyAll.setTextSize(14.5f);
        btnCopyAll.setTypeface(null, Typeface.BOLD);
        btnCopyAll.setTextColor(Color.WHITE);
        btnCopyAll.setGravity(Gravity.CENTER);
        btnCopyAll.setBackgroundResource(R.drawable.bg_chip_active);
        btnCopyAll.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnCopyAll.setOnClickListener(v -> {
            copyNoteToClipboard();
            dialog.dismiss();
        });
        inner.addView(btnCopyAll);

        card.addView(inner);
        root.addView(card);
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
        }
        dialog.show();
    }

    private void showSidebar() {
        if (sidebarColorMenu != null && sidebarColorMenu.getVisibility() != View.VISIBLE) {
            hideKeyboard();
            sidebarColorMenu.setTranslationX(sidebarWidthPx);
            sidebarColorMenu.setVisibility(View.VISIBLE);
            sidebarColorMenu.animate()
                    .translationX(0)
                    .setDuration(250)
                    .start();

            if (colorPaletteContainer != null) colorPaletteContainer.setVisibility(View.GONE);
            if (layoutAiToolsContainer != null) {
                layoutAiToolsContainer.setVisibility(View.GONE);
                btnCloseAi.setVisibility(View.GONE);
                btnAiAction.setVisibility(View.VISIBLE);
            }
        }
    }

    private void hideSidebar() {
        if (sidebarColorMenu != null && sidebarColorMenu.getVisibility() == View.VISIBLE) {
            sidebarColorMenu.animate()
                    .translationX(sidebarWidthPx)
                    .setDuration(250)
                    .withEndAction(() -> sidebarColorMenu.setVisibility(View.GONE))
                    .start();
        }
    }

    private void setupKeyboardListener() {
        View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            boolean isKeyboardOpen = keypadHeight > screenHeight * 0.15;

            if (isKeyboardOpen) {
                if (bottomToolsContainer != null && bottomToolsContainer.getVisibility() != View.VISIBLE) {
                    bottomToolsContainer.setVisibility(View.VISIBLE);
                }

                if (!wasKeyboardOpen && sidebarColorMenu != null && sidebarColorMenu.getVisibility() == View.VISIBLE) {
                    sidebarColorMenu.setVisibility(View.GONE);
                }
            }
            wasKeyboardOpen = isKeyboardOpen;
        });
    }

    private static class ColorOption {
        int viewId; String hex; String shape;
        ColorOption(int viewId, String hex, String shape) {
            this.viewId = viewId; this.hex = hex; this.shape = shape;
        }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void setupColorSidebar() {
        colorOptionsList = new ArrayList<>();
        colorOptionsList.add(new ColorOption(R.id.color_white, "#FFFFFF", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_ef9a9a, "#EF9A9A", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_e57373, "#E57373", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_b71c1c, "#B71C1C", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_f06292, "#F06292", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_ce93d8, "#CE93D8", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_e1bee7, "#E1BEE7", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_b39ddb, "#B39DDB", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_d1c4e9, "#D1C4E9", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_9fa8da, "#9FA8DA", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_c5cae9, "#C5CAE9", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_b2ebf2, "#B2EBF2", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_80cbc4, "#80CBC4", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_b2dfdb, "#B2DFDB", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_a5d6a7, "#A5D6A7", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_c8e6c9, "#C8E6C9", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_f9fbe7, "#F9FBE7", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_ffe0b2, "#FFE0B2", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_d7ccc8, "#D7CCC8", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_bcaaa4, "#BCAAA4", "CIRCLE"));
        colorOptionsList.add(new ColorOption(R.id.color_f0f4c3, "#F0F4C3", "CIRCLE"));

        for (ColorOption option : colorOptionsList) {
            View itemView = findViewById(option.viewId);
            if (itemView == null) continue;

            CardView cardShape = itemView.findViewById(R.id.color_shape);
            cardShape.setCardBackgroundColor(Color.parseColor(option.hex));

            cardShape.setOnClickListener(v -> {
                currentPageColor = option.hex;
                applyPageColor(currentPageColor);
                markAsUnsaved();
                hideKeyboard();
            });
        }
    }

    private void applyPageColor(String hexColor) {
        int parsedColor;
        try {
            parsedColor = Color.parseColor(hexColor);
        } catch (Exception e) {
            parsedColor = Color.WHITE;
            hexColor = "#FFFFFF";
        }
        findViewById(R.id.root_layout).setBackgroundColor(parsedColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(parsedColor);

            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (isColorLight(parsedColor)) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }

        for (ColorOption option : colorOptionsList) {
            View itemView = findViewById(option.viewId);
            if (itemView != null) {
                FrameLayout checkContainer = itemView.findViewById(R.id.check_container);
                if (option.hex.equalsIgnoreCase(hexColor)) {
                    checkContainer.setVisibility(View.VISIBLE);
                } else {
                    checkContainer.setVisibility(View.GONE);
                }
            }
        }
    }

    private boolean isColorLight(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness < 0.5;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings settings = webViewEditor.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webViewEditor.setBackgroundColor(Color.TRANSPARENT);
        webViewEditor.setWebChromeClient(new WebChromeClient());
        webViewEditor.addJavascriptInterface(new WebAppInterface(), "Android");

        webViewEditor.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                isWebViewLoaded = true;
                if (!pendingHtmlToLoad.isEmpty()) {
                    loadHtmlIntoEditor(pendingHtmlToLoad);
                    pendingHtmlToLoad = "";
                }
            }
        });

        webViewEditor.loadUrl("file:///android_asset/rich_editor.html");
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void onTextChanged(final String html, final String text) {
            runOnUiThread(() -> {
                hasUnsavedChanges = true;
                updateEditorStatus("Unsaved changes");

                if (html != null) {
                    currentEditorHtml = html;
                }
                currentPlainText = text != null ? text : "";

                int count = 0;
                if (text != null && !text.trim().isEmpty()) {
                    count = text.trim().split("\\s+").length;
                }
                int readMin = Math.max(1, (int) Math.ceil(count / 200.0));

                if (tvWordCount != null) {
                    tvWordCount.setText(count + (count == 1 ? " word" : " words") + " • " + readMin + "m read");
                }
            });
        }

        @JavascriptInterface
        public void saveNoteAndClose(final String html) {
            runOnUiThread(() -> {
                if (html != null) {
                    currentEditorHtml = html;
                }
                saveNote();
                finish();
            });
        }
    }

    private void execJs(String jsCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webViewEditor.evaluateJavascript("javascript:" + jsCode, null);
        } else {
            webViewEditor.loadUrl("javascript:" + jsCode);
        }
    }

    private void loadHtmlIntoEditor(String html) {
        if (html == null) html = "";
        try {
            String encodedBase64 = Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            execJs("setHtmlFromBase64('" + encodedBase64 + "');");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadExistingNote() {
        if (getIntent() == null) return;
        noteId = getIntent().getIntExtra("note_id", -1);
        String title = getIntent().getStringExtra("note_title");
        String content = getIntent().getStringExtra("note_content");
        currentPageColor = getIntent().getStringExtra("note_color");
        currentIsPinned = getIntent().getBooleanExtra("note_pinned", false);
        String cat = getIntent().getStringExtra("note_category");
        boolean isTemplateNew = getIntent().getBooleanExtra("is_template_new", false);

        if (cat != null && !cat.isEmpty()) {
            currentCategory = cat;
        }
        if (tvCategoryChip != null) {
            tvCategoryChip.setText(currentCategory);
        }
        updatePinIconState();

        if (title != null) etTitle.setText(title);

        if (content != null) {
            currentEditorHtml = content;
            if (isWebViewLoaded) {
                loadHtmlIntoEditor(content);
            } else {
                pendingHtmlToLoad = content;
            }
        }

        if (currentPageColor == null || currentPageColor.isEmpty()) {
            currentPageColor = "#FFFFFF";
        }
        applyPageColor(currentPageColor);
        hasUnsavedChanges = isTemplateNew;
    }

    private void saveAndExit() {
        if (isWebViewLoaded) {
            execJs("if(window.Android) window.Android.saveNoteAndClose(document.querySelector('[contenteditable=true]') ? document.querySelector('[contenteditable=true]').innerHTML : document.body.innerHTML);");
        } else {
            saveNote();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        saveAndExit();
    }

    private void setupToolbar() {
        findViewById(R.id.btn_back).setOnClickListener(v -> saveAndExit());

        btnFillColor = findViewById(R.id.btn_fill_color);
        btnFillColor.setOnClickListener(v -> {
            if (sidebarColorMenu != null) {
                if (sidebarColorMenu.getVisibility() == View.VISIBLE) {
                    hideSidebar();
                } else {
                    showSidebar();
                }
            }
        });

        findViewById(R.id.btn_undo).setOnClickListener(v -> { execJs("formatDoc('undo')"); markAsUnsaved(); });
        findViewById(R.id.btn_redo).setOnClickListener(v -> { execJs("formatDoc('redo')"); markAsUnsaved(); });
        findViewById(R.id.btn_bold).setOnClickListener(v -> { execJs("formatDoc('bold')"); markAsUnsaved(); });
        findViewById(R.id.btn_italic).setOnClickListener(v -> { execJs("formatDoc('italic')"); markAsUnsaved(); });
        findViewById(R.id.btn_underline).setOnClickListener(v -> { execJs("formatDoc('underline')"); markAsUnsaved(); });
        findViewById(R.id.btn_strike).setOnClickListener(v -> { execJs("formatDoc('strikeThrough')"); markAsUnsaved(); });
        findViewById(R.id.btn_h1).setOnClickListener(v -> { execJs("toggleHeading('H1')"); markAsUnsaved(); });
        findViewById(R.id.btn_h2).setOnClickListener(v -> { execJs("toggleHeading('H2')"); markAsUnsaved(); });
        findViewById(R.id.btn_h3).setOnClickListener(v -> { execJs("toggleHeading('H3')"); markAsUnsaved(); });
        findViewById(R.id.btn_h4).setOnClickListener(v -> { execJs("toggleHeading('H4')"); markAsUnsaved(); });
        findViewById(R.id.btn_h5).setOnClickListener(v -> { execJs("toggleHeading('H5')"); markAsUnsaved(); });
        findViewById(R.id.btn_h6).setOnClickListener(v -> { execJs("toggleHeading('H6')"); markAsUnsaved(); });
        findViewById(R.id.btn_number).setOnClickListener(v -> { execJs("formatDoc('insertOrderedList')"); markAsUnsaved(); });
        findViewById(R.id.btn_bullet).setOnClickListener(v -> { execJs("formatDoc('insertUnorderedList')"); markAsUnsaved(); });
        findViewById(R.id.btn_check).setOnClickListener(v -> { execJs("insertCheckbox()"); markAsUnsaved(); });
        findViewById(R.id.btn_link).setOnClickListener(v -> insertLink());
        findViewById(R.id.btn_image).setOnClickListener(v -> openGallery());

        // New Toolbar Productivity Actions
        View btnVoice = findViewById(R.id.btn_voice_input);
        if (btnVoice != null) {
            btnVoice.setOnClickListener(v -> startVoiceDictation());
        }

        View btnInsertDate = findViewById(R.id.btn_insert_date);
        if (btnInsertDate != null) {
            btnInsertDate.setOnClickListener(v -> insertCurrentTimestamp());
        }

        View btnInsertDivider = findViewById(R.id.btn_insert_divider);
        if (btnInsertDivider != null) {
            btnInsertDivider.setOnClickListener(v -> {
                execJs("insertDivider()");
                markAsUnsaved();
            });
        }

        View btnCopyNote = findViewById(R.id.btn_copy_note);
        if (btnCopyNote != null) {
            btnCopyNote.setOnClickListener(v -> copyNoteToClipboard());
        }

        View btnHighlight = findViewById(R.id.btn_highlight);
        btnHighlight.setOnClickListener(v -> { execJs("toggleHighlight('#FFE58A')"); markAsUnsaved(); });
        btnHighlight.setOnLongClickListener(v -> {
            if (colorPaletteContainer != null) colorPaletteContainer.setVisibility(colorPaletteContainer.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            if (layoutAiToolsContainer != null) {
                layoutAiToolsContainer.setVisibility(View.GONE);
                btnCloseAi.setVisibility(View.GONE);
                btnAiAction.setVisibility(View.VISIBLE);
            }
            hideSidebar();
            return true;
        });

        String[] hexColors = {"#FFE58A", "#A5FF8A", "#8AE8FF", "#FF8A8A", "#E58AFF", "#DDDDDD"};
        int[] ids = {R.id.color_yellow, R.id.color_green, R.id.color_cyan, R.id.color_red, R.id.color_magenta, R.id.color_grey};
        for (int i = 0; i < ids.length; i++) {
            final String hex = hexColors[i];
            View v = findViewById(ids[i]);
            if (v != null) v.setOnClickListener(view -> {
                execJs("toggleHighlight('" + hex + "')");
                markAsUnsaved();
                colorPaletteContainer.setVisibility(View.GONE);
            });
        }
    }

    private void insertCurrentTimestamp() {
        String stamp = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
        execJs("insertTimestampPill('" + stamp + "')");
        markAsUnsaved();
    }

    private void copyNoteToClipboard() {
        String title = etTitle.getText().toString().trim();
        String body = currentPlainText != null ? currentPlainText.trim() : "";
        if (body.isEmpty() && currentEditorHtml != null) {
            body = Html.fromHtml(currentEditorHtml).toString().trim();
        }
        String combined = (title.isEmpty() ? "" : title + "\n\n") + body;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("NotNote", combined));
            Toast.makeText(this, "📋 Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoiceDictation() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to dictate into your note...");
            startActivityForResult(intent, VOICE_INPUT_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Voice dictation is not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void insertLink() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_insert_link);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setGravity(Gravity.BOTTOM);

        EditText etLinkInput = dialog.findViewById(R.id.et_link_input);
        View btnCancel = dialog.findViewById(R.id.btn_cancel_link);
        View btnInsert = dialog.findViewById(R.id.btn_insert_link);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnInsert.setOnClickListener(v -> {
            String url = etLinkInput.getText().toString().trim();
            if (url.isEmpty()) return;
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;

            execJs("formatDoc('createLink', '" + url + "')");
            markAsUnsaved();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE_REQUEST);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(fallback, PICK_IMAGE_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            insertImage(data.getData());
        } else if (requestCode == VOICE_INPUT_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String spokenText = matches.get(0);
                String encoded = Base64.encodeToString(spokenText.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                execJs("insertDictatedText('" + encoded + "')");
                markAsUnsaved();
            }
        }
    }

    private String copyImageToInternalStorage(Uri uri) throws Exception {
        File dir = new File(getFilesDir(), "note_images");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create image directory");
        File target = new File(dir, "img_" + System.currentTimeMillis() + ".png");
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("Unable to read image");
            out = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.flush();
            return target.getAbsolutePath();
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    private void insertImage(Uri uri) {
        try {
            String path = copyImageToInternalStorage(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            int width = options.outWidth;
            int height = options.outHeight;

            int maxWidth = getResources().getDisplayMetrics().widthPixels - dp(48);
            if (width > maxWidth) {
                float ratio = maxWidth / (float) width;
                width = maxWidth;
                height = Math.round(height * ratio);
            }

            execJs("insertImage('" + path + "', " + width + ", " + height + ")");
            markAsUnsaved();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to add image", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupAiToolbar() {
        btnAiAction = findViewById(R.id.btn_ai_action);
        btnCloseAi = findViewById(R.id.btn_close_ai);
        btnAiSend = findViewById(R.id.btn_ai_send);
        etAiInput = findViewById(R.id.et_ai_input);

        btnAiSend.setEnabled(false);
        btnAiSend.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
        btnAiSend.setColorFilter(Color.parseColor("#888888"));

        // Populate One-Tap AI & Smart Productivity Chips
        if (aiQuickChipsContainer != null) {
            aiQuickChipsContainer.removeAllViews();
            String[][] quickPrompts = new String[][]{
                    {"✨ Summarize", "Summarize this note into concise bullet points"},
                    {"☑️ Extract Tasks", "Convert key action items in this note into a checklist of tasks"},
                    {"✅ Fix Grammar", "Fix grammar, spelling, and polish the writing clearly"},
                    {"📝 Continue Writing", "Continue writing and expand on the ideas in this note naturally"},
                    {"🎯 Key Takeaways", "Highlight the top 3 key takeaways from this note"},
                    {"🧹 Structure & Clean", "Organize and structure this note with clean headings and bullets"}
            };

            for (String[] item : quickPrompts) {
                String label = item[0];
                String promptCommand = item[1];

                TextView chip = new TextView(this);
                chip.setText(label);
                chip.setTextSize(12.5f);
                chip.setTypeface(null, Typeface.BOLD);
                chip.setTextColor(Color.parseColor("#1E293B"));
                chip.setBackgroundResource(R.drawable.bg_chip_inactive);
                chip.setPadding(dp(12), dp(6), dp(12), dp(6));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.setMargins(0, 0, dp(8), 0);
                chip.setLayoutParams(lp);

                chip.setOnClickListener(v -> {
                    executeAiCommand(promptCommand);
                    hideKeyboard();
                });

                aiQuickChipsContainer.addView(chip);
            }
        }

        etAiInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    btnAiSend.setEnabled(false);
                    btnAiSend.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
                    btnAiSend.setColorFilter(Color.parseColor("#888888"));
                } else {
                    btnAiSend.setEnabled(true);
                    btnAiSend.setBackgroundTintList(ColorStateList.valueOf(Color.BLACK));
                    btnAiSend.setColorFilter(Color.WHITE);
                }
            }
        });

        btnAiAction.setOnClickListener(v -> {
            layoutAiToolsContainer.setVisibility(View.VISIBLE);
            btnAiAction.setVisibility(View.GONE);
            btnCloseAi.setVisibility(View.VISIBLE);
            if (colorPaletteContainer != null) colorPaletteContainer.setVisibility(View.GONE);
            hideSidebar();
        });

        btnCloseAi.setOnClickListener(v -> {
            layoutAiToolsContainer.setVisibility(View.GONE);
            btnCloseAi.setVisibility(View.GONE);
            btnAiAction.setVisibility(View.VISIBLE);
        });

        btnAiSend.setOnClickListener(v -> {
            String customPrompt = etAiInput.getText().toString().trim();
            if (!customPrompt.isEmpty()) {
                executeAiCommand(customPrompt);
                etAiInput.setText("");
                hideKeyboard();
            }
        });
    }

    private void executeAiCommand(String command) {
        String title = etTitle.getText().toString().trim();
        String plainContent = Html.fromHtml(currentEditorHtml).toString().trim();
        String contextText = "Title: " + title + "\n\nContent:\n" + plainContent;

        if (plainContent.isEmpty()) {
            contextText = "[The note is currently empty. User is asking you to write something new.]";
        }

        final String finalContextText = contextText;
        String provider = aiPrefs.getString("selected_provider", "");

        if (provider.isEmpty()) {
            // Instant Smart Local Assistant fallback when API key isn't configured yet, plus friendly hint
            String smartLocalResponse = buildSmartLocalFallback(title, plainContent, command);
            editorHelper.processAiResponse(smartLocalResponse);
            Toast.makeText(this, "✨ Smart Local Assistant applied (Add Gemini/Cloudflare key in Settings for cloud AI)", Toast.LENGTH_LONG).show();
            if (layoutAiToolsContainer != null) layoutAiToolsContainer.setVisibility(View.GONE);
            if (btnCloseAi != null) {
                btnCloseAi.setVisibility(View.GONE);
                btnAiAction.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (tvEditorStatus != null) tvEditorStatus.setText("AI is thinking...");
        if (layoutAiToolsContainer != null) layoutAiToolsContainer.setVisibility(View.GONE);
        if (btnCloseAi != null) {
            btnCloseAi.setVisibility(View.GONE);
            btnAiAction.setVisibility(View.VISIBLE);
        }

        new Thread(() -> {
            try {
                String response;
                if (provider.equals("gemini")) {
                    String encryptedKey = aiPrefs.getString("encrypted_api_key_gemini", "");
                    String apiKey = SecurityHelper.decrypt(encryptedKey);
                    String model = aiPrefs.getString("selected_model_gemini", "gemini-1.5-flash");
                    response = AiApiManager.callGeminiForEditor(apiKey, model, finalContextText, command);
                } else {
                    String encryptedKey = aiPrefs.getString("encrypted_api_key_cf", "");
                    String apiKey = SecurityHelper.decrypt(encryptedKey);
                    String accountId = aiPrefs.getString("cf_account_id", "");
                    String model = aiPrefs.getString("selected_model_cf", "@cf/meta/llama-3.2-3b-instruct");
                    response = AiApiManager.callCloudflareForEditor(apiKey, accountId, model, finalContextText, command);
                }

                runOnUiThread(() -> {
                    try {
                        editorHelper.processAiResponse(response);
                    } catch (Exception ex) {
                        Toast.makeText(EditorActivity.this, "UI Processing Error", Toast.LENGTH_SHORT).show();
                    } finally {
                        if (tvEditorStatus != null) tvEditorStatus.setText("Editing Note");
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (tvEditorStatus != null) tvEditorStatus.setText("Editing Note");
                    Toast.makeText(EditorActivity.this, "AI Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String buildSmartLocalFallback(String title, String plainContent, String command) {
        String lowerCmd = command.toLowerCase(Locale.ROOT);
        String[] rawLines = plainContent.isEmpty() ? new String[0] : plainContent.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        sb.append("[TITLE] ").append(title.isEmpty() ? "Smart Note" : "NO_CHANGE").append("\n");
        sb.append("[CONTENT] \n\n");

        if (lowerCmd.contains("task") || lowerCmd.contains("checklist") || lowerCmd.contains("to-do") || lowerCmd.contains("todo")) {
            sb.append("### ☑️ Action Items\n");
            if (rawLines.length == 0) {
                sb.append("☐ Define primary objective\n");
                sb.append("☐ Complete first milestone\n");
                sb.append("☐ Review and finalize\n");
            } else {
                for (String line : rawLines) {
                    String clean = line.replaceAll("^[•\\-*☐☑\\d.]+\\s*", "").trim();
                    if (!clean.isEmpty()) {
                        sb.append("☐ ").append(clean).append("\n");
                    }
                }
            }
        } else if (lowerCmd.contains("summar") || lowerCmd.contains("key")) {
            sb.append("### ✨ Quick Summary\n");
            if (rawLines.length == 0) {
                sb.append("- **Core Topic:** ").append(title.isEmpty() ? "New Note" : title).append("\n");
                sb.append("- **Next Step:** Add key details and action items.\n");
            } else {
                int count = 0;
                for (String line : rawLines) {
                    String clean = line.trim();
                    if (!clean.isEmpty()) {
                        sb.append("- ").append(clean).append("\n");
                        count++;
                        if (count >= 4) break;
                    }
                }
            }
        } else {
            sb.append("### 💡 Smart Outline: ").append(command).append("\n");
            sb.append("☐ Key point 1\n");
            sb.append("☐ Key point 2\n");
            sb.append("☐ Follow-up item\n");
        }
        return sb.toString();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }

    private void updateEditorStatus(String status) {
        if (tvEditorStatus != null) tvEditorStatus.setText(status);
    }

    public void markAsUnsaved() {
        runOnUiThread(() -> {
            hasUnsavedChanges = true;
            updateEditorStatus("Unsaved changes");
        });
    }

    private void saveNote() {
        if (!hasUnsavedChanges && noteId != -1) return;

        String title = etTitle.getText().toString().trim();
        String html = currentEditorHtml != null ? currentEditorHtml : "";

        if (title.isEmpty() && (html.isEmpty() || html.equals("<br>"))) return;

        if (noteId == -1) {
            long id = dbHelper.insertNote(title, html, currentPageColor, currentIsPinned, currentCategory);
            if (id > 0) {
                noteId = (int) id;
                hasUnsavedChanges = false;
            }
        } else {
            dbHelper.updateNote(noteId, title, html, currentPageColor, currentIsPinned, currentCategory);
            hasUnsavedChanges = false;
        }
        updateEditorStatus("Saved");
    }

    @Override
    protected void onPause() {
        saveNote();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (focusCountDownTimer != null) {
            focusCountDownTimer.cancel();
        }
        super.onDestroy();
    }
}
