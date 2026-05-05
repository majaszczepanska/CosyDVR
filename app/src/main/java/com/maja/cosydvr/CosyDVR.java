package com.maja.cosydvr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
//import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.Toast;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.os.IBinder;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.core.content.ContextCompat;


public class CosyDVR extends Activity{

    BackgroundVideoRecorder mService;
    Button favButton,recButton,flsButton,exiButton,focButton,nigButton;
    View mainView;
    boolean mBound = false;
    boolean mayClick = false;
    private int mWidth=1,mHeight=1;
    private float mScaleFactor = 4.0f;
    private final String[] mFocusNames = {"I",
			 "V",
			 "A",
			 "M",
			 "E",
			 };

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(4.0f, Math.min(mScaleFactor, 14.0f));
	      	if(mBound) {
	      		mService.setZoom(mScaleFactor);
	    	}
            return true;
        }
    }

    /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      setContentView(R.layout.main);

      favButton = findViewById(R.id.fav_button);
      recButton = findViewById(R.id.rec_button);
      nigButton = findViewById(R.id.nig_button);
	  flsButton = findViewById(R.id.fls_button);
	  exiButton = findViewById(R.id.exi_button);
	  focButton = findViewById(R.id.foc_button);
	  mainView = findViewById(R.id.mainview);
      
      favButton.setOnClickListener(favButtonOnClickListener);
      recButton.setOnClickListener(recButtonOnClickListener);
      focButton.setOnClickListener(focButtonOnClickListener);
      nigButton.setOnClickListener(nigButtonOnClickListener);
      flsButton.setOnClickListener(flsButtonOnClickListener);
      exiButton.setOnClickListener(exiButtonOnClickListener);
      exiButton.setOnLongClickListener(exiButtonOnLongClickListener);
      recButton.setOnLongClickListener(recButtonOnLongClickListener);
      nigButton.setOnLongClickListener(nigButtonOnLongClickListener);

      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      final ScaleGestureDetector mScaleDetector = new ScaleGestureDetector(this, new ScaleListener());
      mainView.setOnTouchListener(new OnTouchListener() {
          @SuppressLint("ClickableViewAccessibility")
          @Override
          public boolean onTouch(View v, MotionEvent event) {
         	 mScaleDetector.onTouchEvent(event);
         	 //Extra analysis for single tap detection. Swipes are detected as autofocus too for now
         	 int action = event.getAction() & MotionEvent.ACTION_MASK;
         	 switch(action) {
         	 	case MotionEvent.ACTION_DOWN : {
         	 		mayClick = true;	//first finger touch is like click
         	 		break;
         	 	}
         	 	case MotionEvent.ACTION_POINTER_DOWN : {
         	 		mayClick = false;	//second finger is not click
         	 		break;
         	 	}
         	 	case MotionEvent.ACTION_UP : {
         	 		if(mayClick) {		//first finger up. check if it was single one
         	 			if(mBound) {
         	 				mService.autoFocus();
         	 			}
         	 		}
     	 			mayClick = false;
         	 	}  
         	 }
         	 return true;
          }
      });
	  updateInterface();
	  mainView.post(new Runnable() {
		  @Override
		  public void run() {
			  if (checkAndRequestPermissions()) {
				  setupServiceAndSize();
			  }
		  }
	  });
  }
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 100) {
			boolean allGranted = true;
			for (int result : grantResults) {
				if (result != PackageManager.PERMISSION_GRANTED) {
					allGranted = false;
					break;
				}
			}

			if (allGranted) {
				setupServiceAndSize(); // Użytkownik się zgodził -> odpalamy kamerę!
			} else {
				showHint("Brak wymaganych uprawnień!");
			}
		}
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1234) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (android.provider.Settings.canDrawOverlays(this)) {
					Log.i("CosyDVR", "Zgoda na nakładki uzyskana, odpalam serwis!");
					setupServiceAndSize(); // Odpalamy kamerę po powrocie!
				} else {
					showHint("Bez nakładek aplikacja nie zadziała!");
				}
			}
		}
	}
/*
  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
      super.onWindowFocusChanged(hasFocus);
      if(hasFocus){//check permissions
		  if (checkAndRequestPermissions()) {
			  setupServiceAndSize();
		  }
      }
   }*/

   private boolean checkAndRequestPermissions() {
	   java.util.ArrayList<String> permsList = new java.util.ArrayList<>();
	   permsList.add(Manifest.permission.CAMERA);
	   permsList.add(android.Manifest.permission.RECORD_AUDIO);
	   permsList.add(android.Manifest.permission.ACCESS_FINE_LOCATION);

	   if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
		   permsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
	   }

	   if (android.os.Build.VERSION.SDK_INT >= 33) {
		   permsList.add(android.Manifest.permission.POST_NOTIFICATIONS);
	   }

	   String[] permissions = permsList.toArray(new String[0]);
	   boolean allGranted = true;
	   for (String p : permissions) {
		   if(androidx.core.content.ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
			   allGranted = false;
			   break;
		   }
	   }
	   if (!allGranted) {
		   androidx.core.app.ActivityCompat.requestPermissions(this, permissions, 100);
		   return false;
	   }
	   return true;
   }

   private void setupServiceAndSize() {
       if (!android.provider.Settings.canDrawOverlays(this)) {
           showHint("Allow to display over other apps");
           Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                   android.net.Uri.parse("package:" + getPackageName()));
           startActivityForResult(intent, 1234);
           return; //stop and go to settings
       }
       //acquire screen size
	   Display display = getWindowManager().getDefaultDisplay();
	   Point size = new Point();
	   display.getSize(size);
	   mWidth = size.x;
	   mHeight = size.y - favButton.getHeight();

	   Intent intent = new Intent(getApplicationContext(), BackgroundVideoRecorder.class);
	   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	   //startService(intent);
	   androidx.core.content.ContextCompat.startForegroundService(this, intent);
	   bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
   }

  @Override  
  public void onDestroy(){
	  if(mBound) {
		  unbindService(mConnection);
          mBound = false;
	  }
	  super.onDestroy();
  }
  
  @Override
  public void onPause(){
	  if(mBound) {
		  mService.ChangeSurface(1, 1);
	  }
	  super.onPause();
	  this.unregisterReceiver(receiver);
  }

  @Override
  public void onResume(){
    	updateInterface(); //after preferences
	  if(mBound) {
		  mService.ChangeSurface(mWidth, mHeight);
	  }
	  super.onResume();
      ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
  }

public void showHint(String text){
	SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
	if (!sharedPref.getBoolean("hide_hints", false)) {
		Toast.makeText(this, text, Toast.LENGTH_LONG).show();
	//boolean HIDE_HINTS = sharedPref.getBoolean("hide_hints", false);
	//if(!HIDE_HINTS) {
		//Toast infotoast = Toast.makeText(CosyDVR.this, text, Toast.LENGTH_LONG);
		//infotoast.setGravity(Gravity.BOTTOM/* | Gravity.FILL_HORIZONTAL*/,0,0);
		//infotoast.setMargin(0,0);
		//infotoast.show();
	}
}

@SuppressLint("SetTextI18n")
public void updateInterface(){
	SharedPreferences sharedPref = PreferenceManager
			.getDefaultSharedPreferences(this);
	boolean REVERSE_ORIENTATION = sharedPref.getBoolean("reverse_landscape", false);
	if(REVERSE_ORIENTATION) {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
	} else {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	}
	if(mBound) {
		favButton.setText(getString(R.string.fav) + " [" + mService.isFavorite() + "]");
		if(mService.isRecording()) {
			recButton.setText(getString(R.string.restart));
		} else {
			recButton.setText(getString(R.string.start));
		}
	}
}
  
 Button.OnClickListener favButtonOnClickListener
  = new Button.OnClickListener(){
  @SuppressLint("SetTextI18n")
  @Override
  public void onClick(View v) {
   // TODO Auto-generated method stub
	  if(mBound) {
		  mService.toggleFavorite();
		  favButton.setText(getString(R.string.fav) + " [" + mService.isFavorite() + "]");
	  }
 }};

  Button.OnClickListener recButtonOnClickListener
  = new Button.OnClickListener(){

@Override
public void onClick(View v) {
	if (mBound && mService != null) {
		showHint(getString(R.string.longclick) + ": " + getString(R.string.preferences));
		mService.RestartRecording();
	} else {
		showHint("Wait, connecting with camera..");
	}
}};

Button.OnClickListener focButtonOnClickListener
= new Button.OnClickListener(){
@SuppressLint("SetTextI18n")
@Override
public void onClick(View v) {
	  if(mBound) {
		  mService.toggleFocus();
		  focButton.setText(getString(R.string.focus) + " [" + mFocusNames[mService.getFocusMode()] + "]");
	  }
}};


Button.OnClickListener nigButtonOnClickListener
= new Button.OnClickListener(){
@Override
public void onClick(View v) {
	showHint(getString(R.string.longclick) + ": " + getString(R.string.timelapse));
	  if(mBound) {
		  mService.toggleNight();
	  }
}};

Button.OnClickListener flsButtonOnClickListener
= new Button.OnClickListener(){
@Override
public void onClick(View v) {
// TODO Auto-generated method stub
	  if(mBound) {
		  mService.toggleFlash();
	  }
}};

Button.OnLongClickListener recButtonOnLongClickListener
= new Button.OnLongClickListener(){
@Override
public boolean onLongClick(View v) {
// TODO Auto-generated method stub
	if (mBound && mService != null) {
		mService.ChangeSurface(1, 1);
		Intent myIntent = new Intent(getApplicationContext(), CosyDVRPreferenceActivity.class);
		startActivity(myIntent);
	} else {
		showHint("Wait, camera loading...");
	}
	return true;
}};

Button.OnLongClickListener nigButtonOnLongClickListener
= new Button.OnLongClickListener(){
@Override
public boolean onLongClick(View v) {
	  if(mBound) {
		  mService.toggleTimeLapse();
	  }
	  return true;
}};

Button.OnClickListener exiButtonOnClickListener
= v -> showHint(getString(R.string.longclick) + ": " + getString(R.string.exit));

Button.OnLongClickListener exiButtonOnLongClickListener
= v -> {
	if(mBound) {
		unbindService(CosyDVR.this.mConnection);
		CosyDVR.this.mBound = false;
	}
	stopService(new Intent(CosyDVR.this, BackgroundVideoRecorder.class));
	CosyDVR.this.finish();
	return true;
};

/** Defines callbacks for service binding, passed to bindService() */
private final ServiceConnection mConnection = new ServiceConnection() {

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        // We've bound to LocalService, cast the IBinder and get LocalService instance
		Log.d("CosyDVR_DEBUG", "HURA! Połączono z usługą.");
        BackgroundVideoRecorder.LocalBinder binder = (BackgroundVideoRecorder.LocalBinder) service;
        mService = binder.getService();
        mBound = true;
        mService.ChangeSurface(mWidth, mHeight);
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
		Log.d("CosyDVR_DEBUG", "zleee");
        mBound = false;
    }

};

private final IntentFilter filter = new IntentFilter("com.maja.cosydvr.updateinterface");

private final BroadcastReceiver receiver = new BroadcastReceiver(){
    
    @Override
    public void onReceive(Context c, Intent i) {
    	updateInterface();
    }
};
}
