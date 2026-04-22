package com.example.udtddk.history;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;

import com.example.udtddk.BaseActivity;
import com.example.udtddk.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends BaseActivity {

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextView         tabChat, tabNotif;
    private View             tabIndicatorLeft, tabIndicatorRight;
    private LinearLayout     listContainer;
    private ProgressBar      progressLoading;
    private LinearLayout     layoutEmpty;
    private TextView         tvEmptyIcon, tvEmptyTitle, tvEmptySubText;
    private NestedScrollView scrollContent;


    private DatabaseReference db;
    private String userId;
    private float  currentBmi;


    private boolean isShowingChat = false;


    private static final String C_BG           = "#F0F7F4";
    private static final String C_SURFACE       = "#FFFFFF";
    private static final String C_DIVIDER       = "#DCF0E6";
    private static final String C_TEXT_PRI      = "#0F2D1F";
    private static final String C_TEXT_SEC      = "#3D6B52";
    private static final String C_TEXT_HINT     = "#7A9E8A";
    private static final String C_GREEN_DARK    = "#1A6B43";
    private static final String C_GREEN_MID     = "#2E8B57";
    private static final String C_GREEN_LIGHT   = "#B7E5CE";
    private static final String C_GREEN_PALE    = "#EAF7EF";
    private static final String C_WATER_DARK    = "#0369A1";
    private static final String C_WATER_LIGHT   = "#BAE6FD";
    private static final String C_WATER_PALE    = "#F0F9FF";
    private static final String C_SLEEP_DARK    = "#6D28D9";
    private static final String C_SLEEP_LIGHT   = "#DDD6FE";
    private static final String C_SLEEP_PALE    = "#F5F3FF";
    private static final String C_HEALTH_DARK   = "#92400E";
    private static final String C_HEALTH_LIGHT  = "#FDE68A";
    private static final String C_HEALTH_PALE   = "#FFFBEB";
    private static final String C_AI_DARK       = "#065F46";
    private static final String C_AI_LIGHT      = "#A7F3D0";
    private static final String C_AI_PALE       = "#ECFDF5";
    private static final String C_DELETE_RED    = "#EF4444";
    private static final String C_DELETE_PALE   = "#FEF2F2";

    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        userId     = getIntent().getStringExtra("NguoiDungId");
        currentBmi = getIntent().getFloatExtra("BMI", 0f);


        if (userId == null || userId.isEmpty()) {
            userId = "";
        }

        bindViews();
        setupBottomNav(R.id.nav_history);

        db = FirebaseDatabase
                .getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("NguoIDung")
                .child(userId)
                .child("LicSuMucTieu");

        tabChat .setOnClickListener(v -> switchTab(true));
        tabNotif.setOnClickListener(v -> switchTab(false));

        // FIX: mở mặc định tab Thông báo, không phải Chat
        switchTab(false);
    }

    private void bindViews() {
        tabChat           = findViewById(R.id.tabChat);
        tabNotif          = findViewById(R.id.tabNotif);
        tabIndicatorLeft  = findViewById(R.id.tabIndicatorLeft);
        tabIndicatorRight = findViewById(R.id.tabIndicatorRight);
        listContainer     = findViewById(R.id.listContainer);
        progressLoading   = findViewById(R.id.progressLoading);
        layoutEmpty       = findViewById(R.id.layoutEmpty);
        tvEmptyIcon       = findViewById(R.id.tvEmptyIcon);
        tvEmptyTitle      = findViewById(R.id.tvEmptyText);
        tvEmptySubText    = findViewById(R.id.tvEmptySubText);
        scrollContent     = findViewById(R.id.scrollContent);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }


    private void switchTab(boolean showChat) {
        if (isShowingChat == showChat && listContainer.getChildCount() > 0) return;
        isShowingChat = showChat;

        styleTab(tabChat,  showChat);
        styleTab(tabNotif, !showChat);


        tabIndicatorLeft .setVisibility(showChat  ? View.VISIBLE : View.INVISIBLE);
        tabIndicatorRight.setVisibility(!showChat ? View.VISIBLE : View.INVISIBLE);

        listContainer.removeAllViews();
        if (showChat) loadChatHistory(); else loadNotifHistory();
    }

    private void styleTab(TextView tab, boolean active) {
        tab.setTextColor(Color.parseColor(active ? C_GREEN_DARK : C_TEXT_HINT));
        tab.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(active ? Color.parseColor(C_GREEN_PALE) : Color.TRANSPARENT);
        tab.setBackground(bg);
    }


    private void loadChatHistory() {
        showLoading();
        db.child("chat").orderByChild("timestamp").limitToLast(50)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        List<DataSnapshot> items = reversed(snap);
                        if (items.isEmpty()) {
                            showEmpty("💬", "Chưa có cuộc trò chuyện nào",
                                    "Hãy hỏi trợ lý AI nhé!");
                            return;
                        }
                        showContent();
                        for (DataSnapshot item : items) {
                            String q   = item.child("question") .getValue(String.class);
                            String ans = item.child("answer")   .getValue(String.class);
                            Long   ts  = item.child("timestamp").getValue(Long.class);
                            if (q != null && ans != null)
                                addAnimated(buildChatCard(q, ans, fmt(ts)));
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError e) {
                        showEmpty("⚠️", "Lỗi tải dữ liệu", e.getMessage());
                    }
                });
    }


    private void loadNotifHistory() {
        showLoading();

        // FIX: guard khi userId rỗng
        if (userId == null || userId.isEmpty()) {
            showEmpty("⚠️", "Chưa đăng nhập", "Vui lòng đăng nhập để xem lịch sử thông báo");
            return;
        }

        db.child("thong_bao").orderByChild("timestamp").limitToLast(50)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        List<DataSnapshot> items = reversed(snap);
                        if (items.isEmpty()) {
                            showEmpty("🔔", "Chưa có thông báo nào",
                                    "Đặt lịch nhắc uống nước hoặc đi ngủ nhé!");
                            return;
                        }
                        showContent();
                        for (DataSnapshot item : items) {
                            String title   = item.child("title")    .getValue(String.class);
                            String content = item.child("content")  .getValue(String.class);
                            String type    = item.child("type")     .getValue(String.class);
                            Long   ts      = item.child("timestamp").getValue(Long.class);
                            String key     = item.getKey();


                            if (title == null || title.isEmpty()) continue;


                            String resolvedType = (type != null && !type.isEmpty())
                                    ? type : inferType(title);

                            addAnimated(buildNotifCard(
                                    title,
                                    content != null ? content : "",
                                    resolvedType,
                                    fmt(ts),
                                    key));
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError e) {
                        showEmpty("⚠️", "Lỗi tải dữ liệu", e.getMessage());
                    }
                });
    }


    private String inferType(String title) {
        if (title == null) return "ai";
        String t = title.toLowerCase();
        if (t.contains("nước") || t.contains("uống") || t.contains("💧") || t.contains("water"))
            return "water";
        if (t.contains("ngủ") || t.contains("sleep") || t.contains("🌙") || t.contains("giờ đi ngủ"))
            return "sleep";
        if (t.contains("bmi")  || t.contains("cân")   || t.contains("📊")
                || t.contains("sức khoẻ") || t.contains("sức khỏe") || t.contains("health"))
            return "health";
        return "ai";
    }


    private View buildChatCard(String question, String answer, String time) {
        CardView card = makeCard();
        LinearLayout inner = vStack();
        inner.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout header = hStack(Gravity.CENTER_VERTICAL);
        header.addView(makePill("Trò chuyện AI", C_GREEN_DARK, C_GREEN_LIGHT));
        header.addView(flex());
        header.addView(label("🕐 " + time, C_TEXT_HINT, 10, Typeface.NORMAL));
        inner.addView(header);
        inner.addView(makeDivider(10, 10));
        inner.addView(makeBubble("🧑", question,
                C_GREEN_PALE, C_GREEN_LIGHT, C_TEXT_PRI, true));
        inner.addView(vSpace(8));
        inner.addView(makeBubble("🤖", answer,
                "#F6FAF8", C_DIVIDER, C_TEXT_SEC, false));

        card.addView(inner);
        return card;
    }


    private View buildNotifCard(String title, String content,
                                String type, String time, String key) {
        String[] pal = palette(type);
        String   ico = notifIcon(type);

        CardView card = makeCard();
        LinearLayout outer = vStack();


        LinearLayout inner = hStack(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(14), dp(14), dp(14), dp(14));


        TextView tvIcon = new TextView(this);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(50), dp(50));
        ip.setMarginEnd(dp(14));
        tvIcon.setLayoutParams(ip);
        tvIcon.setText(ico);
        tvIcon.setTextSize(22);
        tvIcon.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor(pal[1]));
        tvIcon.setBackground(circle);
        inner.addView(tvIcon);


        LinearLayout col = vStack();
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout topRow = hStack(Gravity.CENTER_VERTICAL);
        topRow.addView(makePill(typeLabel(type), pal[0], pal[1]));
        topRow.addView(flex());
        topRow.addView(label("🕐 " + time, C_TEXT_HINT, 10, Typeface.NORMAL));
        col.addView(topRow);

        TextView tvTitle = label(title, C_TEXT_PRI, 13, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMargins(0, dp(5), 0, 0);
        tvTitle.setLayoutParams(tp);
        col.addView(tvTitle);


        if (!content.isEmpty()) {
            TextView tvPreview = label(content, C_TEXT_SEC, 11, Typeface.NORMAL);
            tvPreview.setMaxLines(1);
            tvPreview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            pp.setMargins(0, dp(2), 0, 0);
            tvPreview.setLayoutParams(pp);
            col.addView(tvPreview);
        }


        TextView tvArrow = new TextView(this);
        tvArrow.setText("▼");
        tvArrow.setTextColor(Color.parseColor(C_TEXT_HINT));
        tvArrow.setTextSize(9);
        tvArrow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ap.setMargins(0, dp(4), 0, 0);
        tvArrow.setLayoutParams(ap);
        col.addView(tvArrow);

        inner.addView(col);
        outer.addView(inner);


        LinearLayout detailLayout = buildDetailSection(title, content, type, time, pal, ico);
        detailLayout.setVisibility(View.GONE);
        outer.addView(detailLayout);

        card.addView(outer);


        setupNotifInteraction(card, outer, detailLayout, tvArrow, key);

        return card;
    }


    private LinearLayout buildDetailSection(String title, String content,
                                            String type, String time,
                                            String[] pal, String ico) {
        LinearLayout layout = vStack();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(14), 0, dp(14), dp(14));
        layout.setLayoutParams(lp);

        layout.addView(makeDivider(0, 12));

        GradientDrawable detailBg = new GradientDrawable();
        detailBg.setCornerRadius(dp(12));
        detailBg.setColor(Color.parseColor(pal[2]));
        detailBg.setStroke(dp(1), Color.parseColor(pal[1]));
        layout.setBackground(detailBg);
        layout.setPadding(dp(14), dp(12), dp(14), dp(12));


        LinearLayout headerRow = hStack(Gravity.CENTER_VERTICAL);
        TextView tvIco = new TextView(this);
        tvIco.setText(ico);
        tvIco.setTextSize(18);
        LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        icp.setMarginEnd(dp(8));
        tvIco.setLayoutParams(icp);
        headerRow.addView(tvIco);
        headerRow.addView(label(typeLabel(type).toUpperCase(), pal[0], 10, Typeface.BOLD));
        layout.addView(headerRow);


        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, dp(8), 0, 0);
        TextView tvDetailTitle = label(title, C_TEXT_PRI, 14, Typeface.BOLD);
        tvDetailTitle.setLayoutParams(titleLp);
        layout.addView(tvDetailTitle);


        if (!content.isEmpty()) {
            LinearLayout contentBox = vStack();
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cbLp.setMargins(0, dp(8), 0, 0);
            contentBox.setLayoutParams(cbLp);
            GradientDrawable cbBg = new GradientDrawable();
            cbBg.setCornerRadius(dp(8));
            cbBg.setColor(Color.WHITE);
            contentBox.setBackground(cbBg);
            contentBox.setPadding(dp(12), dp(10), dp(12), dp(10));

            TextView tvContent = label(content, C_TEXT_SEC, 13, Typeface.NORMAL);
            tvContent.setLineSpacing(dp(3), 1f);
            contentBox.addView(tvContent);
            layout.addView(contentBox);
        }

        // Thời gian nhận
        LinearLayout.LayoutParams tsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tsLp.setMargins(0, dp(10), 0, 0);
        LinearLayout tsRow = hStack(Gravity.CENTER_VERTICAL);
        tsRow.setLayoutParams(tsLp);
        tsRow.addView(label("📅  Nhận lúc: ", C_TEXT_HINT, 11, Typeface.NORMAL));
        tsRow.addView(label(time, C_TEXT_SEC, 11, Typeface.BOLD));
        layout.addView(tsRow);


        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.setMargins(0, dp(10), 0, 0);
        TextView tvHint = label("← Vuốt sang trái để xóa thông báo này", C_DELETE_RED, 11, Typeface.ITALIC);
        tvHint.setLayoutParams(hintLp);
        tvHint.setGravity(Gravity.CENTER);
        layout.addView(tvHint);

        return layout;
    }


    private void setupNotifInteraction(CardView card,
                                       LinearLayout outer,
                                       LinearLayout detailLayout,
                                       TextView tvArrow,
                                       String key) {
        final boolean[] expanded  = {false};
        final float[]   startX    = {0f};
        final float[]   startY    = {0f};
        final boolean[] swiping   = {false};

        GestureDetector gd = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        expanded[0] = !expanded[0];
                        toggleDetail(detailLayout, tvArrow, expanded[0]);
                        return true;
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float vX, float vY) {
                        if (e1 == null) return false;
                        float dX = e2.getX() - e1.getX();
                        float dY = Math.abs(e2.getY() - e1.getY());
                        if (dX < -dp(80) && dY < dp(60)) {
                            animateDeleteCard(card, key);
                            return true;
                        }
                        return false;
                    }
                });

        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0]   = event.getX();
                    startY[0]   = event.getY();
                    swiping[0]  = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getX() - startX[0]);
                    float dy = Math.abs(event.getY() - startY[0]);
                    if (dx > dp(12) && dx > dy) {
                        swiping[0] = true;
                        float rawDx = event.getX() - startX[0];
                        if (rawDx < 0) {
                            card.setTranslationX(rawDx * 0.7f);
                            float fraction = Math.min(1f, -rawDx / dp(160));
                            card.setAlpha(1f - fraction * 0.4f);
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (swiping[0]) {
                        float totalDx = startX[0] - event.getX();
                        if (totalDx > dp(120)) {
                            animateDeleteCard(card, key);
                        } else {
                            card.animate().translationX(0f).alpha(1f)
                                    .setDuration(200).start();
                        }
                        swiping[0] = false;
                    }
                    break;
            }
            gd.onTouchEvent(event);
            return true;
        });
    }

    // ── Animation mở/đóng chi tiết ────────────────────────────────────────────
    private void toggleDetail(LinearLayout detail, TextView arrow, boolean expand) {
        if (expand) {
            detail.setVisibility(View.VISIBLE);
            detail.setAlpha(0f);
            detail.setTranslationY(-dp(8));
            detail.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            arrow.setText("▲");
        } else {
            detail.animate()
                    .alpha(0f).translationY(-dp(8))
                    .setDuration(180)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> detail.setVisibility(View.GONE))
                    .start();
            arrow.setText("▼");
        }
    }

    // ── Vuốt xoá: trượt + co lại + xoá Firebase ──────────────────────────────
    private void animateDeleteCard(CardView card, String key) {
        card.animate()
                .translationX(-card.getWidth())
                .alpha(0f)
                .setDuration(280)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> collapseAndRemove(card, key))
                .start();
    }

    private void collapseAndRemove(CardView card, String key) {
        int startH = card.getMeasuredHeight();
        ValueAnimator va = ValueAnimator.ofInt(startH, 0);
        va.setDuration(200);
        va.addUpdateListener(anim -> {
            int val = (int) anim.getAnimatedValue();
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
            lp.height       = val;
            lp.bottomMargin = (int) (dp(12) * (1f - anim.getAnimatedFraction()));
            card.setLayoutParams(lp);
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                listContainer.removeView(card);
                // FIX: chỉ xoá nếu key hợp lệ
                if (key != null && !key.isEmpty())
                    db.child("thong_bao").child(key).removeValue();
                if (listContainer.getChildCount() == 0)
                    showEmpty("🗑️", "Đã xóa tất cả thông báo",
                            "Không còn thông báo nào trong lịch sử");
            }
        });
        va.start();
    }

    // ── Helpers loại thông báo ─────────────────────────────────────────────────
    private String[] palette(String type) {
        switch (type) {
            case "water":  return new String[]{C_WATER_DARK,  C_WATER_LIGHT,  C_WATER_PALE};
            case "sleep":  return new String[]{C_SLEEP_DARK,  C_SLEEP_LIGHT,  C_SLEEP_PALE};
            case "health": return new String[]{C_HEALTH_DARK, C_HEALTH_LIGHT, C_HEALTH_PALE};
            default:       return new String[]{C_AI_DARK,     C_AI_LIGHT,     C_AI_PALE};
        }
    }

    private String notifIcon(String type) {
        switch (type) {
            case "water":  return "💧";
            case "sleep":  return "🌙";
            case "health": return "📊";
            default:       return "🤖";
        }
    }

    private String typeLabel(String type) {
        switch (type) {
            case "water":  return "Uống nước";
            case "sleep":  return "Nhắc ngủ";
            case "health": return "Sức khoẻ";
            default:       return "Từ AI";
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI FACTORY
    // ══════════════════════════════════════════════════════════════════════════
    private CardView makeCard() {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        card.setRadius(dp(16));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);
        return card;
    }

    private LinearLayout vStack() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hStack(int gravity) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(gravity);
        return l;
    }

    private TextView label(String text, String color, int sp, int style) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(sp);
        tv.setTypeface(null, style);
        return tv;
    }

    private View makePill(String text, String darkColor, String lightColor) {
        TextView pill = new TextView(this);
        pill.setText(text);
        pill.setTextColor(Color.parseColor(darkColor));
        pill.setTextSize(9);
        pill.setTypeface(null, Typeface.BOLD);
        pill.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(Color.parseColor(lightColor));
        pill.setBackground(bg);
        return pill;
    }

    private View makeBubble(String emoji, String text,
                            String bgColor, String borderColor,
                            String textColor, boolean bold) {
        LinearLayout row = hStack(Gravity.TOP);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(Color.parseColor(bgColor));
        bg.setStroke(dp(1), Color.parseColor(borderColor));
        row.setBackground(bg);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView emo = new TextView(this);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(22), dp(22));
        ep.setMarginEnd(dp(8));
        ep.topMargin = dp(1);
        emo.setLayoutParams(ep);
        emo.setText(emoji);
        emo.setTextSize(13);
        emo.setGravity(Gravity.CENTER);
        row.addView(emo);

        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tv.setText(text);
        tv.setTextColor(Color.parseColor(textColor));
        tv.setTextSize(13);
        tv.setLineSpacing(dp(2), 1f);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        row.addView(tv);
        return row;
    }

    private View makeDivider(int mTop, int mBot) {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(mTop), 0, dp(mBot));
        v.setLayoutParams(lp);
        v.setBackgroundColor(Color.parseColor(C_DIVIDER));
        return v;
    }

    private View vSpace(int dpVal) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(dpVal)));
        return v;
    }

    private View flex() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, dp(1), 1f));
        return v;
    }

    // ── Animation thêm card ────────────────────────────────────────────────────
    private void addAnimated(View card) {
        card.setAlpha(0f);
        card.setTranslationY(dp(16));
        listContainer.addView(card);
        long delay = (long) listContainer.getChildCount() * 55;
        card.postDelayed(() ->
                        card.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(260)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .start(),
                delay);
    }

    // ── Trạng thái hiển thị ────────────────────────────────────────────────────
    private void showLoading() {
        progressLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        scrollContent.setVisibility(View.GONE);
    }

    private void showEmpty(String ico, String title, String sub) {
        progressLoading.setVisibility(View.GONE);
        scrollContent.setVisibility(View.GONE);
        tvEmptyIcon.setText(ico);
        tvEmptyTitle.setText(title);
        tvEmptySubText.setText(sub);
        layoutEmpty.setVisibility(View.VISIBLE);
    }

    private void showContent() {
        progressLoading.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        scrollContent.setVisibility(View.VISIBLE);
    }

    // ── Utils ──────────────────────────────────────────────────────────────────
    private List<DataSnapshot> reversed(DataSnapshot snap) {
        List<DataSnapshot> list = new ArrayList<>();
        for (DataSnapshot c : snap.getChildren()) list.add(c);
        Collections.reverse(list);
        return list;
    }

    private String fmt(Long ts) {
        if (ts == null) return "";
        return new SimpleDateFormat("dd/MM/yyyy  HH:mm",
                Locale.getDefault()).format(new Date(ts));
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    @Override protected String getUserId()     { return userId; }
    @Override protected float  getCurrentBmi() { return currentBmi; }
}