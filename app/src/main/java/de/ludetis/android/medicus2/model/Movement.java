package de.ludetis.android.medicus2.model;

/**
 * Created by uwe on 20.01.16.
 */
public class Movement {
    public float x,y,vx,vy,rot,rotv;

    public Movement(float x, float y, float rot, float vx,float vy, float rotv) {
        this.x=x; this.y=y;
        this.vx=vx; this.vy=vy;
        this.rot=rot; this.rotv=rotv;
    }

    public void move(float t) {
        x+= vx*t;
        y+= vy*t;
        rot+=rotv*t;
        while(rot>=360) rot-=360;
        while(rot<0) rot+=360;
    }
}
