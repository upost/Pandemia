package de.ludetis.android.medicus2;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import de.ludetis.android.tools.BaseGameActivity;


public class MainActivity extends BaseGameActivity {

    GameService gameService;
    private VirusDatabase virusDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addTypeface("pirulen");

        setContentView(R.layout.activity_main);

        setTypeface((TextView) findViewById(R.id.title), "pirulen");
        findViewById(R.id.title).startAnimation(AnimationUtils.loadAnimation(this,R.anim.fadein));

        virusDatabase = new VirusDatabase(this);

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
    protected void onDestroy() {
        virusDatabase.close();
        super.onDestroy();
    }
}
