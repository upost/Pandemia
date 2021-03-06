package de.ludetis.android.pandemia.model;

import java.util.Set;

/**
 * Created by uwe on 19.07.16.
 */
public class MapEvent {

    public Type getType() {
        return type;
    }

    public enum Type { REGION_UPDATED,}
    private Type type;
    private long regioncode;
    private Set<Biohazard> biohazards;

    public MapEvent(Type type, long regioncode, Set<Biohazard> biohazards) {
        this.type = type;
        this.regioncode = regioncode;
        this.biohazards = biohazards;
    }

    public long getRegioncode() {
        return regioncode;
    }

    public Set<Biohazard> getBiohazards() {
        return biohazards;
    }
}
