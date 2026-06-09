package com.ayamsz.cosydvr;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.text.format.DateFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Binder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.Bundle;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Locale;
//import java.net.InetAddress;
//import java.net.Socket;
//import java.net.UnknownHostException;

import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

@SuppressWarnings("deprecation")
public class BackgroundVideoRecorder extends Service implements
		SurfaceHolder.Callback, MediaRecorder.OnInfoListener, LocationListener {
	// CONSTANTS-OPTIONS
	public long MAX_TEMP_FOLDER_SIZE_KB = 10000000;
	public long MIN_FREE_SPACE_KB = 1000000;
	public int MAX_VIDEO_DURATION = 600000;
	public int VIDEO_WIDTH = 1920;
	public int VIDEO_HEIGHT = 1080;
	public int VIDEO_FRAME_RATE = 30;
	public int TIME_LAPSE_FACTOR = 1;
	public int MAX_VIDEO_BIT_RATE = 15000000;
	// public int MAX_VIDEO_BIT_RATE = 256000; //=for streaming;
	public int REFRESH_TIME = 1000;
	public String VIDEO_FILE_EXT = ".mp4";
	public String SRT_FILE_EXT = ".srt";
	public String GPX_FILE_EXT = ".gpx";
	// public int AUDIO_SOURCE = CAMERA;
	public String SD_CARD_PATH = "";
	public String BASE_FOLDER = "";
	public String SAVED_FOLDER = "/saved/";
	public String TEMP_FOLDER = "/temp/";

	public boolean AUTOSTART = false;
	public boolean USEGPS = true;

	public int CAMERA_ID = 0;

	public boolean RECORD_AUDIO = true;

	public String SPEED_UNITS = "kmh";
        public int ORIENTATION_ANGLE = 0;
        public int ORIENTATION_HINT = 0;

	// Binder given to clients
	private final IBinder mBinder = new LocalBinder();
	private WindowManager windowManager = null;
	private SurfaceView surfaceView = null;
	private Camera camera = null;
	private MediaRecorder mediaRecorder = null;
	private boolean isrecording = false;
	private int isSaved = 0;
	private boolean currentFileSaved = false;
	private int focusmode = 0;
	private int scenemode = 0;
	private int flashmode = 0;
	private int zoomfactor = 0;
	private String currentfile = null;

	private SurfaceHolder mSurfaceHolder = null;
	private PowerManager.WakeLock mWakeLock = null;
	private Timer mTimer = null;
	private TimerTask mTimerTask = null;

	private TextView mTextView = null;
	private TextView mSpeedView = null;
	private TextView mBatteryView = null;

	private View mButtonOverlay = null;
	private Button btnSaveOvl, btnRecordOvl;

	private long mSrtCounter = 0;
	private Handler mHandler = null;

	private OutputStreamWriter mSrtWriter = null;
	private OutputStreamWriter mGpxWriter = null;
	private long mSrtBegin = 0;
	private long mNewFileBegin = 0;

	private LocationManager mLocationManager = null;
	private Location mLocation = null;
	private long mPrevTim = 0;

	private AudioManager mAudioManager = null;
	private android.media.AudioFocusRequest mFocusRequest = null;

	private final AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener = focusChange -> {
		switch (focusChange) {
			case AudioManager.AUDIOFOCUS_LOSS:
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
				// If we lose focus (e.g. someone calls), we can optionally mute the recording
				// or simply log this fact. Most dashcams continue recording without sound.
				Log.d("CosyDVR", "Audio Focus lost");
				break;
			case AudioManager.AUDIOFOCUS_GAIN:
				Log.d("CosyDVR", "Audio Focus gained");
				break;
		}
	};

	// private List<String> mFocusModes;
	private final String[] mFocusModes = { Parameters.FOCUS_MODE_INFINITY,
			Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, Parameters.FOCUS_MODE_AUTO,
			Parameters.FOCUS_MODE_MACRO, Parameters.FOCUS_MODE_EDOF, };

	private final String[] mSceneModes = { Parameters.SCENE_MODE_AUTO,
			Parameters.SCENE_MODE_NIGHT, };


	private final String[] mFlashModes = { Parameters.FLASH_MODE_OFF,
			Parameters.FLASH_MODE_TORCH, };

	// some troubles with video files @SuppressLint("HandlerLeak")

	private final Handler hudHandler = new Handler(Looper.getMainLooper());
	private final Runnable hudRunnable = new Runnable() {
		@Override
		public void run() {
			updateHUD();
			hudHandler.postDelayed(this, 1000);
		}
	};

	private static final class HandlerExtension extends Handler {
		private final WeakReference<BackgroundVideoRecorder> mService;

		HandlerExtension(BackgroundVideoRecorder service) {
			super(Looper.getMainLooper());
			mService = new WeakReference<>(service);
		}

		@android.annotation.SuppressLint("MissingPermission")
		public void handleMessage(@androidx.annotation.NonNull Message msg) {
			BackgroundVideoRecorder service = mService.get();
			if (service == null || !service.isrecording) {
				return;
			}
			
			StringBuilder srtBuilder = new StringBuilder();
			StringBuilder gpxBuilder = new StringBuilder();
			Date datetime = new Date();
			long tick = (service.mSrtBegin - service.mNewFileBegin)/service.TIME_LAPSE_FACTOR; // relative srt text begin/
													// i.e. prev tick time
			int hour = (int) (tick / (1000 * 60 * 60));
			int min = (int) (tick % (1000 * 60 * 60) / (1000 * 60));
			int sec = (int) (tick % (1000 * 60) / (1000));
			int mil = (int) (tick % (1000));
			srtBuilder.append(String.format(Locale.US, "%d\n%02d:%02d:%02d,%03d --> ",
							service.mSrtCounter, hour, min, sec, mil));

			service.mSrtBegin = SystemClock.elapsedRealtime();
			tick = (service.mSrtBegin - service.mNewFileBegin)/service.TIME_LAPSE_FACTOR; // relative srt text end. i.e.
												// this tick time
			hour = (int) (tick / (1000 * 60 * 60));
			min = (int) (tick % (1000 * 60 * 60) / (1000 * 60));
			sec = (int) (tick % (1000 * 60) / (1000));
			mil = (int) (tick % (1000));
			srtBuilder.append(String.format(Locale.US, "%02d:%02d:%02d,%03d\n", hour, min, sec,
							mil));
			srtBuilder.append(DateFormat.format("yyyy-MM-dd_kk-mm-ss",
							datetime.getTime()).toString()).append("\n");

                        if (service.USEGPS) {
			        try {
			                service.mLocation = service.mLocationManager
						.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			        } catch (Exception e) {
			        	service.mLocation = null;
			        }
                        }

			double lat = -1, lon = -1, alt = -1;
			float spd = 0, acc = -1;
			int sat = 0;
			long tim = 0;

			if (service.USEGPS && service.mLocation != null) {
				lat = service.mLocation.getLatitude();
				lon = service.mLocation.getLongitude();
				tim = service.mLocation.getTime() / 1000; // millisec to sec
				alt = service.mLocation.getAltitude();
				acc = service.mLocation.getAccuracy();
				spd = service.mLocation.getSpeed() * 3.6f; // by GPS
				if (service.mLocation.getExtras() != null) {
					sat = service.mLocation.getExtras().getInt("satellites");
				}
			}

			srtBuilder.append(String.format(Locale.US, 
							"lat:%1.6f,lon:%1.6f,alt:%1.0f,spd:%1.1fkm/h\nacc:%01.1fm,sat:%d,tim:%d\n\n",
							lat, lon, alt, spd, acc, sat, tim));
			gpxBuilder.append(String.format(Locale.US, "<trkpt lon=\"%1.8f\" lat=\"%1.8f\">\n",
							lon, lat).replace(",", "."));
			gpxBuilder.append(String.format(Locale.US, "<ele>%1.0f</ele>\n", alt));
			gpxBuilder.append("<time>")
					.append(DateFormat.format("yyyy-MM-dd", datetime.getTime()).toString())
					.append("T")
					.append(DateFormat.format("kk:mm:ss", datetime.getTime()).toString())
					.append("Z</time>\n");
			gpxBuilder.append("</trkpt>\n");
			if (service.mPrevTim == 0 && tim != service.mPrevTim) {
				service.mPrevTim = tim;
			}
			try {
				if (service.isrecording) {
					service.mSrtWriter.write(srtBuilder.toString());
					if (tim != service.mPrevTim && lat > 0) {
						service.mGpxWriter.write(gpxBuilder.toString());
					}
				}
			} catch (IOException e) {
				Log.e("CosyDVR", "Writer error", e);
			}

			service.mPrevTim = tim;
		}
	}

	/**
	 * Class used for the client Binder. Because we know this service always
	 * runs in the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		BackgroundVideoRecorder getService() {
			// Return this instance of LocalService so clients can call public
			// methods
			return BackgroundVideoRecorder.this;
		}
	}

	@Override
	public void onCreate() {
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		AUTOSTART = sharedPref.getBoolean("autostart_recording", false);

		Intent myIntent = new Intent(this, CosyDVR.class);
		myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int pendingFlags = PendingIntent.FLAG_IMMUTABLE;
        PendingIntent.getActivity(this, 0, myIntent, pendingFlags);

		//notification channel
		String channelId = "cosydvr_background_channel";
		android.app.NotificationChannel channel = new android.app.NotificationChannel(
				channelId,
				"CosyDVR Recording",
				android.app.NotificationManager.IMPORTANCE_LOW
		);
		android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.createNotificationChannel(channel);
		}

		updateNotification();

		// Create new SurfaceView, set its size to 1x1, move it to the top left
		// corner and set this service as a callback
		windowManager = (WindowManager) this
				.getSystemService(Context.WINDOW_SERVICE);
		surfaceView = new SurfaceView(this);
		int overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

		LayoutParams layoutParams = new WindowManager.LayoutParams(
				1, 1, overlayType,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				PixelFormat.TRANSLUCENT);

		layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
		try {
			windowManager.addView(surfaceView, layoutParams);
		} catch (Exception ignored) {
		}

		mTextView = new TextView(this);
		LayoutParams textParams = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				overlayType,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				PixelFormat.TRANSLUCENT);
		textParams.gravity = Gravity.START | Gravity.TOP;
		windowManager.addView(mTextView, textParams);
		mTextView.setTextColor(Color.parseColor("#FFFFFF"));
		mTextView.setShadowLayer(5, 0, 0, Color.parseColor("#000000"));
		mTextView.setText("--");

		mSpeedView = new TextView(this);
		LayoutParams speedParams = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				overlayType,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				PixelFormat.TRANSLUCENT);
		speedParams.gravity = Gravity.END | Gravity.TOP;
		windowManager.addView(mSpeedView, speedParams);
		mSpeedView.setTextColor(Color.parseColor("#A0A0A0"));
		mSpeedView.setShadowLayer(5, 0, 0, Color.parseColor("#000000"));
		mSpeedView.setTextSize(56);
		mSpeedView.setText("---");

		mBatteryView = new TextView(this);
		LayoutParams batParams = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				overlayType,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				PixelFormat.TRANSLUCENT);
		batParams.gravity = Gravity.CENTER;
		windowManager.addView(mBatteryView, batParams);
		mBatteryView.setTextColor(Color.parseColor("#FFFFFF"));
		mBatteryView.setShadowLayer(5, 0, 0, Color.parseColor("#000000"));
		mBatteryView.setTextSize(80);
		mBatteryView.setText("");

		surfaceView.getHolder().addCallback(this);
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"com.maja.cosydvr:CosyDVRWakeLock");

		mHandler = new HandlerExtension(this);

		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		hudHandler.post(hudRunnable);
		startGps();
		createButtonOverlay();
	}

	@SuppressLint("InflateParams")
    private void createButtonOverlay() {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mButtonOverlay = inflater.inflate(R.layout.overlay_buttons, null);

		int overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

		WindowManager.LayoutParams params = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				overlayType,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT);

		params.gravity = Gravity.BOTTOM;
		windowManager.addView(mButtonOverlay, params);
		mButtonOverlay.setVisibility(View.GONE);

		btnSaveOvl = mButtonOverlay.findViewById(R.id.ovl_btn_save);
		btnRecordOvl = mButtonOverlay.findViewById(R.id.ovl_btn_record);
		Button btnGalleryOvl = mButtonOverlay.findViewById(R.id.ovl_btn_gallery);
		Button btnSettingsOvl = mButtonOverlay.findViewById(R.id.ovl_btn_settings);
		Button btnExitOvl = mButtonOverlay.findViewById(R.id.ovl_btn_exit);

		// Set initial colors for Gallery, Settings and Exit
		btnGalleryOvl.getBackground().setTint(Color.parseColor("#2196F3"));
		btnSettingsOvl.getBackground().setTint(Color.parseColor("#455A64"));
		btnExitOvl.getBackground().setTint(Color.parseColor("#E53935"));

		btnRecordOvl.setOnClickListener(v -> {
			if (isrecording) {
				StopRecording();
			} else {
				StartRecording();
			}
			updateHUD();
		});

		btnSaveOvl.setOnClickListener(v -> {
			toggleSave();
			updateHUD();
		});

		btnGalleryOvl.setOnClickListener(v -> {
			ChangeSurface(1, 1);
			Intent intent = new Intent(this, GalleryActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		});

		btnSettingsOvl.setOnClickListener(v -> {
			ChangeSurface(1, 1);
			Intent intent = new Intent(this, CosyDVRPreferenceActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		});

		btnExitOvl.setOnClickListener(v -> {
			stopSelf();
			android.os.Process.killProcess(android.os.Process.myPid());
		});
	}

	// Method called right after Surface created (initializing and starting
	// MediaRecorder)
	@Override
	public void surfaceCreated(@androidx.annotation.NonNull SurfaceHolder surfaceHolder) {
		mSurfaceHolder = surfaceHolder;
		try {
			if (camera == null) {
				camera = Camera.open(CAMERA_ID);
				camera.setDisplayOrientation(ORIENTATION_ANGLE);
			}
			camera.setPreviewDisplay(mSurfaceHolder);
			camera.startPreview();
		} catch (Exception e) {
			Log.e("CosyDVR", "Preview error: " + e.getMessage());
		}
		if (AUTOSTART) {
			StartRecording();
		}
	}

	public int checkIsSaved() {
		return isSaved;
	}

	public boolean isRecording() {
		return isrecording;
	}

	public void StopRecording() {
		if (isrecording) {
			Stop();
			ResetReleaseLock();
			mTimer.cancel();
			mTimer.purge();
			mTimer = null;
			mTimerTask.cancel();
			mTimerTask = null;
			try {
				mGpxWriter.write("</trkseg>\n" + "</trk>\n" + "</gpx>"); // GPX
																			// footer

				mSrtWriter.flush();
				mSrtWriter.close();
				mGpxWriter.flush();
				mGpxWriter.close();
			} catch (IOException e) {
				Log.e("CosyDVR", "Error closing writers", e);
			}

			if (currentfile != null && currentFileSaved) {
				File tmpfile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER // "/CosyDVR/temp/"
						+ currentfile + VIDEO_FILE_EXT);
				File favfile = new File(SD_CARD_PATH + BASE_FOLDER + SAVED_FOLDER // "/CosyDVR/saved/"
						+ currentfile + VIDEO_FILE_EXT);
				if (!tmpfile.renameTo(favfile)) Log.e("CosyDVR", "Failed to rename video");
				tmpfile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER // "/CosyDVR/temp/"
						+ currentfile + SRT_FILE_EXT);
				favfile = new File(SD_CARD_PATH + BASE_FOLDER + SAVED_FOLDER // "/CosyDVR/saved/"
						+ currentfile + SRT_FILE_EXT);
				if (!tmpfile.renameTo(favfile)) Log.e("CosyDVR", "Failed to rename srt");
				tmpfile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER // "/CosyDVR/temp/"
						+ currentfile + GPX_FILE_EXT);
				favfile = new File(SD_CARD_PATH + BASE_FOLDER + SAVED_FOLDER // "/CosyDVR/saved"
						+ currentfile + GPX_FILE_EXT);
				if (!tmpfile.renameTo(favfile)) Log.e("CosyDVR", "Failed to rename gpx");
			}
			isSaved = 0;
			currentFileSaved = false;
			isrecording = false;
			updateNotification();
		}
		abandonAudioFocus();
	}

	public void UpdateLayoutInterface() {
		Intent intent = new Intent();
		intent.setAction("com.maja.cosydvr.updateinterface");
		sendBroadcast(intent); 
	}
	
	public void RestartRecording() {
		int tempSaved = isSaved;
		StopRecording();
		isSaved = tempSaved;
		StartRecording();
	}

	public void StartRecording() {
		requestAudioFocus();
		/* Rereading preferences */
		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(this);
		USEGPS = sharedPref.getBoolean("use_gps", true);
		focusmode = Integer.parseInt(sharedPref.getString("focus_mode_pref", "0"));
		ORIENTATION_ANGLE = Integer.parseInt(sharedPref.getString(
				"orientation_angle", "0"));
		ORIENTATION_HINT = Integer.parseInt(sharedPref.getString(
				"orientation_hint", "0"));
		MAX_VIDEO_BIT_RATE = Integer.parseInt(sharedPref.getString(
				"video_bitrate", "15000000"));

		String resolution = sharedPref.getString("video_resolution", "1920x1080");
		String[] resParts = resolution.split("x");
		VIDEO_WIDTH = Integer.parseInt(resParts[0]);
		VIDEO_HEIGHT = Integer.parseInt(resParts[1]);


		RECORD_AUDIO = sharedPref.getBoolean("record_audio", true);
		SPEED_UNITS = sharedPref.getString("speed_units", "kmh");

		VIDEO_FRAME_RATE = Integer.parseInt(sharedPref.getString("video_frame_rate",
				"30"));
        TIME_LAPSE_FACTOR = 1;
		int durationInMinutes = Integer.parseInt(sharedPref.getString("video_duration", "10"));
		MAX_VIDEO_DURATION = durationInMinutes * 60 * 1000;
		MAX_TEMP_FOLDER_SIZE_KB = Long.parseLong(sharedPref.getString(
				"max_temp_folder_size", "600000"));
		MIN_FREE_SPACE_KB = Long.parseLong(sharedPref.getString(
				"min_free_space", "600000"));
		File externalDir = getExternalFilesDir(null);
		if (externalDir != null) {
			SD_CARD_PATH = sharedPref.getString("sd_card_path", externalDir.getAbsolutePath());
		} else {
			SD_CARD_PATH = sharedPref.getString("sd_card_path", getFilesDir().getAbsolutePath());
		}
		// create temp and fav folders
		File mFolder = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER); //"/CosyDVR/temp/");
		if (!mFolder.exists()) {
			if (!mFolder.mkdirs()) Log.e("CosyDVR", "Failed to create temp folder");
		}
		mFolder = new File(SD_CARD_PATH + BASE_FOLDER + SAVED_FOLDER); //"/CosyDVR/saved/");
		if (!mFolder.exists()) {
			if (!mFolder.mkdirs()) Log.e("CosyDVR", "Failed to create saved folder");
		}
		//first of all make sure we have enough free space
		freeSpace();

		/* start */
		OpenUnlockPrepareStart();

		applyCameraParameters();
		File srtFile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER //"/CosyDVR/temp/"
				 + currentfile + SRT_FILE_EXT);
		File gpxFile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER //"/CosyDVR/temp/" 
				+ currentfile + GPX_FILE_EXT);
		try {
			mSrtWriter = new FileWriter(srtFile);
			mGpxWriter = new FileWriter(gpxFile);
			mGpxWriter
					.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
							+ "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"CosyDVR\">\n"
							+ "<trk>\n" + "<trkseg>\n"); // header
		} catch (IOException e) {
			Log.e("CosyDVR", "Error creating writers", e);
		}
        
		mNewFileBegin = SystemClock.elapsedRealtime();
		mSrtBegin = mNewFileBegin;

		mTimer = new Timer();
		mTimerTask = new TimerTask() {
			@Override
			public void run() {
				// What you want to do goes here
				mSrtCounter++;
				mHandler.obtainMessage(1).sendToTarget();
			}
		};
		mTimer.schedule(mTimerTask, 0, (long) REFRESH_TIME * TIME_LAPSE_FACTOR);
		currentFileSaved = (isSaved == 1);
		updateNotification();
		UpdateLayoutInterface();
	}

	private void Stop() {
		if (isrecording && mediaRecorder != null) {
			try {
				mediaRecorder.stop();
			} catch (RuntimeException e) {
				Log.e("CosyDVR", "Error while stopping MediaRecordera: " + e.getMessage());
			}


			mediaRecorder.reset();
			mediaRecorder.release();
			mediaRecorder = null;

			try {
				camera.reconnect();
				camera.startPreview();
			} catch (Exception e) {
				Log.e("CosyDVR", "Camera is probably used by the different app: " + e.getMessage());
				try {
					camera.release();
				} catch (Exception ignored) {}

				camera = null;
			}
		}
	}

	private void ResetReleaseLock() {
		if (isrecording && mediaRecorder != null) {
			mediaRecorder.reset();
			mediaRecorder.release();

			camera.lock();
			camera.release();
			mWakeLock.release();
		}
	}

	private void OpenUnlockPrepareStart() {
		if (!isrecording) {
			mWakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
			try {
				if (camera == null) {
					camera = Camera.open(CAMERA_ID);
					camera.setDisplayOrientation(ORIENTATION_ANGLE);
				}

				camera.setErrorCallback((error, camera1) -> {
					Log.e("CosyDVR", "Camera used by the different process, error: " + error);

					new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
						android.widget.Toast.makeText(getApplicationContext(),
								"CosyDVR: Recording stopped! \nCamera taken over by another app.",
								android.widget.Toast.LENGTH_LONG).show();
					});

					String channelId1 = "cosydvr_background_channel";
					Intent myIntent1 = new Intent(BackgroundVideoRecorder.this, CosyDVR.class);
					myIntent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					PendingIntent pendingIntent1 = PendingIntent.getActivity(
							BackgroundVideoRecorder.this, 0, myIntent1, PendingIntent.FLAG_IMMUTABLE);

					Notification warningNotification = new Notification.Builder(BackgroundVideoRecorder.this, channelId1)
							.setContentTitle("CosyDVR - WARNING")
							.setContentText("Camera lost! Recording paused.")
							.setSmallIcon(R.drawable.cosydvricon)
							.setContentIntent(pendingIntent1)
							.setColor(android.graphics.Color.RED)
							.build();

					android.app.NotificationManager manager1 = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					if (manager1 != null) {
						manager1.notify(1, warningNotification);
					}

					triggerVibration();
					StopRecording();
				});


				mediaRecorder = new MediaRecorder();
				camera.unlock();

				// mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
				mediaRecorder.setCamera(camera);

				// Step 2: Set sources
				if (RECORD_AUDIO) {
					mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
				}
				mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
				mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

				if (RECORD_AUDIO) {
					mediaRecorder.setAudioEncoder(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH).audioCodec);
				}

				mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC);

				mediaRecorder.setVideoEncodingBitRate(this.MAX_VIDEO_BIT_RATE);
				mediaRecorder.setVideoSize(this.VIDEO_WIDTH, this.VIDEO_HEIGHT);// 640x480,800x480
				mediaRecorder.setVideoFrameRate(this.VIDEO_FRAME_RATE);
				if(this.TIME_LAPSE_FACTOR > 1) {
					mediaRecorder.setCaptureRate(1.0 * this.VIDEO_FRAME_RATE / this.TIME_LAPSE_FACTOR);
				}
				currentfile = DateFormat.format("yyyy-MM-dd_kk-mm-ss",
						new Date().getTime()).toString();
				// if we write to file
				mediaRecorder.setOutputFile(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER //"/CosyDVR/temp/"
						+ currentfile + VIDEO_FILE_EXT);

				// Step 5: Set the preview output
				mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
				// Step 6: Duration and listener
				mediaRecorder.setMaxDuration(this.MAX_VIDEO_DURATION);
				mediaRecorder.setMaxFileSize(0); // 0 - no limit
				mediaRecorder.setOnInfoListener(this);

				mediaRecorder.setOnErrorListener((mr, what, extra) -> {
					Log.e("CosyDVR", "MediaRecorder Error: " + what + ", " + extra);
					triggerVibration();
					StopRecording(); 

					new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
						android.widget.Toast.makeText(getApplicationContext(),
								"CosyDVR ERROR: Camera connection lost!",
								android.widget.Toast.LENGTH_LONG).show();
					});
				});

				mediaRecorder.setOrientationHint(ORIENTATION_HINT); //mark videofile as rotated

				mediaRecorder.prepare();
				mediaRecorder.start();
				isrecording = true;
			} catch (Exception e) {
				Log.e("CosyDVR", "Error starting recording", e);
				isrecording = false;
				ResetReleaseLock();
			}
		}
	}

	public void freeSpace() {
		File dir = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER);
		File[] filelist = dir.listFiles();
		if (filelist == null || filelist.length == 0) return;

		// get limit of days
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		int retentionDays = Integer.parseInt(sharedPref.getString("retention_time", "7"));

		long currentTime = System.currentTimeMillis();
		long limitInMillis = retentionDays * 24 * 60 * 60 * 1000L;

		// delete old files
		if (retentionDays > 0) {
			for (File file : filelist) {
				long fileAge = currentTime - file.lastModified();
				if (fileAge > limitInMillis) {
					deleteFileWithExtras(file);
					Log.d("CosyDVR", "Time clean: deleted " + file.getName());
				}
			}
		}

		// check space
		long freeSpaceKB = dir.getFreeSpace() / 1024;
		long warningLimitKB = 1048576; // 1GB

		if (freeSpaceKB < warningLimitKB) {
			Log.w("CosyDVR", "Storage critically low!");

			// notification - too less space
			new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(getApplicationContext(),
					"WARNING: Low Storage (< 1GB)! Video might not save properly. Please free up space.",
					android.widget.Toast.LENGTH_LONG).show());
		}
	}

	// other files (.srt, .gpx)
	private void deleteFileWithExtras(File file) {
		String baseName = file.getAbsolutePath().replaceAll("\\..*$", "");
		if (!new File(baseName + SRT_FILE_EXT).delete()) Log.d("CosyDVR", "Failed to delete srt");
		if (!new File(baseName + GPX_FILE_EXT).delete()) Log.d("CosyDVR", "Failed to delete gpx");
		if (!file.delete()) Log.d("CosyDVR", "Failed to delete video file");
	}

	public void autoFocus() {
		if (mFocusModes[focusmode].equals(Parameters.FOCUS_MODE_AUTO)
				|| mFocusModes[focusmode].equals(Parameters.FOCUS_MODE_MACRO)) {
			camera.autoFocus(null);
		}
	}
	
	public void applyCameraParameters() {
		if (camera != null) {
			Parameters parameters = camera.getParameters();
			if(parameters.getSupportedFocusModes().contains(mFocusModes[focusmode])) {
				parameters.setFocusMode(mFocusModes[focusmode]);
			}
            if(parameters.getSupportedSceneModes() != null
                && parameters.getSupportedSceneModes().contains(mSceneModes[scenemode])) {
				parameters.setSceneMode(mSceneModes[scenemode]);
			}
            if(parameters.getSupportedFlashModes() != null
                && parameters.getSupportedFlashModes().contains(mFlashModes[flashmode])) {
				parameters.setFlashMode(mFlashModes[flashmode]);
			}
            if (parameters.isZoomSupported()) {
                parameters.setZoom(zoomfactor);
                camera.setParameters(parameters);
            }
			camera.setParameters(parameters);
		}
	}

	public void setZoom(float mval) {
		if (camera != null) {
			Parameters parameters = camera.getParameters();
			if (parameters.isZoomSupported()) {
				zoomfactor = (int) (parameters.getMaxZoom() * (mval - 4) / 10.0);
				parameters.setZoom(zoomfactor);
				camera.setParameters(parameters);
			}
		}

	}



	private void requestAudioFocus() {
		if (mAudioManager == null) return;

        mFocusRequest = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(mOnAudioFocusChangeListener)
                .build();
        mAudioManager.requestAudioFocus(mFocusRequest);
    }

	private void abandonAudioFocus() {
		if (mAudioManager == null) return;

		if (mFocusRequest != null) {
			mAudioManager.abandonAudioFocusRequest(mFocusRequest);
		} else {
			mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
		}
	}

	public void toggleSave() {
		triggerVibration();
		isSaved = (isSaved + 1) % 2;
		if (isSaved == 1){
			currentFileSaved = true;
		}
		updateNotification();
	}

	public void ChangeSurface(int width, int height) {
		/*
		int finalWidth = width;
		int finalHeight = height;
		if (width > 1 && height > 1) {
			float videoRatio = (float) this.VIDEO_WIDTH / this.VIDEO_HEIGHT;
			float screenRatio = (float) width / height;

			if (videoRatio > screenRatio) {
				finalHeight = (int) (width / videoRatio);
			} else {
				finalWidth = (int) (height * videoRatio);
			}
		}*/
		int overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

		LayoutParams layoutParams = new WindowManager.LayoutParams(
				width, height, overlayType,
				LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
				PixelFormat.TRANSLUCENT);
		if (width == 1) {
			layoutParams.gravity = Gravity.START | Gravity.TOP;
		} else {
			layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
		}
		windowManager.updateViewLayout(surfaceView, layoutParams);
		if (width > 1) {
			mTextView.setVisibility(TextView.VISIBLE);
			mSpeedView.setVisibility(TextView.VISIBLE);
			mBatteryView.setVisibility(TextView.VISIBLE);
			if (mButtonOverlay != null) mButtonOverlay.setVisibility(View.VISIBLE);
		} else {
			mTextView.setVisibility(TextView.INVISIBLE);
			mSpeedView.setVisibility(TextView.INVISIBLE);
			mBatteryView.setVisibility(TextView.INVISIBLE);
			if (mButtonOverlay != null) mButtonOverlay.setVisibility(View.GONE);
		}
	}

	public void showOverlays() {
		if (mTextView != null) mTextView.setVisibility(TextView.VISIBLE);
		if (mSpeedView != null) mSpeedView.setTextColor(Color.parseColor("#FFFFFF")); // Przywracamy biały kolor
		if (mSpeedView != null) mSpeedView.setVisibility(TextView.VISIBLE);
	}

	// Stop isrecording and remove SurfaceView
	@Override
	public void onDestroy() {
		StopRecording();
		if (camera != null) {
			camera.stopPreview();
			camera.release();
			camera = null;
		}

		stopGps();
		if (mButtonOverlay != null) windowManager.removeView(mButtonOverlay);
		windowManager.removeView(surfaceView);
		windowManager.removeView(mTextView);
		windowManager.removeView(mSpeedView);
		windowManager.removeView(mBatteryView);
		hudHandler.removeCallbacks(hudRunnable);
	}


	@Override
	public void surfaceChanged(@androidx.annotation.NonNull SurfaceHolder surfaceHolder, int format, int width, int height) {
		if (mSurfaceHolder.getSurface() == null || camera == null || isrecording) {
			return;
		}
		try {
			camera.stopPreview();
		} catch (Exception ignored) {
		}
		try {
			camera.setPreviewDisplay(mSurfaceHolder);
			camera.startPreview();
		} catch (Exception e) {
			Log.e("CosyDVR", "Error refreshing camera", e);
		}
	}

	@Override
	public void surfaceDestroyed(@androidx.annotation.NonNull SurfaceHolder surfaceHolder) {
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && intent.getAction() != null) {
			String action = intent.getAction();
			switch (action) {
				case "SAVE":
					toggleSave();
					updateHUD();
					break;
				case "STOP":
					StopRecording();
					updateHUD();
					break;
				case "START":
					StartRecording();
					updateHUD();
					break;
			}
		}
		return START_STICKY;
	}

	private void triggerVibration() {
		android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		if (vibrator != null && vibrator.hasVibrator()) {
			vibrator.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 200, 100, 200, 100, 200}, -1));
		}
	}

	private void updateNotification() {
		String channelId = "cosydvr_background_channel";
		Intent myIntent = new Intent(this, CosyDVR.class);
		myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, myIntent, PendingIntent.FLAG_IMMUTABLE);

		String contentText = isrecording ? (isSaved == 1 ? "Recording (SAVING...)" : "Recording in Progress") : "Service Active - Ready";

		Notification.Builder builder = new Notification.Builder(this, channelId)
				.setContentTitle("CosyDVR")
				.setContentText(contentText)
				.setSmallIcon(R.drawable.cosydvricon)
				.setContentIntent(pendingIntent);

		if (isrecording) {
			builder.addAction(new Notification.Action.Builder(null, "SAVE", getPendingIntent("SAVE")).build());
			builder.addAction(new Notification.Action.Builder(null, "STOP", getPendingIntent("STOP")).build());
		} else {
			builder.addAction(new Notification.Action.Builder(null, "START", getPendingIntent("START")).build());
		}

		Notification notification = builder.build();

		if (!isrecording) {
			// On first launch or when not recording
			int foregroundServiceTypes = 0;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				foregroundServiceTypes = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					foregroundServiceTypes |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA |
							android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
				}
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				startForeground(1, notification, foregroundServiceTypes);
			} else {
				startForeground(1, notification);
			}
		} else {
			android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (manager != null) {
				manager.notify(1, notification);
			}
		}
	}

	private PendingIntent getPendingIntent(String action) {
		Intent intent = new Intent(this, BackgroundVideoRecorder.class);
		intent.setAction(action);
		return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onInfo(MediaRecorder mr, int what, int extra) {
		if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
			this.RestartRecording();
		}
	}

	public void onLocationChanged(@NonNull Location location) {
		// Doing something with the position...
	}

	public void onProviderDisabled(@NonNull String provider) {
	}

	public void onProviderEnabled(@NonNull String provider) {
	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	@android.annotation.SuppressLint("MissingPermission")
	private void startGps() {
                if (!USEGPS) return;
		if (mLocationManager == null)
			mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (mLocationManager != null) {
			try {
				mLocationManager.requestLocationUpdates(
						LocationManager.GPS_PROVIDER, 250 /* ms */, 0 /* m */,
						this); // minTime,minDistance
			} catch (Exception e) {
				Log.e("CosyDVR", "exception: " + e.getMessage());
				Log.e("CosyDVR", "exception: " + e);
			}
		}
	}

	private void stopGps() {
		if (mLocationManager != null) {
			mLocationManager.removeUpdates(this);
		}
		mLocationManager = null;
	}

	@SuppressLint("SetTextI18n")
    private void updateHUD() {
		if (mTextView == null || mSpeedView == null) return;

		Date datetime = new Date();
		String cleanHUD = DateFormat.format("yyyy-MM-dd HH:mm:ss", datetime.getTime()).toString();

		int sat = 0;
		float spd = 0;
		if (USEGPS && mLocation != null) {
			float speedFactor = SPEED_UNITS.equals("mph") ? 2.23694f : 3.6f;
			spd = mLocation.getSpeed() * speedFactor;
			if (mLocation.getExtras() != null) {
				sat = mLocation.getExtras().getInt("satellites");
			}
		}

		String gpsColor;
		String gpsStatus;
		if (sat >= 9) {
			gpsStatus = "Excellent";
			gpsColor = "#0b9800";
		} else if (sat >= 6) {
			gpsStatus = "Good";
			gpsColor = "#8BC34A";
		} else if (sat >= 3) {
			gpsStatus = "Weak";
			gpsColor = "#FF0000";
		} else {
			gpsStatus = "Searching...";
			gpsColor = "#A0A0A0";
		}

		cleanHUD += "<br>GPS: <font color='" + gpsColor + "'><b>" + gpsStatus + "</b></font>";

		if (isrecording) {
			long tick = (SystemClock.elapsedRealtime() - mNewFileBegin) / TIME_LAPSE_FACTOR;
			int min = (int) (tick % (1000 * 60 * 60) / (1000 * 60));
			int sec = (int) (tick % (1000 * 60) / 1000);
			cleanHUD += String.format(Locale.US, "<br>Rec: %02d:%02d", min, sec);
		}

		mTextView.setText(android.text.Html.fromHtml(cleanHUD, android.text.Html.FROM_HTML_MODE_LEGACY));

		if (USEGPS) {
			if (mLocationManager != null && !mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				mSpeedView.setText("GPS OFF");
				mSpeedView.setTextColor(Color.parseColor("#A0A0A0"));
			} else {
				mSpeedView.setText(String.format(Locale.US, "%1.1f", spd));
				mSpeedView.setTextColor(Color.parseColor("#FFFFFF"));
			}
		} else {
			mSpeedView.setText("---");
			mSpeedView.setTextColor(Color.parseColor("#A0A0A0"));
		}

		// Update Overlay Buttons state
		if (btnRecordOvl != null) {
			btnRecordOvl.setText(isrecording ? "STOP" : "START");
			if (isrecording) {
				btnRecordOvl.getBackground().setTint(Color.parseColor("#E53935")); // Red
			} else {
				btnRecordOvl.getBackground().setTint(Color.parseColor("#43A047")); // Green
			}
		}
		if (btnSaveOvl != null) {
			btnSaveOvl.setText(isSaved == 1 ? "SAVING" : "SAVE");
			if (isSaved == 1) {
				btnSaveOvl.getBackground().setTint(Color.parseColor("#FF9800")); // Orange
			} else {
				btnSaveOvl.getBackground().setTint(Color.parseColor("#673AB7")); // Violet
			}
		}
	}
}
