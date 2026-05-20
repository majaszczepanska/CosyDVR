package com.ayamsz.cosydvr;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        File externalDir = getExternalFilesDir(null);
        String defaultPath = (externalDir != null) ? externalDir.getAbsolutePath() : getFilesDir().getAbsolutePath();
        SD_CARD_PATH = sharedPref.getString("sd_card_path", defaultPath);

        Button btnTemp = findViewById(R.id.btnTemp);
        Button btnSaved = findViewById(R.id.btnSaved);
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
        currentFiles = getVideosFromFolder(folderName);
        List<String> fileNames = new ArrayList<>();
        for (File f : currentFiles) {
            fileNames.add(f.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_white, fileNames);
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
}



