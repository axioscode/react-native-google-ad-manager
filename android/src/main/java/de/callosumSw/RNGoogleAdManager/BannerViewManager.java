package de.callosumSw.RNGoogleAdManager;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.facebook.react.views.view.ReactViewGroup;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class BannerView extends ReactViewGroup {
    private final String LOG_TAG = "RNGoogleAdManager";

    public static final String BANNER = "BANNER";
    public static final String MEDIUM_RECTANGLE = "MEDIUM_RECTANGLE";

    public static final Map<String, AdSize> supportedAdSizesMap;
    static {
        Map<String, AdSize> map = new HashMap<String, AdSize>();
        map.put(BANNER, AdSize.BANNER);
        map.put(MEDIUM_RECTANGLE, AdSize.MEDIUM_RECTANGLE);

        supportedAdSizesMap = Collections.unmodifiableMap(map);
    }

    protected PublisherAdView adView;
    protected String adId = null;
    protected AdSize adSize = null;
    protected ArrayList<String> testDeviceIds = null;

    public BannerView (final Context context) {
        super(context);
        this.adView = new PublisherAdView(context);
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

    protected AdSize getGAMAdSizeFromString(String size){
        return supportedAdSizesMap.get(size);
    }

    protected void setListeners(){
        this.adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                // Code to be executed when an ad finishes loading.
                Log.d(LOG_TAG, "Ad loaded");

                final Context context = getContext();

                int width = adView.getAdSize().getWidthInPixels(context);
                int height = adView.getAdSize().getHeightInPixels(context);
                int left = adView.getLeft();
                int top = adView.getTop();
                adView.measure(width, height);
                adView.layout(left, top, left + width, top + height);

                ReactContext reactContext = (ReactContext)getContext();
                reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                        getId(),
                        "adLoaded",
                        null);
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                String errorMessage = getFailedToLoadReason(errorCode);
                // Code to be executed when an ad request fails.
                Log.d(LOG_TAG, "Ad failed to load. Reason: " + errorMessage);

                WritableMap event = Arguments.createMap();
                event.putString("errorMessage", errorMessage);
                ReactContext reactContext = (ReactContext)getContext();
                reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                        getId(),
                        "adFailedToLoad",
                        event);
            }
        });
    }

    public void loadAd(){
        PublisherAdRequest.Builder adRequestBuilder = new PublisherAdRequest.Builder();

        for(String testId : testDeviceIds){
            adRequestBuilder.addTestDevice(testId);
        }

        Log.d(LOG_TAG, "Requesting Banner " + this.adView.getAdUnitId() + " with size " + this.adView.getAdSize());

        PublisherAdRequest adRequest = adRequestBuilder.build();
        this.adView.loadAd(adRequest);
    }

    protected void loadAdIfPropsSet(){
        if(this.adId != null && this.adSize != null && testDeviceIds != null){
            this.setListeners();
            this.addView(this.adView);
            this.loadAd();
        }
    }
}

public class BannerViewManager extends ViewGroupManager<BannerView> {
    private static final String REACT_CLASS = "RNGAMBannerView";

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected BannerView createViewInstance(ThemedReactContext context) {
        return new BannerView(context);
    }

    @Override
    public void addView(BannerView parent, View child, int index) {
        throw new RuntimeException("RNGAMBannerView cannot have subviews");
    }

    @Override
    public Map getExportedCustomBubblingEventTypeConstants() {
        return MapBuilder.builder()
                .put("adLoaded",
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onAdLoaded")))
                .put("adFailedToLoad",
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onAdFailedToLoad")))
                .build();
    }

    @ReactProp(name = "adId")
    public void setAdId(BannerView view, @Nullable String adId) {
        view.adId = adId;
        view.adView.setAdUnitId(adId);
        view.loadAdIfPropsSet();
    }

    @ReactProp(name = "size")
    public void setSize(BannerView view, @Nullable String size) {
        AdSize adSize = view.getGAMAdSizeFromString(size);
        view.adSize = adSize;
        view.adView.setAdSizes(adSize);
        view.loadAdIfPropsSet();
    }

    @ReactProp(name = "testDeviceIds")
    public void setTestDeviceIds(BannerView view, ReadableArray testDeviceIds) {
        ArrayList<String> list = new ArrayList<>();

        for(int i = 0; i < testDeviceIds.size(); i++){
            String item = testDeviceIds.getString(i);
            list.add(item);
        }

        view.testDeviceIds = list;
        view.loadAdIfPropsSet();
    }
}
