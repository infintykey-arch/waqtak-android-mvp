package com.wirdi.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** A locally stored note attached to one study course. */
public final class CourseNote {
    public final String id;
    public final String courseId;
    public final String title;
    public final String text;
    public final long createdAtMillis;

    public CourseNote(String courseId, String title, String text) {
        this(UUID.randomUUID().toString(), courseId, title, text, System.currentTimeMillis());
    }

    private CourseNote(String id, String courseId, String title, String text, long createdAtMillis) {
        this.id = id == null ? "" : id;
        this.courseId = courseId == null ? "" : courseId;
        this.title = title == null ? "" : title;
        this.text = text == null ? "" : text;
        this.createdAtMillis = Math.max(0, createdAtMillis);
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("courseId", courseId);
        object.put("title", title);
        object.put("text", text);
        object.put("createdAtMillis", createdAtMillis);
        return object;
    }

    static CourseNote fromJson(JSONObject object) {
        return new CourseNote(
                object.optString("id", ""),
                object.optString("courseId", ""),
                object.optString("title", ""),
                object.optString("text", ""),
                object.optLong("createdAtMillis", 0));
    }
}
