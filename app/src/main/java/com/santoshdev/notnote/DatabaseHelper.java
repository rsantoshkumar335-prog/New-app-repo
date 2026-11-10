package com.santoshdev.notnote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "notes_db";
    private static final int DATABASE_VERSION = 4;

    private static final String TABLE_NOTES = "notes";
    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_CONTENT = "content";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_IS_PRIVATE = "is_private";
    private static final String COL_PAGE_COLOR = "page_color";
    private static final String COL_IS_PINNED = "is_pinned";
    private static final String COL_CATEGORY = "category";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NOTES + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TITLE + " TEXT, " +
                COL_CONTENT + " TEXT, " +
                COL_TIMESTAMP + " INTEGER, " +
                COL_IS_PRIVATE + " INTEGER DEFAULT 0, " +
                COL_PAGE_COLOR + " TEXT DEFAULT '#FFFFFF', " +
                COL_IS_PINNED + " INTEGER DEFAULT 0, " +
                COL_CATEGORY + " TEXT DEFAULT 'General')";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NOTES + " ADD COLUMN " + COL_IS_PRIVATE + " INTEGER DEFAULT 0");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NOTES + " ADD COLUMN " + COL_PAGE_COLOR + " TEXT DEFAULT '#FFFFFF'");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NOTES + " ADD COLUMN " + COL_IS_PINNED + " INTEGER DEFAULT 0");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_NOTES + " ADD COLUMN " + COL_CATEGORY + " TEXT DEFAULT 'General'");
            } catch (Exception ignored) {}
        }
    }

    public long insertNote(String title, String content, String pageColor) {
        return insertNote(title, content, pageColor, false, "General", false);
    }

    public long insertNote(String title, String content, String pageColor, boolean isPinned, String category) {
        return insertNote(title, content, pageColor, isPinned, category, false);
    }

    public long insertNote(String title, String content, String pageColor, boolean isPinned, String category, boolean isPrivate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title != null ? title : "");
        values.put(COL_CONTENT, content != null ? content : "");
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        values.put(COL_IS_PRIVATE, isPrivate ? 1 : 0);
        values.put(COL_PAGE_COLOR, pageColor != null ? pageColor : "#FFFFFF");
        values.put(COL_IS_PINNED, isPinned ? 1 : 0);
        values.put(COL_CATEGORY, (category != null && !category.isEmpty()) ? category : "General");
        return db.insert(TABLE_NOTES, null, values);
    }

    public int updateNote(int id, String title, String content, String pageColor) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title != null ? title : "");
        values.put(COL_CONTENT, content != null ? content : "");
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        values.put(COL_PAGE_COLOR, pageColor != null ? pageColor : "#FFFFFF");
        return db.update(TABLE_NOTES, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public int updateNote(int id, String title, String content, String pageColor, boolean isPinned, String category) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title != null ? title : "");
        values.put(COL_CONTENT, content != null ? content : "");
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        values.put(COL_PAGE_COLOR, pageColor != null ? pageColor : "#FFFFFF");
        values.put(COL_IS_PINNED, isPinned ? 1 : 0);
        values.put(COL_CATEGORY, (category != null && !category.isEmpty()) ? category : "General");
        return db.update(TABLE_NOTES, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void updateNoteContentOnly(int id, String content) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_CONTENT, content != null ? content : "");
        db.update(TABLE_NOTES, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void updateNotePin(int id, boolean isPinned) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IS_PINNED, isPinned ? 1 : 0);
        db.update(TABLE_NOTES, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void updateNoteCategory(int id, String category) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_CATEGORY, (category != null && !category.isEmpty()) ? category : "General");
        db.update(TABLE_NOTES, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public long duplicateNote(Note source) {
        if (source == null) return -1;
        String copyTitle = source.getTitle().isEmpty() ? "Copy of Note" : source.getTitle() + " (Copy)";
        return insertNote(copyTitle, source.getContent(), source.getPageColor(), source.isPinned(), source.getCategory(), source.isPrivate());
    }

    public void deleteNote(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NOTES, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    private Note readNoteFromCursor(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
        String content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT));
        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
        boolean isPrivate = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_PRIVATE)) == 1;
        String pageColor = cursor.getString(cursor.getColumnIndexOrThrow(COL_PAGE_COLOR));
        if (pageColor == null || pageColor.isEmpty()) pageColor = "#FFFFFF";

        boolean isPinned = false;
        int pinIdx = cursor.getColumnIndex(COL_IS_PINNED);
        if (pinIdx != -1) {
            isPinned = cursor.getInt(pinIdx) == 1;
        }

        String category = "General";
        int catIdx = cursor.getColumnIndex(COL_CATEGORY);
        if (catIdx != -1) {
            String rawCat = cursor.getString(catIdx);
            if (rawCat != null && !rawCat.trim().isEmpty()) {
                category = rawCat;
            }
        }

        return new Note(id, title, content, timestamp, isPrivate, pageColor, isPinned, category);
    }

    public List<Note> getAllNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_NOTES +
                " WHERE " + COL_IS_PRIVATE + " = 0" +
                " ORDER BY " + COL_IS_PINNED + " DESC, " + COL_TIMESTAMP + " DESC",
                null
        );

        if (cursor.moveToFirst()) {
            do {
                notes.add(readNoteFromCursor(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return notes;
    }

    public List<Note> getPrivateNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_NOTES +
                " WHERE " + COL_IS_PRIVATE + " = 1" +
                " ORDER BY " + COL_IS_PINNED + " DESC, " + COL_TIMESTAMP + " DESC",
                null
        );

        if (cursor.moveToFirst()) {
            do {
                notes.add(readNoteFromCursor(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return notes;
    }

    public void updateNotePrivacy(int id, boolean isPrivate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IS_PRIVATE, isPrivate ? 1 : 0);
        db.update(TABLE_NOTES, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }
}
