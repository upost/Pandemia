package de.ludetis.android.medicus2.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.ludetis.android.medicus2.model.Virus;

/**
 * Created by uwe on 21.01.16.
 */
public class SingleVirusView extends SurfaceView implements SurfaceHolder.Callback {

    private static final long FRAME_INTERVAL = 30;
    private ScheduledExecutorService executorService;

    private Virus virus;
    private Paint paintVirus1,paintVirus2;
    private float rot;
    private float rotv;
    private Random rnd = new Random();

    public SingleVirusView(Context context) {
        super(context);
        init();
    }

    public SingleVirusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SingleVirusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintVirus1 = new Paint();
        paintVirus1.setAntiAlias(true);
        paintVirus2 = new Paint();
        paintVirus2.setAntiAlias(true);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        getHolder().addCallback(this);
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(renderer, FRAME_INTERVAL, FRAME_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        executorService.shutdown();
    }

    public Virus getVirus() {
        return virus;
    }

    public void setVirus(Virus virus) {
        this.virus = virus;
        rotv = rnd.nextFloat()*2-1;
    }


    private Runnable renderer = new Runnable() {
        @Override
        public void run() {
            Canvas canvas = null;
            try {
                canvas = getHolder().lockCanvas();
                synchronized (getHolder()) {
                    rot+= rotv;
                    // clear canvas
                    canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                    doDraw(canvas);
                }
            } finally {
                getHolder().unlockCanvasAndPost(canvas);
            }
        }
    };

    private void doDraw(Canvas canvas) {
        if(virus!=null)
            VirusView.drawVirus(canvas, canvas.getWidth()/2, canvas.getHeight()/2, rot,virus,Math.min(canvas.getWidth(),canvas.getHeight())/15f,paintVirus1,paintVirus2);
    }
}
