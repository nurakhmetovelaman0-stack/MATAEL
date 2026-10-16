
package com.matael.app;

import org.json.JSONException;
import org.json.JSONObject;

public final class MataelNote {

    public String id;
    public String title;
    public String body;

    public long created;
    public long updated;

    public String folderId;
    public boolean favorite;
    public boolean pinned;

    public int accentColor;
    public String cover;
    public String kind;

    public MataelNote() {
        long now = System.currentTimeMillis();

        id = java.util.UUID.randomUUID().toString();
        title = "";
        body = "";

        created = now;
        updated = now;

        folderId = "";
        favorite = false;
        pinned = false;

        accentColor = 0;
        cover = "";
        kind = "note";
    }

    public static MataelNote fromJson(JSONObject json) {
        MataelNote note = new MataelNote();

        note.id = json.optString("id", note.id);
        note.title = json.optString("title", "");
        note.body = json.optString("body", "");

        note.updated = json.optLong("updated",
                System.currentTimeMillis());

        note.created = json.optLong("created", note.updated);

        note.folderId = json.optString("folderId", "");
        note.favorite = json.optBoolean("favorite", false);
        note.pinned = json.optBoolean("pinned", false);

        note.accentColor = json.optInt("accentColor", 0);
        note.cover = json.optString("cover", "");
        note.kind = json.optString("kind", "note");

        return note;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("id", id);
        json.put("title", title);
        json.put("body", body);
        json.put("updated", updated);

        json.put("created", created);
        json.put("folderId", folderId);
        json.put("favorite", favorite);
        json.put("pinned", pinned);

        json.put("accentColor", accentColor);
        json.put("cover", cover);
        json.put("kind", kind);

        return json;
    }
}
