package com.santoshdev.notnote;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements NotesAdapter.NoteActionListener {

    private RecyclerView recyclerNotes;
    private FrameLayout btnAddNote, btnQuickTemplates, btnQuickVault;
    private View btnSettings, btnSortNotes;
    private TextView tvWorkspaceStats, tvSortLabel;
    private EditText etSearch;
    private ImageView btnViewList, btnViewGrid, btnClearSearch;
    private HorizontalScrollView chipScrollContainer;
    private LinearLayout chipGroupContainer;

    private LinearLayout searchCard, toggleContainer, emptyStateContainer, emptySearchContainer;

    private NotesAdapter adapter;
    private List<Note> allNotes;
    private DatabaseHelper dbHelper;
    private boolean isGridView = false;

    private SharedPreferences prefs;

    private String currentFilterChip = "All";
    private int currentSortMode = 0; // 0 = Newest, 1 = Oldest, 2 = Title A-Z, 3 = Color
    private static final String[] SORT_LABELS = {"Newest", "Oldest", "Title A-Z", "Color"};
    private static final String[] FILTER_CHIPS = {
            "All", "📌 Pinned", "☑️ Tasks", "🎨 Palettes", "💼 Work", "🏠 Personal", "💡 Ideas", "📚 Study"
    };

    private static final String SVG_UNCHECK_SQUARE = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100%\" height=\"100%\" fill=\"currentColor\" viewBox=\"0 0 256 256\"><path d=\"M208,48V208H48V48H208m0-16H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V48a16,16,0,0,0-16-16Z\"></path></svg>";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        dbHelper = new DatabaseHelper(this);
        allNotes = new ArrayList<>();

        prefs = getSharedPreferences("NotNotePrefs", MODE_PRIVATE);
        isGridView = prefs.getBoolean("isGridView", false);
        currentSortMode = prefs.getInt("sortMode", 0);

        recyclerNotes = findViewById(R.id.recycler_notes);
        btnAddNote = findViewById(R.id.btn_add_note);
        btnQuickTemplates = findViewById(R.id.btn_quick_templates);
        btnQuickVault = findViewById(R.id.btn_quick_vault);
        btnSettings = findViewById(R.id.btn_settings);
        btnSortNotes = findViewById(R.id.btn_sort_notes);
        tvWorkspaceStats = findViewById(R.id.tv_workspace_stats);
        tvSortLabel = findViewById(R.id.tv_sort_label);
        etSearch = findViewById(R.id.et_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        btnViewList = findViewById(R.id.btn_view_list);
        btnViewGrid = findViewById(R.id.btn_view_grid);

        chipScrollContainer = findViewById(R.id.chip_scroll_container);
        chipGroupContainer = findViewById(R.id.chip_group_container);

        searchCard = findViewById(R.id.search_card);
        toggleContainer = findViewById(R.id.toggle_container);

        emptyStateContainer = findViewById(R.id.empty_state_container);
        emptySearchContainer = findViewById(R.id.empty_search_container);

        if (tvSortLabel != null) {
            tvSortLabel.setText(SORT_LABELS[currentSortMode % SORT_LABELS.length]);
        }

        adapter = new NotesAdapter(this, this);
        adapter.setHasStableIds(true);
        recyclerNotes.setAdapter(adapter);

        setupFilterChips();
        setupRecyclerViewLayout();
        setupClickListeners();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setupRecyclerViewLayout();
    }

    private int getResponsiveSpanCount() {
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        if (widthDp >= 840) return 4;
        if (widthDp >= 560) return 3;
        return 2;
    }

    private void setupFilterChips() {
        if (chipGroupContainer == null) return;
        chipGroupContainer.removeAllViews();

        for (String chipLabel : FILTER_CHIPS) {
            TextView chip = new TextView(this);
            chip.setText(chipLabel);
            chip.setTextSize(13f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dpToPx(14), dpToPx(7), dpToPx(14), dpToPx(7));

            boolean isSelected = chipLabel.equals(currentFilterChip);
            chip.setBackgroundResource(isSelected ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive);
            chip.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#334155"));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dpToPx(34)
            );
            lp.setMargins(0, 0, dpToPx(8), 0);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                currentFilterChip = chipLabel;
                setupFilterChips();
                filterNotes(etSearch.getText().toString());
            });

            chipGroupContainer.addView(chip);
        }
    }

    private void setupRecyclerViewLayout() {
        if (isGridView) {
            int spans = getResponsiveSpanCount();
            StaggeredGridLayoutManager staggeredManager = new StaggeredGridLayoutManager(spans, StaggeredGridLayoutManager.VERTICAL);
            staggeredManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
            recyclerNotes.setLayoutManager(staggeredManager);

            btnViewGrid.setBackgroundResource(R.drawable.bg_toggle_selection);
            btnViewList.setBackgroundResource(0);
        } else {
            LinearLayoutManager linearManager = new LinearLayoutManager(this);
            recyclerNotes.setLayoutManager(linearManager);

            btnViewList.setBackgroundResource(R.drawable.bg_toggle_selection);
            btnViewGrid.setBackgroundResource(0);
        }

        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setAddDuration(180);
        animator.setRemoveDuration(180);
        animator.setMoveDuration(200);
        animator.setChangeDuration(160);
        recyclerNotes.setItemAnimator(animator);

        if (adapter != null) {
            adapter.setGridView(isGridView);
        }
    }

    private void setupClickListeners() {
        btnAddNote.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, EditorActivity.class));
        });

        View btnEmptyCreate = findViewById(R.id.btn_empty_create);
        if (btnEmptyCreate != null) {
            btnEmptyCreate.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, EditorActivity.class));
            });
        }

        View btnEmptyTemplate = findViewById(R.id.btn_empty_template);
        if (btnEmptyTemplate != null) {
            btnEmptyTemplate.setOnClickListener(v -> openTemplatesDialog());
        }

        if (btnQuickTemplates != null) {
            btnQuickTemplates.setOnClickListener(v -> openTemplatesDialog());
        }

        if (btnQuickVault != null) {
            btnQuickVault.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, PrivateLockActivity.class));
            });
        }

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        if (btnSortNotes != null) {
            btnSortNotes.setOnClickListener(v -> {
                currentSortMode = (currentSortMode + 1) % SORT_LABELS.length;
                prefs.edit().putInt("sortMode", currentSortMode).apply();
                if (tvSortLabel != null) {
                    tvSortLabel.setText(SORT_LABELS[currentSortMode]);
                }
                filterNotes(etSearch.getText().toString());
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                etSearch.setText("");
            });
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (btnClearSearch != null) {
                    btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                }
                filterNotes(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnViewList.setOnClickListener(v -> {
            if (isGridView) {
                isGridView = false;
                prefs.edit().putBoolean("isGridView", false).apply();
                setupRecyclerViewLayout();
            }
        });

        btnViewGrid.setOnClickListener(v -> {
            if (!isGridView) {
                isGridView = true;
                prefs.edit().putBoolean("isGridView", true).apply();
                setupRecyclerViewLayout();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.clearMetadataCache();
        }
        reloadNotesAndRefresh();
    }

    private void reloadNotesAndRefresh() {
        allNotes.clear();
        allNotes.addAll(dbHelper.getAllNotes());
        updateWorkspaceStats();
        filterNotes(etSearch.getText().toString());
    }

    private void updateWorkspaceStats() {
        if (tvWorkspaceStats == null) return;
        if (allNotes.isEmpty()) {
            tvWorkspaceStats.setText("Ready to capture ideas");
            return;
        }
        int pinnedCount = 0;
        int openTasks = 0;
        for (Note n : allNotes) {
            if (n.isPinned()) pinnedCount++;
            NotesAdapter.NoteMetadata meta = adapter.parseNoteMetadata(n);
            for (String t : meta.tasks) {
                if (t.startsWith("0")) openTasks++;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(allNotes.size()).append(allNotes.size() == 1 ? " note" : " notes");
        if (pinnedCount > 0) {
            sb.append(" • ").append(pinnedCount).append(" pinned");
        }
        if (openTasks > 0) {
            sb.append(" • ").append(openTasks).append(openTasks == 1 ? " task left" : " tasks left");
        }
        tvWorkspaceStats.setText(sb.toString());
    }

    public void filterNotes(String query) {
        List<Note> filteredNotes = new ArrayList<>();
        String lowerCaseQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";

        for (Note note : allNotes) {
            NotesAdapter.NoteMetadata meta = adapter.parseNoteMetadata(note);

            // Check chip filter first
            if ("📌 Pinned".equals(currentFilterChip) && !note.isPinned()) continue;
            if ("☑️ Tasks".equals(currentFilterChip) && meta.tasks.isEmpty()) continue;
            if ("🎨 Palettes".equals(currentFilterChip) && meta.colors.isEmpty()) continue;
            if ("💼 Work".equals(currentFilterChip) && !"Work".equalsIgnoreCase(note.getCategory())) continue;
            if ("🏠 Personal".equals(currentFilterChip) && !"Personal".equalsIgnoreCase(note.getCategory())) continue;
            if ("💡 Ideas".equals(currentFilterChip) && !"Ideas".equalsIgnoreCase(note.getCategory())) continue;
            if ("📚 Study".equals(currentFilterChip) && !"Study".equalsIgnoreCase(note.getCategory())) continue;

            // Check text search query
            if (!lowerCaseQuery.isEmpty()) {
                String content = note.getContent() != null ? note.getContent() : "";
                String cleanContent;
                if (Build.VERSION.SDK_INT >= 24) {
                    cleanContent = Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString().toLowerCase(Locale.ROOT);
                } else {
                    cleanContent = Html.fromHtml(content).toString().toLowerCase(Locale.ROOT);
                }
                String titleLower = note.getTitle().toLowerCase(Locale.ROOT);
                String catLower = note.getCategory().toLowerCase(Locale.ROOT);

                if (!titleLower.contains(lowerCaseQuery) && !cleanContent.contains(lowerCaseQuery) && !catLower.contains(lowerCaseQuery)) {
                    continue;
                }
            }

            filteredNotes.add(note);
        }

        // Apply sorting while keeping pinned notes on top
        Collections.sort(filteredNotes, (a, b) -> {
            if (a.isPinned() != b.isPinned()) {
                return a.isPinned() ? -1 : 1;
            }
            if (currentSortMode == 1) {
                return Long.compare(a.getTimestamp(), b.getTimestamp());
            } else if (currentSortMode == 2) {
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            } else if (currentSortMode == 3) {
                return a.getPageColor().compareToIgnoreCase(b.getPageColor());
            } else {
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            }
        });

        adapter.updateList(filteredNotes);
        updateUIState(filteredNotes.size());
    }

    private void updateUIState(int listSize) {
        if (allNotes.isEmpty()) {
            searchCard.setVisibility(View.GONE);
            toggleContainer.setVisibility(View.GONE);
            if (btnSortNotes != null) btnSortNotes.setVisibility(View.GONE);
            if (chipScrollContainer != null) chipScrollContainer.setVisibility(View.GONE);

            emptyStateContainer.setVisibility(View.VISIBLE);
            emptySearchContainer.setVisibility(View.GONE);
            recyclerNotes.setVisibility(View.GONE);
        } else {
            searchCard.setVisibility(View.VISIBLE);
            toggleContainer.setVisibility(View.VISIBLE);
            if (btnSortNotes != null) btnSortNotes.setVisibility(View.VISIBLE);
            if (chipScrollContainer != null) chipScrollContainer.setVisibility(View.VISIBLE);
            emptyStateContainer.setVisibility(View.GONE);

            if (listSize == 0) {
                recyclerNotes.setVisibility(View.GONE);
                emptySearchContainer.setVisibility(View.VISIBLE);
            } else {
                recyclerNotes.setVisibility(View.VISIBLE);
                emptySearchContainer.setVisibility(View.GONE);
            }
        }
    }

    private String makeTaskLineHtml(String taskText) {
        return "<span class=\"task-line\"><span class=\"checkbox\" contenteditable=\"false\">" + SVG_UNCHECK_SQUARE + "</span> " + taskText + "</span><br>";
    }

    private void openTemplatesDialog() {
        final Dialog sheet = new Dialog(this);
        LinearLayout rootWrapper = new LinearLayout(this);
        rootWrapper.setOrientation(LinearLayout.VERTICAL);
        rootWrapper.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        CardView sheetCard = new CardView(this);
        sheetCard.setRadius(dpToPx(28));
        sheetCard.setCardBackgroundColor(Color.WHITE);
        sheetCard.setCardElevation(dpToPx(8));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(22), dpToPx(16), dpToPx(22), dpToPx(24));

        // Handle bar
        View handle = new View(this);
        handle.setBackgroundColor(Color.parseColor("#CBD5E1"));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(5));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.setMargins(0, 0, 0, dpToPx(16));
        inner.addView(handle, handleLp);

        TextView title = new TextView(this);
        title.setText("⚡ Instant Productivity Templates");
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        inner.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Start a structured note in one tap");
        subtitle.setTextSize(13.5f);
        subtitle.setTextColor(Color.parseColor("#64748B"));
        subtitle.setPadding(0, dpToPx(4), 0, dpToPx(16));
        inner.addView(subtitle);

        String todayStr = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date());

        Object[][] templates = new Object[][]{
                {
                        "☑️ Daily Action Plan",
                        "Top 3 priorities, schedule & daily checkboxes",
                        "#F9FBE7",
                        "Personal",
                        "Daily Plan — " + todayStr,
                        "<h3>🎯 Top 3 Priorities</h3>" +
                                makeTaskLineHtml("Priority #1: Most important deliverable") +
                                makeTaskLineHtml("Priority #2: Key follow-up or review") +
                                makeTaskLineHtml("Priority #3: Quick win task") +
                                "<h3>☀️ Schedule & Tasks</h3>" +
                                makeTaskLineHtml("09:00 AM — Morning deep work sprint") +
                                makeTaskLineHtml("02:00 PM — Sync & messages") +
                                makeTaskLineHtml("06:00 PM — Wrap up & plan tomorrow") +
                                "<h3>💡 Daily Notes</h3><p>Write quick thoughts here...</p>"
                },
                {
                        "📋 Meeting Minutes & Action Items",
                        "Structured agenda, key decisions, and owner tasks",
                        "#E0F2FE",
                        "Work",
                        "Meeting Notes — " + todayStr,
                        "<p><b>Date:</b> " + todayStr + "<br><b>Attendees:</b> Team</p>" +
                                "<h3>📌 Agenda & Discussion</h3>" +
                                "<ul><li>Project status & milestones</li><li>Key blockers & solutions</li></ul>" +
                                "<h3>✅ Action Items</h3>" +
                                makeTaskLineHtml("Share summary document with team") +
                                makeTaskLineHtml("Schedule next milestone review")
                },
                {
                        "⏱️ Deep Work / Study Sprint",
                        "Pomodoro goals, key concepts & review checklist",
                        "#EDE9FE",
                        "Study",
                        "Study & Focus Session",
                        "<h3>⏱️ Focus Sprint Goals</h3>" +
                                makeTaskLineHtml("Sprint 1 (25m): Read & outline core concepts") +
                                makeTaskLineHtml("Sprint 2 (25m): Practice problems / implementation") +
                                "<h3>🧠 Key Takeaways</h3>" +
                                "<ul><li><b>Concept 1:</b> Summary of main idea</li><li><b>Concept 2:</b> Key formula or insight</li></ul>"
                },
                {
                        "🎨 Design & Color Palette Spec",
                        "Auto-renders live color swatches on your dashboard",
                        "#FCE7F3",
                        "Ideas",
                        "UI Brand Palette & Ideas",
                        "<p>Brand color tokens & visual direction:</p>" +
                                "<p>Primary Indigo: #4F46E5<br>Emerald Accent: #10B981<br>Amber Highlight: #F59E0B<br>Slate Dark: #0F172A</p>" +
                                "<h3>✨ Next Design Steps</h3>" +
                                makeTaskLineHtml("Test contrast ratios across light cards") +
                                makeTaskLineHtml("Finalize typography hierarchy")
                },
                {
                        "🛒 Smart Checklist & Essentials",
                        "Fast check-off list for shopping, travel, or packing",
                        "#DCFCE7",
                        "Personal",
                        "Quick Checklist",
                        "<h3>🛒 Essentials</h3>" +
                                makeTaskLineHtml("Item 1") +
                                makeTaskLineHtml("Item 2") +
                                makeTaskLineHtml("Item 3") +
                                makeTaskLineHtml("Item 4")
                }
        };

        for (Object[] tpl : templates) {
            String tplLabel = (String) tpl[0];
            String tplDesc = (String) tpl[1];
            String tplColor = (String) tpl[2];
            String tplCategory = (String) tpl[3];
            String tplNoteTitle = (String) tpl[4];
            String tplHtml = (String) tpl[5];

            CardView itemCard = new CardView(this);
            itemCard.setRadius(dpToPx(18));
            itemCard.setCardElevation(0);
            itemCard.setCardBackgroundColor(Color.parseColor("#F8FAFC"));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardLp.setMargins(0, 0, 0, dpToPx(10));
            itemCard.setLayoutParams(cardLp);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

            CardView dot = new CardView(this);
            dot.setRadius(dpToPx(10));
            dot.setCardElevation(0);
            dot.setCardBackgroundColor(Color.parseColor(tplColor));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
            dot.setLayoutParams(dotLp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textLp.setMargins(dpToPx(12), 0, 0, 0);
            textCol.setLayoutParams(textLp);

            TextView tvName = new TextView(this);
            tvName.setText(tplLabel);
            tvName.setTextSize(15f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(Color.parseColor("#0F172A"));

            TextView tvSub = new TextView(this);
            tvSub.setText(tplDesc);
            tvSub.setTextSize(12.5f);
            tvSub.setTextColor(Color.parseColor("#64748B"));

            textCol.addView(tvName);
            textCol.addView(tvSub);

            row.addView(dot);
            row.addView(textCol);
            itemCard.addView(row);

            itemCard.setOnClickListener(v -> {
                sheet.dismiss();
                Intent intent = new Intent(MainActivity.this, EditorActivity.class);
                intent.putExtra("note_title", tplNoteTitle);
                intent.putExtra("note_content", tplHtml);
                intent.putExtra("note_color", tplColor);
                intent.putExtra("note_category", tplCategory);
                intent.putExtra("is_template_new", true);
                startActivity(intent);
            });

            inner.addView(itemCard);
        }

        sheetCard.addView(inner);
        rootWrapper.addView(sheetCard);
        sheet.setContentView(rootWrapper);

        if (sheet.getWindow() != null) {
            sheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            sheet.getWindow().setGravity(Gravity.BOTTOM);
        }
        sheet.show();
    }

    @Override
    public void onNoteClick(Note note) {
        Intent intent = new Intent(MainActivity.this, EditorActivity.class);
        intent.putExtra("note_id", note.getId());
        intent.putExtra("note_title", note.getTitle());
        intent.putExtra("note_content", note.getContent());
        intent.putExtra("note_color", note.getPageColor());
        intent.putExtra("note_pinned", note.isPinned());
        intent.putExtra("note_category", note.getCategory());
        startActivity(intent);
    }

    @Override
    public void onNoteTaskToggled(Note note, String updatedHtml) {
        dbHelper.updateNoteContentOnly(note.getId(), updatedHtml);
        updateWorkspaceStats();
    }

    @Override
    public void onNotesPinToggle(List<Note> notes) {
        if (notes == null || notes.isEmpty()) return;
        boolean shouldPin = false;
        for (Note n : notes) {
            if (!n.isPinned()) {
                shouldPin = true;
                break;
            }
        }
        for (Note n : notes) {
            dbHelper.updateNotePin(n.getId(), shouldPin);
        }
        reloadNotesAndRefresh();
        Toast.makeText(this, shouldPin ? "Pinned to top" : "Unpinned", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNotesCategoryChange(List<Note> notes) {
        if (notes == null || notes.isEmpty()) return;
        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        CardView card = new CardView(this);
        card.setRadius(dpToPx(24));
        card.setCardBackgroundColor(Color.WHITE);
        card.setCardElevation(dpToPx(8));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

        TextView title = new TextView(this);
        title.setText("🏷️ Assign Category");
        title.setTextSize(19f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        title.setPadding(0, 0, 0, dpToPx(12));
        inner.addView(title);

        String[] categories = {"General", "Work", "Personal", "Ideas", "Study"};
        for (String cat : categories) {
            TextView item = new TextView(this);
            item.setText(cat);
            item.setTextSize(16f);
            item.setTypeface(null, Typeface.BOLD);
            item.setTextColor(Color.parseColor("#1E293B"));
            item.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
            item.setBackgroundResource(R.drawable.bg_chip_inactive);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, dpToPx(8));
            item.setLayoutParams(lp);

            item.setOnClickListener(v -> {
                for (Note n : notes) {
                    dbHelper.updateNoteCategory(n.getId(), cat);
                }
                dialog.dismiss();
                reloadNotesAndRefresh();
                Toast.makeText(MainActivity.this, "Category set to " + cat, Toast.LENGTH_SHORT).show();
            });
            inner.addView(item);
        }

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

    @Override
    public void onNotesDuplicate(List<Note> notes) {
        if (notes == null || notes.isEmpty()) return;
        for (Note n : notes) {
            dbHelper.duplicateNote(n);
        }
        reloadNotesAndRefresh();
        Toast.makeText(this, notes.size() + " note(s) duplicated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNotesShare(List<Note> notes) {
        if (notes != null && !notes.isEmpty()) {
            if (notes.size() > 1) {
                Toast.makeText(this, "Sharing the first selected note...", Toast.LENGTH_SHORT).show();
            }
            openShareDialog(notes.get(0));
        }
    }

    @Override
    public void onNotesDelete(List<Note> notes) {
        if (notes != null && !notes.isEmpty()) {
            for (Note note : notes) {
                dbHelper.deleteNote(note.getId());
                allNotes.remove(note);
            }
            updateWorkspaceStats();
            filterNotes(etSearch.getText().toString());
            Toast.makeText(this, notes.size() + " Note(s) deleted", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onNotesPrivate(List<Note> notes) {
        if (notes != null && !notes.isEmpty()) {
            for (Note note : notes) {
                dbHelper.updateNotePrivacy(note.getId(), true);
                allNotes.remove(note);
            }
            updateWorkspaceStats();
            filterNotes(etSearch.getText().toString());
            Toast.makeText(this, notes.size() + " Note(s) moved to Private Vault", Toast.LENGTH_SHORT).show();
        }
    }

    private void openShareDialog(Note note) {
        final Dialog shareBottomSheet = new Dialog(MainActivity.this);
        shareBottomSheet.setContentView(R.layout.dialog_share_note);

        if (shareBottomSheet.getWindow() != null) {
            shareBottomSheet.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            shareBottomSheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            shareBottomSheet.getWindow().setGravity(Gravity.BOTTOM);
        }

        shareBottomSheet.findViewById(R.id.btn_share_pdf_action).setOnClickListener(v -> {
            shareBottomSheet.dismiss();
            Toast.makeText(this, "Preparing PDF for share...", Toast.LENGTH_SHORT).show();
            processNoteForPdfOrImage(note, true, false);
        });

        shareBottomSheet.findViewById(R.id.btn_download_pdf_action).setOnClickListener(v -> {
            shareBottomSheet.dismiss();
            processNoteForPdfOrImage(note, false, false);
        });

        shareBottomSheet.findViewById(R.id.btn_share_image_action).setOnClickListener(v -> {
            shareBottomSheet.dismiss();
            Toast.makeText(this, "Generating Image...", Toast.LENGTH_SHORT).show();
            processNoteForPdfOrImage(note, true, true);
        });

        shareBottomSheet.findViewById(R.id.btn_share_text_action).setOnClickListener(v -> {
            shareBottomSheet.dismiss();
            shareNoteAsText(note);
        });

        shareBottomSheet.show();
    }

    private void shareNoteAsText(Note note) {
        String htmlContent = note.getContent();
        if (htmlContent == null) htmlContent = "";

        htmlContent = htmlContent
                .replaceAll("(?is)<span class=\"checkbox checked\"[^>]*>.*?</span>", "[✓] ")
                .replaceAll("(?is)<span class=\"checkbox\"[^>]*>.*?</span>", "[ ] ")
                .replaceAll("(?i)<b>", "<b>*")
                .replaceAll("(?i)</b>", "*</b>")
                .replaceAll("(?i)<strong>", "<strong>*")
                .replaceAll("(?i)</strong>", "*</strong>")
                .replaceAll("(?i)<i>", "<i>_")
                .replaceAll("(?i)</i>", "_</i>")
                .replaceAll("(?i)<em>", "<em>_")
                .replaceAll("(?i)</em>", "_</em>")
                .replaceAll("(?i)<strike>", "<strike>~")
                .replaceAll("(?i)</strike>", "~</strike>")
                .replaceAll("(?i)<s>", "<s>~")
                .replaceAll("(?i)</s>", "~</s>");

        Spanned cleanSpanned;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cleanSpanned = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY);
        } else {
            cleanSpanned = Html.fromHtml(htmlContent);
        }

        String plainText = cleanSpanned.toString().replaceAll("\uFFFC", "").replaceAll("\n{3,}", "\n\n").trim();
        String shareBody = "*" + note.getTitle().trim() + "*\n\n" + plainText;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, note.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(intent, "Share Note as Text"));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void processNoteForPdfOrImage(Note note, boolean isShare, boolean isImage) {
        int pageWidth = 800;
        int pageHeight = 1131;
        int margin = 60;
        final int contentWidth = pageWidth - (2 * margin);
        final int contentHeight = pageHeight - (2 * margin);

        TextView tv = new TextView(this);
        tv.setPadding(0, 0, 0, 0);
        tv.setTextSize(16f);
        tv.setTextColor(Color.parseColor("#333333"));
        tv.setLineSpacing(0, 1.3f);

        String rawContent = note.getContent() != null ? note.getContent() : "";
        String titleHtml = "<h2 style='text-align:center; margin-top:0px;'><b>" + note.getTitle() + "</b></h2><br>";
        String preparedHtml = titleHtml + rawContent;

        preparedHtml = preparedHtml.replaceAll("•\\s*", "• &nbsp;&nbsp;");
        preparedHtml = preparedHtml.replaceAll("(\\d+\\.)\\s*([a-zA-Z])", "$1 &nbsp;&nbsp;$2");
        preparedHtml = preparedHtml.replaceAll("(?i)<li>\\s*", "<li>&nbsp;&nbsp;");

        preparedHtml = preparedHtml.replaceAll("(?i)<span[^>]*style=\"[^\"]*text-decoration\\s*:\\s*line-through[^\"]*\"[^>]*>(.*?)</span>", "<s>$1</s>");

        preparedHtml = preparedHtml
                .replaceAll("(?is)<span class=\"checkbox checked\"[^>]*>.*?</span>", "<img src=\"checkbox_checked\"> ")
                .replaceAll("(?is)<span class=\"checkbox\"[^>]*>.*?</span>", "<img src=\"checkbox_unchecked\"> ")
                .replaceAll("<svg[^>]*>.*?</svg>", "<img src=\"checkbox_unchecked\"> ")
                .replaceAll("☑", "<img src=\"checkbox_checked\"> ")
                .replaceAll("☐", "<img src=\"checkbox_unchecked\"> ");

        Html.ImageGetter imageGetter = source -> {
            try {
                String path = source;
                int targetW = -1;
                int targetH = -1;

                int sep = path.indexOf("|w=");
                if (sep >= 0) {
                    try {
                        String[] parts = path.substring(sep + 1).split("\\|");
                        for (String part : parts) {
                            String numStr = part.substring(2).replaceAll("[^0-9.]", "");
                            if (part.startsWith("w=")) targetW = (int) Float.parseFloat(numStr);
                            if (part.startsWith("h=")) targetH = (int) Float.parseFloat(numStr);
                        }
                    } catch (Exception ignored) {}
                    path = path.substring(0, sep);
                }

                if (path.startsWith("file://")) path = path.substring(7);

                Drawable d = null;
                if (path.equals("tick_icon")) {
                    d = getResources().getDrawable(R.drawable.ic_tick);
                    d.setBounds(0, 0, 40, 40);
                    return d;
                } else if (path.equals("checkbox_checked")) {
                    d = getResources().getDrawable(android.R.drawable.checkbox_on_background);
                    if (d != null) d.setBounds(0, 0, dpToPx(18), dpToPx(18));
                    return d;
                } else if (path.equals("checkbox_unchecked")) {
                    d = getResources().getDrawable(android.R.drawable.checkbox_off_background);
                    if (d != null) d.setBounds(0, 0, dpToPx(18), dpToPx(18));
                    return d;
                }

                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();

                    if (targetW > 0) {
                        float ratio = (float) h / w;
                        w = targetW;
                        h = targetH > 0 ? targetH : Math.round(targetW * ratio);
                    } else {
                        float maxImgWidth = contentWidth;
                        float maxImgHeight = contentHeight * 0.45f;
                        float widthRatio = maxImgWidth / w;
                        float heightRatio = maxImgHeight / h;
                        float scale = Math.min(widthRatio, heightRatio);
                        if (scale > 1.2f) scale = 1.2f;
                        w = Math.round(w * scale);
                        h = Math.round(h * scale);
                    }

                    if (w > contentWidth) {
                        float scale = (float) contentWidth / w;
                        w = Math.round(w * scale);
                        h = Math.round(h * scale);
                    }

                    if (w <= 0) w = 1;
                    if (h <= 0) h = 1;

                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);

                    int finalWidth = contentWidth;
                    int finalHeight = h;

                    Bitmap finalBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888);
                    Canvas bCanvas = new Canvas(finalBitmap);
                    bCanvas.drawColor(Color.TRANSPARENT);

                    int leftOffset = (finalWidth - w) / 2;

                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    BitmapShader shader = new BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    Matrix matrix = new Matrix();
                    matrix.setTranslate(leftOffset, 0);
                    shader.setLocalMatrix(matrix);
                    paint.setShader(shader);

                    RectF rect = new RectF(leftOffset, 0, leftOffset + w, h);
                    bCanvas.drawRoundRect(rect, 16, 16, paint);

                    BitmapDrawable bd = new BitmapDrawable(getResources(), finalBitmap);
                    bd.setBounds(0, 0, finalWidth, finalHeight);
                    return bd;
                }
                return d;
            } catch (Exception e) {
                return null;
            }
        };

        Spanned spanned;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            spanned = Html.fromHtml(preparedHtml, Html.FROM_HTML_MODE_LEGACY, imageGetter, null);
        } else {
            spanned = Html.fromHtml(preparedHtml, imageGetter, null);
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(spanned);

        URLSpan[] urls = ssb.getSpans(0, ssb.length(), URLSpan.class);
        for (URLSpan span : urls) ssb.removeSpan(span);

        UnderlineSpan[] underlines = ssb.getSpans(0, ssb.length(), UnderlineSpan.class);
        for (UnderlineSpan span : underlines) ssb.removeSpan(span);

        ForegroundColorSpan[] colorSpans = ssb.getSpans(0, ssb.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : colorSpans) {
            int color = span.getForegroundColor();
            if (color == Color.YELLOW || (Color.red(color) > 200 && Color.green(color) > 200 && Color.blue(color) < 150)) {
                int start = ssb.getSpanStart(span);
                int end = ssb.getSpanEnd(span);
                ssb.removeSpan(span);
                ssb.setSpan(new BackgroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(Color.BLACK), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        ssb.append("\n\n\n");
        int footerStart = ssb.length();
        ssb.append("  Created with NotNote");

        ssb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), footerStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(Color.parseColor("#999999")), footerStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), footerStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new RelativeSizeSpan(0.85f), footerStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        try {
            Drawable icon = getResources().getDrawable(R.drawable.ic_ink, null);
            if (icon != null) {
                icon.mutate();
                icon.setTint(Color.parseColor("#999999"));
                int iconSize = (int) (tv.getTextSize() * 1.3f);
                icon.setBounds(0, 0, iconSize, iconSize);
                ssb.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), footerStart, footerStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception ignored) {}

        tv.setText(ssb);

        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        tv.measure(widthMeasureSpec, heightMeasureSpec);
        tv.layout(0, 0, tv.getMeasuredWidth(), tv.getMeasuredHeight());

        File cacheDir = new File(getCacheDir(), "shared_notes");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        String safeTitle = note.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_");
        if (safeTitle.trim().isEmpty()) safeTitle = "Notnote_" + note.getId();

        try {
            if (isImage) {
                int imgWidth = tv.getMeasuredWidth() + (2 * margin);
                int imgHeight = Math.max(tv.getMeasuredHeight() + (2 * margin), 100);

                Bitmap bitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);

                canvas.save();
                canvas.translate(margin, margin);
                tv.draw(canvas);
                canvas.restore();

                File file = new File(cacheDir, safeTitle + ".jpg");
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.close();

                shareFile(file, "image/jpeg");
            } else {
                PdfDocument document = new PdfDocument();
                Layout layout = tv.getLayout();

                if (layout != null) {
                    int totalLines = layout.getLineCount();
                    int currentLine = 0;
                    int pageNum = 1;

                    while (currentLine < totalLines) {
                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                        PdfDocument.Page page = document.startPage(pageInfo);
                        Canvas canvas = page.getCanvas();
                        canvas.drawColor(Color.WHITE);

                        int startY = layout.getLineTop(currentLine);
                        int availableHeight = contentHeight;
                        int endLine = currentLine;

                        while (endLine < totalLines && (layout.getLineBottom(endLine) - startY) <= availableHeight) {
                            endLine++;
                        }
                        if (endLine == currentLine) endLine++;

                        int endY = endLine < totalLines ? layout.getLineTop(endLine) : layout.getLineBottom(endLine - 1);

                        canvas.save();
                        canvas.translate(margin, margin - startY);
                        canvas.clipRect(0, startY, contentWidth, endY);

                        tv.draw(canvas);
                        canvas.restore();

                        document.finishPage(page);
                        currentLine = endLine;
                        pageNum++;
                    }
                }

                if (isShare) {
                    File file = new File(cacheDir, safeTitle + ".pdf");
                    FileOutputStream fos = new FileOutputStream(file);
                    document.writeTo(fos);
                    fos.close();
                    document.close();

                    shareFile(file, "application/pdf");
                } else {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadsDir.exists()) downloadsDir.mkdirs();

                    String baseName = safeTitle;
                    File file = new File(downloadsDir, baseName + ".pdf");
                    int counter = 1;
                    while (file.exists()) {
                        file = new File(downloadsDir, baseName + " (" + counter + ").pdf");
                        counter++;
                    }

                    FileOutputStream fos = new FileOutputStream(file);
                    document.writeTo(fos);
                    fos.close();
                    document.close();

                    try {
                        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                        if (downloadManager != null) {
                            downloadManager.addCompletedDownload(
                                    file.getName(),
                                    "Downloaded successfully.",
                                    true,
                                    "application/pdf",
                                    file.getAbsolutePath(),
                                    file.length(),
                                    true
                            );
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Toast.makeText(this, "PDF Downloaded: " + file.getName(), Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error generating file", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(File file, String mimeType) {
        try {
            Uri uri = FileProvider.getUriForFile(this, "com.santoshdev.notnote.provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Document"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Share Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
