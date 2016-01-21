package de.ludetis.android.medicus2.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.ludetis.android.medicus2.R;
import de.ludetis.android.medicus2.VirusDatabase;
import de.ludetis.android.medicus2.model.Movement;
import de.ludetis.android.medicus2.model.Virus;

/**
 * Created by uwe on 20.01.16.
 */
public class VirusView extends SurfaceView implements SurfaceHolder.Callback {

    private static final long FRAME_INTERVAL = 30;
    private ScheduledExecutorService executorService;
    private VirusDatabase virusDatabase;
    private BitmapDrawable bg;
    private Rect bgRect;
    private Paint paintBitmap,paintVirus1,paintVirus2;
    private Context context;
    private Map<String,Movement> virusMovement = new HashMap<>();
    private Random rnd = new Random();

    public VirusView(Context context) {
        super(context);
        init(context);

    }

    private void init(Context context) {
        this.context = context;
        paintBitmap = new Paint();
        paintBitmap.setAntiAlias(true);
        paintVirus1 = new Paint();
        paintVirus1.setAntiAlias(true);
        paintVirus2 = new Paint();
        paintVirus2.setAntiAlias(true);
        bg = (BitmapDrawable) context.getResources().getDrawable(R.drawable.bg);
        bgRect = new Rect(0,0,bg.getBitmap().getWidth(),bg.getBitmap().getHeight());
        getHolder().addCallback(this);
    }

    public VirusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VirusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        virusDatabase = new VirusDatabase(context);
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(renderer, FRAME_INTERVAL, FRAME_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        executorService.shutdown();
        //virusDatabase.close();
    }

    private Runnable renderer = new Runnable() {
        @Override
        public void run() {
            Canvas canvas = null;
            try {
                canvas = getHolder().lockCanvas();
                movement(canvas.getWidth(), canvas.getHeight());
                synchronized (getHolder()) {
                    doDraw(canvas);
                }
            } finally {
                getHolder().unlockCanvasAndPost(canvas);
            }
        }
    };

    private void movement(int width,int height) {
        for(String id: virusDatabase.getViruses()) {
            Movement m = virusMovement.get(id);
            if(m==null) {
                m=new Movement(rnd.nextInt(width), rnd.nextInt(height), rnd.nextFloat()*2-1, rnd.nextFloat()*2-1);
                virusMovement.put(id,m);
            }
            moveVirus(width, height, id,m);
        }
    }

    private void moveVirus(int width,int height, String id, Movement m) {
        m.move(scale(0.05f));
        if(m.x>width) m.vx= -m.vx;
        if(m.y>width) m.vy= -m.vy;
        if(m.x<0) m.vx= -m.vx;
        if(m.y<0) m.vy= -m.vy;
    }

    private void doDraw(Canvas canvas) {

        canvas.drawBitmap(bg.getBitmap(), bgRect, canvas.getClipBounds(), paintBitmap);
        int h=0;
        for(String id: virusDatabase.getViruses()) {
            Movement m = virusMovement.get(id);
            drawVirus(canvas, Math.round(m.x), Math.round(m.y), virusDatabase.findVirus(id));
            h+=120;
        }
    }

    private void drawVirus(Canvas canvas, int x, int y, Virus virus) {
        paintVirus1.setColor(virus.getColor1());
        paintVirus1.setStyle(Paint.Style.FILL_AND_STROKE);
        paintVirus2.setColor(virus.getColor2());
        paintVirus2.setStrokeWidth(scale(0.25f));
        paintVirus2.setStyle(Paint.Style.FILL_AND_STROKE);

        canvas.drawCircle(x,y,scale(1),paintVirus2);
        float deg=360/virus.getLimbs();
        Random rnd = new Random(virus.getSeed());
        int limbSegments = 3+rnd.nextInt(5);
        for(int limb=0; limb<virus.getLimbs(); limb++) {
            Random rnd2 = new Random(virus.getSeed()+1);
            float sublimbScale=rnd2.nextFloat()/2+1;
            canvas.rotate(deg,x,y);
            for(int segment=0; segment<limbSegments; segment++) {
                Random rnd3 = new Random(rnd2.nextInt(999));
                canvas.drawLine(x, y, x + scale(1+1*segment), y, paintVirus1);
                if(rnd2.nextBoolean())
                    canvas.drawCircle(x + scale(1+1*segment), y, scale(0.5f), paintVirus1);
                else {
                    paintVirus2.setStrokeWidth(scale(0.2f+rnd3.nextFloat()/3f));
                    canvas.drawLine(x + scale(1 + 1 * segment), y, x + scale(1 + 1*segment), y + scale(sublimbScale), paintVirus2);
                    canvas.drawLine(x + scale(1 + 1 * segment), y, x + scale(1 + 1*segment), y - scale(sublimbScale), paintVirus2);
                }
            }
        }
    }

    private float scale(float c) {
        return context.getResources().getDisplayMetrics().xdpi * c / 25f;
    }
}
