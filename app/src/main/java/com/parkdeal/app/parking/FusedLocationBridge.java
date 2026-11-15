package com.parkdeal.app.parking;

import ai.pleos.playground.location.fused.listener.FusedLocationListener;
import ai.pleos.playground.location.fused.model.LocationData;

/**
 * FusedLocation 리스너를 Java 로 구현한다. FusedLocation 1.1.3 의 Kotlin 메타데이터를 우리 Kotlin 2.0 컴파일러가
 * 읽지 못해 Kotlin 에서 override 가 인식되지 않는다("overrides nothing"). Java 는 바이트코드 시그니처만 본다.
 */
public final class FusedLocationBridge implements FusedLocationListener {
    public interface Callback {
        void onLocation(double lat, double lng);
        void onFailure(Exception e);
    }

    private final Callback callback;

    public FusedLocationBridge(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onChanged(LocationData location) {
        callback.onLocation(location.getLatitude(), location.getLongitude());
    }

    @Override
    public void onError(Exception e) {
        callback.onFailure(e);
    }
}
