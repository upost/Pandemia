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
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;

import java.util.List;
import java.util.NavigableSet;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.greenrobot.event.EventBus;
import de.ludetis.android.medicus2.model.GameEvent;
import de.ludetis.android.medicus2.model.Virus;

public class GameService extends Service implements LocationListener, IMqttActionListener, MqttCallback {
    private static final String BROKER_URI = "tcp://h8.ludetis-spiele.de";
    private static final double GRID_SIZE_FACTOR = 10; // grid size = 1/GRID_SIZE_FACTOR °
    private static final String LOG_TAG = "GameService";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long MIN_LOCATION_UPDATE_INTERVAL_MS = 1000 * 10;
    private static final float MIN_LOCATION_UPDATE_DISTANCE_M = 100;
    private static final int MAX_VIRUS=10;

    private MqttAndroidClient mqttClient;
    private String clientId;
    private LocationManager locationManager;
    private String currentTopic,lastSubscribedTopic;
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private VirusDatabase virusDatabase;
    private final Random rnd = new Random();

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
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,MIN_LOCATION_UPDATE_INTERVAL_MS,MIN_LOCATION_UPDATE_DISTANCE_M,this);
        else
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,MIN_LOCATION_UPDATE_INTERVAL_MS,MIN_LOCATION_UPDATE_DISTANCE_M,this);

        findBestLastLocation();

        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setKeepAliveInterval(0); // no keepalive pings
            mqttClient.connect(options, null, this);
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

        EventBus.getDefault().register(this);
    }

    public void onEvent(GameEvent gameEvent) {

    }

    private void findBestLastLocation() {
        long minTime=0;
        float bestAccuracy = Float.MAX_VALUE;
        Location bestResult=null;
        long bestTime=0;
        List<String> matchingProviders = locationManager.getAllProviders();
        for (String provider: matchingProviders) {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                float accuracy = location.getAccuracy();
                long time = location.getTime();

                if ((time > minTime && accuracy < bestAccuracy)) {
                    bestResult = location;
                    bestAccuracy = accuracy;
                    bestTime = time;
                }
                else if (time < minTime &&
                        bestAccuracy == Float.MAX_VALUE && time > bestTime){
                    bestResult = location;
                    bestTime = time;
                }
            }
        }
        if(bestResult!=null) {
            Log.d(LOG_TAG, "found best last location");
            currentTopic = topic(bestResult);
        }
    }

    private void heartbeat() {
        try {

            mutation();

            if (currentTopic != null) {

                String payload = virusDatabase.findAllVirusDataAsJson();
                try {
                    Log.d(LOG_TAG, "publishing to " + currentTopic + ": " + payload);
                    mqttClient.publish(currentTopic, new MqttMessage(payload.getBytes()));
                } catch (MqttException e) {
                    // TODO
                }
                subscribeToCurrentTopic();
            } else {
                Log.d(LOG_TAG, "waiting for location...");
            }
        }catch (Exception e) {
            Log.e(LOG_TAG, "Exception during heartbeat ",e);
        }
    }

    private void mutation() {
        NavigableSet<String> viruses = virusDatabase.getViruses();
        if(viruses.isEmpty()) {
            Virus v = VirusFactory.createMutation(null);
            Log.d(LOG_TAG, "new virus: " + v.getId());
            virusDatabase.addVirus(v);
            EventBus.getDefault().post(new GameEvent(GameEvent.Type.NEW_VIRUS, v.getId(), 0));
        } else {
            // mutation
            for(String id : virusDatabase.getViruses()) {
                Virus virus = virusDatabase.findVirus(id);
                int mutationPropability = calcMutationPropability(virus);
                if(rnd.nextInt(10000)<mutationPropability) {
                    Virus v = VirusFactory.createMutation(virus);
                    Log.d(LOG_TAG, "mutation of virus "+id+": new virus: " + v.getId());
                    virusDatabase.addVirus(v);
                    EventBus.getDefault().post(new GameEvent(GameEvent.Type.NEW_VIRUS,v.getId(),0));
                }
            }
//            if(virusDatabase.getViruses().size()>MAX_VIRUS) {
//                // look for virus with minimal stamina and kill it
//                String minimalStaminaVirusId=null;
//                for(String id : virusDatabase.getViruses()) {
//                    Virus virus = virusDatabase.findVirus(id);
//                    if(minimalStaminaVirusId==null ||
//                            virus.getStamina()<virusDatabase.findVirus(minimalStaminaVirusId).getStamina()) {
//                        minimalStaminaVirusId=id;
//                    }
//                }
//                if(minimalStaminaVirusId!=null) {
//                    virusDatabase.removeVirus(minimalStaminaVirusId);
//                    Log.d(LOG_TAG, "killed virus with low stamina: " + minimalStaminaVirusId);
//                    EventBus.getDefault().post(new GameEvent(GameEvent.Type.KILLED_VIRUS, minimalStaminaVirusId, 0));
//                }
//            }
        }

    }

    private int calcMutationPropability(Virus virus) {
        return virus.getMutability();
    }

    @Override
    public void onDestroy() {
        //virusDatabase.close();

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
            currentTopic=topic;
            subscribeToCurrentTopic();
        }
    }

    private void subscribeToCurrentTopic() {
        if(mqttClient.isConnected() && lastSubscribedTopic!=currentTopic) {
            try {
                if(lastSubscribedTopic!=null)
                    mqttClient.unsubscribe(lastSubscribedTopic);
                mqttClient.subscribe(currentTopic, 0);
                lastSubscribedTopic = currentTopic;
                Log.d(LOG_TAG, "subscribed to " + currentTopic);
            } catch (MqttException e) {
                Log.w(LOG_TAG, "could not subscribe to: " + currentTopic + ": ", e);
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
