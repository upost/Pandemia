package de.ludetis.android.pandemia;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.greenrobot.event.EventBus;
import de.ludetis.android.pandemia.model.Biohazard;
import de.ludetis.android.pandemia.model.BiohazardOverlayItem;
import de.ludetis.android.pandemia.model.GameEvent;
import de.ludetis.android.pandemia.model.MapEvent;
import de.ludetis.android.pandemia.model.Virus;
import de.ludetis.android.pandemia.view.SingleVirusView;
import de.ludetis.android.pandemia.view.VirusView;
import de.ludetis.android.tools.BaseGameActivity;


public class MainActivity extends BaseGameActivity implements VirusView.OnVirusTappedListener, View.OnClickListener, ItemizedIconOverlay.OnItemGestureListener<BiohazardOverlayItem> {

    private static final String LOG_TAG = "MainActivity";
    public static final String FONT_NAME = "pirulen";
    private GameService gameService;
    private GameDatabase gameDatabase;
    private VirusView virusView;
    private SingleVirusView singleVirusView;
    private TextView virusInfo;
    private TextView zombification;
    private int score=0;
    private Handler handler = new Handler();
    private MyLocationNewOverlay myLocationOverlay;
    private ItemizedIconOverlay<BiohazardOverlayItem> biohazardOverlay;
    private List<BiohazardOverlayItem> items = new ArrayList<BiohazardOverlayItem>();
    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addTypeface("pirulen");

        setContentView(R.layout.activity_main);

        gameDatabase = new GameDatabase(this);

        virusView = (VirusView)findViewById(R.id.virus_view);
        virusView.setListener(this);

        singleVirusView = (SingleVirusView)findViewById(R.id.virus_detail);

        virusInfo = (TextView) findViewById(R.id.virus_info);
        setTypeface(virusInfo, FONT_NAME);

        zombification = (TextView) findViewById(R.id.zombification);
        setTypeface(zombification, FONT_NAME);

        mapView = (MapView) findViewById(R.id.map);

        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        IMapController mapController = mapView.getController();
        mapController.setZoom(16);
        mapView.setMinZoomLevel(16);


        final ITileSource tileSource = TileSourceFactory.DEFAULT_TILE_SOURCE;

        TilesOverlay tilesOverlay = new TilesOverlay(new MapTileProviderBasic(this, tileSource), this);
        // make redish
        //tilesOverlay.setColorFilter(new PorterDuffColorFilter(0xffff8888, PorterDuff.Mode.MULTIPLY));
        // inverse
        tilesOverlay.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {
                -1,  0,  0,  0, 255,
                0, -1,  0,  0, 255,
                0,  0, -1,  0, 255,
                0,  0,  0,  1,   0
        })));

        mapView.getOverlays().add(tilesOverlay);

        biohazardOverlay = new ItemizedIconOverlay<>(items,getResources().getDrawable(R.mipmap.biohazard),this,this);
        mapView.getOverlays().add(biohazardOverlay);
        myLocationOverlay = new MyLocationNewOverlay(mapView);

        mapView.getOverlays().add(myLocationOverlay);

        EventBus.getDefault().register(this);

        Intent intent = new Intent(this, GameService.class);
        bindService(intent, serverConnection, BIND_AUTO_CREATE);
        startService(intent);

    }


    public ServiceConnection serverConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder binder) {
            gameService = ((GameService.GameServiceBinder)binder).getService();
            updateBiohazardOverlay(gameService.getBioHazards());
        }

        public void onServiceDisconnected(ComponentName className) {

        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();

        updateUI();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        //virusDatabase.close();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        myLocationOverlay.disableMyLocation();

        super.onPause();
    }

    public void onEventMainThread(GameEvent gameEvent) {
        updateUI();
        if(gameEvent.getType().equals(GameEvent.Type.NEW_VIRUS)) {
            Virus v = gameDatabase.findVirus(gameEvent.getId());
            TextView tv = new TextView(this);
            tv.setText(getString(R.string.infected_with) + " " + v.getName());
            setTypeface(tv,FONT_NAME);
            tv.setTextColor(getResources().getColor(R.color.lightred));
            Toast toast = new Toast(this);
            toast.setView(tv);
            toast.setGravity(Gravity.CENTER,0,0);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    public void onEventMainThread(MapEvent mapEvent) {
        if(mapEvent.getType().equals(MapEvent.Type.REGION_UPDATED)) {
            Log.d(LOG_TAG," updating biohazard overlay");
            updateBiohazardOverlay(mapEvent.getBiohazards());
        }
        mapView.invalidate();
    }

    private void updateBiohazardOverlay(Set<Biohazard> biohazards) {
        biohazardOverlay.removeAllItems();
        for(Biohazard b : biohazards) {
            biohazardOverlay.addItem(new BiohazardOverlayItem(b.getId(), gameDatabase,
                    new GeoPoint(b.getLocation().getLatitude(), b.getLocation().getLongitude()),
                    getResources().getDrawable(R.mipmap.biohazard), getResources().getDrawable(R.mipmap.biohazard_grey)));
        }
        mapView.invalidate();
    }

    @Override
    public void onVirusTapped(String virusId) {
        Log.d(LOG_TAG, "tapped virus " + virusId);
        Virus v = gameDatabase.findVirus(virusId);
        singleVirusView.setVirus(v);
        showView(R.id.details);

        String s="<b>"+v.getName()+"</b><br/><br/>"
                +getString(R.string.zombification)+" " + v.getStrength() +"<br/><br/>"
                +getString(R.string.mutability)+" " + v.getMutability() + "<br/>";
                //+getString(R.string.strength)+ " " + v.getStrength()+"<br/>";
                //+getString(R.string.stamina)+" "+v.getStamina()+"<br/>";

        virusInfo.setText(Html.fromHtml(s));
    }

    public void updateUI() {
        zombification.setText(Html.fromHtml(getString(R.string.total_zombification)) + " " + calcTotalZombification());
        mapView.invalidate();
    }

    private int calcTotalZombification() {
        int res=0;
        for(String id : gameDatabase.getViruses()) {
            res += gameDatabase.findVirus(id).getStrength();
        }
        return res;
    }

    @Override
    public void onClick(View view) {

    }

    @Override
    public boolean onItemSingleTapUp(int index, BiohazardOverlayItem item) {
        TextView tv = new TextView(this);
        tv.setText(getString(R.string.biohazard) + " " + item.getTitle() + " "
            + (gameDatabase.hasVisitedBiohazard(item.getId())? getString(R.string.visited):""));
        setTypeface(tv,FONT_NAME);
        tv.setTextColor(getResources().getColor(R.color.lightred));
        Toast toast = new Toast(this);
        toast.setView(tv);
        toast.setGravity(Gravity.CENTER,0,0);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
        return false;
    }

    @Override
    public boolean onItemLongPress(int index, BiohazardOverlayItem item) {
        return false;
    }


}
