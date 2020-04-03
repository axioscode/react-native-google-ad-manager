package de.callosumSw.RNGoogleAdManager;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.facebook.react.views.view.ReactViewGroup;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.doubleclick.AppEventListener;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import org.prebid.mobile.BannerAdUnit;
import org.prebid.mobile.ResultCode;
import org.prebid.mobile.OnCompleteListener;
import org.prebid.mobile.addendum.AdViewUtils;
import org.prebid.mobile.addendum.PbFindSizeError;

import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

class BannerView extends ReactViewGroup {
    public static final String LOG_TAG = "RNGoogleAdManager";

    public static final String AD_CLICKED = "AD_CLICKED";
    public static final String AD_CLOSED = "AD_CLOSED";
    public static final String AD_FAILED = "AD_FAILED";
    public static final String AD_LOADED = "AD_LOADED";
    public static final String AD_REQUEST = "AD_REQUEST";
    public static final String PROPS_SET = "PROPS_SET";

    protected PublisherAdView adView;
    protected String adId = null;
    protected ArrayList<AdSize> adSizes = null;
    protected Boolean isFluid = false;
    protected String prebidAdId = null;
    protected ArrayList<String> testDeviceIds = null;
    protected Map<String, Object> targeting = null;
    private LinearLayout fluidLayout = null;

    public BannerView (final Context context) {
        super(context);
    }

    public BannerView(Context context, Activity activity) {
        super(context);
//        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//            @Override
//            public void onGlobalLayout() {
//                requestLayout();
//            }
//        });
//        this.fluidLayout = (LinearLayout) activity.getLayoutInflater().inflate(R.layout.fluidad, null);
//        this.addView(fluidLayout);
    }

    private void sendIfPropsSet(){
        if(adId != null && adSizes != null){
            WritableMap event = Arguments.createMap();

            ReactContext reactContext = (ReactContext)getContext();
            reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                    getId(),
                    PROPS_SET,
                    event);
        }
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        post(measureAndLayout);
    }

    private final Runnable measureAndLayout = new Runnable() {
        @Override
        public void run() {
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
    };

    private void addAdView(){
        this.addView(this.adView);
    }

    private void createAdView(){
        try {
            final Context context = getContext();

//            if(isFluid){
////                PublisherAdView ad = (PublisherAdView) this.fluidLayout.findViewById(R.id.fluid_av_main);
//
////                this.adView = (PublisherAdView) this.fluidLayout.findViewById(R.id.fluid_av_main);
////                fluidLayout.removeView(ad);
//            } else{
//                this.adView = new PublisherAdView(context);
//            }
            this.adView = new PublisherAdView(context);

            this.adView.setAdUnitId(adId);



            if(isFluid){
                adView.setAdSizes(AdSize.FLUID);
            } else {
                AdSize []arr = this.adSizes.toArray(new AdSize[0]);
                this.adView.setAdSizes(arr);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private void destroyAdView(){
        if (this.adView != null) {
            this.adView.destroy();
        }
    }

    private void removeAdView(){
        this.removeView(this.adView);
    }

    private String getFailedToLoadReason(int code){
        switch (code){
            case PublisherAdRequest.ERROR_CODE_INTERNAL_ERROR:
                return "Internal Error";
            case PublisherAdRequest.ERROR_CODE_INVALID_REQUEST:
                return "Invalid Request";
            case PublisherAdRequest.ERROR_CODE_NETWORK_ERROR:
                return "Network error";
            case PublisherAdRequest.ERROR_CODE_NO_FILL:
                return "No Fill";
            default:
                return "Could not get message. Unknown code: " + code;
        }
    }

    public class BannerAppEventListener extends Activity implements AppEventListener {
        @Override
        public void onAppEvent(String name, String info) {
            switch (name) {
                case AD_CLICKED: {
                    Log.d(LOG_TAG, "Ad clicked");

                    WritableMap event = Arguments.createMap();
                    event.putString("url", info);

                    ReactContext reactContext = (ReactContext)getContext();
                    reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                            getId(),
                            AD_CLICKED,
                            event);

                    break;
                }

                case AD_CLOSED: {
                    Log.d(LOG_TAG, "Ad closed");

                    destroyAdView();

                    ReactContext reactContext = (ReactContext)getContext();
                    reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                            getId(),
                            AD_CLOSED,
                            null);

                    break;
                }
            }
        }
    }

    private void sendLoadEvent(int width, int height) {
        WritableMap event = Arguments.createMap();
        event.putInt("width", width);
        event.putInt("height", height);

        ReactContext reactContext = (ReactContext)getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                AD_LOADED,
                event);
    }

    private void handleLoad(String adServer) {
        try {
            Log.d(LOG_TAG, "Ad loaded. Server: " + adServer);

            final Context context = getContext();

            AdSize size = adView.getAdSize();

            int widthInPixel = size.getWidthInPixels(context);
            int width = size.getWidth();
            int heightInPixel = size.getHeightInPixels(context);
            int height = size.getHeight();
            int left = adView.getLeft();
            int top = adView.getTop();


            adView.measure(width, height);
            adView.layout(left, top, left + widthInPixel, top + heightInPixel);
            sendLoadEvent(width, height);
//            Timer timer = new Timer();
//            timer.schedule(new TimerTask() {
//                public void run() {
////                    adView.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
////                            MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
////                    adView.layout(0, 0, adView.getMeasuredWidth(), adView.getMeasuredHeight());
//                    sendLoadEvent(adView.getMeasuredWidth(), adView.getMeasuredHeight());
//                }
//            }, 1000);

//            adView.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
//                    MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
//            adView.layout(0, 0, adView.getMeasuredWidth(), adView.getMeasuredHeight());
//            getViewTreeObserver().dispatchOnGlobalLayout();
////            Choreographer.getInstance().postFrameCallback(this);

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    void setupLayoutHack() {
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                manuallyLayoutChildren();
                getViewTreeObserver().dispatchOnGlobalLayout();
                Choreographer.getInstance().postFrameCallback(this);
//                handleLoad("GAM");
            }
        });
    }

    void manuallyLayoutChildren() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        }
    }


    private void setListeners(){
//        setupLayoutHack();
        ViewTreeObserver viewTreeObserver = this.adView.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
                @Override
                public void onDraw() {
                    int viewWidth = adView.getWidth();
                    int viewHeight = adView.getHeight();
                    if(viewHeight != 131){
                        System.out.println(viewHeight);
                    }
                }
            });

//            viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
//                @Override
//                public boolean onPreDraw() {
//                    int viewWidth = adView.getWidth();
//                    int viewHeight = adView.getHeight();
//                    System.out.println(viewHeight);
//                    return true;
//                }
//            });
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
//                    adView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    int viewWidth = adView.getWidth();
                    int viewHeight = adView.getHeight();
                    System.out.println(viewHeight);
                }
            });
//            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//                @Override
//                public void onGlobalLayout() {
////                    adView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
//                    int viewWidth = adView.getWidth();
//                    int viewHeight = adView.getHeight();
//                    System.out.println(viewHeight);
//                }
//            });
        }
        this.adView.setAppEventListener(new BannerAppEventListener());
        this.adView.setAdListener(new AdListener() {

            private void showToast(String message) {
                final Context context = getContext();
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                try {
                    if(!"".equals(prebidAdId)){
                        AdViewUtils.findPrebidCreativeSize(adView, new AdViewUtils.PbFindSizeListener() {
                            @Override
                            public void success(int width, int height) {
                                if(isFluid){
                                    adView.setAdSizes(AdSize.FLUID);
                                } else {
                                    adView.setAdSizes(new AdSize(width, height));
                                }
                                handleLoad("Prebid");
                            }

                            @Override
                            public void failure(@NonNull PbFindSizeError error) {
                                handleLoad("GAM");
                            }
                        });
                    } else {
                        handleLoad("GAM");
                    }
                } catch (Exception e) {

                }
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                try {
                    String errorMessage = getFailedToLoadReason(errorCode);

                    Log.d(LOG_TAG, "Ad failed to load. Reason: " + errorMessage);

                    destroyAdView();

                    WritableMap event = Arguments.createMap();
                    event.putString("errorMessage", errorMessage);
                    ReactContext reactContext = (ReactContext)getContext();
                    reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                        getId(),
                        AD_FAILED,
                        event);
                } catch (Exception e) {

                }
            }
        });
    }

    private void loadAd(){
        PublisherAdRequest.Builder adRequestBuilder = new PublisherAdRequest.Builder();

        for(String testId : testDeviceIds){
            adRequestBuilder.addTestDevice(testId);
        }

        for (Map.Entry<String, Object> entry : targeting.entrySet()) {
            String key = entry.getKey();
            ArrayList value =  (ArrayList) entry.getValue();

            adRequestBuilder.addCustomTargeting(key, value);
        }

        final PublisherAdRequest adRequest = adRequestBuilder.build();
        final String adUnitId = this.adView.getAdUnitId();
        final AdSize adSize = this.adView.getAdSize();

        WritableMap event = Arguments.createMap();

        ReactContext reactContext = (ReactContext)getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), AD_REQUEST, event);

        if(!"".equals(prebidAdId)){
            final String prebidAdUnitId = this.prebidAdId;

            BannerAdUnit bannerAdUnit = new BannerAdUnit(prebidAdUnitId, 300, 250);

            Log.d(LOG_TAG, "Prebid request with adunit id " + prebidAdUnitId);

            bannerAdUnit.fetchDemand(adRequest, new OnCompleteListener() {
                @Override
                public void onComplete(ResultCode resultCode) {
                    Log.d(LOG_TAG, "Prebid response code: " + resultCode);
                    Log.d(LOG_TAG, "GAM Banner request with adunit id " + adUnitId + " with size " + adSize);
                    BannerView.this.adView.loadAd(adRequest);
                }
            });
        } else {
            Log.d(LOG_TAG, "GAM Banner request with adunit id " + adUnitId + " with size " + adSize);
            this.adView.loadAd(adRequest);
        }
    }

    protected void addBannerView() {
        if(this.adView == null ){
            this.createAdView();
            this.setListeners();
        }
        this.addAdView();
    }

    protected void destroyBanner() {
        if(this.adView != null) {
            this.destroyAdView();
        }
    }

    protected void loadBanner() {
        if(this.adView != null) {
            final String _adUnitId = this.adView.getAdUnitId();

            if (!adId.equals(_adUnitId) && _adUnitId != null) {
                this.destroyAdView();
            }
        }

        if(this.adView == null) {
            this.createAdView();
            this.setListeners();
        }

        this.loadAd();
    }

    protected void removeBannerView() {
        if(this.adView != null) {
            this.removeAdView();
        }
    }

    protected void setIsFluid(Boolean fluid) {
        isFluid = fluid;
        if(this.adView != null) {
            if (isFluid) {
                adView.setAdSizes(AdSize.FLUID);
            } else {
                AdSize[] arr = adSizes.toArray(new AdSize[0]);
                this.adView.setAdSizes(arr);
            }
        }
    }

    protected void setAdUnitId() {
        if(this.adView == null){
            sendIfPropsSet();
        }
    }

    protected void setAdSizes() {
        if(this.adView != null) {
            if(isFluid){
                adView.setAdSizes(AdSize.FLUID);
            } else {
                AdSize[] arr = adSizes.toArray(new AdSize[0]);
                this.adView.setAdSizes(arr);
            }
        }
        sendIfPropsSet();
    }
}

public class BannerViewManager extends ViewGroupManager<BannerView> {
    private static final String REACT_CLASS = "RNGAMBannerView";

    public static final int COMMAND_ADD_BANNER_VIEW = 1;
    public static final int COMMAND_DESTROY_BANNER = 2;
    public static final int COMMAND_LOAD_BANNER = 3;
    public static final int COMMAND_REMOVE_BANNER_VIEW = 4;

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    public boolean needsCustomLayoutForChildren() {
        return true;
    }

    @Override
    protected BannerView createViewInstance(ThemedReactContext context) {
        return new BannerView(context, context.getCurrentActivity());
    }

    @Override
    public void addView(BannerView parent, View child, int index) {
        throw new RuntimeException("RNGAMBannerView cannot have subviews");
    }

    @Override
    public Map getExportedCustomBubblingEventTypeConstants() {
        return MapBuilder.builder()
                .put(BannerView.AD_CLICKED,
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onAdClicked")))
                .put(BannerView.AD_CLOSED,
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onAdClosed")))
                .put(BannerView.AD_FAILED,
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onAdFailedToLoad")))
                .put(BannerView.AD_LOADED,
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onAdLoaded")))
                .put(BannerView.AD_REQUEST,
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onAdRequest")))
                .put(BannerView.PROPS_SET,
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onPropsSet")))
                .build();
    }

    @ReactProp(name = "adId")
    public void setAdId(BannerView view, @Nullable String adId) {
        view.adId = adId;
        view.setAdUnitId();
    }

    @ReactProp(name = "adSizes")
    public void setSize(BannerView view, @Nullable ReadableArray adSizes) {
        try {
            ArrayList<AdSize> list = new ArrayList<>();

            for(int i = 0; i < adSizes.size(); i++){
                ReadableArray sizes = adSizes.getArray(i);
                Integer width = sizes.getInt(0);
                Integer height = sizes.getInt(1);
                AdSize adSize = new AdSize(width, height);
                list.add(adSize);
            }

            view.adSizes = list;
            view.setAdSizes();
        } catch (Exception e) {

        }
    }

    @ReactProp(name = "fluid")
    public void setFluid(BannerView view, @Nullable Boolean fluid) {
        view.setIsFluid(fluid);
    }

    @ReactProp(name = "prebidAdId")
    public void setPrebidAdId(BannerView view, @Nullable String prebidAdId) {
        view.prebidAdId = prebidAdId;
    }

    @ReactProp(name = "testDeviceIds")
    public void setTestDeviceIds(BannerView view, ReadableArray testDeviceIds) {
        ArrayList<String> list = new ArrayList<>();

        for(int i = 0; i < testDeviceIds.size(); i++){
            String item = testDeviceIds.getString(i);
            list.add(item);
        }

        view.testDeviceIds = list;
    }

    @ReactProp(name = "targeting")
    public void setTargeting(BannerView view, ReadableMap targeting) {
        view.targeting = targeting.toHashMap();
    }

    @Nullable
    @Override
    public Map<String, Integer> getCommandsMap() {
        return MapBuilder.of(
                "addBannerView", COMMAND_ADD_BANNER_VIEW,
                "destroyBanner", COMMAND_DESTROY_BANNER,
                "loadBanner", COMMAND_LOAD_BANNER,
                "removeBannerView", COMMAND_REMOVE_BANNER_VIEW
        );
    }

    @Override
    public void receiveCommand(BannerView view, int commandId, @Nullable ReadableArray args) {
        switch (commandId) {
            case COMMAND_ADD_BANNER_VIEW:
                view.addBannerView();
                break;

            case COMMAND_DESTROY_BANNER:
                view.destroyBanner();
                break;

            case COMMAND_LOAD_BANNER:
                view.loadBanner();
                break;

            case COMMAND_REMOVE_BANNER_VIEW:
                view.removeBannerView();
                break;
        }
    }
}
