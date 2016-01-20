package de.ludetis.android.medicus2.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Created by uwe on 20.01.16.
 */
public class Virus implements Serializable {

    private String id;
    private int limbs;
    private int color1,color2;
    private int seed;

    public Virus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public JSONObject asJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("id",id);
            o.put("limbs",limbs);
            o.put("color1",color1);
            o.put("color2",color2);
            o.put("seed",seed);
        } catch (JSONException e) {
            //
        }
        return o;
    }

    public int getLimbs() {
        return limbs;
    }

    public int getColor1() {
        return color1;
    }

    public int getColor2() {
        return color2;
    }

    public int getSeed() {
        return seed;
    }

    public void init(int limbs, int color1, int color2, int seed) {
        this.limbs=limbs;
        this.color1=color1;
        this.color2=color2;
        this.seed=seed;
    }
}
