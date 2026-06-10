package com.ayamsz.cosydvr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

public class CosyDVR extends AppCompatActivity {
	private View mainView;
	private Button btnRecord, btnSave, btnGallery, btnSettings, btnExit;

	private BackgroundVideoRecorder mService;
	private boolean mBound = false;
	private int mWidth, mHeight;
	private float mScaleFactor = 4.0f;
	private boolean mayClick = false;
	private boolean mUserStoppedManually = false;

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();
            mScaleFactor = Math.max(4.0f, Math.min(mScaleFactor, 14.0f));
	      	if(mBound) {
	      		mService.setZoom(mScaleFactor);
	    	}
            return true;
        }
    }

  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);

	  mainView = findViewById(R.id.mainview);
	  btnRecord = findViewById(R.id.btn_record);
	  btnSave = findViewById(R.id.btn_save);
	  btnGallery = findViewById(R.id.btn_gallery);
	  btnSettings = findViewById(R.id.btn_settings);
	  btnExit = findViewById(R.id.btn_exit);

	  btnRecord.setOnClickListener(v -> {
		  if (mBound && mService != null) {
			  if (mService.isRecording()) {
				  mService.StopRecording();
				  mUserStoppedManually = true;
				  showHint("Recording Stopped");
			  } else {
				  mService.StartRecording();
				  mUserStoppedManually = false;
				  showHint("Recording Started");
			  }
			  updateInterface();
		  } else {
			  showHint("Wait, connecting with camera...");
		  }
	  });

	  btnSave.setOnClickListener(v -> {
		  if (mBound && mService != null) {
			  mService.toggleSave();
			  updateInterface();
		  }
	  });

	  btnGallery.setOnClickListener(v -> {
		  if (mBound && mService != null) {
			  mService.ChangeSurface(1, 1);
		  }
		  Intent intent = new Intent(this, GalleryActivity.class);
		  startActivity(intent);
	  });

	  btnSettings.setOnClickListener(v -> {
		  if (mBound && mService != null) {
			  mService.ChangeSurface(1, 1);
		  }
		  Intent intent = new Intent(this, CosyDVRPreferenceActivity.class);
		  startActivity(intent);
	  });

	  btnExit.setOnClickListener(v -> {
		  if (mBound && mService != null) {
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
         	 int action = event.getAction() & MotionEvent.ACTION_MASK;
         	 switch(action) {
         	 	case MotionEvent.ACTION_DOWN : {
         	 		mayClick = true;
         	 		break;
         	 	}
         	 	case MotionEvent.ACTION_POINTER_DOWN : {
         	 		mayClick = false;
         	 		break;
         	 	}
         	 	case MotionEvent.ACTION_UP : {
         	 		if(mayClick) {
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

	  if (checkAndRequestPermissions()) {
		  setupServiceAndSize();
	  }
	  updateInterface();
  }

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == 100) {
			boolean allGranted = true;
			for (int result : grantResults) {
				if (result != PackageManager.PERMISSION_GRANTED) {
					allGranted = false;
					break;
				}
			}
			if (allGranted) {
				setupServiceAndSize();
			} else {
				showHint("Permission denied");
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1234) {
			if (android.provider.Settings.canDrawOverlays(this)) {
				setupServiceAndSize();
			} else {
				showHint("App no responding without access");
			}
		}
	}

   private boolean checkAndRequestPermissions() {
	   java.util.ArrayList<String> permsList = new java.util.ArrayList<>();
	   permsList.add(Manifest.permission.CAMERA);
	   permsList.add(Manifest.permission.RECORD_AUDIO);
	   permsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
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
           return;
       }
	   if (mainView != null) {
		   mainView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
	   }
	   Display display = getWindowManager().getDefaultDisplay();
	   Point size = new Point();
	   display.getRealSize(size);
	   mWidth = size.x;
	   mHeight = size.y;
	   Intent intent = new Intent(getApplicationContext(), BackgroundVideoRecorder.class);
	   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
		super.onResume();
		IntentFilter filter = new IntentFilter("com.maja.cosydvr.updateinterface");
		ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

		if(mBound && mService != null) {
			mService.showOverlays();
			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
			boolean autostart = sharedPref.getBoolean("autostart_recording", false);

			if (autostart && !mService.isRecording() && !mUserStoppedManually) {
				mService.StartRecording();
				mUserStoppedManually = false;
			}

			mainView.post(() -> {
				mWidth = mainView.getWidth();
				mHeight = mainView.getHeight();
				if (mWidth > 0 && mHeight > 0) {
					mService.ChangeSurface(mWidth, mHeight);

					mainView.postDelayed(() -> {
						mainView.setBackgroundColor(android.graphics.Color.BLACK);
						getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK));
					}, 400);
				}
			});
		}
		updateInterface();
	}

public void showHint(String text){
	Toast.makeText(this, text, Toast.LENGTH_LONG).show();
}

@SuppressLint("SetTextI18n")
public void updateInterface(){
	SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
	boolean REVERSE_ORIENTATION = sharedPref.getBoolean("reverse_landscape", false);
	if(REVERSE_ORIENTATION) {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
	} else {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	}
	if(mBound && btnRecord != null) {
		if(mService.isRecording()) {
			btnRecord.setText("STOP");
			btnRecord.setBackground(getRoundedBackground("#E53935"));
		} else {
			btnRecord.setText("START");
			btnRecord.setBackground(getRoundedBackground("#43A047"));
		}
		if(mService.checkIsSaved() > 0) {
			btnSave.setText("SAVING");
			btnSave.setBackground(getRoundedBackground("#FF9800"));
		} else {
			btnSave.setText("SAVE");
			btnSave.setBackground(getRoundedBackground("#673AB7"));
		}
		btnGallery.setBackground(getRoundedBackground("#2196F3"));
		btnSettings.setBackground(getRoundedBackground("#455A64"));
		btnExit.setBackground(getRoundedBackground("#E53935"));
	}
}

	private android.graphics.drawable.GradientDrawable getRoundedBackground(String hexColor) {
		android.graphics.drawable.Drawable background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.rounded_button);
		if (background == null) return null;
		android.graphics.drawable.GradientDrawable gradientDrawable = (android.graphics.drawable.GradientDrawable) background.mutate();
		gradientDrawable.setColor(android.graphics.Color.parseColor(hexColor));
		return gradientDrawable;
	}

private final ServiceConnection mConnection = new ServiceConnection() {
	@Override
	public void onServiceConnected(ComponentName className, IBinder service) {
		BackgroundVideoRecorder.LocalBinder binder = (BackgroundVideoRecorder.LocalBinder) service;
		mService = binder.getService();
		mBound = true;
		updateInterface();
		mainView.post(() -> {
			mWidth = mainView.getWidth();
			mHeight = mainView.getHeight();
			if (mWidth > 0 && mHeight > 0) {
				mService.ChangeSurface(mWidth, mHeight);
				mainView.postDelayed(() -> {
					mainView.setBackgroundColor(android.graphics.Color.BLACK);
					getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK));
				}, 400);
			}
		});
	}
	@Override
	public void onServiceDisconnected(ComponentName arg0) {
		mBound = false;
	}
};

	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateInterface();
		}
	};
}
