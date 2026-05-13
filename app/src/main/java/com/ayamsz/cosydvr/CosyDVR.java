package com.ayamsz.cosydvr;

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

import com.ayamsz.cosydvr.R;


public class CosyDVR extends Activity{

    BackgroundVideoRecorder mService;
	Button btnRecord, btnLock, btnGallery, btnSettings, btnExit;
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
	  mainView = findViewById(R.id.mainview);


	  btnRecord = findViewById(R.id.btn_record);
	  btnLock = findViewById(R.id.btn_lock);
	  btnGallery = findViewById(R.id.btn_gallery);
	  btnSettings = findViewById(R.id.btn_settings);
	  btnExit = findViewById(R.id.btn_exit);


	  btnRecord.setOnClickListener(v -> {
		  if (mBound && mService != null) {
			  if (mService.isRecording()) {
				  mService.StopRecording();
				  showHint("Recording Stopped");
			  } else {
				  mService.StartRecording();
				  showHint("Recording Started");
			  }
			  updateInterface();
		  } else {
			  showHint("Wait, connecting with camera...");
		  }
	  });


	  btnLock.setOnClickListener(v -> {
		  if(mBound) {
			  mService.toggleFavorite();
			  updateInterface();
		  }
	  });


	  btnGallery.setOnClickListener(v -> {
		  showHint("Opening Gallery...");

	  });


	  btnSettings.setOnClickListener(v -> {
		  if (mBound && mService != null) {
			  mService.ChangeSurface(1, 1);
			  Intent myIntent = new Intent(getApplicationContext(), CosyDVRPreferenceActivity.class);
			  startActivity(myIntent);
		  } else {
			  showHint("Wait, camera loading...");
		  }
	  });

	  btnExit.setOnClickListener(v -> {
		  if(mBound && mService != null) {
			  mService.StopRecording();
			  unbindService(mConnection);
			  mBound = false;
		  }
		  stopService(new Intent(CosyDVR.this, BackgroundVideoRecorder.class));

		  finishAndRemoveTask();

		  System.exit(0);
	  });

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
				setupServiceAndSize(); //open camera, all permissions on
			} else {
				showHint("Permission denied");
			}
		}
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1234) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (android.provider.Settings.canDrawOverlays(this)) {
					Log.i("CosyDVR", "Permission accessed");
					setupServiceAndSize(); // open camera
				} else {
					showHint("App no responding without access");
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
	   if (mainView != null) {
		   mainView.setBackgroundColor(android.graphics.Color.BLACK);
	   }

       //acquire screen size
	   Display display = getWindowManager().getDefaultDisplay();
	   Point size = new Point();
	   display.getRealSize(size);
	   mWidth = size.x;
	   if (btnRecord != null && btnRecord.getHeight() > 0) {
		   mHeight = size.y - btnRecord.getHeight();
	   } else {
		   mHeight = size.y - 150; // Zabezpieczenie na wypadek, gdyby przycisk jeszcze się nie narysował
	   }
	   Intent intent = new Intent(getApplicationContext(), BackgroundVideoRecorder.class);
	   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	   //startService(intent);
	   androidx.core.content.ContextCompat.startForegroundService(this, intent);
	   bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
   }
	@Override
	public void onBackPressed() {
		if(mBound) {
			mService.StopRecording();
			unbindService(mConnection);
			mBound = false;
		}
		stopService(new Intent(this, BackgroundVideoRecorder.class));
		finish();
		super.onBackPressed();
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
			mainView.post(new Runnable() {
				@Override
				public void run() {
					mWidth = mainView.getWidth();
					mHeight = mainView.getHeight();

					if (mWidth > 0 && mHeight > 0) {
						mService.ChangeSurface(mWidth, mHeight);
					}
				}
			});
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
	if(mBound && btnRecord != null) {
		// Zmiana tekstu dla przycisku nagrywania
		if(mService.isRecording()) {
			btnRecord.setText("STOP");
		} else {
			btnRecord.setText("START");
		}

		// Zmiana tekstu dla przycisku zabezpieczania (0 = odblokowany, 1/2 = zablokowany)
		if(mService.isFavorite() > 0) {
			btnLock.setText("LOCKED");
		} else {
			btnLock.setText("LOCK");
		}
	}
}


/** Defines callbacks for service binding, passed to bindService() */
private final ServiceConnection mConnection = new ServiceConnection() {

	@Override
	public void onServiceConnected(ComponentName className, IBinder service) {
		Log.d("CosyDVR_DEBUG", "Connected to Background Recorder Service.");
		BackgroundVideoRecorder.LocalBinder binder = (BackgroundVideoRecorder.LocalBinder) service;
		mService = binder.getService();
		mBound = true;
		updateInterface();

		mainView.post(new Runnable() {
			@Override
			public void run() {
				mWidth = mainView.getWidth();
				mHeight = mainView.getHeight();

				if (mWidth > 0 && mHeight > 0) {
					mService.ChangeSurface(mWidth, mHeight);
				}
			}
		});
	}

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
		Log.d("CosyDVR_DEBUG", "Service disconnected.");
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
