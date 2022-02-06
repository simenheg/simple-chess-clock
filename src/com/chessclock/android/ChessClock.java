/*************************************************************************
 * File: ChessClock.java
 * 
 * Implements the main form/class for Chess Clock.
 * 
 * Created: 2010-06-22
 * 
 * Author: Carter Dewey
 * 
 *************************************************************************
 *
 *   This file is part of Simple Chess Clock (SCC).
 *    
 *   SCC is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   SCC is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with SCC.  If not, see <http://www.gnu.org/licenses/>.
 *
 *************************************************************************/

package com.chessclock.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.provider.*;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import java.lang.Math;

public class ChessClock extends Activity {
	
	/**-----------------------------------
	 *            CONSTANTS
	 *-----------------------------------*/
	/** Version info and debug tag constants */
	public static final String TAG = "INFO";
	public static final String V_MAJOR = "2";
	public static final String V_MINOR = "9";
	public static final String V_MINI = "0";

	/** Constants for the dialog windows */
	private static final int RESET = 1;

    /** Clock tick length, in milliseconds */
    private static int TICK_LENGTH = 100;

    /** If showDeciseconds is enabled,
     * display deciseconds for times shorter than this
     * threshold, in seconds.
     */
    private static int SHOW_DECISECONDS_THRESHOLD = 10;

	/** Time control values */
	private static String NO_DELAY = "None";
	private static String FISCHER = "Fischer";
	private static String BRONSTEIN = "Bronstein";

    /** Time unit values */
    private static String HOURS = "Hours";
    private static String MINUTES = "Minutes";
    private static String SECONDS = "Seconds";

	/**-----------------------------------
	 *     CHESSCLOCK CLASS MEMBERS
	 *-----------------------------------*/
	/** Objects/Classes */
	private Handler myHandler = new Handler();
	private DialogFactory DF = new DialogFactory();
	private PowerManager pm;
	private WakeLock wl;
	private String delay = NO_DELAY;
	private String alertTone;
	private Ringtone ringtone = null;
    private String initTimeUnits = MINUTES;
    private String delayTimeUnits = SECONDS;

    /** Time per player, in initTimeUnits. */
    private int initTime1 = 10;
    private int initTime2 = 10;
    private boolean differentInitTime = false;

	private int b_delay;
	private long t_P1;
	private long t_P2;
	private int delay_time;
	private int onTheClock = 0;
	private int savedOTC = 0;

	private boolean haptic = false;
    private boolean blackBackground = false;
	private boolean timeup = false;
	private boolean prefmenu = false;
	private boolean delayed = false;
    private boolean showDeciseconds = true;

    /** Provide haptic feedback to the user of the given view. */
    private void performHapticFeedback(View v) {
        v.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        );
    }

    /**
     * Return the color corresponding to the given ID (as defined in
     * colors.xml).
     */
    private int color(int id) {
        return getResources().getColor(id, null);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);    
        
        /** Get rid of the status bar */
        requestWindowFeature(Window.FEATURE_NO_TITLE);  
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,   
        						WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        /** Create a PowerManager object so we can get the wakelock */
        pm = (PowerManager) getSystemService(ChessClock.POWER_SERVICE);  
        wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "ChessWakeLock");
        
        setContentView(R.layout.main);
        
        setUpGame(true);
    }
    
    @Override
    public void onPause() {
    	if ( wl.isHeld() ) {
    		wl.release();
    	}
        stopAlert();
    	PauseGame();
    	super.onPause();
    }
    
    @Override
    public void onResume() {
    	/** Get the wakelock */
	    wl.acquire();
        stopAlert();
	    super.onResume();
    }
    
    @Override
    public void onDestroy() {
    	if ( wl.isHeld() ) {
    		wl.release();
    	}
        stopAlert();
    	super.onDestroy();
    }

    /** Return the init time for a player based on the preferences. */
    private int initTime(int player) {
        return differentInitTime
            ? player == 1 ? initTime1 : initTime2
            : initTime1;
    }

    /** Return time in milliseconds based on the specified unit. */
    private int toMillis(int time, String timeUnit) {
        if (timeUnit.equals(HOURS)) {
            return time * 60 * 60 * 1000;
        } else if (timeUnit.equals(MINUTES)) {
            return time * 60 * 1000;
        } else if (timeUnit.equals(SECONDS)) {
            return time * 1000;
        } else {
            throw new java.lang.RuntimeException("Invalid timeUnit: " + timeUnit);
        }
    }

    // Set the given clock to the given time + delay
    private void setClock(TextView clock, long time, long bronsteinDelay) {
        String delayTime = formatTime(bronsteinDelay, true);
        String delayString = bronsteinDelay > 0
            ? (showDeciseconds ? "\n" : "") + "+" + delayTime
            : "";
        clock.setText(formatTime(time) + delayString);
    }

    private void setClock(TextView clock, long time) {
        setClock(clock, time, 0);
    }

    /**
     * Format the provided time to a readable string.
     * @param t - time, in milliseconds
     * @param compact - whether or not to use compact format
     */
    private String formatTime(long t, boolean compact) {
        //If not displaying deciseconds, round up to the nearest second.
        if (!showDeciseconds) {
            t = (long)Math.ceil(t / 1000.0) * 1000;
        }

        int deciseconds = (int)(t / 100) % 10;
        int seconds = (int)(t / 1000) % 60;
        int minutes = (int)(t / 1000 / 60) % 60;
        int hours = (int)(t / 1000 / 60 / 60);

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else if (showDeciseconds && (seconds < SHOW_DECISECONDS_THRESHOLD)) {
            String format = compact ? "%d.%d" : "0:%02d.%d";
            return String.format(format, seconds, deciseconds);
        } else {
            String format = compact ? "%d": "0:%02d";
            return String.format(format, seconds);
        }
    }

    private String formatTime(long t) {
        return formatTime(t, false);
    }

    private void initRingtone() {
        Uri uri = Uri.parse(alertTone);
        ringtone = RingtoneManager.getRingtone(getBaseContext(), uri);
    }

    private void playAlert() {
        initRingtone();
        if (ringtone != null) {
            ringtone.play();
        }
    }

    private void stopAlert() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
    	prefmenu = true;
        stopAlert();
    	PauseGame();
    	return true;
    }

	public void onWindowFocusChanged(boolean b) {
		if ( !prefmenu ) {
			CheckForNewPrefs();
		} else {
			prefmenu = false;
		}
	}
	
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = new Dialog(this);
		switch ( id ) {
			case RESET:
				dialog = ResetDialog();
				break;
		}
		
		return dialog;
	}
	
	/** Click handler for player 1's clock. */
	public OnClickListener P1ClickHandler = new OnClickListener() {
		public void onClick(View v) {
            if (onTheClock == 1 || onTheClock == 0) {
                performHapticFeedback(v);
            }
			P1Click();
		}
	};
	
	/** Click handler for player 2's clock */
	public OnClickListener P2ClickHandler = new OnClickListener() {
		public void onClick(View v) {
            if (onTheClock == 2 || onTheClock == 0) {
                performHapticFeedback(v);
            }
			P2Click();
		}
	};
	
	/** Click handler for the pause button */
	public OnClickListener PauseListener = new OnClickListener() {
		public void onClick(View v) {
            performHapticFeedback(v);
            PauseToggle();
		}
	};
	
    /** Click handler for the menu button */
    public OnClickListener MenuListener = new OnClickListener() {
        public void onClick(View v) {
            performHapticFeedback(v);
            showPrefs();
        }
    };

	/** Starts the Preferences menu intent */
	private void showPrefs() {
		Intent prefsActivity = new Intent(ChessClock.this, Prefs.class);
		startActivity(prefsActivity);
	}

    /** Return an integer preference. */
    private int getIntPref(String pref, int fallback) {
        SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(this);

        try {
            return Integer.parseInt(
                prefs.getString(pref, Integer.toString(fallback))
            );
        } catch (Exception ex) {
            Editor e = prefs.edit();
            e.putString(pref, Integer.toString(fallback));
            e.commit();
            return fallback;
        }
    }

	/** 
	 * Checks for changes to the current preferences. We only want
	 * to re-create the game if something has been changed, so we
	 * check for differences any time onWindowFocusChanged() is called.
	 */
	public void CheckForNewPrefs() {
		SharedPreferences prefs = PreferenceManager
    	.getDefaultSharedPreferences(this);
		
		alertTone = prefs.getString("prefAlertSound", Settings.System.DEFAULT_RINGTONE_URI.toString());
		
        /** Check for new game time settings. */
        if (!prefs.getString("prefInitTimeUnits", MINUTES).equals(initTimeUnits)) {
            setUpGame(true);
        };

        if (getIntPref("prefInitTime1", 10) != initTime1
              || getIntPref("prefInitTime2", 10) != initTime2) {
            setUpGame(true);
        }

        if (prefs.getBoolean("prefDifferentInitTime", false) != differentInitTime) {
            setUpGame(true);
        }

        /** Check for new delay settings. */
        if (!prefs.getString("prefDelay", NO_DELAY).equals(delay)){
            setUpGame(true);
        }

        if (!prefs.getString("prefDelayTimeUnits", SECONDS).equals(delayTimeUnits)) {
            setUpGame(true);
        };

        if (getIntPref("prefDelayTime", 0) != delay_time) {
            setUpGame(true);
        }

		boolean new_haptic = prefs.getBoolean("prefHaptic", false);
		if ( new_haptic != haptic ) {
			// No reason to reload the clocks for this one
            setUpGame(false);
		}

        boolean new_bb = prefs.getBoolean("prefBlackBackground", false);
        if (new_bb != blackBackground) {
            // No reason to reload the clocks for this one
            setUpGame(false);
        }

        if (prefs.getBoolean("prefShowDeciseconds", true) != showDeciseconds) {
            setUpGame(false);
        }
    }
	
	/** Creates and displays the "Reset Clocks" alert dialog */
	private Dialog ResetDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_message_reset)
            .setCancelable(false)
            .setPositiveButton(
                R.string.dialog_button_yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        setUpGame(true);
                        dialog.dismiss();
                    }
                })
            .setNegativeButton(
                R.string.dialog_button_no,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
		AlertDialog alert = builder.create();

		return alert;
	}
	
	/** Called when P1ClickHandler registers a click/touch event */
	private void P1Click() {
        if (onTheClock == 2) {
            return;
        }

        TextView p1 = (TextView)findViewById(R.id.t_Player1);
        TextView p2 = (TextView)findViewById(R.id.t_Player2);
        View l1 = (View)findViewById(R.id.l_Player1);
        View l2 = (View)findViewById(R.id.l_Player2);

        if (delay.equals(FISCHER) && (onTheClock == 1 || savedOTC == 1)) {
            t_P1 += toMillis(delay_time, delayTimeUnits);
            setClock(p1, t_P1);
        }

        if (delay.equals(BRONSTEIN)) {
            setClock(p1, t_P1);
            setClock(p2, t_P2, (savedOTC == 2) ? b_delay : toMillis(delay_time, delayTimeUnits));
        }

        // Register that player 2's time is running now
        onTheClock = 2;
        // Unless we're unpausing player 2, reset their delayed status
        delayed = delayed && (savedOTC == 2);
        savedOTC = 0;

        p2.setTextColor(color(R.color.active_text));
        p1.setTextColor(color(R.color.inactive_text));
        l2.setBackgroundColor(color(R.color.highlight));
        l1.setVisibility(View.INVISIBLE);
        l2.setVisibility(View.VISIBLE);
		
        Button pp = (Button)findViewById(R.id.Pause);
        pp.setBackgroundResource(R.drawable.pause_button);

        /**
         * Unregister the handler from player 1's clock and create a new one
         * which we register with player 2's clock.
         */
        myHandler.removeCallbacks(mUpdateTimeTask);
        myHandler.removeCallbacks(mUpdateTimeTask2);
        myHandler.postDelayed(mUpdateTimeTask2, TICK_LENGTH);
	}

    /** Return true if out of time. */
    private boolean outOfTime(long timeLeft) {
        if (delay.equals(BRONSTEIN)) {
            return timeLeft + b_delay == 0;
        }

        return timeLeft == 0;
    }

    /** Handles the "tick" event for Player 1's clock */
    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            Button b1 = (Button)findViewById(R.id.Player1);
            Button b2 = (Button)findViewById(R.id.Player2);
            TextView p1 = (TextView)findViewById(R.id.t_Player1);
            TextView p2 = (TextView)findViewById(R.id.t_Player2);

            // Check for delays and apply them
            if (delay.equals(BRONSTEIN)){
                if (delayed) {
                    b_delay = Math.max(0, b_delay - TICK_LENGTH);
                } else {
                    delayed = true;
                    b_delay = toMillis(delay_time, delayTimeUnits);
                }
                // If delay remaining, negate tick
                t_P1 += (b_delay > 0) ? TICK_LENGTH : 0;
            }

            // Deduct tick from P1's clock
            if (t_P1 > 0) {
                t_P1 -= TICK_LENGTH;
            }
            setClock(p1, t_P1, b_delay);

            if (outOfTime(t_P1)) {
                timeup = true;
                Button pp = (Button)findViewById(R.id.Pause);
                View l1 = (View)findViewById(R.id.l_Player1);

                l1.setBackgroundColor(color(R.color.timesup));
                performHapticFeedback(l1);

                b1.setClickable(false);
                b2.setClickable(false);
                pp.setBackgroundResource(R.drawable.reset_button);
                playAlert();
                myHandler.removeCallbacks(mUpdateTimeTask);
            } else {
                // Re-post the handler so it waits until the next tick
                myHandler.postDelayed(this, TICK_LENGTH);
            }
        }
    };
	
	/** Called when P2ClickHandler registers a click/touch event */
	private void P2Click() {
        if (onTheClock == 1) {
            return;
        }

        Button b1 = (Button)findViewById(R.id.Player1);
        Button b2 = (Button)findViewById(R.id.Player2);
        TextView p1 = (TextView)findViewById(R.id.t_Player1);
        TextView p2 = (TextView)findViewById(R.id.t_Player2);
        View l1 = (View)findViewById(R.id.l_Player1);
        View l2 = (View)findViewById(R.id.l_Player2);

        if (delay.equals(FISCHER) && (onTheClock == 2 || savedOTC == 2)) {
            t_P2 += toMillis(delay_time, delayTimeUnits);
            setClock(p2, t_P2);
        }

        if (delay.equals(BRONSTEIN)) {
            setClock(p2, t_P2);
            setClock(p1, t_P1, (savedOTC == 1) ? b_delay : toMillis(delay_time, delayTimeUnits));
        }

        // Register that player 1's time is running now
        onTheClock = 1;
        // Unless we're unpausing player 1, reset their delayed status
        delayed = delayed && (savedOTC == 1);
        savedOTC = 0;

        p1.setTextColor(color(R.color.active_text));
        p2.setTextColor(color(R.color.inactive_text));
        l1.setBackgroundColor(color(R.color.highlight));
        l1.setVisibility(View.VISIBLE);
        l2.setVisibility(View.INVISIBLE);

		Button pp = (Button)findViewById(R.id.Pause);
        pp.setBackgroundResource(R.drawable.pause_button);
		
		/** 
         * Unregister the handler from player 2's clock and create a new one
         * which we register with player 1's clock.
		 */
		myHandler.removeCallbacks(mUpdateTimeTask);
		myHandler.removeCallbacks(mUpdateTimeTask2);
        myHandler.postDelayed(mUpdateTimeTask, TICK_LENGTH);
    }
				
    /** Handles the "tick" event for Player 2's clock */
    private Runnable mUpdateTimeTask2 = new Runnable() {
        public void run() {
            Button b1 = (Button)findViewById(R.id.Player1);
            Button b2 = (Button)findViewById(R.id.Player2);
            TextView p1 = (TextView)findViewById(R.id.t_Player1);
            TextView p2 = (TextView)findViewById(R.id.t_Player2);

            // Check for delays and apply them
            if (delay.equals(BRONSTEIN)){
                if (delayed) {
                    b_delay = Math.max(0, b_delay - TICK_LENGTH);
                } else {
                    delayed = true;
                    b_delay = toMillis(delay_time, delayTimeUnits);
                }
                // If delay remaining, negate tick
                t_P2 += (b_delay > 0) ? TICK_LENGTH : 0;
            }

            // Deduct tick from P2's clock
            if (t_P2 > 0) {
                t_P2 -= TICK_LENGTH;
            }
            setClock(p2, t_P2, b_delay);

            if (outOfTime(t_P2)) {
                timeup = true;
                Button pp = (Button)findViewById(R.id.Pause);
                View l2 = (View)findViewById(R.id.l_Player2);

                l2.setBackgroundColor(color(R.color.timesup));
                performHapticFeedback(l2);

                b1.setClickable(false);
                b2.setClickable(false);
                pp.setBackgroundResource(R.drawable.reset_button);
                playAlert();
                myHandler.removeCallbacks(mUpdateTimeTask2);
            } else {
                // Re-post the handler so it waits until the next tick
                myHandler.postDelayed(this, TICK_LENGTH);
            }
        }
    };

	/** 
	 * Pauses both clocks. This is called when the options
	 * menu is opened, since the game needs to pause
	 * but not un-pause, whereas PauseToggle() will switch
	 * back and forth between the two.
	 *  */
	private void PauseGame() {
        TextView p1 = (TextView)findViewById(R.id.t_Player1);
        TextView p2 = (TextView)findViewById(R.id.t_Player2);
        View l1 = (View)findViewById(R.id.l_Player1);
        View l2 = (View)findViewById(R.id.l_Player2);
		Button pp = (Button)findViewById(R.id.Pause);
		
		/** Save the currently running clock, then pause */
		if ( ( onTheClock != 0 ) && ( !timeup ) ) {
			savedOTC = onTheClock;
			onTheClock = 0;
			
            p1.setTextColor(color(R.color.inactive_text));
            p2.setTextColor(color(R.color.inactive_text));
            l1.setBackgroundColor(color(R.color.inactive_text));
            l2.setBackgroundColor(color(R.color.inactive_text));
            pp.setBackgroundResource(R.drawable.reset_button);
		
			myHandler.removeCallbacks(mUpdateTimeTask);
			myHandler.removeCallbacks(mUpdateTimeTask2);
		}
	}
	
	/** Called when the pause button is clicked */
	private void PauseToggle() {
        TextView p1 = (TextView)findViewById(R.id.t_Player1);
        TextView p2 = (TextView)findViewById(R.id.t_Player2);
        View l1 = (View)findViewById(R.id.l_Player1);
        View l2 = (View)findViewById(R.id.l_Player2);
		Button pp = (Button)findViewById(R.id.Pause);

        /** Figure out if we need to pause or reset. */
        if (onTheClock == 0 || outOfTime(t_P1) || outOfTime(t_P2)) {
            Log.v(TAG, "Info: Resetting.");
            stopAlert();
            showDialog(RESET);
        } else {
            savedOTC = onTheClock;
            onTheClock = 0;

            p1.setTextColor(color(R.color.inactive_text));
            p2.setTextColor(color(R.color.inactive_text));
            l1.setBackgroundColor(color(R.color.inactive_text));
            l2.setBackgroundColor(color(R.color.inactive_text));
            pp.setBackgroundResource(R.drawable.reset_button);

            myHandler.removeCallbacks(mUpdateTimeTask);
            myHandler.removeCallbacks(mUpdateTimeTask2);
        }
	}
	
	/** Set up (or refresh) all game parameters */
    private void setUpGame(boolean resetClocks) {
	    /** Load all stored preferences */
	    SharedPreferences prefs = PreferenceManager
    	.getDefaultSharedPreferences(this);

        TextView p1 = (TextView)findViewById(R.id.t_Player1);
        TextView p2 = (TextView)findViewById(R.id.t_Player2);
        p1.setTextColor(color(R.color.active_text));
        p2.setTextColor(color(R.color.active_text));

        View l1 = (View)findViewById(R.id.l_Player1);
        View l2 = (View)findViewById(R.id.l_Player2);
        l1.setVisibility(View.INVISIBLE);
        l2.setVisibility(View.INVISIBLE);

        /** Take care of a haptic change if needed */
        haptic = prefs.getBoolean("prefHaptic", false);
        Button b1 = (Button)findViewById(R.id.Player1);
        Button b2 = (Button)findViewById(R.id.Player2);
        Button pause = (Button)findViewById(R.id.Pause);
        Button menu = (Button)findViewById(R.id.Menu);

        b1.setHapticFeedbackEnabled(haptic);
        b2.setHapticFeedbackEnabled(haptic);
        pause.setHapticFeedbackEnabled(haptic);
        menu.setHapticFeedbackEnabled(haptic);

        /* Set the preferred backgroud color. */
        blackBackground = prefs.getBoolean("prefBlackBackground", false);
        if (blackBackground) {
            b1.setBackgroundColor(color(R.color.bg_black));
            b2.setBackgroundColor(color(R.color.bg_black));
        } else {
            b1.setBackgroundColor(color(R.color.bg_dark));
            b2.setBackgroundColor(color(R.color.bg_dark));
        }

        showDeciseconds = prefs.getBoolean("prefShowDeciseconds", true);

        if (resetClocks) {
            delay = prefs.getString("prefDelay", NO_DELAY);
            initTimeUnits = prefs.getString("prefInitTimeUnits", MINUTES);
            delayTimeUnits = prefs.getString("prefDelayTimeUnits", SECONDS);

            differentInitTime = prefs.getBoolean("prefDifferentInitTime", false);
            initTime1 = getIntPref("prefInitTime1", 10);
            initTime2 = getIntPref("prefInitTime2", 10);
            delay_time = getIntPref("prefDelayTime", 0);

            alertTone = prefs.getString("prefAlertSound", Settings.System.DEFAULT_RINGTONE_URI.toString());
            if (alertTone.equals("")) {
                alertTone = Settings.System.DEFAULT_RINGTONE_URI.toString();
                Editor e = prefs.edit();
                e.putString("prefAlertSound", alertTone);
                e.commit();
            }

            initRingtone();
            onTheClock = 0;
            savedOTC = 0;
            delayed = false;

            t_P1 = toMillis(initTime(1), initTimeUnits);
            t_P2 = toMillis(initTime(2), initTimeUnits);
            b_delay = delay.equals(BRONSTEIN) ? toMillis(delay_time, delayTimeUnits) : 0;

            // Register the click listeners
            b1.setOnClickListener(P1ClickHandler);
            b2.setOnClickListener(P2ClickHandler);
            pause.setOnClickListener(PauseListener);
            menu.setOnClickListener(MenuListener);
        }

        // Format and display the clocks
        setClock(p1, t_P1, (savedOTC == 1) ? b_delay : 0);
        setClock(p2, t_P2, (savedOTC == 2) ? b_delay : 0);
	}
}
