package com.ayamsz.cosydvr;

import android.app.PendingIntent;
import android.app.Service;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.Gravity;
import android.view.SurfaceView;
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
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Binder;
import android.os.Message;
import android.os.SystemClock;
import android.os.Bundle;

import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.String;
//import java.net.InetAddress;
//import java.net.Socket;
//import java.net.UnknownHostException;

import android.os.PowerManager;
import android.preference.PreferenceManager;

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
	public String SD_CARD_PATH = "";//(getExternalMediaDirs())[0].getAbsolutePath();//Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
//			.getAbsolutePath();
	public String BASE_FOLDER = "";///CosyDVR";
	//~ public String BASE_FOLDER = "/Android/data/es.esy.CosyDVR/files"; //possible fix for KitKat
	public String SAVED_FOLDER = "/saved/";
	public String TEMP_FOLDER = "/temp/";
/*for KitKat, we can use something like:
* final File[] dirs = context.getExternalFilesDirs(null); //null means default type
* //find a dir that has most of the space and save using StatFs
*/
	public boolean AUTOSTART = false;
	public boolean USEGPS = true;
	public boolean REVERSE_ORIENTATION = false;
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
	private int timelapsemode = 0;
	private int zoomfactor = 0;
	private String currentfile = null;

	private SurfaceHolder mSurfaceHolder = null;
	private PowerManager.WakeLock mWakeLock = null;
	private Timer mTimer = null;
	private TimerTask mTimerTask = null;

	public TextView mTextView = null;
	public TextView mSpeedView = null;
	public TextView mBatteryView = null;
	public long mSrtCounter = 0;
	public Handler mHandler = null;

	private File mSrtFile = null;
	private File mGpxFile = null;
	private OutputStreamWriter mSrtWriter = null;
	private OutputStreamWriter mGpxWriter = null;
	private long mSrtBegin = 0;
	private long mNewFileBegin = 0;

	private LocationManager mLocationManager = null;
	private Location mLocation = null;
	private long mPrevTim = 0;

	// private List<String> mFocusModes;
	private String[] mFocusModes = { Parameters.FOCUS_MODE_INFINITY,
			Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, Parameters.FOCUS_MODE_AUTO,
			Parameters.FOCUS_MODE_MACRO, Parameters.FOCUS_MODE_EDOF, };

	private String[] mSceneModes = { Parameters.SCENE_MODE_AUTO,
			Parameters.SCENE_MODE_NIGHT, };


	private String[] mFlashModes = { Parameters.FLASH_MODE_OFF,
			Parameters.FLASH_MODE_TORCH, };

	// some troubles with video files @SuppressLint("HandlerLeak")

	private Handler hudHandler = new Handler();
	private Runnable hudRunnable = new Runnable() {
		@Override
		public void run() {
			updateHUD();
			hudHandler.postDelayed(this, 1000);
		}
	};

	private final class HandlerExtension extends Handler {
		@android.annotation.SuppressLint("MissingPermission")
		public void handleMessage(Message msg) {
			if (!isrecording) {
				return;
			}
			/* every second update for debug purposes only
			 * Intent intent = new Intent();
			 * intent.setAction("com.maja.cosydvr.updateinterface");
			 * sendBroadcast(intent);
			 */
			
			String srt = new String();
			String gpx = new String();
			Date datetime = new Date();
			long tick = (mSrtBegin - mNewFileBegin)/TIME_LAPSE_FACTOR; // relative srt text begin/
													// i.e. prev tick time
			int hour = (int) (tick / (1000 * 60 * 60));
			int min = (int) (tick % (1000 * 60 * 60) / (1000 * 60));
			int sec = (int) (tick % (1000 * 60) / (1000));
			int mil = (int) (tick % (1000));
			srt = srt
					+ String.format("%d\n%02d:%02d:%02d,%03d --> ",
							mSrtCounter, hour, min, sec, mil);

			mSrtBegin = SystemClock.elapsedRealtime();
			tick = (mSrtBegin - mNewFileBegin)/TIME_LAPSE_FACTOR; // relative srt text end. i.e.
												// this tick time
			hour = (int) (tick / (1000 * 60 * 60));
			min = (int) (tick % (1000 * 60 * 60) / (1000 * 60));
			sec = (int) (tick % (1000 * 60) / (1000));
			mil = (int) (tick % (1000));
			srt = srt
					+ String.format("%02d:%02d:%02d,%03d\n", hour, min, sec,
							mil);
			srt = srt
					+ DateFormat.format("yyyy-MM-dd_kk-mm-ss",
							datetime.getTime()).toString() + "\n";

                        if (USEGPS) {
                                // Get the location manager
			        // Criteria criteria = new Criteria();
			        // String bestProvider = mLocationManager.getBestProvider(criteria,
		        	// false);
		        	// Location location =
		        	// mLocationManager.getLastKnownLocation(bestProvider);
			        try {
			                mLocation = mLocationManager
						.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			        } catch (Exception e) {
			        	mLocation = null;
			        }
                        }

			double lat = -1, lon = -1, alt = -1;
			float spd = 0, acc = -1;
			int sat = 0, bat = 0;
			long tim = 0;

			if (USEGPS && mLocation != null) {
				/*
				 * if (mPrevLocation == null) { mPrevLocation = mLocation; //for
				 * null to not occur during operation }
				 */
				lat = mLocation.getLatitude();
				lon = mLocation.getLongitude();
				tim = mLocation.getTime() / 1000; // millisec to sec
				alt = mLocation.getAltitude();
				acc = mLocation.getAccuracy();
				spd = mLocation.getSpeed() * 3.6f; // by GPS
				/*
				 * if(mLocation.getTime() != mPrevLocation.getTime() &&
				 * (mLocation.getTime()-3000) < mPrevLocation.getTime()){ spd =
				 * 3.6f * 1000 * mLocation.distanceTo(mPrevLocation) /
				 * (mLocation.getTime() - mPrevLocation.getTime()); }
				 */
				sat = mLocation.getExtras().getInt("satellites");
				// mPrevLocation = mLocation;
			}

			srt = srt
					+ String.format(
							"lat:%1.6f,lon:%1.6f,alt:%1.0f,spd:%1.1fkm/h\nacc:%01.1fm,sat:%d,tim:%d\n\n",
							lat, lon, alt, spd, acc, sat, tim);
			gpx = gpx
					+ String.format("<trkpt lon=\"%1.8f\" lat=\"%1.8f\">\n",
							lon, lat).replace(",", ".");
			gpx = gpx + String.format("<ele>%1.0f</ele>\n", alt);
			gpx = gpx
					+ "<time>"
					+ DateFormat.format("yyyy-MM-dd", datetime.getTime())
							.toString()
					+ "T"
					+ DateFormat.format("kk:mm:ss", datetime.getTime())
							.toString() + "Z</time>\n";
			gpx = gpx + "</trkpt>\n";
			if (mPrevTim == 0 && tim != mPrevTim) {
				mPrevTim = tim;
			}
			try {
				if (isrecording) {
					mSrtWriter.write(srt);
					if (tim != mPrevTim && lat > 0) {
						mGpxWriter.write(gpx);
					}
				}
			} catch (IOException e) {
			}

			mPrevTim = tim;
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
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, myIntent, pendingFlags);

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

		Notification notification = new Notification.Builder(this, channelId)
				.setContentTitle("CosyDVR")
				.setContentText(" Background Recorder Service")
				.setSmallIcon(R.drawable.cosydvricon)
				.setContentIntent(pendingIntent)
				.build();
		startForeground(1, notification);

		// Create new SurfaceView, set its size to 1x1, move it to the top left
		// corner and set this service as a callback
		windowManager = (WindowManager) this
				.getSystemService(Context.WINDOW_SERVICE);
		surfaceView = new SurfaceView(this);
		int overlayType;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
		} else {
			overlayType = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
		}

		LayoutParams layoutParams = new WindowManager.LayoutParams(
				1, 1, overlayType,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				PixelFormat.TRANSLUCENT);

		layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
		try {
			windowManager.addView(surfaceView, layoutParams);
		} catch (Exception e) {
		}

		mTextView = new TextView(this);
		LayoutParams textParams = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				overlayType,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				PixelFormat.TRANSLUCENT);
		textParams.gravity = Gravity.LEFT | Gravity.TOP;
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
		speedParams.gravity = Gravity.RIGHT | Gravity.TOP;
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

		mHandler = new HandlerExtension();

		hudHandler.post(hudRunnable);
		startGps();

	}

	// Method called right after Surface created (initializing and starting
	// MediaRecorder)
	@Override
	public void surfaceCreated(SurfaceHolder surfaceHolder) {
		mSurfaceHolder = surfaceHolder;
		try {
			if (camera == null) {
				camera = Camera.open();
				camera.setDisplayOrientation(ORIENTATION_ANGLE);
			}
			camera.setPreviewDisplay(mSurfaceHolder);
			camera.startPreview();
		} catch (Exception e) {
			Log.e("CosyDVR", "Preview error");
		}
		if (AUTOSTART) {
			StartRecording();
		}
	}

	public int getFocusMode() {
		return focusmode;
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
			}

			if (currentfile != null && currentFileSaved) {
				File tmpfile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER // "/CosyDVR/temp/"
						+ currentfile + VIDEO_FILE_EXT);
				File favfile = new File(SD_CARD_PATH + BASE_FOLDER + SAVED_FOLDER // "/CosyDVR/saved/"
						+ currentfile + VIDEO_FILE_EXT);
				tmpfile.renameTo(favfile);
				tmpfile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER // "/CosyDVR/temp/"
						+ currentfile + SRT_FILE_EXT);
				favfile = new File(SD_CARD_PATH + BASE_FOLDER + SAVED_FOLDER // "/CosyDVR/saved/"
						+ currentfile + SRT_FILE_EXT);
				tmpfile.renameTo(favfile);
				tmpfile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER // "/CosyDVR/temp/"
						+ currentfile + GPX_FILE_EXT);
				favfile = new File(SD_CARD_PATH + BASE_FOLDER + SAVED_FOLDER // "/CosyDVR/saved"
						+ currentfile + GPX_FILE_EXT);
				tmpfile.renameTo(favfile);

				isSaved = 0;
			}
			isSaved = 0;
			currentFileSaved = false;
			isrecording = false;
		}
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
		VIDEO_WIDTH = Integer.parseInt(sharedPref.getString("video_width",
				"1920"));
		VIDEO_HEIGHT = Integer.parseInt(sharedPref.getString("video_height",
				"1080"));
		VIDEO_FRAME_RATE = Integer.parseInt(sharedPref.getString("video_frame_rate",
				"30"));
		TIME_LAPSE_FACTOR = (timelapsemode==0) ? 1: Integer.parseInt(sharedPref.getString("time_lapse_factor",
				"1"));
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
			mFolder.mkdirs();
		}
		mFolder = new File(SD_CARD_PATH + BASE_FOLDER + SAVED_FOLDER); //"/CosyDVR/saved/");
		if (!mFolder.exists()) {
			mFolder.mkdirs();
		}
		//first of all make sure we have enough free space
		freeSpace();

		/* start */
		OpenUnlockPrepareStart();

		applyCameraParameters();
		/*
		 * debug Parameters parameters = camera.getParameters();
		 * //parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO); if
		 * (parameters.getSupportedFocusModes().contains(Parameters.
		 * FOCUS_MODE_INFINITY)) {
		 * parameters.setFocusMode(Parameters.FOCUS_MODE_INFINITY); }
		 * 
		 * //parameters.setFocusMode(Parameters.FOCUS_MODE_FIXED); if(!isnight){
		 * if(parameters.getSupportedSceneModes() != null &&
		 * parameters.getSupportedSceneModes
		 * ().contains(Camera.Parameters.SCENE_MODE_AUTO)) {
		 * parameters.setSceneMode(Parameters.SCENE_MODE_AUTO); } } else {
		 * if(parameters.getSupportedSceneModes() != null &&
		 * parameters.getSupportedSceneModes
		 * ().contains(Camera.Parameters.SCENE_MODE_NIGHT)) {
		 * parameters.setSceneMode(Parameters.SCENE_MODE_NIGHT); } }
		 * 
		 * camera.setParameters(parameters);
		 */
		mSrtCounter = 0;
		mSrtFile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER //"/CosyDVR/temp/"
				 + currentfile + SRT_FILE_EXT);
		mSrtFile.setWritable(true);
		mGpxFile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER //"/CosyDVR/temp/" 
				+ currentfile + GPX_FILE_EXT);
		mGpxFile.setWritable(true);
		try {
			mSrtWriter = new FileWriter(mSrtFile);
			mGpxWriter = new FileWriter(mGpxFile);
			mGpxWriter
					.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
							+ "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"CosyDVR\">\n"
							+ "<trk>\n" + "<trkseg>\n"); // header
		} catch (IOException e) {
		};
        
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
		mTimer.scheduleAtFixedRate(mTimerTask, 0, REFRESH_TIME * TIME_LAPSE_FACTOR);
		currentFileSaved = (isSaved == 1);
		UpdateLayoutInterface();
	}

	private void Stop() {
		if (isrecording && mediaRecorder != null) {
			mediaRecorder.stop();
			mediaRecorder.reset();
			mediaRecorder.release();
			mediaRecorder = null;

			try {
				camera.reconnect();
				camera.startPreview();
			} catch (IOException e) {
				e.printStackTrace();
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
			mWakeLock.acquire();
			try {
				if (camera == null) {
					camera = Camera.open();
					camera.setDisplayOrientation(ORIENTATION_ANGLE);
				}
				mediaRecorder = new MediaRecorder();
				camera.unlock();

				// mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
				mediaRecorder.setCamera(camera);

				// Step 2: Set sources
				mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
				mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

				// Step 3: Set a CamcorderProfile (requires API Level 8 or
				// higher)
				// mediaRecorder.setProfile(CamcorderProfile
				// .get(CamcorderProfile.QUALITY_HIGH));
				mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
				mediaRecorder.setAudioEncoder(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH).audioCodec);// MediaRecorder.AudioEncoder.HE_AAC
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
				// if we stream
				/*
				 * String hostname =
				 * "rtmp://a.rtmp.youtube.com/stetsuk.80gq-20ea-tet3-2hfb"; int
				 * port = 1234; Socket socket; try { socket = new
				 * Socket(InetAddress.getByName(hostname), port);
				 * ParcelFileDescriptor pfd =
				 * ParcelFileDescriptor.fromSocket(socket);
				 * mediaRecorder.setOutputFile(pfd.getFileDescriptor()); } catch
				 * (UnknownHostExcept
				 * ion e1) { // TODO Auto-generated catch
				 * block e1.printStackTrace(); } catch (IOException e1) { //
				 * TODO Auto-generated catch block e1.printStackTrace(); }
				 */

				/*
				 * mediaRecorder.setAudioChannels(CamcorderProfile.get(
				 * CamcorderProfile.QUALITY_HIGH).audioChannels);
				 * mediaRecorder.setAudioSamplingRate
				 * (CamcorderProfile.get(CamcorderProfile
				 * .QUALITY_HIGH).audioSampleRate);
				 * mediaRecorder.setAudioEncodingBitRate
				 * (CamcorderProfile.get(CamcorderProfile
				 * .QUALITY_HIGH).audioBitRate);
				 */

				// Step 5: Set the preview output
				mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
				// Step 6: Duration and listener
				mediaRecorder.setMaxDuration(this.MAX_VIDEO_DURATION);
				mediaRecorder.setMaxFileSize(0); // 0 - no limit
				mediaRecorder.setOnInfoListener(this);
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
		File dir = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER); //"/CosyDVR/temp/");
		File[] filelist = dir.listFiles();
		if (filelist == null) return;
		Arrays.sort(filelist, new Comparator<File>() {
			public int compare(File f1, File f2) {
				return Long.valueOf(f2.lastModified()).compareTo(
						f1.lastModified());
			}
		});
		long totalSizeKb = 0;
		int i;
		for (i = 0; i < filelist.length; i++) {
			totalSizeKb += filelist[i].length() / 1024;
		}
		i = filelist.length - 1;
		// if(Build.VERSION.SDK_INT >= 11) {
		while (i > 0 && (totalSizeKb > this.MAX_TEMP_FOLDER_SIZE_KB)
		    || (dir.getFreeSpace() / 1024) < this.MIN_FREE_SPACE_KB) {
			totalSizeKb -= filelist[i].length() / 1024;
			filelist[i].delete();
			i--;
		}
		// } else {
		// while (i > 0 && totalSizeKb > this.MAX_TEMP_FOLDER_SIZE_KB) {
		// totalSizeKb -= filelist[i].length()/1024;
		// filelist[i].delete();
		// i--;
		// }
		// }
	}

	public void autoFocus() {
		if (mFocusModes[focusmode] == Parameters.FOCUS_MODE_AUTO
				|| mFocusModes[focusmode] == Parameters.FOCUS_MODE_MACRO) {
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



	public void toggleTimeLapse() {
		if (camera != null) {
			timelapsemode = (timelapsemode+1)%2;
			RestartRecording();
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

	public void toggleSave() {

		isSaved = (isSaved + 1) % 2;
		if (isSaved == 1){
			currentFileSaved = true;
		}
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
		int overlayType;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
		} else {
			overlayType = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
		}

		LayoutParams layoutParams = new WindowManager.LayoutParams(
				width, height, overlayType,
				LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
				PixelFormat.TRANSLUCENT);
		if (width == 1) {
			layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
		} else {
			layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
		}
		windowManager.updateViewLayout(surfaceView, layoutParams);
		if (width > 1) {
			mTextView.setVisibility(TextView.VISIBLE);
			mSpeedView.setVisibility(TextView.VISIBLE);
			mBatteryView.setVisibility(TextView.VISIBLE);
		} else {
			mTextView.setVisibility(TextView.INVISIBLE);
			mSpeedView.setVisibility(TextView.INVISIBLE);
			mBatteryView.setVisibility(TextView.INVISIBLE);
		}
	}

	public void hideOverlays() {
		if (mTextView != null) mTextView.setVisibility(TextView.GONE);
		if (mSpeedView != null) mSpeedView.setVisibility(TextView.GONE);
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
		windowManager.removeView(surfaceView);
		windowManager.removeView(mTextView);
		windowManager.removeView(mSpeedView);
		windowManager.removeView(mBatteryView);
		hudHandler.removeCallbacks(hudRunnable);
	}


	@Override
	public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
		if (mSurfaceHolder.getSurface() == null || camera == null || isrecording) {
			return;
		}
		try {
			camera.stopPreview();
		} catch (Exception e) {
		}
		try {
			camera.setPreviewDisplay(mSurfaceHolder);
			camera.startPreview();
		} catch (Exception e) {
			Log.e("CosyDVR", "Error refreshing camera", e);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
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

	public void onLocationChanged(Location location) {
		// Doing something with the position...
	}

	public void onProviderDisabled(String provider) {
	}

	public void onProviderEnabled(String provider) {
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
						(LocationListener) this); // minTime,minDistance
				// if ( !mLocationManager.isProviderEnabled(
				// LocationManager.GPS_PROVIDER ) )
				// Toast.makeText(getApplicationContext(),
				// getString(R.string.gps_disabled), Toast.LENGTH_LONG).show();
			} catch (Exception e) {
				Log.e("CosyDVR", "exception: " + e.getMessage());
				Log.e("CosyDVR", "exception: " + e.toString());
			}
		}
	}

	private void stopGps() {
		if (mLocationManager != null) {
			mLocationManager.removeUpdates((LocationListener) this);
		}
		mLocationManager = null;
	}

	private void updateHUD() {
		if (mTextView == null || mSpeedView == null) return;

		Date datetime = new Date();
		String cleanHUD = DateFormat.format("yyyy-MM-dd HH:mm:ss", datetime.getTime()).toString();

		int sat = 0;
		float spd = 0;
		if (USEGPS && mLocation != null) {
			spd = mLocation.getSpeed() * 3.6f;
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
			cleanHUD += String.format("<br>Rec: %02d:%02d", min, sec);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			mTextView.setText(android.text.Html.fromHtml(cleanHUD, android.text.Html.FROM_HTML_MODE_LEGACY));
		} else {
			mTextView.setText(android.text.Html.fromHtml(cleanHUD));
		}

		if (USEGPS) {
			if (mLocationManager != null && !mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				mSpeedView.setText("GPS OFF");
				mSpeedView.setTextColor(Color.parseColor("#A0A0A0"));
			} else {
				mSpeedView.setText(String.format("%1.1f", spd));
				mSpeedView.setTextColor(Color.parseColor("#FFFFFF"));
			}
		} else {
			mSpeedView.setText("---");
			mSpeedView.setTextColor(Color.parseColor("#A0A0A0"));
		}
	}
}
