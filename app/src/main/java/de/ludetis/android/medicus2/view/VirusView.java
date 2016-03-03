package de.ludetis.android.medicus2.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

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
public class VirusView extends TextureView implements TextureView.SurfaceTextureListener {



    public interface OnVirusTappedListener {
        void onVirusTapped(String virusId);
    }

    private static final long FRAME_INTERVAL = 30;
    private ScheduledExecutorService executorService;
    private VirusDatabase virusDatabase;
    private BitmapDrawable bg;
    private Rect bgRect;
    private Paint paintBitmap,paintVirus1,paintVirus2;
    private Context context;
    private Map<String,Movement> virusMovement = new HashMap<>();
    private Random rnd = new Random();
    private OnVirusTappedListener listener;

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
        setOpaque(false);
        setSurfaceTextureListener(this);
        setFocusable(false);
        setWillNotDraw(false);
        this.setOnTouchListener(touchListener);
    }

    public VirusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VirusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setListener(OnVirusTappedListener listener) {
        this.listener = listener;
    }

    private OnTouchListener touchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if(listener!=null) {
                if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    return true; // otherwise ACTION_UP will not fire
                }
                if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    float x = motionEvent.getX();
                    float y = motionEvent.getY();
                    float bestDist=scale(5);
                    String bestVirus = null;
                    for(String v : virusMovement.keySet()) {
                        double dist = Math.hypot(x-virusMovement.get(v).x, y-virusMovement.get(v).y);
                        if(dist<bestDist) {
                            bestVirus=v;
                            bestDist=(float)dist;
                        }
                    }
                    if(bestVirus!=null) {
                        listener.onVirusTapped(bestVirus);
                        return true;
                    }
                }
            }
            return false;
        }
    };




    private Runnable renderer = new Runnable() {
        @Override
        public void run() {
            Canvas canvas = null;
            try {
                canvas = lockCanvas();
                movement(canvas.getWidth(), canvas.getHeight());
                doDraw(canvas);
            } finally {
                unlockCanvasAndPost(canvas);
            }
        }
    };

    private void movement(int width,int height) {
        for(String id: virusDatabase.getViruses()) {
            Movement m = virusMovement.get(id);
            if(m==null) {
                m=new Movement(rnd.nextInt(width), rnd.nextInt(height), rnd.nextInt(360), rnd.nextFloat()*2-1, rnd.nextFloat()*2-1, rnd.nextFloat()*2-1);
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
            drawVirus(canvas, m.x, m.y, m.rot, virusDatabase.findVirus(id), scale(1),  paintVirus1, paintVirus2);
            h+=120;
        }
    }

    public static void drawVirus(Canvas canvas, float x, float y, float rot, Virus virus, float scaleFactor, Paint paintVirus1, Paint paintVirus2) {
        paintVirus1.setColor(virus.getColor1());
        paintVirus1.setStyle(Paint.Style.FILL_AND_STROKE);
        paintVirus2.setColor(virus.getColor2());
        paintVirus2.setStrokeWidth(scaleFactor * 0.25f);
        paintVirus2.setStyle(Paint.Style.FILL_AND_STROKE);

        canvas.save();
        canvas.rotate(-rot, x, y);
        canvas.drawCircle(x,y,scaleFactor,paintVirus2);
        float deg=360/virus.getLimbs();
        Random rnd = new Random(virus.getSeed());
        int limbSegments = 3+rnd.nextInt(5);
        for(int limb=0; limb<virus.getLimbs(); limb++) {
            Random rnd2 = new Random(virus.getSeed()+1);
            float sublimbScale=rnd2.nextFloat()/2+1;
            canvas.rotate(deg,x,y);
            for(int segment=0; segment<limbSegments; segment++) {
                Random rnd3 = new Random(rnd2.nextInt(999));
                canvas.drawLine(x, y, x + scaleFactor*(1+1*segment), y, paintVirus1);
                if(rnd2.nextBoolean())
                    canvas.drawCircle(x + scaleFactor*(1+1*segment), y, scaleFactor*0.5f, paintVirus1);
                else {
                    paintVirus2.setStrokeWidth(scaleFactor*(0.2f+rnd3.nextFloat()/3f));
                    canvas.drawLine(x + scaleFactor*(1 + 1 * segment), y, x + scaleFactor*(1 + 1*segment), y + scaleFactor*(sublimbScale), paintVirus2);
                    canvas.drawLine(x + scaleFactor*(1 + 1 * segment), y, x + scaleFactor*(1 + 1*segment), y - scaleFactor*(sublimbScale), paintVirus2);
                }
            }
        }
        canvas.restore();
    }

    private float scale(float c) {
        return context.getResources().getDisplayMetrics().xdpi * c / 25f;
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        virusDatabase = new VirusDatabase(context);
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(renderer, FRAME_INTERVAL, FRAME_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        executorService.shutdown();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }
}
