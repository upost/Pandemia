package de.ludetis.android.medicus2;

import android.content.Context;

import org.json.JSONArray;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.NavigableSet;

import de.ludetis.android.medicus2.model.Virus;

/**
 * Created by uwe on 20.01.16.
 */
public class VirusDatabase {

    private final BTreeMap<String, Virus> map;
    private DB db;

    public VirusDatabase(Context context) {
        this.db = DBMaker.newFileDB(new File(context.getDir("db",0),"virus.db")).make();
        map = db.getTreeMap("v");
    }

    public void  close() {
        db.commit();
        db.close();
    }

    public void addVirus(Virus v) {
        map.put(v.getId(), v);
        db.commit();
    }

    public NavigableSet<String> getViruses() {
        return map.keySet();
    }

    public Virus findVirus(String id) {
        return map.get(id);
    }

    public String findAllVirusDataAsJson() {
        JSONArray res = new JSONArray();
        NavigableSet<String> viruses = getViruses();
        for(String id : viruses) {
            Virus v = findVirus(id);
            res.put( v.asJSON() );
        }
        return res.toString();
    }
}
