package com.example.udtddk.ai;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;

import com.example.udtddk.BaseActivity;
import com.example.udtddk.BuildConfig;
import com.example.udtddk.R;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends BaseActivity {

    private static final String TAG = "ChatActivity";

    // ✅ Dùng Gemini API key
    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;

    // ✅ URL Gemini (gemini-2.0-flash là model mới nhất, miễn phí)
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private static final String SYSTEM_PROMPT =
            "Bạn là trợ lý sức khoẻ BioCare. Chỉ trả lời các câu hỏi liên quan đến sức khoẻ, "
                    + "dinh dưỡng, bài tập, giấc ngủ và cân nặng. "
                    + "Trả lời bằng tiếng Việt, ngắn gọn, dễ hiểu, thân thiện.";

    private LinearLayout chatContainer;
    private ScrollView scrollView;
    private EditText etMessage;
    private ProgressBar progressTyping;

    private final JSONArray chatHistory = new JSONArray();
    private String userId;
    private float bmi;
    private DatabaseReference dbHistory;
    private final OkHttpClient httpClient = new OkHttpClient();

    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private TextView countdownBubble;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        userId = getIntent().getStringExtra("userId");
        bmi = getIntent().getFloatExtra("bmi", 0f);
        if (userId == null || userId.trim().isEmpty()) userId = "guest";

        chatContainer = findViewById(R.id.chatContainer);
        scrollView    = findViewById(R.id.scrollView);
        etMessage     = findViewById(R.id.etMessage);
        progressTyping = findViewById(R.id.progressTyping);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSend = actionId == EditorInfo.IME_ACTION_SEND;
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isSend || isEnter) { sendMessage(); return true; }
            return false;
        });

        if (!"guest".equals(userId)) {
            dbHistory = FirebaseDatabase
                    .getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                    .getReference("nguoi_dung")
                    .child(userId)
                    .child("lich_su")
                    .child("chat");
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });

        initAI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countdownRunnable != null) {
            retryHandler.removeCallbacks(countdownRunnable);
        }
    }


    private void callGemini(String userText) {
        try {
            JSONObject body = new JSONObject();
            JSONArray contents = new JSONArray();
            contents.put(new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", SYSTEM_PROMPT))));
            contents.put(new JSONObject()
                    .put("role", "model")
                    .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", "Được rồi, tôi sẽ chỉ tư vấn về sức khoẻ."))));

            for (int i = 0; i < chatHistory.length(); i++) {
                contents.put(chatHistory.getJSONObject(i));}
            contents.put(new JSONObject()
                    .put("role", "user").put("parts", new JSONArray()
                            .put(new JSONObject().put("text", userText))));

            body.put("contents", contents);
            RequestBody requestBody = RequestBody.create(
                    body.toString(),
                    MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(GEMINI_URL + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build(); httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        setTypingVisible(false);
                        addBotMessage(" Lỗi kết nối Gemini: " + e.getMessage());
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String res = response.body().string();
                    Log.d(TAG, "Gemini response: " + res);
                    runOnUiThread(() -> setTypingVisible(false));
                    try {
                        JSONObject json = new JSONObject(res);
                        String reply = json
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        appendToHistory("user", userText);
                        appendToHistory("model", reply);
                        runOnUiThread(() -> {
                            addBotMessage(reply);
                            saveChatHistory(userText, reply);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + res, e);
                        runOnUiThread(() ->
                                addBotMessage("⚠️ Lỗi đọc phản hồi Gemini"));
                    }
                }
            });

        } catch (Exception e) {
            addBotMessage("❌ Không gửi được yêu cầu: " + e.getMessage());
        }
    }

    private void initAI() {
        if (API_KEY == null || API_KEY.trim().isEmpty()) {
            Toast.makeText(this, "Gemini API Key chưa được cấu hình!", Toast.LENGTH_LONG).show();
            addBotMessage("⚠️ API Key bị trống.\nKiểm tra local.properties rồi build lại.");
            return;
        }

        String bmiInfo = bmi > 0
                ? String.format(Locale.US, "📊 BMI hiện tại của bạn: %.1f\n\n", bmi)
                : "";

        addBotMessage("Xin chào! 👋 Tôi là trợ lý sức khoẻ BioCare.\n\n"
                + bmiInfo
                + "Bạn có thể hỏi tôi về:\n"
                + "• 🥗 Chế độ dinh dưỡng\n"
                + "• 🏃 Bài tập phù hợp\n"
                + "• 😴 Giấc ngủ\n"
                + "• 💧 Lượng nước cần uống\n"
                + "• ⚖️ Kiểm soát cân nặng");
    }

    private void sendMessage() {
        String userText = etMessage.getText().toString().trim();
        if (userText.isEmpty()) return;

        etMessage.setText("");
        addUserMessage(userText);
        setTypingVisible(true);
        callGemini(userText);
    }

    private void saveChatHistory(String question, String answer) {
        if (dbHistory == null) return;
        String key = dbHistory.push().getKey();
        if (key == null) return;

        HashMap<String, Object> data = new HashMap<>();
        data.put("question", question);
        data.put("answer", answer);
        data.put("timestamp", System.currentTimeMillis());
        data.put("bmi", bmi);

        dbHistory.child(key)
                .setValue(data)
                .addOnSuccessListener(unused -> Log.d(TAG, "Đã lưu Firebase: " + key))
                .addOnFailureListener(e -> Log.e(TAG, "Lỗi lưu Firebase", e));
    }

    private void appendToHistory(String role, String text) {
        try {
            chatHistory.put(new JSONObject()
                    .put("role", role)
                    .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", text))));
        } catch (Exception e) {
            Log.e(TAG, "Lỗi appendToHistory", e);
        }
    }

    private void addUserMessage(String text) {
        chatContainer.addView(buildBubble(text, true));
        scrollToBottom();
    }

    private void addBotMessage(String text) {
        chatContainer.addView(buildBubble(text, false));
        scrollToBottom();
    }

    private void setTypingVisible(boolean visible) {
        progressTyping.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private TextView buildBubble(String text, boolean isUser) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(10));

        if (isUser) {
            params.gravity = Gravity.END;
            params.setMarginStart(dpToPx(60));
            tv.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_chat_user));
            tv.setTextColor(0xFFFFFFFF);
        } else {
            params.gravity = Gravity.START;
            params.setMarginEnd(dpToPx(60));
            tv.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_chat_bot));
            tv.setTextColor(0xFF0F2D1F);
        }

        tv.setLayoutParams(params);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTypeface(Typeface.SANS_SERIF);
        tv.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));
        tv.setLineSpacing(dpToPx(3), 1f);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        return tv;
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override protected String getUserId() { return userId; }
    @Override protected float getCurrentBmi() { return bmi; }
}