package de.ludetis.android.medicus2;

import android.graphics.Color;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;
import java.util.UUID;

import de.ludetis.android.medicus2.model.Virus;

/**
 * Created by uwe on 20.01.16.
 */
public class VirusFactory {

    private final static String VIRUS_NAME_CHARS= "XYQCZS123570";
    private final static int VIRUS_NAME_LENGTH=5;
    private final static Random rnd = new Random();

    public static Virus createMutation(Virus v) {
        Virus virus = new Virus(UUID.randomUUID().toString());
        if(v!=null) {
            // take some attributes from v and modify them slightly
            virus.init(createVirusName(), v.getLimbs()+rnd.nextInt(2), v.getColor1()-100+rnd.nextInt(200),
                    v.getColor2()-100+rnd.nextInt(200), rnd.nextInt(Integer.MAX_VALUE), v.getStrength()+rnd.nextInt(2),
                    v.getMutability()+rnd.nextInt(2),v.getStamina()+rnd.nextInt(2));
        } else {
            virus.init(createVirusName(),3+rnd.nextInt(6), Color.rgb(rnd.nextInt(128)+127,rnd.nextInt(128)+127,rnd.nextInt(128)+127),
                    Color.rgb(rnd.nextInt(128)+127,rnd.nextInt(128)+127,rnd.nextInt(128)+127),rnd.nextInt(Integer.MAX_VALUE),
                    1+rnd.nextInt(2),1+rnd.nextInt(2),10+rnd.nextInt(10));

        }
        return virus;
    }

    public static Virus fromJSON(JSONObject o) {
        try {
            Virus v = new Virus(o.getString("id"));
            v.init(o.optString("name",createVirusName()), o.getInt("limbs"), o.getInt("color1"), o.getInt("color2"), o.getInt("seed"),
                    o.optInt("strength",1+rnd.nextInt(2)), o.optInt("mutability",1+rnd.nextInt(2)),o.optInt("stamina",10+rnd.nextInt(10)));
            return v;
        } catch (JSONException e) {
            return null;
        }
    }

    public static String createVirusName() {
        String res="";
        for(int i=0; i<VIRUS_NAME_LENGTH; i++) {
            if(res.length()>0) res+="-";
            res+=VIRUS_NAME_CHARS.charAt(rnd.nextInt(VIRUS_NAME_CHARS.length()));
        }
        return res;
    }


}
