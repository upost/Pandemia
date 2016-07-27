package de.ludetis.android.pandemia.model;

import android.location.Location;

/**
 * Created by uwe on 27.07.16.
 */
public class Biohazard {

    private String id;
    private Location location;
    private long seed;

    public Biohazard(String id, Location location, long seed) {
        this.id = id;
        this.location = location;
        this.seed = seed;
    }

    public Location getLocation() {
        return location;
    }

    public String getId() {
        return id;
    }

    public long getSeed() {
        return seed;
    }
}
