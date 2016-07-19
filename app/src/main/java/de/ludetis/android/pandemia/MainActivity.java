package de.ludetis.android.pandemia;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.ViewFlipper;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.greenrobot.event.EventBus;
import de.ludetis.android.pandemia.model.GameEvent;
import de.ludetis.android.pandemia.model.MapEvent;
import de.ludetis.android.pandemia.model.Virus;
import de.ludetis.android.pandemia.view.SingleVirusView;
import de.ludetis.android.pandemia.view.VirusView;
import de.ludetis.android.tools.BaseGameActivity;
import de.ludetis.android.tools.SimpleAnimationListener;


public class MainActivity extends BaseGameActivity implements VirusView.OnVirusTappedListener, View.OnClickListener, ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {

    private static final String LOG_TAG = "MainActivity";
    public static final String FONT_NAME = "pirulen";
    private GameService gameService;
    private VirusDatabase virusDatabase;
    private VirusView virusView;
    private SingleVirusView singleVirusView;
    private TextView virusInfo;
    private TextView zombification;
    private int score=0;
    private Handler handler = new Handler();
    private MyLocationNewOverlay myLocationOverlay;
    private ItemizedIconOverlay<OverlayItem> biohazardOverlay;
    private List<OverlayItem> items = new ArrayList<OverlayItem>();
    private ViewFlipper flipper;
    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addTypeface("pirulen");

        setContentView(R.layout.activity_main);

        setTypeface((TextView) findViewById(R.id.title), FONT_NAME);
        findViewById(R.id.title).startAnimation(AnimationUtils.loadAnimation(this, R.anim.fadein));
        // fade out and hide title after 5 seconds
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Animation a = AnimationUtils.loadAnimation(MainActivity.this,R.anim.fadeout);
                a.setAnimationListener(new SimpleAnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        hideView(R.id.title);
                    }
                });
                findViewById(R.id.title).startAnimation(a);
            }
        },5000);

        virusDatabase = new VirusDatabase(this);

        virusView = (VirusView)findViewById(R.id.virus_view);
        virusView.setListener(this);

        singleVirusView = (SingleVirusView)findViewById(R.id.virus_detail);

        virusInfo = (TextView) findViewById(R.id.virus_info);
        setTypeface(virusInfo, FONT_NAME);

        zombification = (TextView) findViewById(R.id.zombification);
        setTypeface(zombification, FONT_NAME);

        setTypeface((TextView) findViewById(R.id.to_map),FONT_NAME);
        findViewById(R.id.to_map).setOnClickListener(this);

        setTypeface((TextView) findViewById(R.id.to_virus),FONT_NAME);
        findViewById(R.id.to_virus).setOnClickListener(this);

        mapView = (MapView) findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        IMapController mapController = mapView.getController();
        mapController.setZoom(16);


        flipper = (ViewFlipper) findViewById(R.id.flipper);

        biohazardOverlay = new ItemizedIconOverlay<>(items,getResources().getDrawable(R.mipmap.biohazard),this,this);
        mapView.getOverlays().add(biohazardOverlay);
        myLocationOverlay = new MyLocationNewOverlay(mapView);
        mapView.getOverlays().add(myLocationOverlay);

        Intent intent = new Intent(this, GameService.class);
        bindService(intent, serverConnection, BIND_AUTO_CREATE);
        startService(intent);

    }

    public ServiceConnection serverConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder binder) {

        }

        public void onServiceDisconnected(ComponentName className) {

        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        EventBus.getDefault().register(this);
        updateUI();
    }

    @Override
    protected void onDestroy() {
        //virusDatabase.close();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        myLocationOverlay.disableMyLocation();
        EventBus.getDefault().unregister(this);
        super.onPause();
    }

    public void onEventMainThread(GameEvent gameEvent) {
        updateUI();
    }

    public void onEventMainThread(MapEvent mapEvent) {
        if(mapEvent.getType().equals(MapEvent.Type.REGION_UPDATED)) {
            updateBiohazardOverlay(mapEvent.getBiohazards());
        }
        mapView.invalidate();
    }

    private void updateBiohazardOverlay(Set<Location> biohazards) {
        biohazardOverlay.removeAllItems();
        for(Location l : biohazards) {
            biohazardOverlay.addItem(new OverlayItem("Biohazard","", new GeoPoint(l.getLatitude(), l.getLongitude())));
        }
    }

    @Override
    public void onVirusTapped(String virusId) {
        Log.d(LOG_TAG, "tapped virus " + virusId);
        Virus v = virusDatabase.findVirus(virusId);
        singleVirusView.setVirus(v);

        String s="<b>"+v.getName()+"</b><br/>"
                +getString(R.string.zombification)+" " + v.getStrength() +"<br/>"
                +getString(R.string.greed)+" " + v.getGreed() + "<br/>"
                +getString(R.string.strength)+ " " + v.getStrength()+"<br/>";
                //+getString(R.string.stamina)+" "+v.getStamina()+"<br/>";

        virusInfo.setText(Html.fromHtml(s));
    }

    public void updateUI() {
        zombification.setText(Html.fromHtml(getString(R.string.zombification)) + " " + calcTotalZombification());
    }

    private int calcTotalZombification() {
        int res=0;
        for(String id : virusDatabase.getViruses()) {
            res += virusDatabase.findVirus(id).getStrength();
        }
        return res;
    }

    @Override
    public void onClick(View view) {
        if(view.getId()==R.id.to_map) {
            flipper.setOutAnimation(this,R.anim.view_transition_out_left);
            flipper.setInAnimation(this,R.anim.view_transition_in_right);
            ((ViewFlipper)findViewById(R.id.flipper)).setDisplayedChild(1);
        }
        if(view.getId()==R.id.to_virus) {
            flipper.setOutAnimation(this,R.anim.view_transition_out_right);
            flipper.setInAnimation(this,R.anim.view_transition_in_left);
            ((ViewFlipper)findViewById(R.id.flipper)).setDisplayedChild(0);
        }
    }

    @Override
    public boolean onItemSingleTapUp(int index, OverlayItem item) {
        return false;
    }

    @Override
    public boolean onItemLongPress(int index, OverlayItem item) {
        return false;
    }


}
