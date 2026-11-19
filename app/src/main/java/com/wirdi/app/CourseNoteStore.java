package com.wirdi.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/** Persists course notes on this device, independently from study progress. */
public final class CourseNoteStore {
    private static final String PREFERENCES = "waqtak_course_notes";
    private static final String KEY_NOTES = "notes_v1";

    private CourseNoteStore() { }

    public static synchronized ArrayList<CourseNote> load(Context context, String courseId) {
        ArrayList<CourseNote> notes = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_NOTES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                CourseNote note = CourseNote.fromJson(object);
                if (courseId != null && courseId.equals(note.courseId)) notes.add(note);
            }
        } catch (Exception ignored) { }
        return notes;
    }

    public static synchronized void add(Context context, CourseNote note) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        JSONArray current;
        try {
            current = new JSONArray(preferences.getString(KEY_NOTES, "[]"));
        } catch (Exception ignored) {
            current = new JSONArray();
        }
        try {
            current.put(note.toJson());
        } catch (JSONException ignored) {
            return;
        }
        preferences.edit().putString(KEY_NOTES, current.toString()).apply();
    }
}
