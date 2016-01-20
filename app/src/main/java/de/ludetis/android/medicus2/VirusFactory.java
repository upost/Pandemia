package de.ludetis.android.medicus2;

import android.graphics.Color;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;
import java.util.UUID;

import de.ludetis.android.medicus2.model.Virus;

/**
 * Created by uwe on 20.01.16.
 */
public class VirusFactory {

    public static Random rnd = new Random();

    public static Virus createMutation(Virus v) {
        Virus virus = new Virus(UUID.randomUUID().toString());
        if(v!=null) {
            // TODO take some attributes from v and modify them slightly
        } else {
            virus.init(3+rnd.nextInt(6), Color.rgb(rnd.nextInt(128)+127,rnd.nextInt(128)+127,rnd.nextInt(128)+127),
                    Color.rgb(rnd.nextInt(128)+127,rnd.nextInt(128)+127,rnd.nextInt(128)+127),rnd.nextInt(Integer.MAX_VALUE));

        }
        return virus;
    }

    public static Virus fromJSON(JSONObject o) {
        try {
            Virus v = new Virus(o.getString("id"));
            v.init(o.getInt("limbs"), o.getInt("color1"), o.getInt("color2"), o.getInt("seed"));
            return v;
        } catch (JSONException e) {
            return null;
        }
    }
}
