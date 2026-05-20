package com.ayamsz.cosydvr;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GalleryActivity extends Activity {

    private String SD_CARD_PATH = "";
    private String BASE_FOLDER = "";
    private ListView listView;
    private List<File> currentFiles = new ArrayList<>();
    private Button btnTemp, btnSaved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        File externalDir = getExternalFilesDir(null);
        String defaultPath = (externalDir != null) ? externalDir.getAbsolutePath() : getFilesDir().getAbsolutePath();
        SD_CARD_PATH = sharedPref.getString("sd_card_path", defaultPath);

        btnTemp = findViewById(R.id.btnTemp);
        btnSaved = findViewById(R.id.btnSaved);
        listView = findViewById(R.id.videoListView);

        btnTemp.setOnClickListener(v -> loadFolder("/temp/"));
        btnSaved.setOnClickListener(v -> loadFolder("/saved/"));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            File fileToPlay = currentFiles.get(position);
            playVideo(fileToPlay);
        });

        loadFolder("/saved/");
    }

    private void loadFolder(String folderName) {
        if (folderName.equals("/saved/")) {
            btnSaved.setBackground(getRoundedBackground("#673AB7"));
            btnTemp.setBackground(getRoundedBackground("#455A64"));
        } else {
            btnTemp.setBackground(getRoundedBackground("#2196F3"));
            btnSaved.setBackground(getRoundedBackground("#455A64"));
        }
        currentFiles = getVideosFromFolder(folderName);
        /*List<String> fileNames = new ArrayList<>();
        for (File f : currentFiles) {
            fileNames.add(f.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_white, fileNames);
        */

        VideoAdapter adapter = new VideoAdapter(this, currentFiles);
        listView.setAdapter(adapter);
    }

    private List<File> getVideosFromFolder(String folderName) {
        String path = SD_CARD_PATH + BASE_FOLDER + folderName;
        File directory = new File(path);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".mp4"));

        List<File> videoList = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            videoList.addAll(Arrays.asList(files));
        }
        return videoList;
    }

    private void playVideo(File file) {
        Uri videoUri = androidx.core.content.FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(videoUri, "video/mp4");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }
    private android.graphics.drawable.GradientDrawable getRoundedBackground(String hexColor) {
        android.graphics.drawable.Drawable background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.rounded_button);
        android.graphics.drawable.GradientDrawable gradientDrawable = (android.graphics.drawable.GradientDrawable) background.mutate();
        gradientDrawable.setColor(android.graphics.Color.parseColor(hexColor));
        return gradientDrawable;
    }

    private class VideoAdapter extends ArrayAdapter<File> {

        public VideoAdapter(android.content.Context context, List<File> files) {
            super(context, 0, files);
        }

        @Override
        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = android.view.LayoutInflater.from(getContext()).inflate(R.layout.list_item_video, parent, false);
            }

            File currentFile = getItem(position);
            File previousFile = position > 0 ? getItem(position - 1) : null;

            android.widget.TextView tvDateHeader = convertView.findViewById(R.id.tvDateHeader);
            android.widget.TextView tvTime = convertView.findViewById(R.id.tvTime);
            android.widget.TextView tvSize = convertView.findViewById(R.id.tvSize);

            String fileName = currentFile.getName();

            // 1. DATE FORMAT: from "2026-05-19" to "19.05.2026"
            String datePart = "Unknown date";
            if (fileName.length() >= 10) {
                String rawDate = fileName.substring(0, 10); // "2026-05-19"
                String[] dateParts = rawDate.split("-");
                if (dateParts.length == 3) {
                    datePart = dateParts[2] + "." + dateParts[1] + "." + dateParts[0];
                }
            }

            // 2. START TIME: extract HH:MM (e.g., "19:00")
            String startTimePart = "00:00";
            if (fileName.length() >= 16) {
                String rawTime = fileName.substring(11, 16); // "19-00"
                startTimePart = rawTime.replace("-", ":");
            }

            // 3. VIDEO DURATION (MediaMetadataRetriever)
            String durationPart = "00:00";
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            try {
                retriever.setDataSource(currentFile.getAbsolutePath());
                String durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationStr != null) {
                    long durationMs = Long.parseLong(durationStr);
                    long seconds = (durationMs / 1000) % 60;
                    long minutes = (durationMs / (1000 * 60)) % 60;
                    durationPart = String.format("%02d:%02d", minutes, seconds);
                }
            } catch (Exception e) {
                android.util.Log.e("CosyDVR", "Error reading video duration: " + e.getMessage());
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
            }

            // Grouping logic based on formatted date
            String prevDatePart = "";
            if (previousFile != null) {
                String pName = previousFile.getName();
                if (pName.length() >= 10) {
                    String[] pParts = pName.substring(0, 10).split("-");
                    if (pParts.length == 3) {
                        prevDatePart = pParts[2] + "." + pParts[1] + "." + pParts[0];
                    }
                }
            }

            if (!datePart.equals(prevDatePart)) {
                tvDateHeader.setVisibility(android.view.View.VISIBLE);
                tvDateHeader.setText(datePart);
            } else {
                tvDateHeader.setVisibility(android.view.View.GONE);
            }

            // Set duration in the main text field
            tvTime.setText("Duration: " + durationPart);

            // Combine start time and file size in the subtitle
            long fileSizeInMB = currentFile.length() / (1024 * 1024);
            tvSize.setText("Time: " + startTimePart + "  •  Size: " + fileSizeInMB + " MB");

            return convertView;
        }
    }
}


