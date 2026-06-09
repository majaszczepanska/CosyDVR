package com.ayamsz.cosydvr;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GalleryActivity extends AppCompatActivity {

    private String SD_CARD_PATH = "";
    private final String BASE_FOLDER = "";
    private ListView listView;
    private List<File> currentFiles = new ArrayList<>();
    private Button btnTemp, btnSaved;
    private String currentFolder = "/saved/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                returnToMainMenu();
            }
        });

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        File externalDir = getExternalFilesDir(null);
        String defaultPath = (externalDir != null) ? externalDir.getAbsolutePath() : getFilesDir().getAbsolutePath();
        SD_CARD_PATH = sharedPref.getString("sd_card_path", defaultPath);

        btnTemp = findViewById(R.id.btnTemp);
        btnSaved = findViewById(R.id.btnSaved);
        listView = findViewById(R.id.videoListView);

        android.widget.ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> returnToMainMenu());

        btnTemp.setOnClickListener(v -> loadFolder("/temp/"));
        btnSaved.setOnClickListener(v -> loadFolder("/saved/"));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            File fileToPlay = currentFiles.get(position);
            playVideo(fileToPlay);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
                    File selectedFile = currentFiles.get(position);
                    showActionDialog(selectedFile);
                    return true;
        });

        loadFolder("/saved/");
    }

    private void loadFolder(String folderName) {
        currentFolder = folderName;
        
        // Definiujemy kolory
        int purple = android.graphics.Color.parseColor("#673AB7");
        int blue = android.graphics.Color.parseColor("#2196F3");
        int gray = android.graphics.Color.parseColor("#455A64");

        if (Objects.equals(folderName, "/saved/")) {
            btnSaved.setBackgroundTintList(android.content.res.ColorStateList.valueOf(purple));
            btnTemp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gray));
        } else {
            btnTemp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue));
            btnSaved.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gray));
        }
        
        currentFiles = getVideosFromFolder(folderName);
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
        // Zamiast wysyłać intent do systemu (ACTION_VIEW), odpalamy nasz nowy ekran
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("VIDEO_PATH", file.getAbsolutePath());
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

        @NonNull
        @Override
        public android.view.View getView(int position, android.view.View convertView, @NonNull android.view.ViewGroup parent) {
            android.view.View view = convertView;
            if (view == null) {
                view = android.view.LayoutInflater.from(getContext()).inflate(R.layout.list_item_video, parent, false);
            }

            File currentFile = getItem(position);
            File previousFile = position > 0 ? getItem(position - 1) : null;

            if (currentFile == null) return view;

            android.widget.TextView tvDateHeader = view.findViewById(R.id.tvDateHeader);
            android.widget.TextView tvTime = view.findViewById(R.id.tvTime);
            android.widget.TextView tvSize = view.findViewById(R.id.tvSize);

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
            try (android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever()) {
                retriever.setDataSource(currentFile.getAbsolutePath());
                String durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationStr != null) {
                    long durationMs = Long.parseLong(durationStr);
                    long seconds = (durationMs / 1000) % 60;
                    long minutes = (durationMs / (1000 * 60)) % 60;
                    durationPart = String.format(Locale.US, "%02d:%02d", minutes, seconds);
                }
            } catch (Exception e) {
                android.util.Log.e("CosyDVR", "Error reading video duration: " + e.getMessage());
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

            if (!Objects.equals(datePart, prevDatePart)) {
                tvDateHeader.setVisibility(android.view.View.VISIBLE);
                tvDateHeader.setText(datePart);
            } else {
                tvDateHeader.setVisibility(android.view.View.GONE);
            }

            // Set duration in the main text field
            tvTime.setText(String.format(Locale.US, "Duration: %s", durationPart));

            // Combine start time and file size in the subtitle
            long fileSizeInMB = currentFile.length() / (1024 * 1024);
            tvSize.setText(String.format(Locale.US, "Time: %s  •  Size: %d MB", startTimePart, fileSizeInMB));

            return view;
        }
    }

    private void showActionDialog(File file) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Manage Video");

        if (Objects.equals(currentFolder, "/temp/")) {
            // if file in /temp - choice (move to saved or delete)
            String[] options = {"Move to SAVED", "Delete File"};
            builder.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    moveToSavedWithExtras(file);
                    android.widget.Toast.makeText(this, "Moved to SAVED", android.widget.Toast.LENGTH_SHORT).show();
                } else if (which == 1) {
                    deleteFileWithExtras(file);
                    android.widget.Toast.makeText(this, "File deleted", android.widget.Toast.LENGTH_SHORT).show();
                }
                loadFolder(currentFolder); // refresh file list
            });
        } else {
            // file in saved - choice (move to temp or delete)
            String[] options = {"Move to TEMP (Unprotect)", "Delete File"};
            builder.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    moveToTempWithExtras(file);
                    android.widget.Toast.makeText(this, "Moved to TEMP", android.widget.Toast.LENGTH_SHORT).show();
                } else if (which == 1) {
                    deleteFileWithExtras(file);
                    android.widget.Toast.makeText(this, "File deleted", android.widget.Toast.LENGTH_SHORT).show();
                }
                loadFolder(currentFolder); // refresh
            });
        }

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // delete film with srt and gpx files
    private void deleteFileWithExtras(File file) {
        String baseName = file.getAbsolutePath().replaceAll("\\.mp4$", "");
        if (!new File(baseName + ".srt").delete()) Log.d("CosyDVR", "No srt to delete");
        if (!new File(baseName + ".gpx").delete()) Log.d("CosyDVR", "No gpx to delete");
        if (!file.delete()) Log.e("CosyDVR", "Failed to delete video file");
    }

    // move files to save
    private void moveToSavedWithExtras(File file) {
        File savedDir = new File(SD_CARD_PATH + BASE_FOLDER + "/saved/");
        if (!savedDir.exists()) {
            if (!savedDir.mkdirs()) Log.e("CosyDVR", "Failed to create saved directory");
        }

        String name = file.getName();
        String baseName = name.replaceAll("\\.mp4$", "");

        // files in saved
        File targetMp4 = new File(savedDir, name);
        File targetSrt = new File(savedDir, baseName + ".srt");
        File targetGpx = new File(savedDir, baseName + ".gpx");

        // files in temp
        String sourceBasePath = file.getAbsolutePath().replaceAll("\\.mp4$", "");
        File sourceSrt = new File(sourceBasePath + ".srt");
        File sourceGpx = new File(sourceBasePath + ".gpx");

        // move
        if (!file.renameTo(targetMp4)) Log.e("CosyDVR", "Failed to move mp4");
        if (sourceSrt.exists()) {
            if (!sourceSrt.renameTo(targetSrt)) Log.e("CosyDVR", "Failed to move srt");
        }
        if (sourceGpx.exists()) {
            if (!sourceGpx.renameTo(targetGpx)) Log.e("CosyDVR", "Failed to move gpx");
        }
    }

    // move files back to temp (unprotect)
    private void moveToTempWithExtras(File file) {
        File tempDir = new File(SD_CARD_PATH + BASE_FOLDER + "/temp/");
        if (!tempDir.exists()) {
            if (!tempDir.mkdirs()) Log.e("CosyDVR", "Failed to create temp directory");
        }

        String name = file.getName();
        String baseName = name.replaceAll("\\.mp4$", "");

        // target files in temp
        File targetMp4 = new File(tempDir, name);
        File targetSrt = new File(tempDir, baseName + ".srt");
        File targetGpx = new File(tempDir, baseName + ".gpx");

        // source files in saved
        String sourceBasePath = file.getAbsolutePath().replaceAll("\\.mp4$", "");
        File sourceSrt = new File(sourceBasePath + ".srt");
        File sourceGpx = new File(sourceBasePath + ".gpx");

        // move
        if (!file.renameTo(targetMp4)) Log.e("CosyDVR", "Failed to move mp4 back to temp");
        if (sourceSrt.exists()) {
            if (!sourceSrt.renameTo(targetSrt)) Log.e("CosyDVR", "Failed to move srt back to temp");
        }
        if (sourceGpx.exists()) {
            if (!sourceGpx.renameTo(targetGpx)) Log.e("CosyDVR", "Failed to move gpx back to temp");
        }
    }
    private void returnToMainMenu() {
        Intent intent = new Intent(this, CosyDVR.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finish();
    }
}



