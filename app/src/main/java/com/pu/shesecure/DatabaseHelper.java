package com.pu.shesecure;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "emergencyDB";
    private static final String TABLE_CONTACT = "contacts";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_NUMBER = "number";
    private static final String COLUMN_FAVORITE = "favorite";

    public DatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_CONTACT + " (" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_NAME + " TEXT NOT NULL, " + COLUMN_NUMBER + " TEXT NOT NULL UNIQUE, " + COLUMN_FAVORITE + " INTEGER DEFAULT 0" + ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACT);
        onCreate(db);
    }

    public boolean insertContact(String name, String number, boolean isFavorite) {
        SQLiteDatabase db = this.getWritableDatabase();
        if (getContactsCount(db) >= 5) return false;

        if (isFavorite) {
            ContentValues reset = new ContentValues();
            reset.put(COLUMN_FAVORITE, 0);
            db.update(TABLE_CONTACT, reset, null, null);
        }

        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name.trim());
        values.put(COLUMN_NUMBER, normalizeNumber(number));
        values.put(COLUMN_FAVORITE, isFavorite ? 1 : 0);

        long result = db.insertWithOnConflict(TABLE_CONTACT, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        return result != -1;
    }

    public boolean updateContact(int id, String name, String number, boolean isFavorite) {
        SQLiteDatabase db = this.getWritableDatabase();

        if (isFavorite) {
            ContentValues reset = new ContentValues();
            reset.put(COLUMN_FAVORITE, 0);
            db.update(TABLE_CONTACT, reset, null, null);
        }

        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name.trim());
        values.put(COLUMN_NUMBER, normalizeNumber(number));
        values.put(COLUMN_FAVORITE, isFavorite ? 1 : 0);

        int rows = db.update(TABLE_CONTACT, values, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
        return rows > 0;
    }

    public boolean deleteContact(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_CONTACT, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
        return rows > 0;
    }

    public List<ContactsRecord> getAllContactsData() {
        List<ContactsRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor c = db.query(TABLE_CONTACT, new String[]{COLUMN_ID, COLUMN_NAME, COLUMN_NUMBER, COLUMN_FAVORITE}, null, null, null, null, COLUMN_FAVORITE + " DESC, " + COLUMN_NAME + " ASC")) {

            while (c.moveToNext()) {
                int id = c.getInt(0);
                String name = c.getString(1);
                String number = c.getString(2);
                boolean favorite = c.getInt(3) == 1;
                list.add(new ContactsRecord(id, name, number, favorite));
            }
        }
        return list;
    }

    public String getFavoriteNumber() {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor c = db.query(TABLE_CONTACT, new String[]{COLUMN_NUMBER}, COLUMN_FAVORITE + "=?", new String[]{"1"}, null, null, null, "1")) {
            if (c.moveToFirst()) return c.getString(0);
            return "";
        }
    }

    public List<String> getAllNumbers() {
        List<String> numbers = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor c = db.query(TABLE_CONTACT, new String[]{COLUMN_NUMBER}, null, null, null, null, null)) {
            while (c.moveToNext()) numbers.add(c.getString(0));
        }
        return numbers;
    }

    public int getContactsCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        return getContactsCount(db);
    }

    private int getContactsCount(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CONTACT, null)) {
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        }
    }

    @NonNull
    private String normalizeNumber(@NonNull String number) {
        return number.replaceAll("\\s+", "");
    }
}