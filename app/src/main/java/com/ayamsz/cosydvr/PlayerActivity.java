package com.ayamsz.cosydvr;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends Activity {

    private VideoView videoView;
    private MapView mapView;
    private TextView tvSpeedOverlay;
    private TextView tvDateTimeOverlay;
    private LinearLayout dataOverlay;
    private Marker carMarker;

    private List<GpsEntry> gpsData = new ArrayList<>();
    private GpsEntry lastAppliedEntry = null;

    private static class GpsEntry {
        long timeMs;
        double lat = 0, lon = 0;
        float speed = 0;
        String dateTime = "";
    }

    private Handler syncHandler = new Handler();
    private Runnable syncRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Configuration.getInstance().load(this, android.preference.PreferenceManager.getDefaultSharedPreferences(this));
        setContentView(R.layout.activity_player);

        videoView = findViewById(R.id.videoView);
        mapView = findViewById(R.id.mapView);
        tvSpeedOverlay = findViewById(R.id.tvSpeedOverlay);
        tvDateTimeOverlay = findViewById(R.id.tvDateTimeOverlay);
        dataOverlay = findViewById(R.id.dataOverlay);
        Button btnToggle = findViewById(R.id.btnToggleOverlay);

        // Ustawienia mapy - Mapnik jest najbardziej niezawodny
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(18.0);

        // Standardowy marker (Pineska)
        carMarker = new Marker(mapView);
        carMarker.setTitle("Auto");
        carMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(carMarker);

        // Standardowy kontroler wideo (pojawia się po dotknięciu ekranu)
        MediaController mc = new MediaController(this);
        mc.setAnchorView(videoView);
        videoView.setMediaController(mc);

        btnToggle.setOnClickListener(v -> {
            int visibility = (dataOverlay.getVisibility() == View.VISIBLE) ? View.GONE : View.VISIBLE;
            dataOverlay.setVisibility(visibility);
            mapView.setVisibility(visibility);
        });

        String videoPath = getIntent().getStringExtra("VIDEO_PATH");
        if (videoPath != null) {
            loadSrtData(videoPath.replace(".mp4", ".srt"));
            videoView.setVideoURI(Uri.parse(videoPath));
            
            if (savedInstanceState != null) {
                int pos = savedInstanceState.getInt("videoPos");
                videoView.seekTo(pos);
            }
            
            videoView.start();
            
            // Ustaw mapę na początek trasy
            if (!gpsData.isEmpty()) {
                for (GpsEntry e : gpsData) {
                    if (isValidLocation(e.lat, e.lon)) {
                        GeoPoint p = new GeoPoint(e.lat, e.lon);
                        mapView.getController().setCenter(p);
                        carMarker.setPosition(p);
                        break;
                    }
                }
            }
            startSyncLoop();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (videoView != null) {
            outState.putInt("videoPos", videoView.getCurrentPosition());
        }
    }

    private boolean isValidLocation(double lat, double lon) {
        return Math.abs(lat) > 0.1 && Math.abs(lon) > 0.1 && lat != -1.0;
    }

    private void loadSrtData(String srtPath) {
        File file = new File(srtPath);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("-->")) {
                    GpsEntry entry = new GpsEntry();
                    try {
                        String timeStr = line.split(" --> ")[0];
                        String[] parts = timeStr.replace(",", ":").split(":");
                        entry.timeMs = Integer.parseInt(parts[0]) * 3600000L +
                                       Integer.parseInt(parts[1]) * 60000L +
                                       Integer.parseInt(parts[2]) * 1000L +
                                       Integer.parseInt(parts[3]);

                        entry.dateTime = br.readLine();
                        String dataLine = br.readLine();
                        if (dataLine != null) {
                            // Rozbijamy lat:XX.XX,lon:YY.YY...
                            String[] parts2 = dataLine.split(",");
                            for (String p : parts2) {
                                String clean = p.trim().toLowerCase(Locale.US);
                                if (clean.startsWith("lat:")) entry.lat = Double.parseDouble(clean.substring(4));
                                if (clean.startsWith("lon:")) entry.lon = Double.parseDouble(clean.substring(4));
                                if (clean.contains("spd:")) {
                                    String s = clean.split(":")[1].replaceAll("[^0-9.]", "");
                                    if (!s.isEmpty()) entry.speed = Float.parseFloat(s);
                                }
                            }
                        }
                        gpsData.add(entry);
                    } catch (Exception e) {
                        Log.e("CosyDVR", "Błąd w bloku SRT: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("CosyDVR", "Błąd czytania SRT", e);
        }
    }

    private void startSyncLoop() {
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                if (videoView.isPlaying()) {
                    updateUI(videoView.getCurrentPosition());
                }
                syncHandler.postDelayed(this, 500);
            }
        };
        syncHandler.post(syncRunnable);
    }

    private void updateUI(int currentMs) {
        GpsEntry bestMatch = null;
        for (GpsEntry entry : gpsData) {
            if (entry.timeMs <= currentMs) {
                bestMatch = entry;
            } else {
                break;
            }
        }

        if (bestMatch != null && bestMatch != lastAppliedEntry) {
            lastAppliedEntry = bestMatch;
            
            tvSpeedOverlay.setText(String.format(Locale.US, "%.1f km/h", bestMatch.speed));
            tvDateTimeOverlay.setText(bestMatch.dateTime);

            if (isValidLocation(bestMatch.lat, bestMatch.lon)) {
                GeoPoint point = new GeoPoint(bestMatch.lat, bestMatch.lon);
                carMarker.setPosition(point);
                carMarker.setVisible(true);
                // Centruj mapę na markerze
                mapView.getController().setCenter(point);
            } else {
                carMarker.setVisible(false);
            }
            mapView.invalidate(); // Wymuś odświeżenie mapy
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        syncHandler.removeCallbacks(syncRunnable);
    }
}
