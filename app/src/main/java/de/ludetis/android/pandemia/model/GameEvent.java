package de.ludetis.android.pandemia.model;

/**
 * Created by uwe on 21.01.16.
 */
public class GameEvent {

    public enum Type { NEW_VIRUS, REGION_UPDATED, INIT, KILLED_VIRUS };
    private Type type;
    private String id;
    private int value;

    public GameEvent(Type type, String id, int value) {
        this.type = type;
        this.id = id;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public int getValue() {
        return value;
    }
}
