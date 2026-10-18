package com.matael.app;

import org.json.JSONException;
import org.json.JSONObject;

public final class MataelNote {
    public String id, title, body, folderId, cover, kind;
    public long created, updated;
    public boolean favorite, pinned;
    public int accentColor;
    public MataelNote() {
        long now=System.currentTimeMillis();
        id=java.util.UUID.randomUUID().toString(); title=""; body="";
        created=now; updated=now; folderId=""; favorite=false; pinned=false;
        accentColor=0; cover=""; kind="note";
    }
    public static MataelNote fromJson(JSONObject json) {
        MataelNote n=new MataelNote();
        n.id=json.optString("id",n.id); n.title=json.optString("title","");
        n.body=json.optString("body",""); n.updated=json.optLong("updated",System.currentTimeMillis());
        n.created=json.optLong("created",n.updated); n.folderId=json.optString("folderId","");
        n.favorite=json.optBoolean("favorite",false); n.pinned=json.optBoolean("pinned",false);
        n.accentColor=json.optInt("accentColor",0); n.cover=json.optString("cover","");
        n.kind=json.optString("kind","note"); return n;
    }
    public JSONObject toJson() throws JSONException {
        JSONObject j=new JSONObject(); j.put("id",id); j.put("title",title); j.put("body",body);
        j.put("updated",updated); j.put("created",created); j.put("folderId",folderId);
        j.put("favorite",favorite); j.put("pinned",pinned); j.put("accentColor",accentColor);
        j.put("cover",cover); j.put("kind",kind); return j;
    }
}
