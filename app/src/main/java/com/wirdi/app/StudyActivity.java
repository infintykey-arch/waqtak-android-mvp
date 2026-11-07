package com.wirdi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class StudyActivity extends Activity {
    private static final int TICK_MS = 1000;
    private static final int SAVE_INTERVAL_SECONDS = 10;
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_VOLUME = 1;
    private static final int GESTURE_BRIGHTNESS = 2;

    private final Handler handler = new Handler();
    private String cardId = "";
    private StudyCard card;
    private int secondsSinceLastSave = 0;
    private boolean paused = false;
    private boolean completionShown = false;
    private boolean completedThisSession = false;
    private boolean localReady = false;
    private boolean isVideoFullscreen = false;
    private boolean youtubeNativeFullscreen = false;
    private boolean forceLandscapeForFullscreen = false;
    private boolean showVideoRemaining = true;
    private boolean longPressPending = false;
    private boolean speedBoostActive = false;
    private float desiredPlaybackRate = 1f;
    private float touchDownX;
    private float touchDownY;
    private float gestureStartBrightness;
    private int gestureStartVolume;
    private int gestureMode = GESTURE_NONE;
    private int touchSlop;
    private int videoHeight;
    private int originalSystemUiVisibility;
    private int youtubeOriginalSystemUiVisibility;
    private int initialRequestedOrientation;
    private int playerControlsIndex = -1;
    private double videoCurrentSeconds = 0;
    private double videoDurationSeconds = 0;

    private TextView timeLabel, toggleLabel, progressLabel, videoTimeLabel, fullscreenButton, speedButton, speedIndicator, gestureIndicator;
    private ProgressBar progressBar;
    private LinearLayout root, header, body, playerControls;
    private TextView stopButton;
    private FrameLayout mediaFrame;
    private VideoView videoView;
    private MediaPlayer localPlayer;
    private WebView webView;
    private View youtubeCustomView;
    private WebChromeClient.CustomViewCallback youtubeCustomViewCallback;
    private FrameLayout youtubeFullscreenContainer;

    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (!longPressPending || !localReady || videoView == null || isFinishing()) return;
            longPressPending = false;
            speedBoostActive = true;
            applyPlaybackRate(desiredPlaybackRate * 2f);
            if (speedIndicator != null) {
                speedIndicator.setText("×" + formatRate(desiredPlaybackRate * 2f));
                speedIndicator.setVisibility(View.VISIBLE);
            }
        }
    };

    private final Runnable hideGestureIndicator = () -> {
        if (gestureIndicator != null) gestureIndicator.setVisibility(View.GONE);
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (card == null || isFinishing()) return;
            if (!paused && (!"local".equals(card.sourceType) || (localReady && videoView != null && videoView.isPlaying()))) {
                String today = StudyCard.today();
                if (!today.equals(card.lastDate)) card.elapsedToday = 0;
                card.elapsedToday++;
                card.lastDate = today;
                if ("local".equals(card.sourceType) && videoView != null && videoView.isPlaying()) {
                    card.positionSeconds = Math.max(0, videoView.getCurrentPosition() / 1000L);
                    videoCurrentSeconds = Math.max(0, videoView.getCurrentPosition() / 1000.0);
                    int duration = videoView.getDuration();
                    videoDurationSeconds = duration > 0 ? duration / 1000.0 : 0;
                    updateVideoTimeLabel();
                } else card.positionSeconds++;
                if (card.elapsedToday >= card.dailyMinutes * 60L && !completionShown) {
                    completionShown = true; completedThisSession = true; markCompleted(card); secondsSinceLastSave = 0; save(); showCompletedDialog();
                } else if (++secondsSinceLastSave >= SAVE_INTERVAL_SECONDS) {
                    secondsSinceLastSave = 0;
                    save();
                }
                updateTimerUi();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        cardId = getIntent().getStringExtra(AlarmScheduler.EXTRA_ID);
        card = StudyStore.find(this, cardId == null ? "" : cardId);
        if (card == null) { finish(); return; }
        card.resetForNewDay();
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        initialRequestedOrientation = getRequestedOrientation();
        buildUi();
        handler.postDelayed(ticker, TICK_MS);
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BG);
        header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(Ui.dp(this,16), Ui.dp(this,8), Ui.dp(this,16), Ui.dp(this,8));
        TextView back = Ui.text(this, "‹", 32, Ui.SILVER, true); back.setGravity(Gravity.CENTER); header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this,44), Ui.dp(this,48))); back.setOnClickListener(v -> closeSession());
        TextView name = Ui.text(this, card.name, 17, Ui.WHITE, true); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, Ui.dp(this,48), 1); nlp.leftMargin = Ui.dp(this,8); header.addView(name, nlp); root.addView(header);

        mediaFrame = new FrameLayout(this); mediaFrame.setBackgroundColor(Color.BLACK);
        videoHeight = Math.min(Ui.dp(this,260), Math.max(Ui.dp(this,165), getResources().getDisplayMetrics().widthPixels * 9 / 16));
        root.addView(mediaFrame, new LinearLayout.LayoutParams(-1, videoHeight));
        if ("local".equals(card.sourceType) && card.localUri != null && !card.localUri.isEmpty()) {
            videoView = new VideoView(this); mediaFrame.addView(videoView, new FrameLayout.LayoutParams(-1,-1));
            videoView.setOnTouchListener(this::handlePlayerTouch);
            try {
                videoView.setVideoURI(Uri.parse(card.localUri));
                MediaController controller = new MediaController(this); controller.setAnchorView(videoView); videoView.setMediaController(controller);
                videoView.setOnPreparedListener(mp -> {
                    localReady = true; localPlayer = mp;
                    videoView.seekTo((int)(card.positionSeconds * 1000));
                    setLocalPlaybackRate(desiredPlaybackRate);
                    videoView.start(); paused = false; updateToggle();
                });
                videoView.setOnCompletionListener(mp -> { paused = true; updateToggle(); });
                videoView.setOnErrorListener((mp, what, extra) -> { localReady = false; return false; });
            } catch (Exception e) { showMediaError(mediaFrame, "تعذر فتح ملف الفيديو. عدّل الكورس واختر الملف من جديد."); }
        } else if (YoutubeLink.isVideoId(card.youtubeId) || YoutubeLink.isPlaylistId(card.youtubePlaylistId)) {
            webView = new WebView(this); webView.setBackgroundColor(Color.BLACK);
            WebSettings settings = webView.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setMediaPlaybackRequiresUserGesture(false);
            webView.setWebChromeClient(new WebChromeClient() {
                @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                    if (youtubeCustomView != null) { callback.onCustomViewHidden(); return; }
                    youtubeCustomView = view;
                    youtubeCustomViewCallback = callback;
                    youtubeOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
                    youtubeFullscreenContainer = new FrameLayout(StudyActivity.this);
                    youtubeFullscreenContainer.setBackgroundColor(Color.BLACK);
                    youtubeFullscreenContainer.addView(view, new FrameLayout.LayoutParams(-1, -1));
                    ((FrameLayout)getWindow().getDecorView()).addView(youtubeFullscreenContainer, new FrameLayout.LayoutParams(-1, -1));
                    root.setVisibility(View.GONE);
                    youtubeNativeFullscreen = true;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    getWindow().getDecorView().setSystemUiVisibility(fullscreenSystemUiFlags());
                }

                @Override public void onHideCustomView() { exitYoutubeNativeFullscreen(); }
            });
            mediaFrame.addView(webView, new FrameLayout.LayoutParams(-1,-1));
            String videoId = YoutubeLink.isVideoId(card.youtubeId) ? card.youtubeId : "";
            String playlistId = YoutubeLink.isPlaylistId(card.youtubePlaylistId) ? card.youtubePlaylistId : "";
            String embedUrl;
            if (!playlistId.isEmpty() && !videoId.isEmpty()) {
                embedUrl = "https://www.youtube.com/embed/" + videoId + "?list=" + Uri.encode(playlistId) + "&autoplay=1&playsinline=1&rel=0";
            } else if (!playlistId.isEmpty()) {
                embedUrl = "https://www.youtube.com/embed/videoseries?list=" + Uri.encode(playlistId) + "&autoplay=1&playsinline=1&rel=0";
            } else {
                embedUrl = "https://www.youtube.com/embed/" + videoId + "?autoplay=1&start=" + card.positionSeconds + "&playsinline=1&rel=0";
            }
            embedUrl += "&controls=1";
            String html = "<!doctype html><html style='height:100%'><head><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'></head>" +
                    "<body style='margin:0;background:#000;height:100%'><iframe id='ytframe' style='width:100%;height:100%;border:0' src='" + embedUrl.replace("&", "&amp;") + "' allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe>" +
                    "</body></html>";
            webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
        } else showMediaError(mediaFrame, "لا يوجد مصدر فيديو صالح لهذا الكورس.");

        if (videoView != null) addPlayerControls();

        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(Ui.dp(this,22), Ui.dp(this,20), Ui.dp(this,22), Ui.dp(this,18));
        TextView remaining = Ui.text(this, "الوقت المتبقي من جلسة اليوم", 14, Ui.MUTED, false); remaining.setGravity(Gravity.CENTER);
        body.addView(remaining, new LinearLayout.LayoutParams(-1, Ui.dp(this,32)));
        timeLabel = Ui.text(this, "00:00", 42, Ui.WHITE, true); timeLabel.setGravity(Gravity.CENTER);
        body.addView(timeLabel, new LinearLayout.LayoutParams(-1, Ui.dp(this,68)));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progressBar.setMax(100); progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE)); progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.SURFACE2));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, Ui.dp(this,7)); plp.topMargin = Ui.dp(this,8); plp.bottomMargin = Ui.dp(this,8); body.addView(progressBar, plp);
        progressLabel = Ui.text(this, "", 12, Ui.MUTED, false); progressLabel.setGravity(Gravity.CENTER);
        body.addView(progressLabel, new LinearLayout.LayoutParams(-1, Ui.dp(this,28)));
        TextView toggle = Ui.text(this, "Ⅱ   إيقاف احتساب الوقت مؤقتًا", 14, Ui.SILVER, true); toggle.setGravity(Gravity.CENTER);
        toggle.setBackground(Ui.shape(Ui.SURFACE2, Ui.LINE, 24, this));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, Ui.dp(this,50)); tlp.topMargin = Ui.dp(this,12); body.addView(toggle, tlp);
        toggleLabel = toggle; toggle.setOnClickListener(v -> toggleTracking());
        TextView note = Ui.text(this, "يمكن إيقاف المؤقّت دون إنهاء الفيديو. يُحفظ تقدّمك تلقائيًا.", 12, Ui.MUTED, false); note.setGravity(Gravity.CENTER); note.setMaxLines(2);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, Ui.dp(this,44)); noteLp.topMargin = Ui.dp(this,7); body.addView(note, noteLp);
        root.addView(body, new LinearLayout.LayoutParams(-1,0,1));
        stopButton = Ui.text(this, "إنهاء الجلسة", 14, Ui.MUTED, true); stopButton.setGravity(Gravity.CENTER); stopButton.setBackground(Ui.shape(Ui.SURFACE, Ui.LINE, 14, this));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, Ui.dp(this,48)); slp.setMargins(Ui.dp(this,22),0,Ui.dp(this,22),Ui.dp(this,18)); root.addView(stopButton, slp); stopButton.setOnClickListener(v -> closeSession());
        setContentView(root); updateTimerUi();
    }

    private void addPlayerControls() {
        playerControls = new LinearLayout(this); playerControls.setOrientation(LinearLayout.HORIZONTAL); playerControls.setGravity(Gravity.CENTER_VERTICAL);
        playerControls.setPadding(Ui.dp(this,8), Ui.dp(this,5), Ui.dp(this,8), Ui.dp(this,5)); playerControls.setBackgroundColor(Ui.SURFACE);
        videoTimeLabel = Ui.text(this, "المتبقي −—:—", 12, Ui.SILVER, true);
        videoTimeLabel.setGravity(Gravity.CENTER); videoTimeLabel.setPadding(Ui.dp(this,7), 0, Ui.dp(this,7), 0); videoTimeLabel.setBackground(Ui.shape(Ui.SURFACE2, Ui.LINE, 12, this));
        videoTimeLabel.setContentDescription("اضغط للتبديل بين الوقت المتبقي والوقت المشاهَد من الفيديو");
        videoTimeLabel.setOnClickListener(v -> { showVideoRemaining = !showVideoRemaining; updateVideoTimeLabel(); });
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(0, Ui.dp(this,42), 1); timeLp.rightMargin = Ui.dp(this,5); playerControls.addView(videoTimeLabel, timeLp);
        speedButton = Ui.text(this, "×1 السرعة", 12, Ui.WHITE, true);
        speedButton.setGravity(Gravity.CENTER); speedButton.setPadding(Ui.dp(this,8), 0, Ui.dp(this,8), 0); speedButton.setBackground(Ui.shape(Ui.SURFACE2, Ui.LINE, 12, this));
        speedButton.setContentDescription("اختيار سرعة تشغيل الفيديو"); speedButton.setOnClickListener(v -> showPlaybackRateDialog());
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(-2, Ui.dp(this,42)); speedLp.rightMargin = Ui.dp(this,5); playerControls.addView(speedButton, speedLp);
        fullscreenButton = Ui.text(this, "⛶", 20, Ui.WHITE, true);
        fullscreenButton.setGravity(Gravity.CENTER); fullscreenButton.setPadding(Ui.dp(this,10), 0, Ui.dp(this,10), 0); fullscreenButton.setBackground(Ui.shape(Ui.SURFACE2, Ui.LINE, 12, this));
        fullscreenButton.setContentDescription("ملء الشاشة"); fullscreenButton.setOnClickListener(v -> setVideoFullscreen(!isVideoFullscreen, true));
        playerControls.addView(fullscreenButton, new LinearLayout.LayoutParams(-2, Ui.dp(this,42)));
        root.addView(playerControls, new LinearLayout.LayoutParams(-1, Ui.dp(this,52)));

        speedIndicator = Ui.text(this, "×2", 15, Ui.WHITE, true); speedIndicator.setGravity(Gravity.CENTER);
        speedIndicator.setPadding(Ui.dp(this,12), Ui.dp(this,4), Ui.dp(this,12), Ui.dp(this,4)); speedIndicator.setBackground(Ui.shape(0xCC101820, Ui.BLUE, 20, this)); speedIndicator.setVisibility(View.GONE); speedIndicator.setClickable(false); speedIndicator.setFocusable(false);
        FrameLayout.LayoutParams speedOverlayLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); speedOverlayLp.topMargin = Ui.dp(this,12); mediaFrame.addView(speedIndicator, speedOverlayLp);
        gestureIndicator = Ui.text(this, "", 14, Ui.WHITE, true); gestureIndicator.setGravity(Gravity.CENTER);
        gestureIndicator.setPadding(Ui.dp(this,16), Ui.dp(this,9), Ui.dp(this,16), Ui.dp(this,9)); gestureIndicator.setBackground(Ui.shape(0xCC101820, Ui.LINE, 18, this)); gestureIndicator.setVisibility(View.GONE); gestureIndicator.setClickable(false); gestureIndicator.setFocusable(false);
        FrameLayout.LayoutParams gestureLp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER); mediaFrame.addView(gestureIndicator, gestureLp);
        updateVideoTimeLabel(); updateSpeedButton();
    }

    private boolean handlePlayerTouch(View source, MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX(); touchDownY = event.getY(); gestureMode = GESTURE_NONE; longPressPending = true;
                handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - touchDownX;
                float dy = event.getY() - touchDownY;
                if (longPressPending && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    longPressPending = false; handler.removeCallbacks(longPressRunnable);
                }
                if (gestureMode == GESTURE_NONE && Math.abs(dy) > touchSlop * 2 && Math.abs(dy) > Math.abs(dx) * 1.25f) {
                    gestureMode = touchDownX < source.getWidth() / 2f ? GESTURE_BRIGHTNESS : GESTURE_VOLUME;
                    if (gestureMode == GESTURE_VOLUME) {
                        AudioManager audio = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
                        gestureStartVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                    } else gestureStartBrightness = currentWindowBrightness();
                    handler.removeCallbacks(hideGestureIndicator);
                }
                if (gestureMode == GESTURE_VOLUME || gestureMode == GESTURE_BRIGHTNESS) {
                    applyVerticalGesture(source, dy);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                longPressPending = false; handler.removeCallbacks(longPressRunnable); releasePlaybackRateBoost();
                boolean consumed = gestureMode != GESTURE_NONE;
                gestureMode = GESTURE_NONE;
                if (consumed) handler.postDelayed(hideGestureIndicator, 900);
                return consumed;
            case MotionEvent.ACTION_POINTER_DOWN:
                longPressPending = false; handler.removeCallbacks(longPressRunnable); releasePlaybackRateBoost();
                gestureMode = GESTURE_NONE;
                return false;
            default:
                return gestureMode != GESTURE_NONE;
        }
    }

    private void applyVerticalGesture(View source, float deltaY) {
        float change = -deltaY / Math.max(1f, source.getHeight());
        if (gestureMode == GESTURE_VOLUME) {
            AudioManager audio = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int value = Math.max(0, Math.min(max, gestureStartVolume + Math.round(change * max)));
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
            int percent = Math.round(value * 100f / max);
            showGestureIndicator("الصوت  " + percent + "%");
        } else if (gestureMode == GESTURE_BRIGHTNESS) {
            float value = Math.max(0.05f, Math.min(1f, gestureStartBrightness + change));
            android.view.WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.screenBrightness = value;
            getWindow().setAttributes(attributes);
            showGestureIndicator("الإضاءة  " + Math.round(value * 100f) + "%");
        }
    }

    private float currentWindowBrightness() {
        float brightness = getWindow().getAttributes().screenBrightness;
        if (brightness >= 0f && brightness <= 1f) return brightness;
        try {
            int systemValue = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return Math.max(0.05f, Math.min(1f, systemValue / 255f));
        } catch (Exception ignored) { return 0.5f; }
    }

    private void showGestureIndicator(String text) {
        if (gestureIndicator == null) return;
        gestureIndicator.setText(text); gestureIndicator.setVisibility(View.VISIBLE);
    }

    private void showPlaybackRateDialog() {
        final float[] rates = playbackRateChoices();
        String[] labels = new String[rates.length];
        int checked = 0;
        for (int i = 0; i < rates.length; i++) {
            labels[i] = "×" + formatRate(rates[i]);
            if (Math.abs(rates[i] - desiredPlaybackRate) < 0.01f) checked = i;
        }
        new AlertDialog.Builder(this).setTitle("سرعة الفيديو")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    desiredPlaybackRate = rates[which];
                    applyPlaybackRate(desiredPlaybackRate);
                    updateSpeedButton();
                    dialog.dismiss();
                }).setNegativeButton("إلغاء", null).show();
    }

    private float[] playbackRateChoices() {
        return new float[]{0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f};
    }

    private String formatRate(float rate) {
        if (Math.abs(rate - Math.round(rate)) < 0.01f) return String.valueOf(Math.round(rate));
        return String.format(Locale.US, "%.2f", rate).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void updateSpeedButton() {
        if (speedButton != null) speedButton.setText("×" + formatRate(desiredPlaybackRate) + " السرعة");
    }

    private void applyPlaybackRate(float rate) {
        setLocalPlaybackRate(rate);
        if (speedBoostActive && speedIndicator != null) {
            speedIndicator.setText("×" + formatRate(rate));
            speedIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void setLocalPlaybackRate(float rate) {
        if (localPlayer == null) return;
        try {
            PlaybackParams params = localPlayer.getPlaybackParams();
            params.setSpeed(rate);
            localPlayer.setPlaybackParams(params);
        } catch (Exception ignored) {}
    }

    private void releasePlaybackRateBoost() {
        if (!speedBoostActive) return;
        speedBoostActive = false;
        applyPlaybackRate(desiredPlaybackRate);
        if (speedIndicator != null) speedIndicator.setVisibility(View.GONE);
    }

    private void setVideoFullscreen(boolean fullscreen, boolean lockLandscape) {
        if (videoView == null || root == null || isVideoFullscreen == fullscreen) return;
        isVideoFullscreen = fullscreen;
        View decor = getWindow().getDecorView();
        if (fullscreen) {
            forceLandscapeForFullscreen = lockLandscape;
            originalSystemUiVisibility = decor.getSystemUiVisibility();
            header.setVisibility(View.GONE); body.setVisibility(View.GONE); stopButton.setVisibility(View.GONE);
            playerControlsIndex = root.indexOfChild(playerControls);
            if (playerControlsIndex >= 0) root.removeView(playerControls);
            playerControls.setBackgroundColor(0xCC101820);
            mediaFrame.addView(playerControls, new FrameLayout.LayoutParams(-1, Ui.dp(this,52), Gravity.BOTTOM));
            mediaFrame.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
            decor.setSystemUiVisibility(fullscreenSystemUiFlags());
            if (lockLandscape) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            fullscreenButton.setText("⛶"); fullscreenButton.setContentDescription("إنهاء ملء الشاشة");
        } else {
            header.setVisibility(View.VISIBLE); body.setVisibility(View.VISIBLE); stopButton.setVisibility(View.VISIBLE);
            if (playerControls.getParent() == mediaFrame) mediaFrame.removeView(playerControls);
            playerControls.setBackgroundColor(Ui.SURFACE);
            int insertAt = Math.max(0, Math.min(playerControlsIndex, root.getChildCount()));
            root.addView(playerControls, insertAt, new LinearLayout.LayoutParams(-1, Ui.dp(this,52)));
            mediaFrame.setLayoutParams(new LinearLayout.LayoutParams(-1, videoHeight));
            decor.setSystemUiVisibility(originalSystemUiVisibility);
            if (forceLandscapeForFullscreen) setRequestedOrientation(initialRequestedOrientation);
            forceLandscapeForFullscreen = false;
            fullscreenButton.setText("⛶"); fullscreenButton.setContentDescription("ملء الشاشة");
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (videoView == null || playerControls == null) return;
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && !isVideoFullscreen) {
            setVideoFullscreen(true, false);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && isVideoFullscreen && !forceLandscapeForFullscreen) {
            setVideoFullscreen(false, false);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && (isVideoFullscreen || youtubeNativeFullscreen)) getWindow().getDecorView().setSystemUiVisibility(fullscreenSystemUiFlags());
    }

    @Override public void onBackPressed() {
        if (youtubeNativeFullscreen) exitYoutubeNativeFullscreen();
        else if (isVideoFullscreen) setVideoFullscreen(false, false);
        else super.onBackPressed();
    }

    private int fullscreenSystemUiFlags() {
        return View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    }

    private void exitYoutubeNativeFullscreen() {
        if (!youtubeNativeFullscreen) return;
        youtubeNativeFullscreen = false;
        if (youtubeFullscreenContainer != null) {
            ((FrameLayout)getWindow().getDecorView()).removeView(youtubeFullscreenContainer);
            youtubeFullscreenContainer = null;
        }
        youtubeCustomView = null;
        if (root != null) root.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(youtubeOriginalSystemUiVisibility);
        setRequestedOrientation(initialRequestedOrientation);
        WebChromeClient.CustomViewCallback callback = youtubeCustomViewCallback;
        youtubeCustomViewCallback = null;
        if (callback != null) callback.onCustomViewHidden();
    }

    private void updateVideoTimeLabel() {
        if (videoTimeLabel == null) return;
        if (videoDurationSeconds <= 0) {
            videoTimeLabel.setText(showVideoRemaining ? "المتبقي −—:—" : "المشاهَد  00:00");
            return;
        }
        if (showVideoRemaining) {
            long remaining = Math.max(0, Math.round(videoDurationSeconds - videoCurrentSeconds));
            videoTimeLabel.setText("المتبقي  −" + formatVideoTime(remaining));
        } else videoTimeLabel.setText("المشاهَد  " + formatVideoTime(Math.round(videoCurrentSeconds)));
    }

    private String formatVideoTime(long seconds) {
        long safe = Math.max(0, seconds); long hours = safe / 3600; long minutes = (safe % 3600) / 60; long remainder = safe % 60;
        return hours > 0 ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder) : String.format(Locale.US, "%02d:%02d", minutes, remainder);
    }

    private void showMediaError(FrameLayout parent, String message) {
        TextView t = Ui.text(this, message, 14, Ui.SILVER, false); t.setGravity(Gravity.CENTER); t.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0); parent.addView(t, new FrameLayout.LayoutParams(-1,-1));
    }

    private void toggleTracking() {
        if ("local".equals(card.sourceType) && localReady && videoView != null) {
            if (videoView.isPlaying()) videoView.pause(); else videoView.start();
            paused = !videoView.isPlaying();
        } else paused = !paused;
        updateToggle();
    }
    private void updateToggle() {
        if (toggleLabel != null) {
            boolean actuallyPaused = paused || ("local".equals(card.sourceType) && localReady && videoView != null && !videoView.isPlaying());
            toggleLabel.setText(actuallyPaused ? "▶   استئناف احتساب الوقت" : "Ⅱ   إيقاف احتساب الوقت مؤقتًا");
            toggleLabel.setTextColor(actuallyPaused ? Ui.BLUE : Ui.SILVER);
        }
    }
    private void updateTimerUi() {
        if (timeLabel == null) return;
        long goal = card.dailyMinutes * 60L;
        long remaining = Math.max(0, goal - card.elapsedToday);
        timeLabel.setText(String.format(Locale.US, "%02d:%02d", remaining / 60, remaining % 60));
        progressBar.setProgress((int)Math.min(100, card.elapsedToday * 100 / Math.max(1, goal)));
        progressLabel.setText(String.format(Locale.US, "%d من %d دقيقة", Math.min(card.elapsedToday / 60, card.dailyMinutes), card.dailyMinutes));
        updateToggle();
    }

    private void markCompleted(StudyCard c) {
        String today = StudyCard.today();
        if (today.equals(c.completedDate)) return;
        String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(System.currentTimeMillis() - 86400000L));
        c.streak = yesterday.equals(c.completedDate) ? c.streak + 1 : 1;
        c.completedDate = today;
    }
    private void showCompletedDialog() {
        new AlertDialog.Builder(this).setTitle("انتهى الوقت المحدد")
                .setMessage("أحسنت! أكملت " + card.dailyMinutes + " دقيقة من «" + card.name + "». يمكنك مواصلة المذاكرة أو إنهاء الجلسة.")
                .setCancelable(false)
                .setNegativeButton("إكمال", (d,w) -> { d.dismiss(); })
                .setPositiveButton("إنهاء الجلسة", (d,w) -> { d.dismiss(); closeSession(); })
                .show();
    }
    private void save() {
        ArrayList<StudyCard> all = StudyStore.load(this);
        for (int i=0;i<all.size();i++) if (all.get(i).id.equals(card.id)) { all.set(i, card); break; }
        StudyStore.save(this, all);
    }
    private void closeSession() {
        if (card == null) { finish(); return; }
        if (videoView != null) { try { if (videoView.isPlaying()) card.positionSeconds = videoView.getCurrentPosition()/1000L; videoView.pause(); } catch (Exception ignored) {} }
        save();
        Intent result = new Intent().putExtra("session_completed_today", completedThisSession || card.doneToday());
        setResult(RESULT_OK, result); finish();
    }

    @Override protected void onPause() {
        super.onPause();
        if (speedBoostActive) releasePlaybackRateBoost();
        if (webView != null) webView.onPause();
        if (card != null) { if (videoView != null) { try { if (videoView.isPlaying()) card.positionSeconds = videoView.getCurrentPosition()/1000L; } catch (Exception ignored) {} } save(); }
    }
    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }
    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker); handler.removeCallbacks(longPressRunnable); handler.removeCallbacks(hideGestureIndicator);
        if (youtubeNativeFullscreen) exitYoutubeNativeFullscreen();
        if (isVideoFullscreen) {
            getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
            if (forceLandscapeForFullscreen) setRequestedOrientation(initialRequestedOrientation);
        }
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        if (videoView != null) { try { videoView.stopPlayback(); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
