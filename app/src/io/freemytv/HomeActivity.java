package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

/**
 * The Free My TV home screen (Android's "Home" role; set once from a computer via adb). It runs in the :home process.
 */
public class HomeActivity extends Activity {
    private HomeView home;
    private int prefsVersion = Prefs.version;   // settings changed while we were away: start over

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        home = new HomeView(this);
        setContentView(home);
        getWindow().setBackgroundDrawable(null);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefsVersion != Prefs.version) {
            prefsVersion = Prefs.version;
            recreate();   // a new language or theme: draw everything again
            return;
        }
        if (home != null) {
            home.resume();
            // Back to drawing every pixel ourselves every frame: the fallback set in onPause() would only cost an
            // extra overdraw pass from here on, for no benefit.
            getWindow().setBackgroundDrawable(null);
        }
    }

    @Override protected void onPause() {
        if (home != null) {
            home.pause();
            // Something transient is covering us (e.g. the system Settings side panel, opened from our gear
            // button's "TV Settings" row or the remote's Settings key) and our own render loop just stopped. We
            // normally null the window's background to skip that overdraw every frame, but if Android recreates
            // this window's surface while we sit here paused and not drawing, an undrawn surface defaults to black
            // instead of showing anything of ours. A plain colour matching the current theme means that gap, if it
            // ever happens, reads as "our background", not a black flash — restored to null in onResume() above.
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(home.fallbackColor()));
        }
        super.onPause();
    }

    /** Home button pressed on the home screen: back to the entry state (focus on the first app, sheet closed). */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (home != null) home.reset(true);
    }

    @Override protected void onDestroy() {
        if (home != null) home.destroy();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (home != null && home.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

}
