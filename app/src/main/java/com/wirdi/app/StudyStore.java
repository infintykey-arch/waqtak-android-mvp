package com.wirdi.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public final class StudyStore {
    private static final String PREFS = "wirdi_android_mvp";
    private static final String KEY = "study_cards_v1";
    private StudyStore() {}

    public static ArrayList<StudyCard> load(Context context) {
        ArrayList<StudyCard> cards = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) cards.add(StudyCard.fromJson(array.getJSONObject(i)));
        } catch (Exception ignored) { }
        return cards;
    }

    public static void save(Context context, ArrayList<StudyCard> cards) {
        JSONArray array = new JSONArray();
        for (StudyCard card : cards) {
            try { array.put(card.toJson()); } catch (JSONException ignored) { }
        }
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString(KEY, array.toString()).apply();
    }

    public static StudyCard find(Context context, String id) {
        for (StudyCard c : load(context)) if (c.id.equals(id)) return c;
        return null;
    }
}
