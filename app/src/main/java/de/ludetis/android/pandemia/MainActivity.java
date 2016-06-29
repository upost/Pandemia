package de.ludetis.android.pandemia;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import de.greenrobot.event.EventBus;
import de.ludetis.android.pandemia.model.GameEvent;
import de.ludetis.android.pandemia.model.Virus;
import de.ludetis.android.pandemia.view.SingleVirusView;
import de.ludetis.android.pandemia.view.VirusView;
import de.ludetis.android.tools.BaseGameActivity;
import de.ludetis.android.tools.SimpleAnimationListener;


public class MainActivity extends BaseGameActivity implements VirusView.OnVirusTappedListener {

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
        EventBus.getDefault().unregister(this);
        super.onPause();
    }

    public void onEventMainThread(GameEvent gameEvent) {
        updateUI();
    }

    @Override
    public void onVirusTapped(String virusId) {
        Log.d(LOG_TAG, "tapped virus " + virusId);
        Virus v = virusDatabase.findVirus(virusId);
        singleVirusView.setVirus(v);

        String s="<b>"+v.getName()+"</b><br/>"
                +getString(R.string.zombification)+" " + v.getStrength() +"<br/>"
                +getString(R.string.mutability)+" " + v.getMutability() + "<br/>";
                //+getString(R.string.stamina)+" "+v.getStamina()+"<br/>";

        virusInfo.setText(Html.fromHtml(s));
    }

    public void updateUI() {
        zombification.setText(Html.fromHtml(getString(R.string.total_zombification)) + " " + calcTotalZombification());
    }

    private int calcTotalZombification() {
        int res=0;
        for(String id : virusDatabase.getViruses()) {
            res += virusDatabase.findVirus(id).getStrength();
        }
        return res;
    }
}
