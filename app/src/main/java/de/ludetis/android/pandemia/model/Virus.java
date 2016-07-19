package de.ludetis.android.pandemia.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Created by uwe on 20.01.16.
 */
public class Virus implements Serializable {

    private String id;
    private String name;
    private int limbs;
    private int color1,color2;
    private int seed;
    private int strength;
    private int mutability;
    private int stamina;
    private int greed;

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
            o.put("name",name);
            o.put("limbs",limbs);
            o.put("color1",color1);
            o.put("color2",color2);
            o.put("seed",seed);
            o.put("strength",strength);
            o.put("mutability",mutability);
            o.put("stamina",stamina);
            o.put("greed",greed);
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

    public int getStrength() {
        return strength;
    }

    public int getMutability() {
        return mutability;
    }

    public int getStamina() {
        return stamina;
    }

    public String getName() {
        return name;
    }

    public int getGreed() {
        return greed;
    }

    public void init(String name, int limbs, int color1, int color2, int seed, int strength, int mutability, int stamina, int greed) {
        this.name=name;
        this.limbs=limbs<3?3:limbs;
        this.color1=color1;
        this.color2=color2;
        this.seed=seed;
        this.strength = strength;
        this.mutability = mutability;
        this.stamina = stamina;
        this.greed = greed;
    }
}
