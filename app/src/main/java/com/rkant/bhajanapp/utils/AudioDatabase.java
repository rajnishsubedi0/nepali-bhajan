package com.rkant.bhajanapp.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.rkant.bhajanapp.model.AudioTrack;

import java.util.ArrayList;
import java.util.List;

public class AudioDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "bhajan_audio.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "audio_tracks";

    private static AudioDatabase instance;

    public static synchronized AudioDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new AudioDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private AudioDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id TEXT PRIMARY KEY, " +
                "title TEXT, " +
                "url TEXT, " +
                "duration INTEGER DEFAULT -1, " +
                "category TEXT, " +
                "is_favourite INTEGER DEFAULT 0, " +
                "is_downloaded INTEGER DEFAULT 0, " +
                "local_path TEXT, " +
                "sort_order INTEGER DEFAULT 0" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }


    public void saveTracks(List<AudioTrack> tracks) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < tracks.size(); i++) {
                AudioTrack t = tracks.get(i);
                // Check if track already exists to preserve fav/download state
                AudioTrack existing = getTrackByIdInternal(db, t.id);

                ContentValues cv = new ContentValues();
                cv.put("id", t.id);
                cv.put("title", t.title);
                cv.put("url", t.url);
                cv.put("duration", t.durationSec);
                cv.put("category", t.category);
                cv.put("sort_order", i);

                if (existing != null) {
                    // Preserve existing favourite and download state
                    cv.put("is_favourite", existing.isFavourite ? 1 : 0);
                    cv.put("is_downloaded", existing.isDownloaded ? 1 : 0);
                    cv.put("local_path", existing.localPath);
                } else {
                    cv.put("is_favourite", 0);
                    cv.put("is_downloaded", 0);
                    cv.putNull("local_path");
                }

                db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }


    public List<AudioTrack> getAllTracks() {
        List<AudioTrack> tracks = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE, null, null, null, null, null, "sort_order ASC");

        try {
            while (cursor.moveToNext()) {
                tracks.add(cursorToTrack(cursor));
            }
        } finally {
            cursor.close();
        }
        return tracks;
    }

    public AudioTrack getTrackById(String id) {
        return getTrackByIdInternal(getReadableDatabase(), id);
    }

    private AudioTrack getTrackByIdInternal(SQLiteDatabase db, String id) {
        Cursor cursor = db.query(TABLE, null, "id = ?", new String[]{id}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursorToTrack(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public void toggleFavourite(String id) {
        AudioTrack track = getTrackById(id);
        if (track == null) return;

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_favourite", track.isFavourite ? 0 : 1);
        db.update(TABLE, cv, "id = ?", new String[]{id});
    }


    public boolean isFavourite(String id) {
        AudioTrack track = getTrackById(id);
        return track != null && track.isFavourite;
    }

    public void markDownloaded(String id, String localPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_downloaded", 1);
        cv.put("local_path", localPath);
        db.update(TABLE, cv, "id = ?", new String[]{id});
    }

    public void markNotDownloaded(String id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_downloaded", 0);
        cv.putNull("local_path");
        db.update(TABLE, cv, "id = ?", new String[]{id});
    }


    private AudioTrack cursorToTrack(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
        String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        String url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
        int duration = cursor.getInt(cursor.getColumnIndexOrThrow("duration"));
        String category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
        int isFav = cursor.getInt(cursor.getColumnIndexOrThrow("is_favourite"));
        int isDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow("is_downloaded"));
        String localPath = cursor.getString(cursor.getColumnIndexOrThrow("local_path"));

        AudioTrack track = new AudioTrack(id, title, url, duration);
        track.category = category;
        track.isFavourite = (isFav == 1);
        track.isDownloaded = (isDownloaded == 1);
        track.localPath = localPath;

        if (track.isDownloaded) {
            track.downloadState = AudioTrack.STATE_DOWNLOADED;
        }
        return track;
    }
}