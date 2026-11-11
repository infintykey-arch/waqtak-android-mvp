package com.wirdi.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StudyCard {
    public String id = "";
    public String name = "";
    public String sourceType = "youtube";
    public String youtubeUrl = "";
    public String youtubeId = "";
    public String youtubePlaylistId = "";
    public String localUri = "";
    public String localFileName = "";
    public String scheduledTime = "19:00";
    public static final int ALL_WEEKDAYS = 0x7F;
    // Bits follow java.util.Calendar: Sunday is bit 0, Saturday is bit 6.
    public int weekdaysMask = ALL_WEEKDAYS;
    public int dailyMinutes = 20;
    public int colorIndex = 0;
    public int span = 1;
    public long elapsedToday = 0;
    public long positionSeconds = 0;
    public String lastDate = "";
    public String triggeredDate = "";
    public String completedDate = "";
    public int streak = 0;

    public static final String[] COLORS = {"#438BFF", "#77B9FF", "#AAB8C8", "#47C4C0", "#8E8EFF"};

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public boolean doneToday() { return today().equals(completedDate); }

    public void resetForNewDay() {
        String now = today();
        if (!now.equals(lastDate)) elapsedToday = 0;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("name", name); o.put("sourceType", sourceType);
        o.put("youtubeUrl", youtubeUrl); o.put("youtubeId", youtubeId);
        o.put("youtubePlaylistId", youtubePlaylistId);
        o.put("localUri", localUri); o.put("localFileName", localFileName);
        o.put("scheduledTime", scheduledTime); o.put("weekdaysMask", weekdaysMask);
        o.put("dailyMinutes", dailyMinutes);
        o.put("colorIndex", colorIndex); o.put("span", span);
        o.put("elapsedToday", elapsedToday); o.put("positionSeconds", positionSeconds);
        o.put("lastDate", lastDate); o.put("triggeredDate", triggeredDate);
        o.put("completedDate", completedDate); o.put("streak", streak);
        return o;
    }

    public static StudyCard fromJson(JSONObject o) {
        StudyCard c = new StudyCard();
        c.id = o.optString("id", ""); c.name = o.optString("name", "");
        c.sourceType = o.optString("sourceType", "youtube");
        c.youtubeUrl = o.optString("youtubeUrl", ""); c.youtubeId = o.optString("youtubeId", "");
        c.youtubePlaylistId = o.optString("youtubePlaylistId", "");
        c.localUri = o.optString("localUri", ""); c.localFileName = o.optString("localFileName", "");
        c.scheduledTime = o.optString("scheduledTime", "19:00");
        c.weekdaysMask = o.optInt("weekdaysMask", ALL_WEEKDAYS) & ALL_WEEKDAYS;
        if (c.weekdaysMask == 0) c.weekdaysMask = ALL_WEEKDAYS;
        c.dailyMinutes = Math.max(5, o.optInt("dailyMinutes", 20));
        c.colorIndex = Math.max(0, Math.min(COLORS.length - 1, o.optInt("colorIndex", 0)));
        c.span = Math.max(1, o.optInt("span", 1));
        c.elapsedToday = Math.max(0, o.optLong("elapsedToday", 0));
        c.positionSeconds = Math.max(0, o.optLong("positionSeconds", 0));
        c.lastDate = o.optString("lastDate", ""); c.triggeredDate = o.optString("triggeredDate", "");
        c.completedDate = o.optString("completedDate", ""); c.streak = Math.max(0, o.optInt("streak", 0));
        return c;
    }
}
