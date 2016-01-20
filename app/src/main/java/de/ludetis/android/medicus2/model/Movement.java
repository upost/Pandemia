package de.ludetis.android.medicus2.model;

/**
 * Created by uwe on 20.01.16.
 */
public class Movement {
    public float x,y,vx,vy;

    public Movement(float x, float y, float vx,float vy) {
        this.x=x; this.y=y;
        this.vx=vx; this.vy=vy;
    }

    public void move(float t) {
        x+= vx*t;
        y+= vy*t;
    }
}
