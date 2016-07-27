package de.ludetis.android.pandemia.model;

import android.graphics.drawable.Drawable;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.OverlayItem;

import de.ludetis.android.pandemia.GameDatabase;

/**
 * Created by uwe on 27.07.16.
 */
public class BiohazardOverlayItem extends OverlayItem {

    private final String id;
    private final GameDatabase gameDatabase;
    private Drawable markerNormal;
    private Drawable markerVisited;

    public BiohazardOverlayItem(String id, GameDatabase gameDatabase, GeoPoint geoPoint, Drawable markerNormal, Drawable markerVisited) {
        super(id,"",geoPoint);
        this.id = id;
        this.gameDatabase = gameDatabase;

        this.markerNormal = markerNormal;
        this.markerVisited = markerVisited;
    }


    @Override
    public Drawable getMarker(int stateBitset) {
        return gameDatabase.hasVisitedBiohazard(id) ? markerVisited : markerNormal;
    }


    public String getId() {
        return id;
    }
}
