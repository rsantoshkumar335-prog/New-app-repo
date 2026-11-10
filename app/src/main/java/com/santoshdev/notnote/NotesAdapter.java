package com.santoshdev.notnote;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    public interface NoteActionListener {
        void onNoteClick(Note note);
        void onNotesShare(List<Note> notes);
        void onNotesDelete(List<Note> notes);
        void onNotesPrivate(List<Note> notes);
        default void onNotesPinToggle(List<Note> notes) {}
        default void onNotesCategoryChange(List<Note> notes) {}
        default void onNotesDuplicate(List<Note> notes) {}
        default void onNoteTaskToggled(Note note, String updatedHtml) {}
    }

    private final Context context;
    private final NoteActionListener listener;

    private final Set<Integer> selectedNoteIds = new HashSet<>();
    private int animatedNoteId = -1;
    private boolean isGridView = false;

    private final List<Note> adapterNotes = new ArrayList<>();
    private final LruCache<Integer, NoteMetadata> metadataCache = new LruCache<>(120);
    private final LruCache<Integer, Bitmap> bitmapCache = new LruCache<>(60);

    private Dialog optionsMenuDialog;

    private static final String SVG_CHECK_SQUARE = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100%\" height=\"100%\" fill=\"currentColor\" viewBox=\"0 0 256 256\"><path d=\"M172.24,99.76a6,6,0,0,1,0,8.48l-56,56a6,6,0,0,1-8.48,0l-24-24a6,6,0,0,1,8.48-8.48L112,151.51l51.76-51.75A6,6,0,0,1,172.24,99.76ZM222,48V208a14,14,0,0,1-14,14H48a14,14,0,0,1-14-14V48A14,14,0,0,1,48,34H208A14,14,0,0,1,222,48Zm-12,0a2,2,0,0,0-2-2H48a2,2,0,0,0-2,2V208a2,2,0,0,0,2,2H208a2,2,0,0,0,2-2Z\"></path></svg>";
    private static final String SVG_UNCHECK_SQUARE = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100%\" height=\"100%\" fill=\"currentColor\" viewBox=\"0 0 256 256\"><path d=\"M208,48V208H48V48H208m0-16H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V48a16,16,0,0,0-16-16Z\"></path></svg>";

    public NotesAdapter(Context context, NoteActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setGridView(boolean isGridView) {
        this.isGridView = isGridView;
        notifyDataSetChanged();
    }

    public void clearMetadataCache() {
        metadataCache.evictAll();
    }

    public boolean hasSelection() {
        return !selectedNoteIds.isEmpty();
    }

    public void clearSelection() {
        selectedNoteIds.clear();
        if (optionsMenuDialog != null && optionsMenuDialog.isShowing()) {
            closeOptionsMenu(null);
        } else {
            notifyDataSetChanged();
        }
    }

    public void updateList(List<Note> newNotes) {
        NoteDiffCallback diffCallback = new NoteDiffCallback(this.adapterNotes, newNotes);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        this.adapterNotes.clear();
        this.adapterNotes.addAll(newNotes);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return adapterNotes.get(position).getId();
    }

    @Override
    public int getItemViewType(int position) {
        return isGridView ? 1 : 0;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = (viewType == 1) ? R.layout.item_note_grid : R.layout.item_note_list;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        final Note note = adapterNotes.get(position);
        NoteMetadata meta = parseNoteMetadata(note);

        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);

        String colorHex = note.getPageColor();
        if (colorHex == null || colorHex.isEmpty() || colorHex.equalsIgnoreCase("#FFFFFF")) {
            colorHex = isGridView ? "#FFFFFF" : "#6366F1";
        }

        int baseColor;
        try {
            baseColor = Color.parseColor(colorHex);
        } catch (Exception e) {
            baseColor = Color.parseColor("#FFFFFF");
        }

        if (isGridView) {
            if (holder.cardLayout != null) {
                holder.cardLayout.setCardBackgroundColor(baseColor);
            }
            if (holder.itemView.getLayoutParams() instanceof StaggeredGridLayoutManager.LayoutParams) {
                StaggeredGridLayoutManager.LayoutParams layoutParams = (StaggeredGridLayoutManager.LayoutParams) holder.itemView.getLayoutParams();
                layoutParams.setFullSpan(false);
                holder.itemView.setLayoutParams(layoutParams);
            }
        } else {
            if (holder.colorStrip != null) {
                holder.colorStrip.setBackgroundColor(baseColor);
            }
            if (holder.cardLayout != null) {
                String rawPageColor = note.getPageColor();
                if (rawPageColor == null || rawPageColor.equalsIgnoreCase("#FFFFFF")) {
                    holder.cardLayout.setCardBackgroundColor(Color.WHITE);
                } else {
                    float[] hsv = new float[3];
                    Color.colorToHSV(baseColor, hsv);
                    hsv[1] = Math.min(hsv[1] * 0.18f, 0.22f);
                    hsv[2] = Math.max(hsv[2], 0.96f);
                    holder.cardLayout.setCardBackgroundColor(Color.HSVToColor(hsv));
                }
            }
        }

        // Pin Badge
        if (holder.ivPinBadge != null) {
            holder.ivPinBadge.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);
        }

        // Category Badge
        if (holder.tvCategoryBadge != null) {
            String cat = note.getCategory();
            if (cat != null && !cat.trim().isEmpty() && !cat.equalsIgnoreCase("General")) {
                holder.tvCategoryBadge.setText(cat);
                holder.tvCategoryBadge.setVisibility(View.VISIBLE);
            } else {
                holder.tvCategoryBadge.setVisibility(View.GONE);
            }
        }

        // Task Progress Badge
        if (holder.tvTaskProgress != null) {
            if (!meta.tasks.isEmpty()) {
                int doneCount = 0;
                for (String t : meta.tasks) {
                    if (t.startsWith("1")) doneCount++;
                }
                holder.tvTaskProgress.setText("✓ " + doneCount + "/" + meta.tasks.size());
                holder.tvTaskProgress.setVisibility(View.VISIBLE);
            } else {
                holder.tvTaskProgress.setVisibility(View.GONE);
            }
        }

        holder.tvTitle.setText(note.getTitle());
        if (note.getTitle().trim().isEmpty()) {
            holder.tvTitle.setVisibility(View.GONE);
        } else {
            holder.tvTitle.setVisibility(View.VISIBLE);
        }

        Date date = new Date(note.getTimestamp());
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
        if (holder.tvTime != null) holder.tvTime.setText(timeFormat.format(date));
        if (holder.tvDate != null) holder.tvDate.setText(dateFormat.format(date));

        if (!isGridView) {
            resetListViewHolder(holder);

            if (meta.noteType == NoteMetadata.TYPE_TASK_LIST && holder.llTaskPreview != null) {
                holder.llTaskPreview.setVisibility(View.VISIBLE);
                if (holder.tvPreview != null) holder.tvPreview.setVisibility(View.GONE);
                populateInteractiveTasks(holder.llTaskPreview, note, meta, 3);
            } else if (meta.noteType == NoteMetadata.TYPE_COLOR_PALETTE && holder.llColorPreview != null) {
                holder.llColorPreview.setVisibility(View.VISIBLE);
                if (holder.tvPreview != null) {
                    holder.tvPreview.setVisibility(View.VISIBLE);
                    holder.tvPreview.setText(meta.richPreview);
                    if (meta.richPreview.length() == 0) holder.tvPreview.setVisibility(View.GONE);
                }

                for (int i = 0; i < meta.colors.size(); i++) {
                    String swatchColor = meta.colors.get(i);
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, 0, 0, dpToPx(6));

                    CardView swatch = new CardView(context);
                    swatch.setRadius(dpToPx(4));
                    swatch.setCardElevation(0);
                    try {
                        swatch.setCardBackgroundColor(Color.parseColor(swatchColor));
                    } catch (Exception e) {
                        swatch.setCardBackgroundColor(Color.LTGRAY);
                    }
                    swatch.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)));

                    TextView tv = new TextView(context);
                    tv.setText(swatchColor);
                    tv.setTextSize(12f);
                    tv.setTextColor(Color.parseColor("#475569"));
                    tv.setTypeface(null, Typeface.BOLD);

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.setMargins(dpToPx(8), 0, 0, 0);
                    tv.setLayoutParams(params);

                    row.addView(swatch);
                    row.addView(tv);
                    holder.llColorPreview.addView(row);
                }
            } else {
                if (holder.tvPreview != null) {
                    holder.tvPreview.setVisibility(View.VISIBLE);
                    holder.tvPreview.setText(meta.richPreview);
                    if (meta.richPreview.length() == 0) holder.tvPreview.setVisibility(View.GONE);
                }
            }

            if (meta.imagePath != null && holder.cvSideImage != null) {
                holder.cvSideImage.setVisibility(View.VISIBLE);
                loadImageSafely(meta.imagePath, holder.ivSideImage, note.getId(), position, holder);
            }
        } else {
            if (holder.llTaskPreview != null) {
                holder.llTaskPreview.removeAllViews();
                if (meta.noteType == NoteMetadata.TYPE_TASK_LIST) {
                    holder.llTaskPreview.setVisibility(View.VISIBLE);
                    if (holder.tvPreview != null) holder.tvPreview.setVisibility(View.GONE);
                    populateInteractiveTasks(holder.llTaskPreview, note, meta, 3);
                } else {
                    holder.llTaskPreview.setVisibility(View.GONE);
                    if (holder.tvPreview != null) {
                        holder.tvPreview.setVisibility(meta.richPreview.length() > 0 ? View.VISIBLE : View.GONE);
                        holder.tvPreview.setText(meta.richPreview);
                    }
                }
            } else if (holder.tvPreview != null) {
                holder.tvPreview.setText(meta.richPreview);
            }

            if (meta.imagePath != null && holder.imageContainer != null) {
                holder.imageContainer.setVisibility(View.VISIBLE);
                loadImageSafely(meta.imagePath, holder.ivImage, note.getId(), position, holder);
            } else if (holder.imageContainer != null) {
                holder.imageContainer.setVisibility(View.GONE);
                holder.ivImage.setImageBitmap(null);
            }
        }

        handleSelectionState(holder, note);

        final boolean[] isLongPressed = {false};

        holder.itemView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                holder.itemView.animate().scaleX(0.98f).scaleY(0.98f).setDuration(110).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                if (isLongPressed[0]) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (!selectedNoteIds.contains(note.getId())) {
                            toggleSelection(note.getId(), position);
                        }
                        if (optionsMenuDialog == null || !optionsMenuDialog.isShowing()) {
                            showNoteOptionsDialog();
                        }
                    }
                    isLongPressed[0] = false;
                }
            }
            return false;
        });

        holder.itemView.setOnLongClickListener(v -> {
            isLongPressed[0] = true;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            holder.itemView.animate().scaleX(0.94f).scaleY(0.94f).setDuration(150).start();

            if (selectedNoteIds.isEmpty()) {
                holder.selectionIndicatorContainer.setVisibility(View.VISIBLE);
                holder.viewUnselectedCircle.setVisibility(View.VISIBLE);
                holder.cvSelectedCircle.setVisibility(View.GONE);
            }

            return true;
        });

        holder.itemView.setOnClickListener(v -> {
            if (!selectedNoteIds.isEmpty()) {
                toggleSelection(note.getId(), position);
            } else {
                if (listener != null) listener.onNoteClick(note);
            }
        });
    }

    private void populateInteractiveTasks(LinearLayout container, Note note, NoteMetadata meta, int maxTasks) {
        int count = Math.min(meta.tasks.size(), maxTasks);
        for (int i = 0; i < count; i++) {
            final int taskIndex = i;
            String taskInfo = meta.tasks.get(i);
            final boolean isChecked = taskInfo.startsWith("1");
            String taskText = taskInfo.substring(2);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dpToPx(2), 0, dpToPx(5));

            ImageView icon = new ImageView(context);
            icon.setImageResource(isChecked ? R.drawable.ic_check_square : R.drawable.ic_square_filled);
            icon.setColorFilter(Color.parseColor(isChecked ? "#10B981" : "#475569"));
            icon.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(17), dpToPx(17)));

            TextView tv = new TextView(context);
            tv.setText(taskText);
            tv.setTextSize(13.5f);
            tv.setTextColor(Color.parseColor("#1E293B"));
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(dpToPx(8), 0, 0, 0);
            tv.setLayoutParams(params);

            if (isChecked) {
                tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tv.setAlpha(0.55f);
                icon.setAlpha(0.8f);
            }

            // Allow one-tap task completion directly from Dashboard card when not in selection mode
            row.setOnClickListener(v -> {
                if (!selectedNoteIds.isEmpty()) {
                    int pos = adapterNotes.indexOf(note);
                    if (pos >= 0) toggleSelection(note.getId(), pos);
                    return;
                }
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                String updatedHtml = toggleTaskInHtml(note.getContent(), taskIndex);
                if (updatedHtml != null && listener != null) {
                    note.setContent(updatedHtml);
                    metadataCache.remove(note.getId());
                    int pos = adapterNotes.indexOf(note);
                    if (pos >= 0) notifyItemChanged(pos);
                    listener.onNoteTaskToggled(note, updatedHtml);
                }
            });

            row.addView(icon);
            row.addView(tv);
            container.addView(row);
        }
    }

    private String toggleTaskInHtml(String rawHtml, int targetTaskIndex) {
        if (rawHtml == null || rawHtml.isEmpty()) return null;
        Pattern p = Pattern.compile("(?is)<span[^>]*class=[\"']([^\"']*checkbox[^\"']*)[\"'][^>]*>(.*?)</span>");
        Matcher m = p.matcher(rawHtml);
        StringBuffer sb = new StringBuffer();
        int idx = 0;
        boolean found = false;
        while (m.find()) {
            if (idx == targetTaskIndex) {
                found = true;
                String classes = m.group(1);
                boolean currentlyChecked = classes.toLowerCase(Locale.ROOT).contains("checked");
                String replacement;
                if (currentlyChecked) {
                    replacement = "<span class=\"checkbox\" contenteditable=\"false\">" + SVG_UNCHECK_SQUARE + "</span>";
                } else {
                    replacement = "<span class=\"checkbox checked\" contenteditable=\"false\">" + SVG_CHECK_SQUARE + "</span>";
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            idx++;
        }
        m.appendTail(sb);
        if (found) return sb.toString();

        // Fallback if plain ☐ / ☑ unicode symbols were used
        StringBuilder unicodeBuilder = new StringBuilder();
        int uIdx = 0;
        for (int i = 0; i < rawHtml.length(); i++) {
            char c = rawHtml.charAt(i);
            if (c == '☐' || c == '☑') {
                if (uIdx == targetTaskIndex) {
                    unicodeBuilder.append(c == '☐' ? '☑' : '☐');
                    found = true;
                } else {
                    unicodeBuilder.append(c);
                }
                uIdx++;
            } else {
                unicodeBuilder.append(c);
            }
        }
        return found ? unicodeBuilder.toString() : null;
    }

    private void toggleSelection(int noteId, int position) {
        boolean wasEmpty = selectedNoteIds.isEmpty();

        if (selectedNoteIds.contains(noteId)) {
            selectedNoteIds.remove(noteId);
        } else {
            selectedNoteIds.add(noteId);
            animatedNoteId = noteId;
        }

        boolean isEmpty = selectedNoteIds.isEmpty();

        if (isEmpty) {
            if (optionsMenuDialog != null && optionsMenuDialog.isShowing()) {
                closeOptionsMenu(null);
            } else {
                notifyDataSetChanged();
            }
        } else if (wasEmpty) {
            updateDialogState();
            notifyDataSetChanged();
        } else {
            updateDialogState();
            notifyItemChanged(position);
        }
    }

    private void resetListViewHolder(NoteViewHolder holder) {
        if (holder.cvSideImage != null) holder.cvSideImage.setVisibility(View.GONE);
        if (holder.ivSideImage != null) holder.ivSideImage.setImageBitmap(null);
        if (holder.tvPreview != null) holder.tvPreview.setVisibility(View.GONE);

        if (holder.llTaskPreview != null) {
            holder.llTaskPreview.setVisibility(View.GONE);
            holder.llTaskPreview.removeAllViews();
        }
        if (holder.llColorPreview != null) {
            holder.llColorPreview.setVisibility(View.GONE);
            holder.llColorPreview.removeAllViews();
        }
    }

    private void handleSelectionState(NoteViewHolder holder, Note note) {
        if (!selectedNoteIds.isEmpty()) {
            holder.selectionIndicatorContainer.setVisibility(View.VISIBLE);

            if (selectedNoteIds.contains(note.getId())) {
                holder.cvSelectedCircle.setVisibility(View.VISIBLE);
                holder.viewUnselectedCircle.setVisibility(View.INVISIBLE);

                if (animatedNoteId == note.getId()) {
                    holder.cvSelectedCircle.setAlpha(0f);
                    holder.cvSelectedCircle.setScaleX(0.5f);
                    holder.cvSelectedCircle.setScaleY(0.5f);
                    holder.cvSelectedCircle.animate()
                            .alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(200).start();
                    animatedNoteId = -1;
                } else {
                    holder.cvSelectedCircle.setAlpha(1f);
                    holder.cvSelectedCircle.setScaleX(1f);
                    holder.cvSelectedCircle.setScaleY(1f);
                }
            } else {
                holder.cvSelectedCircle.setVisibility(View.GONE);
                holder.viewUnselectedCircle.setVisibility(View.VISIBLE);
            }
        } else {
            holder.selectionIndicatorContainer.setVisibility(View.GONE);
        }
    }

    public NoteMetadata parseNoteMetadata(Note note) {
        NoteMetadata meta = metadataCache.get(note.getId());
        if (meta != null) return meta;

        meta = new NoteMetadata();
        String rawHtml = note.getContent() != null ? note.getContent() : "";

        String tempHtml = rawHtml.replaceAll("(?i)class=[\"']([^\"]*checked[^\"]*checkbox[^\"]*|[^\"]*checkbox[^\"]*checked[^\"]*)[\"']", "class=\"checkbox checked\"");
        String processedForTasks = tempHtml
                .replaceAll("(?is)<span[^>]*class=\"checkbox checked\"[^>]*>.*?</span>", "\n☑ ")
                .replaceAll("(?is)<span[^>]*class=[\"'][^\"']*checkbox[^\"']*[\"'][^>]*>.*?</span>", "\n☐ ");

        String rawPlainText;
        if (Build.VERSION.SDK_INT >= 24) {
            rawPlainText = Html.fromHtml(processedForTasks, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\uFFFC", "").trim();
        } else {
            rawPlainText = Html.fromHtml(processedForTasks).toString().replaceAll("\uFFFC", "").trim();
        }
        meta.plainText = rawPlainText;

        String[] lines = rawPlainText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("☑")) {
                String text = line.substring(1).trim();
                if (!text.isEmpty()) meta.tasks.add("1|" + text);
            } else if (line.startsWith("☐")) {
                String text = line.substring(1).trim();
                if (!text.isEmpty()) meta.tasks.add("0|" + text);
            }
        }

        Matcher colorMatcher = Pattern.compile("(?i)(?<![\"'=])#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b").matcher(rawPlainText);
        while (colorMatcher.find()) {
            String c = colorMatcher.group(1).toUpperCase();
            if (c.length() == 3) {
                c = "" + c.charAt(0) + c.charAt(0) + c.charAt(1) + c.charAt(1) + c.charAt(2) + c.charAt(2);
            }
            String colorHex = "#" + c;
            if (!meta.colors.contains(colorHex)) meta.colors.add(colorHex);
            if (meta.colors.size() >= 8) break;
        }

        Matcher imgMatcher = Pattern.compile("(?i)<img[^>]+src=\"([^\"]+)\"").matcher(rawHtml);
        if (imgMatcher.find()) {
            String path = imgMatcher.group(1);
            if (path.startsWith("file://")) path = path.substring(7);
            if (!path.contains("checkbox")) {
                meta.imagePath = path;
            }
        }

        String htmlPreview = rawHtml.replaceAll("(?i)<span[^>]*style=\"[^\"]*text-decoration\\s*:\\s*line-through[^\"]*\"[^>]*>(.*?)</span>", "<s>$1</s>");
        htmlPreview = htmlPreview.replaceAll("(?i)<img[^>]*>", "")
                .replaceAll("(?is)<span[^>]*class=[\"'][^\"']*checkbox[^\"']*[\"'][^>]*>.*?</span>", "")
                .replaceAll("☑", "").replaceAll("☐", "")
                .replaceAll("(?i)<ol[^>]*>", "").replaceAll("(?i)</ol>", "")
                .replaceAll("(?i)<ul[^>]*>", "").replaceAll("(?i)</ul>", "")
                .replaceAll("(?i)<li>", "• ").replaceAll("(?i)</li>", "<br>")
                .replaceAll("(?i)<br[^>]*>", "<br>").replaceAll("(?i)</p>", "<br>").replaceAll("(?i)</div>", "<br>");

        if (Build.VERSION.SDK_INT >= 24) {
            meta.richPreview = Html.fromHtml(htmlPreview, Html.FROM_HTML_MODE_COMPACT);
        } else {
            meta.richPreview = Html.fromHtml(htmlPreview);
        }

        if (meta.richPreview instanceof SpannableStringBuilder) {
            SpannableStringBuilder ssb = (SpannableStringBuilder) meta.richPreview;
            while (ssb.length() > 0 && Character.isWhitespace(ssb.charAt(ssb.length() - 1))) {
                ssb.delete(ssb.length() - 1, ssb.length());
            }
        }

        if (!meta.tasks.isEmpty()) {
            meta.noteType = NoteMetadata.TYPE_TASK_LIST;
        } else if (!meta.colors.isEmpty()) {
            meta.noteType = NoteMetadata.TYPE_COLOR_PALETTE;
        } else if (meta.imagePath != null) {
            meta.noteType = NoteMetadata.TYPE_IMAGE_SIDE;
        } else {
            meta.noteType = NoteMetadata.TYPE_NORMAL;
        }

        metadataCache.put(note.getId(), meta);
        return meta;
    }

    private void loadImageSafely(String imgPath, ImageView targetImageView, int noteId, int position, NoteViewHolder holder) {
        Bitmap cachedBitmap = bitmapCache.get(noteId);
        if (cachedBitmap != null) {
            targetImageView.setImageBitmap(cachedBitmap);
        } else {
            targetImageView.setImageBitmap(null);
            new Thread(() -> {
                try {
                    File imgFile = new File(imgPath);
                    if (imgFile.exists()) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 4;
                        Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath(), options);
                        if (bitmap != null) {
                            bitmapCache.put(noteId, bitmap);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (holder.getAdapterPosition() == position) {
                                    targetImageView.setImageBitmap(bitmap);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private List<Note> getSelectedNotes() {
        List<Note> result = new ArrayList<>();
        for (Note n : adapterNotes) {
            if (selectedNoteIds.contains(n.getId())) {
                result.add(n);
            }
        }
        return result;
    }

    private void updateDialogState() {
        if (optionsMenuDialog != null && optionsMenuDialog.isShowing()) {
            TextView tvSelectAll = optionsMenuDialog.findViewById(R.id.tv_select_all_text);
            boolean isAllSelected = selectedNoteIds.size() == adapterNotes.size() && !adapterNotes.isEmpty();

            if (tvSelectAll != null) {
                if (isAllSelected) {
                    tvSelectAll.setText("Deselect All");
                } else {
                    tvSelectAll.setText("Select All (" + selectedNoteIds.size() + ")");
                }
            }
        }
    }

    private void showNoteOptionsDialog() {
        if (optionsMenuDialog == null) {
            optionsMenuDialog = new Dialog(context);
            optionsMenuDialog.setContentView(R.layout.dialog_note_options);
            optionsMenuDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            optionsMenuDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            optionsMenuDialog.getWindow().setGravity(Gravity.BOTTOM);

            optionsMenuDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            optionsMenuDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);

            optionsMenuDialog.setCancelable(false);

            optionsMenuDialog.getWindow().getDecorView().setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    View menu = optionsMenuDialog.findViewById(R.id.menu_container);
                    if (menu != null) {
                        int[] location = new int[2];
                        menu.getLocationOnScreen(location);
                        int x = (int) event.getRawX();
                        int y = (int) event.getRawY();
                        if (x < location[0] || x > location[0] + menu.getWidth() || y < location[1] || y > location[1] + menu.getHeight()) {
                            closeOptionsMenu(null);
                            return true;
                        }
                    }
                }
                return false;
            });

            optionsMenuDialog.setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    closeOptionsMenu(null);
                    return true;
                }
                return false;
            });

            optionsMenuDialog.setOnDismissListener(dialogInterface -> {
                selectedNoteIds.clear();
                notifyDataSetChanged();
                optionsMenuDialog = null;
            });

            optionsMenuDialog.setOnShowListener(dialogInterface -> {
                View menuContainer = optionsMenuDialog.findViewById(R.id.menu_container);
                if (menuContainer != null) {
                    menuContainer.setTranslationY(300f);
                    menuContainer.setAlpha(0f);
                    menuContainer.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(260)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            });

            optionsMenuDialog.findViewById(R.id.btn_menu_select_all).setOnClickListener(v -> {
                boolean isAllSelected = selectedNoteIds.size() == adapterNotes.size() && !adapterNotes.isEmpty();
                if (isAllSelected) {
                    closeOptionsMenu(null);
                } else {
                    for (Note n : adapterNotes) {
                        selectedNoteIds.add(n.getId());
                    }
                    updateDialogState();
                    notifyDataSetChanged();
                }
            });

            View btnPin = optionsMenuDialog.findViewById(R.id.btn_menu_pin);
            if (btnPin != null) {
                btnPin.setOnClickListener(v -> {
                    List<Note> selectedNotes = getSelectedNotes();
                    closeOptionsMenu(() -> {
                        if (listener != null && !selectedNotes.isEmpty()) listener.onNotesPinToggle(selectedNotes);
                    });
                });
            }

            View btnTag = optionsMenuDialog.findViewById(R.id.btn_menu_tag);
            if (btnTag != null) {
                btnTag.setOnClickListener(v -> {
                    List<Note> selectedNotes = getSelectedNotes();
                    closeOptionsMenu(() -> {
                        if (listener != null && !selectedNotes.isEmpty()) listener.onNotesCategoryChange(selectedNotes);
                    });
                });
            }

            View btnDuplicate = optionsMenuDialog.findViewById(R.id.btn_menu_duplicate);
            if (btnDuplicate != null) {
                btnDuplicate.setOnClickListener(v -> {
                    List<Note> selectedNotes = getSelectedNotes();
                    closeOptionsMenu(() -> {
                        if (listener != null && !selectedNotes.isEmpty()) listener.onNotesDuplicate(selectedNotes);
                    });
                });
            }

            optionsMenuDialog.findViewById(R.id.btn_menu_share).setOnClickListener(v -> {
                List<Note> selectedNotes = getSelectedNotes();
                closeOptionsMenu(() -> {
                    if (listener != null && !selectedNotes.isEmpty()) listener.onNotesShare(selectedNotes);
                });
            });

            optionsMenuDialog.findViewById(R.id.btn_menu_private).setOnClickListener(v -> {
                List<Note> selectedNotes = getSelectedNotes();
                closeOptionsMenu(() -> {
                    if (listener != null && !selectedNotes.isEmpty()) listener.onNotesPrivate(selectedNotes);
                });
            });

            optionsMenuDialog.findViewById(R.id.btn_menu_delete).setOnClickListener(v -> {
                Dialog deleteDialog = new Dialog(context);
                deleteDialog.setContentView(R.layout.dialog_delete);
                deleteDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                deleteDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                deleteDialog.getWindow().setGravity(Gravity.BOTTOM);

                WindowManager.LayoutParams delLp = deleteDialog.getWindow().getAttributes();
                delLp.dimAmount = 0.5f;
                deleteDialog.getWindow().setAttributes(delLp);
                deleteDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                deleteDialog.findViewById(R.id.btn_cancel).setOnClickListener(cancelView -> deleteDialog.dismiss());

                deleteDialog.findViewById(R.id.btn_delete).setOnClickListener(confirmView -> {
                    deleteDialog.dismiss();
                    List<Note> selectedNotes = getSelectedNotes();
                    closeOptionsMenu(() -> {
                        if (listener != null && !selectedNotes.isEmpty()) listener.onNotesDelete(selectedNotes);
                    });
                });

                deleteDialog.show();
            });
        }

        updateDialogState();

        if (!optionsMenuDialog.isShowing()) {
            optionsMenuDialog.show();
        }
    }

    private void closeOptionsMenu(Runnable onEndAction) {
        if (optionsMenuDialog != null && optionsMenuDialog.isShowing()) {
            View menuContainer = optionsMenuDialog.findViewById(R.id.menu_container);
            if (menuContainer != null) {
                menuContainer.animate()
                        .translationY(300f)
                        .alpha(0f)
                        .setDuration(240)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> {
                            if (optionsMenuDialog != null) {
                                optionsMenuDialog.dismiss();
                            }
                            if (onEndAction != null) {
                                onEndAction.run();
                            }
                        })
                        .start();
            } else {
                optionsMenuDialog.dismiss();
                if (onEndAction != null) onEndAction.run();
            }
        } else if (onEndAction != null) {
            onEndAction.run();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return adapterNotes.size();
    }

    public static class NoteMetadata {
        int noteType;
        static final int TYPE_NORMAL = 0;
        static final int TYPE_TASK_LIST = 2;
        static final int TYPE_COLOR_PALETTE = 3;
        static final int TYPE_IMAGE_SIDE = 5;

        Spanned richPreview;
        String plainText;
        String imagePath;
        List<String> tasks = new ArrayList<>();
        List<String> colors = new ArrayList<>();
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        CardView cardLayout, imageContainer;
        View colorStrip;
        TextView tvTitle, tvPreview, tvTime, tvDate;
        TextView tvCategoryBadge, tvTaskProgress;
        ImageView ivImage, ivPinBadge;

        FrameLayout selectionIndicatorContainer;
        View viewUnselectedCircle;
        CardView cvSelectedCircle;

        CardView cvSideImage;
        ImageView ivSideImage;
        LinearLayout llTaskPreview, llColorPreview;

        public NoteViewHolder(View itemView) {
            super(itemView);
            cardLayout = itemView.findViewById(R.id.card_layout);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvPreview = itemView.findViewById(R.id.tv_preview);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvCategoryBadge = itemView.findViewById(R.id.tv_category_badge);
            tvTaskProgress = itemView.findViewById(R.id.tv_task_progress);
            ivPinBadge = itemView.findViewById(R.id.iv_pin_badge);

            selectionIndicatorContainer = itemView.findViewById(R.id.selection_indicator_container);
            viewUnselectedCircle = itemView.findViewById(R.id.view_unselected_circle);
            cvSelectedCircle = itemView.findViewById(R.id.cv_selected_circle);

            imageContainer = itemView.findViewById(R.id.image_preview_container);
            ivImage = itemView.findViewById(R.id.iv_note_image);

            colorStrip = itemView.findViewById(R.id.color_strip);
            cvSideImage = itemView.findViewById(R.id.cv_side_image);
            ivSideImage = itemView.findViewById(R.id.iv_side_image);
            llTaskPreview = itemView.findViewById(R.id.ll_task_preview);
            llColorPreview = itemView.findViewById(R.id.ll_color_preview);
        }
    }

    private static class NoteDiffCallback extends DiffUtil.Callback {
        private final List<Note> oldList;
        private final List<Note> newList;

        public NoteDiffCallback(List<Note> oldList, List<Note> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() { return oldList.size(); }

        @Override
        public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Note oldNote = oldList.get(oldItemPosition);
            Note newNote = newList.get(newItemPosition);

            boolean sameTitle = oldNote.getTitle().equals(newNote.getTitle());
            boolean sameContent = oldNote.getContent() != null ? oldNote.getContent().equals(newNote.getContent()) : newNote.getContent() == null;
            boolean sameColor = oldNote.getPageColor() != null ? oldNote.getPageColor().equals(newNote.getPageColor()) : newNote.getPageColor() == null;
            boolean samePin = oldNote.isPinned() == newNote.isPinned();
            boolean sameCat = oldNote.getCategory().equals(newNote.getCategory());

            return sameTitle && sameContent && sameColor && samePin && sameCat;
        }
    }
}
