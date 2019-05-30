/*************************************************************************
 * File: ChessClock.java
 * 
 * Implements the main form/class for Chess Clock.
 * 
 * Created: 6/22/2010
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

public class ChessClock extends Activity {
	
	/**-----------------------------------
	 *            CONSTANTS
	 *-----------------------------------*/
	/** Version info and debug tag constants */
	public static final String TAG = "INFO";
	public static final String V_MAJOR = "2";
	public static final String V_MINOR = "1";
	public static final String V_MINI = "2";

	/** Constants for the dialog windows */
	private static final int RESET = 1;
	
	/** Time control values */
	private static String NO_DELAY = "None";
	private static String FISCHER = "Fischer";
	private static String BRONSTEIN = "Bronstein";
	
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
	
	/** ints/longs */
	private int time;
	private int b_delay;
	private long t_P1;
	private long t_P2;
	private int delay_time;
	private int onTheClock = 0;
	private int savedOTC = 0;
	
	/** booleans */
	private boolean haptic = false;
	private boolean timeup = false;
	private boolean prefmenu = false;
	private boolean delayed = false;

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
    	
    	if (null != ringtone) {
	    	if ( ringtone.isPlaying() ) {
	    		ringtone.stop();
	    	}
    	}
    	
    	PauseGame();
    	super.onPause();
    }
    
    @Override
    public void onResume() {
    	/** Get the wakelock */
	    wl.acquire();
	    
	    if (null != ringtone) {
		    if ( ringtone.isPlaying() ) {
	    		ringtone.stop();
	    	}
	    }
	    super.onResume();
    }
    
    @Override
    public void onDestroy() {
    	if ( wl.isHeld() ) {
    		wl.release();
    	}

    	if (null != ringtone) {
	    	if ( ringtone.isPlaying() ) {
	    		ringtone.stop();
	    	}
    	}
    	super.onDestroy();
    }
	
	/**
	 * Formats the provided time to a readable string
	 * @param time - time to format
	 * @return str_time - formatted time (String)
	 */
	private String FormatTime(long time) {
		int secondsLeft = (int)time / 1000;
		int minutesLeft = secondsLeft / 60;
	    secondsLeft     = secondsLeft % 60;
	    
	    String str_time;
	    
	    if (secondsLeft < 10) {
	        str_time = "" + minutesLeft + ":0" + secondsLeft;
	    } else {
	        str_time = "" + minutesLeft + ":" + secondsLeft;            
	    }
	    
	    return str_time;
	}
    
    public boolean onPrepareOptionsMenu(Menu menu) {
    	prefmenu = true;
    	if ( null != ringtone ) {
	    	if ( ringtone.isPlaying() ) {
	    		ringtone.stop();
	    	}
    	}
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
	
	/** 
	 * Checks for changes to the current preferences. We only want
	 * to re-create the game if something has been changed, so we
	 * check for differences any time onWindowFocusChanged() is called.
	 */
	public void CheckForNewPrefs() {
		SharedPreferences prefs = PreferenceManager
    	.getDefaultSharedPreferences(this);
		
		alertTone = prefs.getString("prefAlertSound", Settings.System.DEFAULT_RINGTONE_URI.toString());
		
		/** Check for a new delay style */
		String new_delay = prefs.getString("prefDelay","None");
		if (new_delay.equals("")) {
			new_delay = "None";
			Editor e = prefs.edit();
			e.putString("prefDelay", "None");
			e.commit();
		}
		
		if ( new_delay != delay ) {
            setUpGame(true);
		}
		
		/** Check for a new game time setting */
		int new_time;
		
		try {
			new_time = Integer.parseInt( prefs.getString("prefTime", "10") );
		} catch (Exception ex) {
			new_time = 10;
			Editor e = prefs.edit();
			e.putString("prefTime", "10");
			e.commit();
		}
		
		if ( new_time != time ) {
            setUpGame(true);
		}
		
		/** Check for a new delay time */
		int new_delay_time;
		try {
			new_delay_time = Integer.parseInt( prefs.getString("prefDelayTime", "0" ) );
		} catch (Exception ex) {
			new_delay_time = 0;
			Editor e = prefs.edit();
			e.putString("prefDelayTime", "0");
			e.commit();
		}
		
		if ( new_delay_time != delay_time ) {
            setUpGame(true);
		}
		
		boolean new_haptic = prefs.getBoolean("prefHaptic", false);
		if ( new_haptic != haptic ) {
			// No reason to reload the clocks for this one
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
        TextView p1 = (TextView)findViewById(R.id.t_Player1);
        TextView p2 = (TextView)findViewById(R.id.t_Player2);
        View l1 = (View)findViewById(R.id.l_Player1);
        View l2 = (View)findViewById(R.id.l_Player2);

        if (onTheClock == 2) {
            return;
        }

        /**
         * Register that player 2's time is running now and that we haven't yet
         * received our delay.
         */
        onTheClock = 2;
        if (savedOTC == 0 || savedOTC == 1) {
            delayed = false;
        }
        savedOTC = 0;

        p2.setTextColor(color(R.color.active_text));
        p1.setTextColor(color(R.color.inactive_text));
        l2.setBackgroundColor(color(R.color.highlight));
        l1.setVisibility(View.INVISIBLE);
        l2.setVisibility(View.VISIBLE);
		
		if ( delay.equals(BRONSTEIN) ) {
			int secondsLeft = (int) (t_P2 / 1000);
			int minutesLeft = secondsLeft / 60;
			secondsLeft     = secondsLeft % 60;
			
			secondsLeft += 1;
			if ( secondsLeft == 60 ) {
				minutesLeft += 1;
				secondsLeft = 0;
			} else if ( t_P2 == 0 ) {
				secondsLeft = 0;
			} else if ( t_P2 == time * 60000 ) {
				secondsLeft -= 1;
			}
			if (secondsLeft < 10) {
                p2.setText("" + minutesLeft + ":0" + secondsLeft);
			} else {
                p2.setText("" + minutesLeft + ":" + secondsLeft);
			}
		}
			   
		Button pp = (Button)findViewById(R.id.Pause);
        pp.setBackgroundResource(R.drawable.pause_button);
			   
		/** 
         * Unregister the handler from player 1's clock and create a new one
         * which we register with player 2's clock.
		 */
		myHandler.removeCallbacks(mUpdateTimeTask);
		myHandler.removeCallbacks(mUpdateTimeTask2);
        myHandler.postDelayed(mUpdateTimeTask2, 100);
	}
		
	/** Handles the "tick" event for Player 1's clock */
	private Runnable mUpdateTimeTask = new Runnable() {
		public void run() {
            Button b1 = (Button)findViewById(R.id.Player1);
            Button b2 = (Button)findViewById(R.id.Player2);
            TextView p1 = (TextView)findViewById(R.id.t_Player1);
            TextView p2 = (TextView)findViewById(R.id.t_Player2);
			String delay_string = "";
			
			/** Check for delays and apply them */
			if ( delay.equals(FISCHER) && !delayed ) {
				delayed = true;
				t_P1 += delay_time * 1000;
			} else if ( delay.equals(BRONSTEIN) && !delayed ) {
				delayed = true;
				b_delay = delay_time * 1000; //Deduct the first .1s;
				t_P1 += 100; //We'll deduct this again shortly
				delay_string = "+" + (b_delay / 1000 );
			} else if ( delay.equals(BRONSTEIN) && delayed ) {
				if ( b_delay > 0 ) {
					b_delay -= 100;
					t_P1 += 100;
				}
				if (b_delay > 0 ) {
					delay_string = "+" + ( ( b_delay / 1000 ) + 1 );
				}
			}
			
			/** Deduct 0.1s from P1's clock */
			t_P1 -= 100;
			long timeLeft = t_P1;
				 
			/** Format for display purposes */
			int secondsLeft = (int) (timeLeft / 1000);
			int minutesLeft = secondsLeft / 60;
			secondsLeft     = secondsLeft % 60;
			
			secondsLeft += 1;
			if ( secondsLeft == 60 ) {
				minutesLeft += 1;
				secondsLeft = 0;
			} else if ( timeLeft == 0 ) {
				secondsLeft = 0;
			} else if ( timeLeft == time * 60000 ) {
				secondsLeft -= 1;
			}
			
			/** Did we run out of time? */
			if ( timeLeft == 0 ) {
				timeup = true;
				Button pp = (Button)findViewById(R.id.Pause);
                View l1 = (View)findViewById(R.id.l_Player1);

                l1.setBackgroundColor(color(R.color.timesup));
                performHapticFeedback(l1);
                p1.setText("0:00");
				
				b1.setClickable(false);
				b2.setClickable(false);
                pp.setBackgroundResource(R.drawable.reset_button);
				
				Uri uri = Uri.parse(alertTone);
				ringtone = RingtoneManager.getRingtone(getBaseContext(), uri);
				if ( null != ringtone ) {
					ringtone.play();
				}

                myHandler.removeCallbacks(mUpdateTimeTask);
				return;
			}

			/** Display the time, omitting leading 0's for times < 10 minutes */
			if (secondsLeft < 10) {
                p1.setText("" + minutesLeft + ":0" + secondsLeft + delay_string);
			} else {
                p1.setText("" + minutesLeft + ":" + secondsLeft + delay_string);
			}
			     
			/** Re-post the handler so it fires in another 0.1s */
			myHandler.postDelayed(this, 100);
		}
	};
	
	/** Called when P2ClickHandler registers a click/touch event */
	private void P2Click() {
        Button b1 = (Button)findViewById(R.id.Player1);
        Button b2 = (Button)findViewById(R.id.Player2);
        TextView p1 = (TextView)findViewById(R.id.t_Player1);
        TextView p2 = (TextView)findViewById(R.id.t_Player2);
        View l1 = (View)findViewById(R.id.l_Player1);
        View l2 = (View)findViewById(R.id.l_Player2);

        if (onTheClock == 1) {
			return;
        }
				 
		/** 
         * Register that player 1's time is running now and that we haven't yet
         * received our delay.
		 */
        onTheClock = 1;
        if (savedOTC == 0 || savedOTC == 2) {
            delayed = false;
        }
        savedOTC = 0;

        p1.setTextColor(color(R.color.active_text));
        p2.setTextColor(color(R.color.inactive_text));
        l1.setBackgroundColor(color(R.color.highlight));
        l1.setVisibility(View.VISIBLE);
        l2.setVisibility(View.INVISIBLE);
		
		if ( delay.equals(BRONSTEIN) ) {
			int secondsLeft = (int) (t_P1 / 1000);
			int minutesLeft = secondsLeft / 60;
			secondsLeft     = secondsLeft % 60;
			
			secondsLeft += 1;
			if ( secondsLeft == 60 ) {
				minutesLeft += 1;
				secondsLeft = 0;
			} else if ( t_P1 == 0 ) {
				secondsLeft = 0;
			} else if ( t_P1 == time * 60000 ) {
				secondsLeft -= 1;
			}
			if (secondsLeft < 10) {
                p1.setText("" + minutesLeft + ":0" + secondsLeft);
			} else {
                p1.setText("" + minutesLeft + ":" + secondsLeft);
			}
		}
		
		Button pp = (Button)findViewById(R.id.Pause);
        pp.setBackgroundResource(R.drawable.pause_button);
		
		/** 
         * Unregister the handler from player 2's clock and create a new one
         * which we register with player 1's clock.
		 */
		myHandler.removeCallbacks(mUpdateTimeTask);
		myHandler.removeCallbacks(mUpdateTimeTask2);
        myHandler.postDelayed(mUpdateTimeTask, 100);
	}
				
	/** Handles the "tick" event for Player 2's clock */
	private Runnable mUpdateTimeTask2 = new Runnable() {
		public void run() {
            Button b1 = (Button)findViewById(R.id.Player1);
            Button b2 = (Button)findViewById(R.id.Player2);
            TextView p1 = (TextView)findViewById(R.id.t_Player1);
            TextView p2 = (TextView)findViewById(R.id.t_Player2);
			String delay_string = "";
			
			/** Check for delays and apply them */
			if ( delay.equals(FISCHER) && !delayed ) {
				delayed = true;
				t_P2 += delay_time * 1000;
			} else if ( delay.equals(BRONSTEIN) && !delayed ) {
				delayed = true;
				b_delay = delay_time * 1000; //Deduct the first .1s;
				t_P2 += 100; //We'll deduct this again shortly
				delay_string = "+" + ( b_delay / 1000 );
			} else if ( delay.equals(BRONSTEIN) && delayed ) {
				if ( b_delay > 0 ) {
					b_delay -= 100;
					t_P2 += 100;
				}
				if (b_delay > 0 ) {
					delay_string = "+" + ( ( b_delay / 1000 ) + 1 );
				}
			}
			
			/** Deduct 0.1s from P2's clock */
			t_P2 -= 100;
			long timeLeft = t_P2;
					
			/** Format for display purposes */
			int secondsLeft = (int) (timeLeft / 1000);
			int minutesLeft = secondsLeft / 60;
			secondsLeft     = secondsLeft % 60;
			
			secondsLeft += 1;
			if ( secondsLeft == 60 ) {
				minutesLeft += 1;
				secondsLeft = 0;
			} else if ( timeLeft == 0 ) {
				secondsLeft = 0;
			} else if ( timeLeft == time * 60000 ) {
				secondsLeft -= 1;
			}
			
			/** Did we run out of time? */
			if ( timeLeft == 0 ) {
				timeup = true;
				Button pp = (Button)findViewById(R.id.Pause);
                View l2 = (View)findViewById(R.id.l_Player2);

                l2.setBackgroundColor(color(R.color.timesup));
                performHapticFeedback(l2);
				p2.setText("0:00");
				
				b1.setClickable(false);
                b2.setClickable(false);
                pp.setBackgroundResource(R.drawable.reset_button);
				
				Uri uri = Uri.parse(alertTone);
				ringtone = RingtoneManager.getRingtone(getBaseContext(), uri);
				if ( null != ringtone ) {
					ringtone.play();
				}

				myHandler.removeCallbacks(mUpdateTimeTask2);
                return;
			}

			/** Display the time, omitting leading 0's for times < 10 minutes */
			if (secondsLeft < 10) {
                p2.setText("" + minutesLeft + ":0" + secondsLeft + delay_string);
			} else {
                p2.setText("" + minutesLeft + ":" + secondsLeft + delay_string);
			}
					     
			/** Re-post the handler so it fires in another 0.1s */
			myHandler.postDelayed(this, 100);
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
        if (onTheClock == 0 || t_P1 == 0 || t_P2 == 0) {
            Log.v(TAG, "Info: Resetting.");
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
        
        if (!resetClocks) {
            return;
        }
	    
		delay = prefs.getString("prefDelay","None");
		if ( delay.equals("")) {
			delay = "None";
			Editor e = prefs.edit();
			e.putString("prefDelay", "None");
			e.commit();
		}
		
		try {
			time = Integer.parseInt( prefs.getString("prefTime", "10") );	
		} catch (Exception ex) {
			time = 10;
			Editor e = prefs.edit();
			e.putString("prefTime", "10");
			e.commit();
		}
		
		try {
			delay_time = Integer.parseInt( prefs.getString("prefDelayTime", "0") );
		} catch (Exception ex) {
			delay_time = 0;
			Editor e = prefs.edit();
			e.putString("prefDelayTime", "0");
			e.commit();
		}
		
		alertTone = prefs.getString("prefAlertSound", Settings.System.DEFAULT_RINGTONE_URI.toString());		
		if (alertTone.equals("")) {
			alertTone = Settings.System.DEFAULT_RINGTONE_URI.toString();
			Editor e = prefs.edit();
			e.putString("prefAlertSound", alertTone);
			e.commit();
		}
		
		Uri uri = Uri.parse(alertTone);
		ringtone = RingtoneManager.getRingtone(getBaseContext(), uri);

        onTheClock = 0;
        savedOTC = 0;
        delayed = false;

		/** Set time equal to minutes * ms per minute */
		t_P1 = time * 60000;
		t_P2 = time * 60000;

        /** Format and display the clocks */
        p1.setText(FormatTime(t_P1));
        p2.setText(FormatTime(t_P2));
        /** Register the click listeners */
        b1.setOnClickListener(P1ClickHandler);
        b2.setOnClickListener(P2ClickHandler);
        pause.setOnClickListener(PauseListener);
        menu.setOnClickListener(MenuListener);
	}
}
