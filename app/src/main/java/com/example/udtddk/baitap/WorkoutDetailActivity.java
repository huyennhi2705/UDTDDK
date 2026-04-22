package com.example.udtddk.baitap;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.udtddk.R;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;

public class WorkoutDetailActivity extends AppCompatActivity {

    private String videoUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_detail);

        // ===== DATA =====
        String ten = getIntent().getStringExtra("TenBaiTap");
        String mota = getIntent().getStringExtra("MoTa");
        videoUrl = getIntent().getStringExtra("DuongDanVideo");
        String mucDo = getIntent().getStringExtra("MucDo");
        String bmi = getIntent().getStringExtra("PhanLoaiTheoBMI");

        int time = getIntent().getIntExtra("ThoiGianTap", 0);
        int calo = getIntent().getIntExtra("LuongCaloTieuHao", 0);

        // ===== UI =====
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.tvDetailName)).setText(ten != null ? ten : "-");
        ((TextView) findViewById(R.id.tvDetailCategory))
                .setText(getBmiEmoji(bmi) + " " + (bmi != null ? bmi : "-"));
        ((TextView) findViewById(R.id.tvDetailLevel)).setText(mucDo != null ? mucDo : "-");
        ((TextView) findViewById(R.id.tvDetailDuration)).setText(time + " phút");
        ((TextView) findViewById(R.id.tvDetailCalories)).setText(calo + " kcal");
        ((TextView) findViewById(R.id.tvDetailDesc)).setText(mota != null ? mota : "-");

        // ===== VIDEO =====
        YouTubePlayerView playerView = findViewById(R.id.youtubePlayer);
        LinearLayout layoutNoVideo = findViewById(R.id.layoutNoVideo);

        boolean hasVideo = videoUrl != null && !videoUrl.isEmpty();

        if (hasVideo) {
            layoutNoVideo.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);

            getLifecycle().addObserver(playerView);

            String videoId = extractVideoId(videoUrl);

            playerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
                @Override
                public void onReady(YouTubePlayer youTubePlayer) {
                    if (videoId != null && !videoId.isEmpty()) {
                        youTubePlayer.loadVideo(videoId, 0);
                    }
                }
            });

        } else {
            playerView.setVisibility(View.GONE);
            layoutNoVideo.setVisibility(View.VISIBLE);
        }
    }

    // ===== EXTRACT YOUTUBE ID =====
    private String extractVideoId(String url) {
        if (url == null || url.isEmpty()) return "";

        String videoId = "";

        if (url.contains("youtu.be/")) {
            videoId = url.substring(url.lastIndexOf("/") + 1);
        } else if (url.contains("watch?v=")) {
            videoId = url.substring(url.indexOf("watch?v=") + 8);
        } else if (url.contains("/shorts/")) {
            videoId = url.substring(url.indexOf("/shorts/") + 8);
        } else if (url.contains("/embed/")) {
            videoId = url.substring(url.indexOf("/embed/") + 7);
        }

        // Cắt param
        if (videoId.contains("?")) {
            videoId = videoId.substring(0, videoId.indexOf("?"));
        }
        if (videoId.contains("&")) {
            videoId = videoId.substring(0, videoId.indexOf("&"));
        }

        return videoId.trim();
    }

    // ===== BMI COLOR =====
    private String getBmiEmoji(String cat) {
        if (cat == null) return "⚪";
        switch (cat) {
            case "Thiếu cân": return "🔵";
            case "Bình thường": return "🟢";
            case "Thừa cân": return "🟡";
            case "Béo phì": return "🔴";
            default: return "⚪";
        }
    }
}