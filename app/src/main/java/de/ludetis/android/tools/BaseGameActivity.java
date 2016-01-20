package de.ludetis.android.tools;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by uwe on 19.09.13.
 */
public class BaseGameActivity extends Activity {

    private Map<String,Typeface> typefaces = new HashMap<String,Typeface>();
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
    }

    protected float scale(float v) {
        return  density*v;
    }

    protected void addTypeface(String name) {
        Typeface typeface = Typeface.createFromAsset(getAssets(),name+".ttf");
        typefaces.put(name, typeface);
    }

    protected void setTypeface(TextView v, String typefaceName) {
        Typeface t = typefaces.get(typefaceName);
        if(t!=null) {
            v.setTypeface(t);
        }
    }

    protected void setText(int resid, String what) {
        TextView tv = (TextView) findViewById(resid);
        if(tv!=null) tv.setText(what);
    }

    protected void setAnimatedClickListener(int resid, final int animResid, final View.OnClickListener listener) {
        final View v = findViewById(resid);
        if(v!=null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Animation a = AnimationUtils.loadAnimation(BaseGameActivity.this,animResid);
                    a.setAnimationListener(new SimpleAnimationListener() {
                        @Override
                        public void onAnimationEnd(Animation animation) {
                            listener.onClick(v);
                        }
                    });
                    v.startAnimation(a);
                }
            });

        }
    }

    protected void hideView(int resid) {
        View v = findViewById(resid);
        if(v!=null) v.setVisibility(View.GONE);
    }

    protected void showView(int resid) {
        View v = findViewById(resid);
        if(v!=null) v.setVisibility(View.VISIBLE);
    }
}
