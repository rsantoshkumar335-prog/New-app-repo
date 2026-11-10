package com.santoshdev.notnote;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.santoshdev.notnote.R;

public class PrivateNotesActivity extends Activity {

    private RecyclerView recyclerPrivateNotes;
    private ImageView btnBackPrivate;
    private LinearLayout emptyStatePrivate;
    
    private PrivateNotesAdapter adapter;
    private List<Note> privateNotes;
    private DatabaseHelper dbHelper;
    
    private SharedPreferences prefs;
    private boolean isGridView = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_notes);

        dbHelper = new DatabaseHelper(this);
        privateNotes = new ArrayList<>();

        recyclerPrivateNotes = findViewById(R.id.recycler_private_notes);
        btnBackPrivate = findViewById(R.id.btn_back_private);
        emptyStatePrivate = findViewById(R.id.empty_state_private);

        prefs = getSharedPreferences("NotNotePrefs", MODE_PRIVATE);
        isGridView = prefs.getBoolean("isGridView", false);

        if (isGridView) {
            StaggeredGridLayoutManager staggeredManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
            staggeredManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
            recyclerPrivateNotes.setLayoutManager(staggeredManager);
        } else {
            recyclerPrivateNotes.setLayoutManager(new LinearLayoutManager(this));
        }

        adapter = new PrivateNotesAdapter();
        recyclerPrivateNotes.setAdapter(adapter);

        btnBackPrivate.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPrivateNotes();
    }

    private void loadPrivateNotes() {
        privateNotes.clear();
        privateNotes.addAll(dbHelper.getPrivateNotes()); 
        
        if (privateNotes.isEmpty()) {
            emptyStatePrivate.setVisibility(View.VISIBLE);
            recyclerPrivateNotes.setVisibility(View.GONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            emptyStatePrivate.setVisibility(View.GONE);
            recyclerPrivateNotes.setVisibility(View.VISIBLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        
        if (adapter != null) {
            adapter.clearSelection();
            adapter.notifyDataSetChanged();
        }
    }

    private class PrivateNotesAdapter extends RecyclerView.Adapter<PrivateNotesAdapter.NoteViewHolder> {
        
        private Set<Integer> selectedNoteIds = new HashSet<>();
        private int animatedNoteId = -1;
        private Dialog optionsMenuDialog;

        public void clearSelection() {
            selectedNoteIds.clear();
            if (optionsMenuDialog != null && optionsMenuDialog.isShowing()) {
                optionsMenuDialog.dismiss();
            }
        }

        @Override
        public NoteViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            int layoutId = isGridView ? R.layout.item_note_grid : R.layout.item_note_list;
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new NoteViewHolder(view);
        }

        @Override
        public void onBindViewHolder(NoteViewHolder holder, int position) {
            final Note note = privateNotes.get(position);
            holder.tvTitle.setText(note.getTitle());
            
            String rawContent = note.getContent() != null ? note.getContent() : "";
            String decodedHtml;
            if (Build.VERSION.SDK_INT >= 24) {
                decodedHtml = Html.fromHtml(rawContent, Html.FROM_HTML_MODE_LEGACY).toString();
            } else {
                decodedHtml = Html.fromHtml(rawContent).toString();
            }
            String cleanPreview = decodedHtml.replaceAll("\uFFFC", " ").replaceAll("\\s+", " ").trim();
            holder.tvPreview.setText(cleanPreview);
            
            Date date = new Date(note.getTimestamp());
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
            
            if (holder.tvTime != null) holder.tvTime.setText(timeFormat.format(date));
            if (holder.tvDate != null) holder.tvDate.setText(dateFormat.format(date));
            
            // Set Base Colors 
            String colorHex = note.getPageColor();
            if (colorHex == null || colorHex.isEmpty()) colorHex = "#F4F4F4";
            int baseColor = Color.parseColor("#F4F4F4");
            try { baseColor = Color.parseColor(colorHex); } catch (Exception e) {}

            if (isGridView) {
                if (holder.cardLayout != null) holder.cardLayout.setCardBackgroundColor(baseColor);
            } else {
                if (holder.colorStrip != null) holder.colorStrip.setBackgroundColor(baseColor);
                if (holder.cardLayout != null) {
                    float[] hsv = new float[3];
                    Color.colorToHSV(baseColor, hsv);
                    hsv[1] = Math.min(hsv[1] * 0.15f, 0.2f); 
                    hsv[2] = Math.max(hsv[2], 0.95f); 
                    holder.cardLayout.setCardBackgroundColor(Color.HSVToColor(hsv));
                }
            }

            // New Selection State Logic
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

            // Normal Click
            holder.itemView.setOnClickListener(v -> {
                if (!selectedNoteIds.isEmpty()) {
                    toggleSelection(note.getId(), position);
                } else {
                    Intent intent = new Intent(PrivateNotesActivity.this, EditorActivity.class);
                    intent.putExtra("note_id", note.getId());
                    intent.putExtra("note_title", note.getTitle());
                    intent.putExtra("note_content", note.getContent());
                    intent.putExtra("note_color", note.getPageColor());
                    startActivity(intent);
                }
            });

            // Long Click
            holder.itemView.setOnLongClickListener(v -> {
                if (!selectedNoteIds.contains(note.getId())) {
                    toggleSelection(note.getId(), position);
                }
                if (optionsMenuDialog == null || !optionsMenuDialog.isShowing()) {
                    showNoteOptionsDialog();
                }
                return true;
            });
        }

        private void toggleSelection(int noteId, int position) {
            boolean wasEmpty = selectedNoteIds.isEmpty();
            
            if (selectedNoteIds.contains(noteId)) {
                selectedNoteIds.remove(noteId);
            } else {
                selectedNoteIds.add(noteId);
                animatedNoteId = noteId;
            }
            
            if (selectedNoteIds.isEmpty()) {
                if (optionsMenuDialog != null && optionsMenuDialog.isShowing()) {
                    optionsMenuDialog.dismiss();
                }
                notifyDataSetChanged(); 
            } else if (wasEmpty) {
                notifyDataSetChanged(); 
            } else {
                notifyItemChanged(position);
            }
        }

        private void showNoteOptionsDialog() {
            if (optionsMenuDialog == null) {
                optionsMenuDialog = new Dialog(PrivateNotesActivity.this);
                optionsMenuDialog.setContentView(R.layout.dialog_remove_private);
                optionsMenuDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                optionsMenuDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                optionsMenuDialog.getWindow().setGravity(Gravity.BOTTOM);
                
                // Allow touches outside the dialog without closing it (for persistent multiple selection)
                optionsMenuDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                optionsMenuDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
                optionsMenuDialog.setCanceledOnTouchOutside(false);

                optionsMenuDialog.setOnDismissListener(dialogInterface -> {
                    selectedNoteIds.clear();
                    notifyDataSetChanged();
                    optionsMenuDialog = null;
                });

                optionsMenuDialog.setOnShowListener(dialogInterface -> {
                    View menuContainer = optionsMenuDialog.findViewById(R.id.menu_container_private);
                    if (menuContainer != null) {
                        menuContainer.setTranslationY(300f);
                        menuContainer.setAlpha(0f);
                        menuContainer.animate()
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(400)
                                .setInterpolator(new OvershootInterpolator(1.0f))
                                .start();
                    }
                });

                CardView btnMenuRemovePrivate = optionsMenuDialog.findViewById(R.id.btn_menu_remove_private);
                if (btnMenuRemovePrivate != null) {
                    btnMenuRemovePrivate.setOnClickListener(removeView -> {
                        int count = selectedNoteIds.size();
                        for (int id : selectedNoteIds) {
                            dbHelper.updateNotePrivacy(id, false);
                        }
                        optionsMenuDialog.dismiss();
                        Toast.makeText(PrivateNotesActivity.this, count + " Note(s) moved back to Dashboard", Toast.LENGTH_SHORT).show();
                        loadPrivateNotes(); 
                    });
                }
            }

            if (!optionsMenuDialog.isShowing()) {
                optionsMenuDialog.show();
            }
        }

        @Override
        public int getItemCount() {
            return privateNotes.size();
        }

        class NoteViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvPreview, tvTime, tvDate;
            
            // UI elements for the new Selection Indicator
            FrameLayout selectionIndicatorContainer;
            View viewUnselectedCircle;
            CardView cvSelectedCircle, cardLayout;
            View colorStrip;
            
            public NoteViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvPreview = itemView.findViewById(R.id.tv_preview);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvDate = itemView.findViewById(R.id.tv_date);
                
                cardLayout = itemView.findViewById(R.id.card_layout);
                colorStrip = itemView.findViewById(R.id.color_strip);

                // Assigned new variables instead of the old "selection_overlay"
                selectionIndicatorContainer = itemView.findViewById(R.id.selection_indicator_container);
                viewUnselectedCircle = itemView.findViewById(R.id.view_unselected_circle);
                cvSelectedCircle = itemView.findViewById(R.id.cv_selected_circle);
            }
        }
    }
}
