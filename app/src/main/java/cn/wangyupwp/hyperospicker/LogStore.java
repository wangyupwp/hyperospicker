package cn.wangyupwp.hyperospicker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class LogStore extends SQLiteOpenHelper {
    static final int LIMIT = 300;
    private static LogStore instance;

    record Entry(long timestamp, String source, PickerLog.Event event, String callerPackage, String callerLabel) { }

    static synchronized LogStore get(Context context) {
        if (instance == null) {
            instance = new LogStore(context.getApplicationContext());
        }
        return instance;
    }

    private LogStore(Context context) {
        super(context, "picker_events.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL,"
                + " source TEXT NOT NULL, event TEXT NOT NULL, caller_package TEXT, caller_label TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion != 1 || newVersion != 2) {
            throw new IllegalStateException("Unsupported log database migration");
        }
        db.execSQL("ALTER TABLE events ADD COLUMN caller_package TEXT");
        db.execSQL("ALTER TABLE events ADD COLUMN caller_label TEXT");
    }

    void record(String source, PickerLog.Event event, String callerPackage, String callerLabel) {
        ContentValues values = new ContentValues();
        values.put("timestamp", System.currentTimeMillis());
        values.put("source", source);
        values.put("event", event.name());
        values.put("caller_package", callerPackage);
        values.put("caller_label", callerLabel);

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.insertOrThrow("events", null, values);
            db.execSQL("DELETE FROM events WHERE _id <= "
                    + "(SELECT _id FROM events ORDER BY _id DESC LIMIT 1 OFFSET ?)", new Object[]{LIMIT});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    List<Entry> latest() {
        var entries = new ArrayList<Entry>();
        try (Cursor rows = getReadableDatabase().query("events",
                new String[]{"timestamp", "source", "event", "caller_package", "caller_label"}, null, null, null, null,
                "_id DESC", Integer.toString(LIMIT))) {
            while (rows.moveToNext()) {
                entries.add(new Entry(rows.getLong(0), rows.getString(1), PickerLog.Event.valueOf(rows.getString(2)),
                        rows.getString(3), rows.getString(4)));
            }
        }
        return entries;
    }

    void clear() {
        getWritableDatabase().delete("events", null, null);
    }
}
