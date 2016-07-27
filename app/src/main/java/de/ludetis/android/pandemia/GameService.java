package de.ludetis.android.pandemia;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.greenrobot.event.EventBus;
import de.ludetis.android.pandemia.model.Biohazard;
import de.ludetis.android.pandemia.model.GameEvent;
import de.ludetis.android.pandemia.model.MapEvent;
import de.ludetis.android.pandemia.model.Virus;

public class GameService extends Service implements LocationListener, IMqttActionListener, MqttCallback {
    private static final String BROKER_URI = "tcp://h10.ludetis-spiele.de";
    private static final double GRID_SIZE_FACTOR = 10; // grid size = 1/GRID_SIZE_FACTOR °
    private static final String LOG_TAG = "GameService";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long MIN_LOCATION_UPDATE_INTERVAL_MS = 1000 * 10;
    private static final float MIN_LOCATION_UPDATE_DISTANCE_M = 100;
    private static final int MAX_VIRUS=10;
    private static final float BIOHAZARD_INFECTION_RADIUS = 250; // meters

    private MqttAndroidClient mqttClient;
    private String clientId;
    private LocationManager locationManager;
    private String currentTopic,lastSubscribedTopic;
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private GameDatabase gameDatabase;
    private final Random rnd = new Random();
    private Set<Biohazard> bioHazards = new HashSet<>();
    private long region=0;
    private Vibrator vibrator;
    private boolean sendEnterMessage;

    public GameService() {
    }

    public class GameServiceBinder extends Binder {
        public GameService getService() {
            return GameService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new GameServiceBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        gameDatabase = new GameDatabase(this);

        // init game DB
        if(gameDatabase.getViruses().isEmpty()) {
            Virus v = VirusFactory.createMutation(null);
            Log.d(LOG_TAG, "new virus: " + v.getId());
            gameDatabase.addVirus(v);
            EventBus.getDefault().post(new GameEvent(GameEvent.Type.NEW_VIRUS, v.getId(), 0));
        }
        clientId = gameDatabase.getSetting("clientId",null);
        if(clientId==null) {
            clientId = UUID.randomUUID().toString();
            gameDatabase.putSetting("clientId",clientId);
            Log.i(LOG_TAG, "persisted new clientId: " + clientId);
        }

        // MQTT
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
        if(gameEvent.getType().equals(GameEvent.Type.INIT)) {
            sendInfectionMessage(true);
        }
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
            Log.d(LOG_TAG, "found best last location: " + bestResult);
            onLocationChanged(bestResult);
        }
    }

    private void heartbeat() {
        try {

            mutation();

            if (currentTopic != null) {
                subscribeToCurrentTopic();

                if(sendEnterMessage) {
                    sendInfectionMessage(true);
                }

            } else {
                Log.d(LOG_TAG, "waiting for location...");
            }

        }catch (Exception e) {
            Log.e(LOG_TAG, "Exception during heartbeat ",e);
        }
    }

    private void sendInfectionMessage(boolean entering) {
        try {
            JSONArray a = gameDatabase.findAllVirusDataAsJson();
            JSONObject o = new JSONObject();
            if(entering) o.put("entering",currentTopic);
            o.put("sender",clientId);
            o.put("viruses", a);
            String payload = o.toString();
            Log.d(LOG_TAG, "publishing to " + currentTopic + ": " + payload);
            IMqttDeliveryToken token = mqttClient.publish(currentTopic, new MqttMessage(payload.getBytes()));
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    sendEnterMessage=false;
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

                }
            });
        } catch (MqttException e) {
            //
        } catch (JSONException e) {
            Log.e(LOG_TAG, "JSONException",e);
        }
    }

    private void mutation() {
        NavigableSet<String> viruses = gameDatabase.getViruses();

        // mutation
        for(String id : gameDatabase.getViruses()) {
            Virus virus = gameDatabase.findVirus(id);
            int mutationPropability = calcMutationPropability(virus);
            if(rnd.nextInt(10000)<mutationPropability) {
                Virus v = VirusFactory.createMutation(virus);
                Log.d(LOG_TAG, "mutation of virus "+id+": new virus: " + v.getId());
                gameDatabase.addVirus(v);
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
            sendEnterMessage=true;
        }

        int thisRegion = calcRegionCode(location);
        if(thisRegion!=region || bioHazards.isEmpty()) {
            Log.d(LOG_TAG, "new region or no biohazards in region " + thisRegion + " - generating biohazards");
            region=thisRegion;
            createBiohazards(location);
            EventBus.getDefault().post(new MapEvent(MapEvent.Type.REGION_UPDATED,region,bioHazards));
        }

        for(Biohazard b : bioHazards) {
            if(b.getLocation().distanceTo(location) < BIOHAZARD_INFECTION_RADIUS) {
                Virus v = VirusFactory.fromBiohazard( b.getSeed(), b.getId());
                if(gameDatabase.findVirus(v.getId())==null) {
                    Log.d(LOG_TAG, "infection by approaching biohazard " + b.getId());
                    gameDatabase.addVisitedBiohazard(b.getId());
                    addVirus(v);
                }
            }
        }
    }

    private void createBiohazards(Location location) {
        bioHazards.clear();
        Random rnd = new Random(region);
        int count = 50+rnd.nextInt(100);
        for(int i=0; i<count; i++) {
            double lo =  Math.floor(location.getLongitude()*GRID_SIZE_FACTOR)/GRID_SIZE_FACTOR+rnd.nextDouble()/GRID_SIZE_FACTOR;
            double la =  Math.floor(location.getLatitude()*GRID_SIZE_FACTOR)/GRID_SIZE_FACTOR+rnd.nextDouble()/GRID_SIZE_FACTOR;
            Location l = new Location("");
            l.setLongitude(lo);
            l.setLatitude(la);
            long seed = rnd.nextLong();
            bioHazards.add(new Biohazard("B-"+Long.toString(region) +"-" + Long.toString(seed),l,seed));
            //Log.d(LOG_TAG, "created biohazard at " + l);
        }
        Log.d(LOG_TAG, "created "+bioHazards.size()+" biohazards");
    }

    private int calcRegionCode(Location location) {
        return (int) ( Math.round(location.getLongitude()*GRID_SIZE_FACTOR)*399*GRID_SIZE_FACTOR +  Math.round(location.getLatitude()*GRID_SIZE_FACTOR) );
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
        //Log.v(LOG_TAG, "location status: " + s);
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
        return "pandemia/grid/" + Long.toString(Math.round(l.getLatitude()*GRID_SIZE_FACTOR))+"_"+Long.toString(Math.round(l.getLongitude()*GRID_SIZE_FACTOR));
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
            //process message
            JSONObject o = new JSONObject(msg);
            String sender = o.optString("sender",null);
            // ignore our own messages, so check sender
            if(sender!=null && !sender.equals(clientId)) {
                // somebody else sent their viruses
                JSONArray a = o.getJSONArray("viruses");
                for(int i=0; i<a.length(); i++) {
                    Virus v = VirusFactory.fromJSON(a.getJSONObject(i));
                    addVirus(v);
                }
                // was the other user just entering our region?
                if(o.optString("entering",null)!=null) {
                    // send our infection
                    sendInfectionMessage(false);
                }

            }



        }
    }

    private void addVirus(Virus v) {
        if(v!=null && gameDatabase.findVirus(v.getId())==null) {
            Log.d(LOG_TAG, "infected with virus: " + v.getId());
            gameDatabase.addVirus(v);
            vibrator.vibrate(300);
            EventBus.getDefault().post(new GameEvent(GameEvent.Type.NEW_VIRUS,v.getId(),1));
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }

    public Set<Biohazard> getBioHazards() {
        return bioHazards;
    }
}
