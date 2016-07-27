package de.ludetis.android.pandemia;

import android.content.Context;

import org.json.JSONArray;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.NavigableSet;

import de.ludetis.android.pandemia.model.Virus;

/**
 * Created by uwe on 20.01.16.
 */
public class GameDatabase {

    private static BTreeMap<String, Virus> virusMap;
    private static NavigableSet<String> visitedBiohazardsSet;
    private static BTreeMap<String, String> settingsMap;
    private static DB db;

    public GameDatabase(Context context) {
        if(db==null)
            db = DBMaker.newFileDB(new File(context.getDir("db",0),"pandemia.db")).make();
        if(virusMap ==null)
            virusMap = db.getTreeMap("virus.v3");
        if(visitedBiohazardsSet==null)
            visitedBiohazardsSet = db.getTreeSet("visited_biohazards.v1");
        if(settingsMap == null)
            settingsMap = db.getTreeMap("settings.v1");

    }

    public void  close() {
        db.commit();
        db.close();
    }

    public void addVirus(Virus v) {
        virusMap.put(v.getId(), v);
        db.commit();
    }

    public NavigableSet<String> getViruses() {
        return virusMap.keySet();
    }

    public Virus findVirus(String id) {
        return virusMap.get(id);
    }

    public JSONArray findAllVirusDataAsJson() {
        JSONArray res = new JSONArray();
        NavigableSet<String> viruses = getViruses();
        for(String id : viruses) {
            Virus v = findVirus(id);
            res.put( v.asJSON() );
        }
        return res;
    }

    public void removeVirus(String id) {
        virusMap.remove(id);
        db.commit();
    }

    public void addVisitedBiohazard(String id) {
        visitedBiohazardsSet.add(id);
        db.commit();
    }

    public boolean hasVisitedBiohazard(String id) {
        return visitedBiohazardsSet.contains(id);
    }

    public void putSetting(String key, String value) {
        settingsMap.put(key,value);
    }

    public String getSetting(String key,String fallback) {
        if(settingsMap.containsKey(key))
            return settingsMap.get(key);
        return fallback;
    }
}
