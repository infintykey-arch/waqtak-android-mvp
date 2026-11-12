package com.wirdi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.AdChoicesView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends LocalizedActivity {
    private static final int REQ_STUDY = 505;
    private static final int REQ_VIDEO = 404;
    private static final int REQ_NOTIFICATIONS = 606;
    private static final int REQ_RINGTONE = 707;
    private static final int[] WEEKDAY_SHORT = {R.string.weekday_sun_short, R.string.weekday_mon_short, R.string.weekday_tue_short, R.string.weekday_wed_short, R.string.weekday_thu_short, R.string.weekday_fri_short, R.string.weekday_sat_short};
    private static final int[] WEEKDAY_FULL = {R.string.weekday_sun, R.string.weekday_mon, R.string.weekday_tue, R.string.weekday_wed, R.string.weekday_thu, R.string.weekday_fri, R.string.weekday_sat};

    private ArrayList<StudyCard> cards = new ArrayList<>();
    private LinearLayout content, grid;
    private FrameLayout mainArea;
    private AdView banner;
    private AdSize bannerAdSize;
    private InterstitialAd interstitial;
    private boolean openingStudy = false;
    private final ArrayList<NativeAd> nativeAds = new ArrayList<>();
    private final ArrayList<FrameLayout> nativeAdSlots = new ArrayList<>();
    private final Handler adHandler = new Handler(Looper.getMainLooper());
    private final Set<FrameLayout> nativeAdsLoading = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<FrameLayout> nativeAdsLoadedSlots = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<FrameLayout, Integer> nativeAdRetryCounts = new IdentityHashMap<>();
    private final Map<FrameLayout, Runnable> nativeAdRetryCallbacks = new IdentityHashMap<>();
    private static final int MAX_NATIVE_AD_RETRIES = 2;
    private static final int MAX_STANDARD_AD_RETRIES = 3;
    private int renderGeneration = 0;
    private int columns = 2;
    private boolean adsInitialized = false;
    private boolean activityResumed = false;
    private boolean bannerRequestInFlight = false;
    private boolean bannerLoaded = false;
    private int bannerRetryCount = 0;
    private Runnable bannerRetryCallback;
    private boolean interstitialRequestInFlight = false;
    private int interstitialRetryCount = 0;
    private Runnable interstitialRetryCallback;
    private boolean exactAlarmPermissionGranted;
    private Dialog formDialog;
    private EditText nameField, resourceUrlField;
    private TextView fileLabel, timeLabel, minutesLabel;
    private LinearLayout resourceGroup, localGroup, colorOptions, weekdayOptions;
    private String formSource = "link";
    private Uri selectedLocalUri;
    private String selectedLocalName = "";
    private StudyCard editingCard;
    private int formColor;
    private String formTime = "19:00";
    private int formWeekdaysMask = StudyCard.ALL_WEEKDAYS;
    private int formMinutes = 20;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        AlarmReceiver.ensureChannel(this);
        cards = StudyStore.load(this);
        exactAlarmPermissionGranted = canScheduleExactAlarms();
        AlarmScheduler.scheduleAll(this, cards);
        buildHome(); renderLoadedCards();
        handleStartIntent(getIntent());
        mainArea.postOnAnimation(() -> {
            if (isFinishing() || isDestroyed()) return;
            MobileAds.initialize(this, status -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                adsInitialized = true;
                loadBanner();
                loadNativeAds();
                loadInterstitial();
            }));
        });
    }

    private void buildHome() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BG);
        AppLocale.applyLayoutDirection(this, root);
        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, Ui.dp(this,72)));
        mainArea = new FrameLayout(this); root.addView(mainArea, new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,94));
        scroll.addView(content, new ScrollView.LayoutParams(-1,-2));
        mainArea.addView(scroll, new FrameLayout.LayoutParams(-1,-1));
        TextView fab = Ui.text(this,"+",32,Color.WHITE,true); fab.setGravity(Gravity.CENTER); fab.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Ui.BLUE,Color.rgb(34,92,183)}));
        fab.setBackground(Ui.shape(Ui.BLUE, Color.rgb(101,174,255), 99, this)); fab.setElevation(Ui.dp(this,8));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(this,60),Ui.dp(this,60),Gravity.BOTTOM|Gravity.END);
        flp.setMargins(0,0,Ui.dp(this,22),Ui.dp(this,20)); mainArea.addView(fab,flp); fab.setOnClickListener(v -> showForm(null));
        root.addView(buildBanner(), new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(Ui.dp(this,16),Ui.dp(this,7),Ui.dp(this,16),Ui.dp(this,6));
        ImageView mark = new ImageView(this); mark.setImageResource(R.drawable.wirdi_splash); mark.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mark.setBackground(Ui.shape(Ui.SURFACE,Ui.LINE,12,this)); mark.setClipToOutline(true);
        bar.addView(mark,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this,getString(R.string.app_name),20,Ui.WHITE,true); TextView sub = Ui.text(this,getString(R.string.home_tagline),11,Ui.MUTED,false);
        brand.addView(title); brand.addView(sub);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,-2,1); blp.setMarginEnd(Ui.dp(this,10)); bar.addView(brand,blp);
        TextView clock = Ui.text(this,clockText(),13,Ui.SILVER,true); clock.setGravity(Gravity.CENTER); clock.setBackground(Ui.shape(Ui.SURFACE,Ui.LINE,30,this)); clock.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);
        bar.addView(clock,new LinearLayout.LayoutParams(-2,Ui.dp(this,36)));
        long untilNextMinute=60_000L-(System.currentTimeMillis()%60_000L);
        clock.postDelayed(new Runnable(){@Override public void run(){ if(!isFinishing()&&!isDestroyed()){clock.setText(clockText());clock.postDelayed(this,60_000L-(System.currentTimeMillis()%60_000L));} }},untilNextMinute);
        return bar;
    }

    private View buildBanner() {
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setGravity(Gravity.CENTER); wrap.setBackgroundColor(Color.rgb(8,12,19));
        TextView label = Ui.text(this,getString(R.string.ad_label),9,Ui.MUTED,false); label.setGravity(Gravity.CENTER); wrap.addView(label,new LinearLayout.LayoutParams(-1,Ui.dp(this,14)));
        banner = new AdView(this); banner.setAdUnitId(getString(R.string.admob_banner_id));
        bannerAdSize = currentAdaptiveBannerSize(); banner.setAdSize(bannerAdSize);
        banner.setAdListener(new AdListener(){
            @Override public void onAdLoaded(){bannerRequestInFlight=false;bannerLoaded=true;bannerRetryCount=0;cancelBannerRetry();android.util.Log.i("WaqtakAds","Banner loaded; response="+banner.getResponseInfo());}
            @Override public void onAdFailedToLoad(LoadAdError error){bannerRequestInFlight=false;bannerLoaded=false;android.util.Log.w("WaqtakAds","Banner failed: code="+error.getCode()+", domain="+error.getDomain()+", message="+error.getMessage()+", response="+error.getResponseInfo());scheduleBannerRetry();}
        });
        wrap.addView(banner,new LinearLayout.LayoutParams(-1,-2));
        return wrap;
    }

    private AdSize currentAdaptiveBannerSize() {
        float density=getResources().getDisplayMetrics().density;
        int widthDp=Math.max(1,(int)(getResources().getDisplayMetrics().widthPixels/density));
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this,widthDp);
    }

    private void loadBanner() {
        if(!adsInitialized || !activityResumed || banner==null || bannerRequestInFlight || bannerLoaded || bannerRetryCallback!=null)return;
        bannerRequestInFlight=true;
        banner.loadAd(new AdRequest.Builder().build());
    }

    private void scheduleBannerRetry() {
        if(!activityResumed || bannerRetryCount>=MAX_STANDARD_AD_RETRIES || bannerRetryCallback!=null)return;
        int retry=++bannerRetryCount;
        bannerRetryCallback=()->{bannerRetryCallback=null;if(activityResumed)loadBanner();};
        adHandler.postDelayed(bannerRetryCallback,adRetryDelayMillis(retry));
    }

    private void cancelBannerRetry() {
        if(bannerRetryCallback!=null){adHandler.removeCallbacks(bannerRetryCallback);bannerRetryCallback=null;}
    }

    private long adRetryDelayMillis(int retryCount) {
        return 15_000L*(1L<<Math.min(4,Math.max(0,retryCount-1)));
    }

    private void render() {
        cards = StudyStore.load(this);
        renderLoadedCards();
    }

    private void renderLoadedCards() {
        renderHero(); renderPermissions(); renderLanguageSettings(); renderSoundSettings(); renderCards();
    }

    private void renderLanguageSettings() {
        View old = content.findViewWithTag("language-settings");
        if (old != null) content.removeView(old);
        LinearLayout box = new LinearLayout(this);
        box.setTag("language-settings");
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this,14),Ui.dp(this,11),Ui.dp(this,14),Ui.dp(this,12));
        box.setBackground(Ui.gradient(Color.rgb(18,29,43),Color.rgb(12,19,29),Ui.LINE,16,this));
        TextView title = Ui.text(this,getString(R.string.language_title),14,Ui.WHITE,true);
        box.addView(title);
        TextView description = Ui.text(this,getString(R.string.language_description),11,Ui.MUTED,false);
        LinearLayout.LayoutParams descriptionLp = new LinearLayout.LayoutParams(-1,Ui.dp(this,25));
        descriptionLp.topMargin = Ui.dp(this,2);
        box.addView(description,descriptionLp);
        boolean currentLanguageIsArabic = "ar".equals(AppLocale.getLocale(this).getLanguage());
        Button change = Ui.button(this,getString(currentLanguageIsArabic
                ? R.string.language_switch_to_english : R.string.language_switch_to_arabic),Ui.BLUE,Color.WHITE);
        change.setContentDescription(getString(R.string.language_switch_accessibility));
        change.setOnClickListener(v -> AppLocale.setLanguage(this,currentLanguageIsArabic ? "en" : "ar"));
        LinearLayout.LayoutParams changeLp = new LinearLayout.LayoutParams(-1,Ui.dp(this,42));
        changeLp.topMargin = Ui.dp(this,5);
        box.addView(change,changeLp);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1,-2);
        boxLp.bottomMargin = Ui.dp(this,15);
        content.addView(box,boxLp);
    }

    private void renderSoundSettings() {
        View old = content.findViewWithTag("sound-settings"); if (old != null) content.removeView(old);
        LinearLayout box = new LinearLayout(this); box.setTag("sound-settings"); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this,14),Ui.dp(this,11),Ui.dp(this,14),Ui.dp(this,12));
        box.setBackground(Ui.gradient(Color.rgb(18,29,43),Color.rgb(12,19,29),Ui.LINE,16,this));
        TextView title = Ui.text(this,getString(R.string.ringtone_title),14,Ui.WHITE,true); box.addView(title);
        TextView description = Ui.text(this,getString(R.string.ringtone_description),11,Ui.MUTED,false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1,Ui.dp(this,25)); dlp.topMargin=Ui.dp(this,2); box.addView(description,dlp);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView current = Ui.text(this,AlarmReceiver.selectedSoundName(this),12,Ui.SILVER,true);
        current.setMaxLines(1); current.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(current,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));
        Button change = Ui.button(this,getString(R.string.change_ringtone),Ui.BLUE,Color.WHITE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2,Ui.dp(this,40)); clp.setMarginEnd(Ui.dp(this,8)); row.addView(change,clp);
        change.setOnClickListener(v->openRingtonePicker()); box.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=Ui.dp(this,15); content.addView(box,lp);
    }

    private void openRingtonePicker() {
        Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION|RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,getString(R.string.choose_ringtone))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,AlarmReceiver.selectedSoundUri(this));
        try { startActivityForResult(picker,REQ_RINGTONE); }
        catch (Exception e) { Toast.makeText(this,getString(R.string.ringtone_picker_error),Toast.LENGTH_LONG).show(); }
    }

    private void renderHero() {
        View old = content.findViewWithTag("hero"); int oldIndex=old==null?content.getChildCount():content.indexOfChild(old); if (old != null) content.removeView(old);
        LinearLayout hero = new LinearLayout(this); hero.setTag("hero"); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,18),Ui.dp(this,16));
        hero.setBackground(Ui.gradient(Color.rgb(23,40,62),Color.rgb(13,20,31),Ui.LINE,21,this));
        TextView label = Ui.text(this,cards.isEmpty()?getString(R.string.daily_plan):getString(R.string.upcoming_alarm),12,Ui.SILVER,false); hero.addView(label);
        if (cards.isEmpty()) {
            TextView msg = Ui.text(this,getString(R.string.empty_plan_prompt),17,Ui.WHITE,true); msg.setMaxLines(2);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1,Ui.dp(this,70)); mp.topMargin=Ui.dp(this,7); hero.addView(msg,mp);
            TextView action = Ui.text(this,getString(R.string.add_course),13,Ui.BLUE,true); hero.addView(action); action.setOnClickListener(v->showForm(null));
        } else {
            StudyCard next = getNextCard();
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.text(this,next.name,19,Ui.WHITE,true); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            String nextState = next.doneToday() ? getString(R.string.done_today) : getString(R.string.after_time, until(AlarmScheduler.nextAlarmTimeMillis(next)));
            TextView when = Ui.text(this, getString(R.string.upcoming_details, nextState, next.dailyMinutes), 12, Ui.MUTED, false);
            info.addView(name); LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(-1,Ui.dp(this,30)); wlp.topMargin=Ui.dp(this,5); info.addView(when,wlp);
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView time = Ui.text(this,fmtTime(next.scheduledTime),17,Ui.BLUE,true); time.setGravity(Gravity.CENTER); time.setBackground(Ui.shape(Color.rgb(10,22,39),Ui.LINE,15,this));
            row.addView(time,new LinearLayout.LayoutParams(Ui.dp(this,112),Ui.dp(this,52)));
            LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2); rlp.topMargin=Ui.dp(this,14); hero.addView(row,rlp);
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=Ui.dp(this,11); content.addView(hero,Math.min(oldIndex,content.getChildCount()),lp);
    }

    private void renderPermissions() {
        View old = content.findViewWithTag("permissions"); if (old != null) content.removeView(old);
        boolean notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        boolean exact = Build.VERSION.SDK_INT < 31 || ((android.app.AlarmManager)getSystemService(ALARM_SERVICE)).canScheduleExactAlarms();
        boolean full = Build.VERSION.SDK_INT < 34 || getSystemService(android.app.NotificationManager.class).canUseFullScreenIntent();
        boolean good = notif && exact && full;
        LinearLayout box = new LinearLayout(this); box.setTag("permissions"); box.setGravity(Gravity.CENTER_VERTICAL); box.setPadding(Ui.dp(this,12),Ui.dp(this,9),Ui.dp(this,12),Ui.dp(this,9));
        box.setBackground(Ui.shape(good?Color.rgb(11,37,36):Color.rgb(43,32,17),good?Color.rgb(25,91,82):Color.rgb(105,74,33),15,this));
        TextView indicator = Ui.text(this,good?"✓":"!",17,good?Ui.GREEN:Color.rgb(255,190,89),true); indicator.setGravity(Gravity.CENTER);
        box.addView(indicator,new LinearLayout.LayoutParams(Ui.dp(this,26),Ui.dp(this,30)));
        TextView status = Ui.text(this,good?getString(R.string.lockscreen_ready):getString(R.string.alarm_permission_needed),11,good?Ui.GREEN:Color.rgb(255,208,138),true); status.setMaxLines(2);
        box.addView(status,new LinearLayout.LayoutParams(0,Ui.dp(this,38),1));
        TextView setup = Ui.text(this,good?getString(R.string.check):getString(R.string.configure),11,Ui.WHITE,true); setup.setGravity(Gravity.CENTER); setup.setBackground(Ui.shape(good?Color.rgb(22,64,62):Color.rgb(88,57,24),Color.TRANSPARENT,12,this));
        box.addView(setup,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,34))); box.setOnClickListener(v->requestAlarmPermissions());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=Ui.dp(this,17); content.addView(box,lp);
    }

    private void renderCards() {
        if (grid != null) content.removeView(grid);
        renderGeneration++;
        clearNativeAdTracking();
        for(NativeAd ad:nativeAds) try{ad.destroy();}catch(Exception ignored){} nativeAds.clear();
        nativeAdSlots.clear();
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setTag("heading");
        TextView title = Ui.text(this,getString(R.string.study_plans),16,Ui.WHITE,true);
        TextView count = Ui.text(this,getString(R.string.plan_count_prefix)+cards.size(),11,Ui.MUTED,false); count.setGravity(Gravity.CENTER);
        heading.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,36),1)); heading.addView(count);
        View oldHeading=content.findViewWithTag("heading"); if(oldHeading!=null)content.removeView(oldHeading);
        LinearLayout.LayoutParams hlp=new LinearLayout.LayoutParams(-1,-2); hlp.bottomMargin=Ui.dp(this,8); content.addView(heading,hlp);
        grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL); grid.setTag("grid"); grid.setClipChildren(false); grid.setClipToPadding(false);
        LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(-1,-2); content.addView(grid,glp);
        int generation=renderGeneration;
        if(cards.isEmpty()) {
            TextView empty=Ui.text(this,getString(R.string.empty_courses),14,Ui.MUTED,false); empty.setGravity(Gravity.CENTER); empty.setBackground(Ui.shape(Ui.SURFACE,Ui.LINE,18,this));
            LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(-1,Ui.dp(this,120)); grid.addView(empty,elp); addNativeAdSlot(generation); return;
        }
        int widthPx=getResources().getDisplayMetrics().widthPixels-Ui.dp(this,32); int widthDp=(int)(widthPx/getResources().getDisplayMetrics().density);
        columns=widthDp>=780?4:(widthDp>=520?3:2);
        int gap=Ui.dp(this,9), cell=(widthPx-gap*(columns-1))/columns;
        LinearLayout row=newRow(); int used=0, rowItems=0;
        for(int i=0;i<cards.size();i++) {
            StudyCard c=cards.get(i); int span=Math.max(1,Math.min(columns,c.span));
            if(rowItems>0 && used+span>columns){addRow(row);row=newRow();used=0;rowItems=0;}
            int itemWidth=cell*span+gap*(span-1);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(itemWidth,itemWidth); if(rowItems>0) cp.setMarginStart(gap);
            row.addView(createCardTile(c),cp); used+=span; rowItems++;
            if((i+1)%5==0) {
                if(rowItems>0){addRow(row);row=newRow();used=0;rowItems=0;}
                addNativeAdSlot(generation);
            }
        }
        if(rowItems>0)addRow(row);
        if(cards.size()>0 && cards.size()<5) {
            addNativeAdSlot(generation);
        }
    }

    private void addNativeAdSlot(int generation) {
        FrameLayout slot=createNativeAdSlot();
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,Ui.dp(this,132));
        slp.topMargin=Ui.dp(this,4); slp.bottomMargin=Ui.dp(this,10);
        grid.addView(slot,slp); nativeAdSlots.add(slot);
        if(adsInitialized) loadNativeAd(slot,generation);
    }

    private void clearNativeAdTracking() {
        for(Runnable retry:nativeAdRetryCallbacks.values()) adHandler.removeCallbacks(retry);
        nativeAdRetryCallbacks.clear(); nativeAdRetryCounts.clear();
        nativeAdsLoading.clear(); nativeAdsLoadedSlots.clear();
    }

    private LinearLayout newRow(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.TOP);row.setClipChildren(false);return row;}
    private void addRow(LinearLayout row){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=Ui.dp(this,9);grid.addView(row,p);}

    private View createCardTile(StudyCard c) {
        FrameLayout outer=new FrameLayout(this); outer.setTag(c.id); int accent=Color.parseColor(StudyCard.COLORS[c.colorIndex]);
        outer.setBackground(Ui.gradient(Color.rgb(20,29,42),Color.rgb(13,19,29),Ui.LINE,18,this)); outer.setClipToOutline(true);
        LinearLayout column=new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(Ui.dp(this,10),Ui.dp(this,9),Ui.dp(this,10),Ui.dp(this,9));
        outer.addView(column,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=Ui.text(this,"◆",12,accent,true);mark.setGravity(Gravity.CENTER);top.addView(mark,new LinearLayout.LayoutParams(Ui.dp(this,23),Ui.dp(this,24)));
        TextView more=Ui.text(this,"⋮",22,Ui.MUTED,true);more.setGravity(Gravity.CENTER);top.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,30),Ui.dp(this,28)));
        LinearLayout.LayoutParams topText=new LinearLayout.LayoutParams(0,Ui.dp(this,27),1);topText.setMarginEnd(Ui.dp(this,5));
        TextView mini=Ui.text(this,c.weekdaysMask==StudyCard.ALL_WEEKDAYS?getString(R.string.all_days):getString(R.string.selected_days),10,Ui.MUTED,false);top.addView(mini,topText);
        column.addView(top); more.setOnClickListener(v->showCardMenu(v,c));
        TextView name=Ui.text(this,c.name,14,Ui.WHITE,true); name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,0,1);nlp.topMargin=Ui.dp(this,3);column.addView(name,nlp);
        LinearLayout chips=new LinearLayout(this);chips.setGravity(Gravity.CENTER_VERTICAL);chips.setOrientation(LinearLayout.HORIZONTAL);
        TextView time=Ui.text(this,fmtTime(c.scheduledTime),10,Ui.SILVER,true);time.setGravity(Gravity.CENTER);time.setBackground(Ui.shape(Ui.SURFACE2,Ui.LINE,22,this));time.setPadding(Ui.dp(this,7),0,Ui.dp(this,7),0);
        chips.addView(time,new LinearLayout.LayoutParams(-2,Ui.dp(this,23)));
        TextView mins=Ui.text(this,getString(R.string.minutes_abbreviation,c.dailyMinutes),10,Ui.MUTED,false);mins.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(-2,Ui.dp(this,23));mlp.setMarginEnd(Ui.dp(this,5));chips.addView(mins,mlp);
        if(c.doneToday()){TextView done=Ui.text(this,"✓",10,Ui.GREEN,true);done.setGravity(Gravity.CENTER);done.setBackground(Ui.shape(Color.rgb(13,48,41),Color.TRANSPARENT,20,this));chips.addView(done,new LinearLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,23)));}
        column.addView(chips);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setTag("card-progress");progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.SURFACE2));
        progress.setProgress((int)Math.min(100,c.elapsedToday*100/Math.max(1,c.dailyMinutes*60L)));LinearLayout.LayoutParams prp=new LinearLayout.LayoutParams(-1,Ui.dp(this,4));prp.topMargin=Ui.dp(this,7);column.addView(progress,prp);
        TextView start=Ui.text(this,c.doneToday()?getString(R.string.resume_course):getString(R.string.start_course),11,Color.WHITE,true);start.setTag("card-start");start.setGravity(Gravity.CENTER);start.setBackground(Ui.shape(accent,Color.TRANSPARENT,18,this));
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,Ui.dp(this,32));stp.topMargin=Ui.dp(this,7);column.addView(start,stp);start.setOnClickListener(v->openStudy(c));
        outer.setOnClickListener(v->showForm(c)); return outer;
    }

    private void showCardMenu(View anchor,StudyCard card) {
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add(0,1,0,getString(R.string.edit_course));menu.getMenu().add(0,2,1,getString(card.span==1?R.string.expand_card:R.string.restore_card_size));menu.getMenu().add(0,3,2,getString(R.string.delete_course));
        menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)showForm(card);else if(item.getItemId()==2){card.span=card.span==1?columns:1;saveCards();render();}else if(item.getItemId()==3)confirmDelete(card);return true;});menu.show();
    }

    private void refreshAfterStudy() {
        cards=StudyStore.load(this);
        renderHero();
        if(grid==null)return;
        for(int i=0;i<grid.getChildCount();i++) {
            View child=grid.getChildAt(i);
            if(!(child instanceof LinearLayout))continue;
            LinearLayout row=(LinearLayout)child;
            for(int j=0;j<row.getChildCount();j++) {
                View tile=row.getChildAt(j); Object tag=tile.getTag();
                if(!(tag instanceof String))continue;
                StudyCard updated=null;
                for(StudyCard card:cards)if(card.id.equals(tag)){updated=card;break;}
                if(updated==null)continue;
                View replacement=createCardTile(updated); replacement.setTag(updated.id);
                ViewGroup.LayoutParams params=tile.getLayoutParams();
                row.removeViewAt(j); row.addView(replacement,j,params);
            }
        }
    }

    private FrameLayout createNativeAdSlot() {
        FrameLayout slot=new FrameLayout(this);slot.setBackground(Ui.gradient(Color.rgb(14,21,31),Color.rgb(10,15,23),Ui.LINE,16,this));
        LinearLayout inside=new LinearLayout(this);inside.setGravity(Gravity.CENTER_VERTICAL);inside.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10));
        TextView mark=Ui.text(this,getString(R.string.ad_label),10,Ui.SILVER,true);mark.setGravity(Gravity.CENTER);mark.setBackground(Ui.shape(Ui.SURFACE2,Ui.LINE,18,this));inside.addView(mark,new LinearLayout.LayoutParams(Ui.dp(this,49),Ui.dp(this,27)));
        TextView text=Ui.text(this,getString(R.string.sponsored_loading),11,Ui.MUTED,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginEnd(Ui.dp(this,9));inside.addView(text,lp);slot.addView(inside,new FrameLayout.LayoutParams(-1,-1));return slot;
    }

    private void loadNativeAd(FrameLayout slot,int generation) {
        if(!adsInitialized || isFinishing() || isDestroyed() || generation!=renderGeneration || slot.getParent()==null
                || nativeAdsLoading.contains(slot) || nativeAdsLoadedSlots.contains(slot) || nativeAdRetryCallbacks.containsKey(slot))return;
        nativeAdsLoading.add(slot);
        AdLoader loader=new AdLoader.Builder(this,getString(R.string.admob_native_id)).forNativeAd(ad->{
            nativeAdsLoading.remove(slot);
            if(isFinishing() || isDestroyed() || generation!=renderGeneration || slot.getParent()==null){ad.destroy();return;}
            nativeAdsLoadedSlots.add(slot); nativeAdRetryCounts.remove(slot);
            nativeAds.add(ad); slot.removeAllViews();
            android.util.Log.i("WaqtakAds","Native ad loaded; response="+ad.getResponseInfo());
            NativeAdView view=new NativeAdView(this); LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,12),Ui.dp(this,26),Ui.dp(this,12),Ui.dp(this,8));
            LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
            TextView adLabel=Ui.text(this,getString(R.string.ad_label),9,Ui.SILVER,true);adLabel.setGravity(Gravity.CENTER);adLabel.setBackground(Ui.shape(Ui.SURFACE2,Ui.LINE,18,this));top.addView(adLabel,new LinearLayout.LayoutParams(Ui.dp(this,43),Ui.dp(this,22)));
            TextView headline=Ui.text(this,"",12,Ui.WHITE,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,Ui.dp(this,28),1);hp.setMarginEnd(Ui.dp(this,9));top.addView(headline,hp);body.addView(top);
            TextView desc=Ui.text(this,"",10,Ui.MUTED,false);desc.setMaxLines(1);body.addView(desc,new LinearLayout.LayoutParams(-1,Ui.dp(this,21)));
            Button cta=Ui.button(this,getString(R.string.ad_learn_more),Ui.BLUE,Color.WHITE);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,Ui.dp(this,30));cp.topMargin=Ui.dp(this,3);body.addView(cta,cp);
            view.addView(body,new FrameLayout.LayoutParams(-1,-1));
            AdChoicesView choices=new AdChoicesView(this); FrameLayout.LayoutParams choicesLp=new FrameLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,22),Gravity.TOP|Gravity.END); view.addView(choices,choicesLp); view.setAdChoicesView(choices);
            view.setHeadlineView(headline);view.setBodyView(desc);view.setCallToActionView(cta);view.setNativeAd(ad);slot.addView(view,new FrameLayout.LayoutParams(-1,-1));
        }).withAdListener(new AdListener(){@Override public void onAdFailedToLoad(LoadAdError error){
            nativeAdsLoading.remove(slot);
            android.util.Log.w("WaqtakAds","Native ad failed: code="+error.getCode()+", domain="+error.getDomain()+", message="+error.getMessage()+", response="+error.getResponseInfo());
            if(isFinishing() || isDestroyed() || generation!=renderGeneration || slot.getParent()==null)return;
            TextView t=Ui.text(MainActivity.this,getString(R.string.ad_unavailable_retry),10,Ui.MUTED,false);t.setGravity(Gravity.CENTER);
            slot.removeAllViews();slot.addView(t,new FrameLayout.LayoutParams(-1,-1));
            Integer previous=nativeAdRetryCounts.get(slot); int retryCount=(previous==null?0:previous)+1;
            nativeAdRetryCounts.put(slot,retryCount);
            if(retryCount<=MAX_NATIVE_AD_RETRIES){
                Runnable retry=()->{nativeAdRetryCallbacks.remove(slot);if(slot.isAttachedToWindow())loadNativeAd(slot,generation);};
                nativeAdRetryCallbacks.put(slot,retry);adHandler.postDelayed(retry,15_000L*retryCount);
            }
        }}).withNativeAdOptions(new NativeAdOptions.Builder().build()).build();
        loader.loadAd(new AdRequest.Builder().build());
    }

    private void loadNativeAds() {
        int generation=renderGeneration;
        for(FrameLayout slot:new ArrayList<>(nativeAdSlots)) loadNativeAd(slot,generation);
    }

    private void loadInterstitial() {
        if(!adsInitialized || !activityResumed || isFinishing() || isDestroyed() || interstitial!=null || interstitialRequestInFlight || interstitialRetryCallback!=null)return;
        interstitialRequestInFlight=true;
        InterstitialAd.load(this,getString(R.string.admob_interstitial_id),new AdRequest.Builder().build(),new InterstitialAdLoadCallback(){
            @Override public void onAdLoaded(InterstitialAd ad){interstitialRequestInFlight=false;interstitialRetryCount=0;cancelInterstitialRetry();interstitial=ad;android.util.Log.i("WaqtakAds","Interstitial loaded; response="+ad.getResponseInfo());}
            @Override public void onAdFailedToLoad(LoadAdError error){interstitialRequestInFlight=false;interstitial=null;android.util.Log.w("WaqtakAds","Interstitial failed: code="+error.getCode()+", domain="+error.getDomain()+", message="+error.getMessage()+", response="+error.getResponseInfo());scheduleInterstitialRetry();}
        });
    }

    private void scheduleInterstitialRetry() {
        if(!activityResumed || isFinishing() || isDestroyed() || interstitialRetryCount>=MAX_STANDARD_AD_RETRIES || interstitialRetryCallback!=null)return;
        int retry=++interstitialRetryCount;
        interstitialRetryCallback=()->{interstitialRetryCallback=null;loadInterstitial();};
        adHandler.postDelayed(interstitialRetryCallback,adRetryDelayMillis(retry));
    }

    private void cancelInterstitialRetry() {
        if(interstitialRetryCallback!=null){adHandler.removeCallbacks(interstitialRetryCallback);interstitialRetryCallback=null;}
    }

    private void preloadInterstitial() { loadInterstitial(); }

    private StudyCard getNextCard() {
        StudyCard best=cards.get(0);long bestAt=AlarmScheduler.nextAlarmTimeMillis(best);
        for(StudyCard c:cards){long at=AlarmScheduler.nextAlarmTimeMillis(c);if(at<bestAt){best=c;bestAt=at;}}
        return best;
    }
    private String until(long alarmAt){try{long minutes=Math.max(0,(alarmAt-System.currentTimeMillis()+59_999L)/60_000L);long days=minutes/1440;long hours=(minutes%1440)/60;long remainder=minutes%60;if(days>0)return getResources().getQuantityString(R.plurals.days_count,(int)days,(int)days);if(hours==0)return getString(R.string.minutes_abbreviation,remainder);return String.format(Locale.ROOT,getString(R.string.hours_minutes_short),hours,remainder);}catch(Exception e){return getString(R.string.soon);}}
    private String fmtTime(String hm){try{String[] p=hm.split(":");int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);java.util.Calendar c=java.util.Calendar.getInstance(AppLocale.getLocale(this));c.set(java.util.Calendar.HOUR_OF_DAY,h);c.set(java.util.Calendar.MINUTE,m);c.set(java.util.Calendar.SECOND,0);return new SimpleDateFormat("h:mm a",AppLocale.getLocale(this)).format(c.getTime());}catch(Exception e){return hm;}}
    private String clockText(){return new SimpleDateFormat("h:mm a",AppLocale.getLocale(this)).format(new Date());}

    private void requestAlarmPermissions() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);return;}
        if(Build.VERSION.SDK_INT>=31){android.app.AlarmManager am=(android.app.AlarmManager)getSystemService(ALARM_SERVICE);if(am!=null&&!am.canScheduleExactAlarms()){try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){openAppSettings();}return;}}
        if(Build.VERSION.SDK_INT>=34){android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);if(nm!=null&&!nm.canUseFullScreenIntent()){try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));}catch(Exception e){openAppSettings();}return;}}
        Toast.makeText(this,getString(R.string.alarm_permissions_enabled),Toast.LENGTH_SHORT).show();
    }
    private boolean canScheduleExactAlarms(){if(Build.VERSION.SDK_INT<31)return true;android.app.AlarmManager am=(android.app.AlarmManager)getSystemService(ALARM_SERVICE);return am!=null&&am.canScheduleExactAlarms();}
    private void openAppSettings(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==REQ_NOTIFICATIONS){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)requestAlarmPermissions();else Toast.makeText(this,getString(R.string.notification_permission_required),Toast.LENGTH_LONG).show();render();}}
    @Override protected void onResume(){super.onResume();activityResumed=true;if(banner!=null)banner.resume();boolean exactAlarmGranted=canScheduleExactAlarms();if(exactAlarmGranted!=exactAlarmPermissionGranted){exactAlarmPermissionGranted=exactAlarmGranted;cards=StudyStore.load(this);AlarmScheduler.scheduleAll(this,cards);}if(content!=null)renderPermissions();if(adsInitialized){loadBanner();loadNativeAds();if(interstitial==null)loadInterstitial();}}
    @Override protected void onPause(){activityResumed=false;if(banner!=null)banner.pause();super.onPause();}

    private void openStudy(StudyCard c){
        if(c==null||openingStudy||isFinishing()||isDestroyed())return;
        openingStudy=true;
        boolean eligible=AdFrequencyGate.recordUserActionAndShouldShow(this);
        if(!eligible||interstitial==null||!activityResumed){if(interstitial==null)loadInterstitial();launchStudy(c);return;}
        InterstitialAd ad=interstitial;interstitial=null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback(){
            @Override public void onAdShowedFullScreenContent(){AdFrequencyGate.markShown(MainActivity.this);}
            @Override public void onAdDismissedFullScreenContent(){preloadInterstitial();launchStudy(c);}
            @Override public void onAdFailedToShowFullScreenContent(AdError error){android.util.Log.w("WaqtakAds","Pre-session interstitial failed to show: code="+error.getCode()+", message="+error.getMessage());preloadInterstitial();launchStudy(c);}
        });
        try{ad.show(this);}catch(Exception error){android.util.Log.w("WaqtakAds","Pre-session interstitial exception; continuing without blocking the session.",error);preloadInterstitial();launchStudy(c);}
    }
    private void launchStudy(StudyCard c){openingStudy=false;if(isFinishing()||isDestroyed())return;Intent i=new Intent(this,StudyActivity.class).putExtra(AlarmScheduler.EXTRA_ID,c.id);startActivityForResult(i,REQ_STUDY);}
    private void handleStartIntent(Intent i){if(i==null)return;String id=i.getStringExtra("start_card_id");if(id!=null&&!id.isEmpty()){i.removeExtra("start_card_id");StudyCard c=StudyStore.find(this,id);if(c!=null)mainArea.postDelayed(()->openStudy(c),180);}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleStartIntent(intent);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_RINGTONE){if(resultCode==RESULT_OK&&data!=null){Uri picked=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);if(picked==null)picked=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);String title=getString(R.string.default_ringtone);try{Ringtone ringtone=RingtoneManager.getRingtone(this,picked);if(ringtone!=null)title=ringtone.getTitle(this);}catch(Exception ignored){}AlarmReceiver.saveSelectedSound(this,picked,title);renderSoundSettings();Toast.makeText(this,getString(R.string.ringtone_changed),Toast.LENGTH_SHORT).show();}return;}if(requestCode==REQ_VIDEO&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){selectedLocalUri=data.getData();try{getContentResolver().takePersistableUriPermission(selectedLocalUri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}selectedLocalName=displayName(selectedLocalUri);if(fileLabel!=null)fileLabel.setText(selectedLocalName);return;}if(requestCode==REQ_STUDY){refreshAfterStudy();preloadInterstitial();}}
    @Override public void onBackPressed(){new AlertDialog.Builder(this).setMessage(getString(R.string.exit_confirmation)).setNegativeButton(getString(R.string.stay),(d,w)->d.dismiss()).setPositiveButton(getString(R.string.exit),(d,w)->finish()).show();}

    private void showForm(StudyCard existing) {
        editingCard=existing; formSource=existing==null?"link":("local".equals(existing.sourceType)?"local":"link");formColor=existing==null?cards.size()%StudyCard.COLORS.length:existing.colorIndex;formTime=existing==null?"19:00":existing.scheduledTime;formWeekdaysMask=existing==null?StudyCard.ALL_WEEKDAYS:existing.weekdaysMask;formMinutes=existing==null?20:existing.dailyMinutes;
        selectedLocalUri=(existing!=null&&"local".equals(existing.sourceType)&&!existing.localUri.isEmpty())?Uri.parse(existing.localUri):null;
        selectedLocalName=existing==null?"":existing.localFileName;
        Dialog dialog=new Dialog(this);formDialog=dialog;
        ScrollView sc=new ScrollView(this);sc.setFillViewport(false);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(Ui.dp(this,20),Ui.dp(this,19),Ui.dp(this,20),Ui.dp(this,23));panel.setBackground(Ui.shape(Ui.SURFACE,Ui.LINE,22,this));sc.addView(panel);
        TextView handle=Ui.text(this,"━━━━━━━━",10,Ui.MUTED,false);handle.setGravity(Gravity.CENTER);panel.addView(handle,new LinearLayout.LayoutParams(-1,Ui.dp(this,17)));
        TextView heading=Ui.text(this,existing==null?getString(R.string.new_course):getString(R.string.edit_course),19,Ui.WHITE,true);LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,Ui.dp(this,43));h.bottomMargin=Ui.dp(this,8);panel.addView(heading,h);
        addLabel(panel,getString(R.string.course_name));nameField=edit(getString(R.string.course_name_hint),existing==null?"":existing.name,InputType.TYPE_CLASS_TEXT);panel.addView(nameField,fieldParams());
        addLabel(panel,getString(R.string.resource_source));LinearLayout switcher=new LinearLayout(this);switcher.setOrientation(LinearLayout.HORIZONTAL);
        Button linkButton=Ui.button(this,getString(R.string.web_link),Ui.SURFACE2,Ui.WHITE);Button local=Ui.button(this,getString(R.string.local_file),Ui.SURFACE2,Ui.WHITE);
        LinearLayout.LayoutParams sw=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);sw.setMarginStart(Ui.dp(this,4));switcher.addView(linkButton,sw);LinearLayout.LayoutParams sw2=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);sw2.setMarginEnd(Ui.dp(this,4));switcher.addView(local,sw2);panel.addView(switcher);
        String currentUrl=existing==null?"":(existing.resourceUrl.isEmpty()?existing.youtubeUrl:existing.resourceUrl);
        resourceGroup=new LinearLayout(this);resourceGroup.setOrientation(LinearLayout.VERTICAL);addLabel(resourceGroup,getString(R.string.resource_url));resourceUrlField=edit(getString(R.string.resource_url_example),currentUrl,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);resourceGroup.addView(resourceUrlField,fieldParams());
        TextView linkHint=Ui.text(this,getString(R.string.resource_url_help),10,Ui.MUTED,false);linkHint.setMaxLines(2);LinearLayout.LayoutParams linkHintLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,38));linkHintLp.topMargin=Ui.dp(this,2);resourceGroup.addView(linkHint,linkHintLp);panel.addView(resourceGroup);
        localGroup=new LinearLayout(this);localGroup.setOrientation(LinearLayout.VERTICAL);addLabel(localGroup,getString(R.string.video_file));fileLabel=Ui.text(this,selectedLocalName.isEmpty()?getString(R.string.choose_local_video):selectedLocalName,12,Ui.SILVER,false);fileLabel.setGravity(Gravity.CENTER);fileLabel.setBackground(Ui.shape(Ui.SURFACE2,Ui.LINE,12,this));panel.addView(localGroup);localGroup.addView(fileLabel,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));fileLabel.setOnClickListener(v->pickVideo());
        Runnable updateSource=()->{boolean link="link".equals(formSource);resourceGroup.setVisibility(link?View.VISIBLE:View.GONE);localGroup.setVisibility(link?View.GONE:View.VISIBLE);linkButton.setBackground(Ui.shape(link?Ui.BLUE:Ui.SURFACE2,Color.TRANSPARENT,12,this));local.setBackground(Ui.shape(!link?Ui.BLUE:Ui.SURFACE2,Color.TRANSPARENT,12,this));};
        linkButton.setOnClickListener(v->{formSource="link";updateSource.run();});local.setOnClickListener(v->{formSource="local";updateSource.run();});updateSource.run();
        addLabel(panel,getString(R.string.repeat_days));weekdayOptions=new LinearLayout(this);weekdayOptions.setOrientation(LinearLayout.HORIZONTAL);weekdayOptions.setGravity(Gravity.CENTER);for(int i=0;i<WEEKDAY_SHORT.length;i++)addWeekdayOption(i);panel.addView(weekdayOptions,new LinearLayout.LayoutParams(-1,Ui.dp(this,40)));
        TextView weekdayHint=Ui.text(this,getString(R.string.choose_at_least_one_day),10,Ui.MUTED,false);LinearLayout.LayoutParams weekdayHintLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,20));weekdayHintLp.topMargin=Ui.dp(this,2);panel.addView(weekdayHint,weekdayHintLp);
        addLabel(panel,getString(R.string.alarm_time));timeLabel=Ui.text(this,fmtTime(formTime)+"   ◷",15,Ui.WHITE,true);timeLabel.setGravity(Gravity.CENTER);timeLabel.setBackground(Ui.shape(Ui.SURFACE2,Ui.LINE,12,this));panel.addView(timeLabel,fieldParams());timeLabel.setOnClickListener(v->showTimePicker());
        TextView timingNotice=Ui.text(this,getString(R.string.alarm_timing_caveat),11,Color.rgb(255,208,138),false);timingNotice.setPadding(Ui.dp(this,10),Ui.dp(this,7),Ui.dp(this,10),Ui.dp(this,7));timingNotice.setBackground(Ui.shape(Color.rgb(43,32,17),Color.rgb(105,74,33),12,this));LinearLayout.LayoutParams timingNoticeLp=new LinearLayout.LayoutParams(-1,-2);timingNoticeLp.topMargin=Ui.dp(this,6);panel.addView(timingNotice,timingNoticeLp);
        addLabel(panel,getString(R.string.daily_study_duration));LinearLayout step=new LinearLayout(this);step.setGravity(Gravity.CENTER);Button minus=Ui.button(this,"−",Ui.SURFACE2,Ui.WHITE);Button plus=Ui.button(this,"+",Ui.SURFACE2,Ui.WHITE);minutesLabel=Ui.text(this,formMinutes+getString(R.string.minute_unit),16,Ui.WHITE,true);minutesLabel.setGravity(Gravity.CENTER);step.addView(minus,new LinearLayout.LayoutParams(Ui.dp(this,55),Ui.dp(this,42)));step.addView(minutesLabel,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));step.addView(plus,new LinearLayout.LayoutParams(Ui.dp(this,55),Ui.dp(this,42)));panel.addView(step);minus.setOnClickListener(v->{formMinutes=Math.max(5,formMinutes-5);minutesLabel.setText(formMinutes+getString(R.string.minute_unit));});plus.setOnClickListener(v->{formMinutes=Math.min(180,formMinutes+5);minutesLabel.setText(formMinutes+getString(R.string.minute_unit));});
        addLabel(panel,getString(R.string.card_color));colorOptions=new LinearLayout(this);colorOptions.setGravity(Gravity.CENTER_VERTICAL);for(int i=0;i<StudyCard.COLORS.length;i++)addColorOption(i);panel.addView(colorOptions,new LinearLayout.LayoutParams(-1,Ui.dp(this,42)));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));alp.topMargin=Ui.dp(this,15);panel.addView(actions,alp);
        Button cancel=Ui.button(this,getString(R.string.cancel),Ui.SURFACE2,Ui.SILVER);Button save=Ui.button(this,getString(R.string.save_course),Ui.BLUE,Color.WHITE);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-1,1);ap.setMarginStart(Ui.dp(this,5));actions.addView(cancel,ap);LinearLayout.LayoutParams ap2=new LinearLayout.LayoutParams(0,-1,1);ap2.setMarginEnd(Ui.dp(this,5));actions.addView(save,ap2);cancel.setOnClickListener(v->dialog.dismiss());save.setOnClickListener(v->saveForm(dialog));
        dialog.setContentView(sc);android.view.Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,590));w.setGravity(Gravity.BOTTOM);w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);android.view.WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.62f;w.setAttributes(lp);}dialog.show();if(w!=null)w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,Math.min(Ui.dp(this,610),getResources().getDisplayMetrics().heightPixels-Ui.dp(this,60)));
    }

    private void addLabel(LinearLayout parent,String label){TextView t=Ui.text(this,label,12,Ui.MUTED,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,28));lp.topMargin=Ui.dp(this,9);parent.addView(t,lp);}
    private void addWeekdayOption(int index){TextView day=Ui.text(this,getString(WEEKDAY_SHORT[index]),10,Ui.WHITE,true);day.setGravity(Gravity.CENTER);day.setContentDescription(getString(WEEKDAY_FULL[index]));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,36),1);lp.setMarginStart(Ui.dp(this,2));lp.setMarginEnd(Ui.dp(this,2));weekdayOptions.addView(day,lp);Runnable update=()->{boolean selected=(formWeekdaysMask&(1<<index))!=0;day.setSelected(selected);day.setContentDescription(getString(WEEKDAY_FULL[index])+(selected?getString(R.string.weekday_selected):getString(R.string.weekday_unselected)));day.setTextColor(selected?Color.WHITE:Ui.MUTED);day.setBackground(Ui.shape(selected?Ui.BLUE:Ui.SURFACE2,selected?Ui.BLUE:Ui.LINE,10,this));};update.run();day.setOnClickListener(v->{formWeekdaysMask^=1<<index;update.run();});}
    private EditText edit(String hint,String value,int input){EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setHint(hint);e.setTextSize(14);e.setTextColor(Ui.WHITE);e.setHintTextColor(Ui.MUTED);e.setInputType(input);e.setPadding(Ui.dp(this,13),0,Ui.dp(this,13),0);e.setBackground(Ui.shape(Ui.SURFACE2,Ui.LINE,12,this));return e;}
    private LinearLayout.LayoutParams fieldParams(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));lp.bottomMargin=Ui.dp(this,2);return lp;}
    private void addColorOption(int index){TextView dot=Ui.text(this,"",10,Ui.WHITE,true);dot.setGravity(Gravity.CENTER);dot.setBackground(Ui.shape(Color.parseColor(StudyCard.COLORS[index]),index==formColor?Ui.WHITE:Color.TRANSPARENT,99,this));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,29),Ui.dp(this,29));lp.setMarginStart(Ui.dp(this,8));colorOptions.addView(dot,lp);dot.setOnClickListener(v->{formColor=index;for(int i=0;i<colorOptions.getChildCount();i++)colorOptions.getChildAt(i).setBackground(Ui.shape(Color.parseColor(StudyCard.COLORS[i]),i==formColor?Ui.WHITE:Color.TRANSPARENT,99,this));});}
    private void showTimePicker(){String[] t=formTime.split(":");int h=19,m=0;try{h=Integer.parseInt(t[0]);m=Integer.parseInt(t[1]);}catch(Exception ignored){}new TimePickerDialog(this,(view,hour,minute)->{formTime=String.format(Locale.US,"%02d:%02d",hour,minute);timeLabel.setText(fmtTime(formTime)+"   ◷");},h,m,false).show();}
    private void pickVideo(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*");try{startActivityForResult(i,REQ_VIDEO);}catch(Exception e){Toast.makeText(this,getString(R.string.local_file_open_error),Toast.LENGTH_SHORT).show();}}
    private String displayName(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return getString(R.string.choose_video_from_device);}
    private void saveForm(Dialog dialog){
        String name=nameField.getText().toString().trim();if(name.isEmpty()){nameField.setError(getString(R.string.course_name_required));return;}
        String url=resourceUrlField.getText().toString().trim();YoutubeLink parsedLink="link".equals(formSource)?YoutubeLink.parse(url):null;String vid=parsedLink==null?"":parsedLink.videoId;String playlistId=parsedLink==null?"":parsedLink.playlistId;
        if("link".equals(formSource)&&!CourseLink.isValidHttpUrl(url)){resourceUrlField.setError(getString(R.string.invalid_resource_url));return;}
        if("local".equals(formSource)&&selectedLocalUri==null&&!(editingCard!=null&&!editingCard.localUri.isEmpty())){Toast.makeText(this,getString(R.string.local_video_required),Toast.LENGTH_SHORT).show();return;}
        if(formWeekdaysMask==0){Toast.makeText(this,getString(R.string.alarm_day_required),Toast.LENGTH_SHORT).show();return;}
        StudyCard c=editingCard==null?new StudyCard():editingCard;if(editingCard==null){c.id=UUID.randomUUID().toString();c.elapsedToday=0;c.streak=0;}
        c.name=name;c.sourceType=formSource;c.scheduledTime=formTime;c.weekdaysMask=formWeekdaysMask;c.dailyMinutes=formMinutes;c.colorIndex=formColor;
        if("link".equals(formSource)){c.resourceUrl=url;c.youtubeUrl=url;c.youtubeId=vid;c.youtubePlaylistId=playlistId;c.localUri="";c.localFileName="";}else{if(selectedLocalUri!=null){c.localUri=selectedLocalUri.toString();c.localFileName=selectedLocalName;}c.resourceUrl="";c.youtubeUrl="";c.youtubeId="";c.youtubePlaylistId="";}
        if(editingCard==null)cards.add(c);saveCards();AlarmScheduler.scheduleDaily(this,c);dialog.dismiss();render();Toast.makeText(this,editingCard==null?getString(R.string.course_added):getString(R.string.changes_saved),Toast.LENGTH_SHORT).show();
    }
    private void saveCards(){StudyStore.save(this,cards);}
    private void confirmDelete(StudyCard c){new AlertDialog.Builder(this).setTitle(getString(R.string.delete_course_confirmation)).setMessage(getString(R.string.delete_course_message,c.name)).setNegativeButton(getString(R.string.cancel),(d,w)->d.dismiss()).setPositiveButton(getString(R.string.delete),(d,w)->{cards.removeIf(x->x.id.equals(c.id));StudyStore.save(this,cards);AlarmScheduler.cancel(this,c.id);render();}).show();}

    @Override protected void onDestroy(){activityResumed=false;cancelBannerRetry();cancelInterstitialRetry();renderGeneration++;clearNativeAdTracking();if(banner!=null)banner.destroy();for(NativeAd ad:nativeAds)try{ad.destroy();}catch(Exception ignored){}nativeAds.clear();super.onDestroy();}
}
