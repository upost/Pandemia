package de.ludetis.android.medicus2;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;

import java.util.NavigableSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.ludetis.android.medicus2.model.Virus;

public class GameService extends Service implements LocationListener, IMqttActionListener, MqttCallback {
    private static final String BROKER_URI = "tcp://h8.ludetis-spiele.de";
    private static final double GRID_SIZE_FACTOR = 10; // grid size = 1/GRID_SIZE_FACTOR °
    private static final String LOG_TAG = "GameService";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    private MqttAndroidClient mqttClient;
    private String clientId;
    private LocationManager locationManager;
    private String currentTopic;
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private VirusDatabase virusDatabase;

    public GameService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return  null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // MQTT
        clientId = UUID.randomUUID().toString();
        mqttClient = new MqttAndroidClient(this, BROKER_URI, clientId);
        mqttClient.setCallback(this);

        // Location
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,this);
        else
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,0,0,this);

        try {
            mqttClient.connect(null, this);
            Log.d(LOG_TAG, "connected to MQTT broker as " + clientId);
        } catch (MqttException e) {
            Log.e(LOG_TAG, "could not connect to MQTT broker at " + BROKER_URI);
        }

        virusDatabase = new VirusDatabase(this);

        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    heartbeat();
                } catch(Exception e) {
                    Log.e(LOG_TAG, "Exception during heartbeat",e);
                }
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void heartbeat() {

        mutation();

        if(currentTopic!=null) {
            String payload = virusDatabase.findAllVirusDataAsJson();
            try {
                Log.d(LOG_TAG, "publishing to " + currentTopic + ": " + payload);
                mqttClient.publish(currentTopic, new MqttMessage(payload.getBytes()));
            } catch (MqttException e) {
                // TODO
            }
        } else {
            Log.d(LOG_TAG, "waiting for location...");
        }
    }

    private void mutation() {
        NavigableSet<String> viruses = virusDatabase.getViruses();
        if(viruses.isEmpty()) {
            Virus v = VirusFactory.createMutation(null);
            Log.d(LOG_TAG, "new virus: " + v.getId());
            virusDatabase.addVirus(v);
        } else {
            // TODO mutation
        }

    }

    @Override
    public void onDestroy() {
        virusDatabase.close();

        executorService.shutdown();

        locationManager.removeUpdates(this);

        try {
            mqttClient.disconnect();
        } catch (MqttException e) {
            // TODO
        }

        super.onDestroy();
    }

    // LocationListener impl
    @Override
    public void onLocationChanged(Location location) {
        String topic = topic(location);
        if(!topic.equals(currentTopic)) {
            Log.d(LOG_TAG, "new grid location: " + topic);
            switchToTopic(topic);
        }
    }

    private void switchToTopic(String newTopic) {
        if(mqttClient.isConnected()) {
            try {
                if(currentTopic!=null)
                    mqttClient.unsubscribe(currentTopic);
                mqttClient.subscribe(newTopic, 0);
                currentTopic = newTopic;
                Log.d(LOG_TAG, "subscribed to " + currentTopic);
            } catch (MqttException e) {
                Log.w(LOG_TAG, "could not subscribe to: " + newTopic + ": ", e);
            }
        }

    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
        Log.v(LOG_TAG, "location status: " + s);
    }

    @Override
    public void onProviderEnabled(String s) {
        Log.v(LOG_TAG, "location provider enabled: " + s);
    }

    @Override
    public void onProviderDisabled(String s) {
        Log.v(LOG_TAG, "location provider disabled: " + s);
    }

    public static String topic(Location l) {
        return "pandemie/grid/" + Long.toString(Math.round(l.getLatitude()*GRID_SIZE_FACTOR))+"_"+Long.toString(Math.round(l.getLongitude()*GRID_SIZE_FACTOR));
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        Log.v(LOG_TAG, "mqtt success");
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.w(LOG_TAG, "mqtt failure: ", exception);
    }

    // MqttCallback
    @Override
    public void connectionLost(Throwable cause) {
        Log.w(LOG_TAG, "mqtt connection lost", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        if(topic.equals(currentTopic)) {
            String msg = new String( message.getPayload() );
            Log.v(LOG_TAG, "mqtt message arrived: " + msg);
            //TODO process message
            JSONArray a = new JSONArray(msg);
            for(int i=0; i<a.length(); i++) {
                Virus v = VirusFactory.fromJSON(a.getJSONObject(i));
                if(v!=null && virusDatabase.findVirus(v.getId())==null) {
                    Log.d(LOG_TAG, "infected with virus: " + v.getId());
                    virusDatabase.addVirus(v);
                }
            }
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }
}
