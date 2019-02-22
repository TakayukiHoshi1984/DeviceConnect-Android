package org.deviceconnect.android.deviceplugin.host.profile;

import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.externaldisplay.ExternalDisplayService;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.MultiWindowService;
import org.deviceconnect.android.deviceplugin.host.recorder.util.CapabilityUtil;
import org.deviceconnect.android.message.DevicePluginContext;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.profile.api.PostApi;
import org.deviceconnect.android.service.DConnectServiceProvider;
import org.deviceconnect.message.DConnectMessage;

import static org.deviceconnect.android.deviceplugin.host.HostDeviceService.DConnectServiceState.Offline;
import static org.deviceconnect.android.deviceplugin.host.HostDeviceService.DConnectServiceState.Online;

public class HostDeviceProfile extends DConnectProfile {

    private DevicePluginContext mDevicePluginContext;
    /**
     * コンストラクタ.
     */
    public HostDeviceProfile(DevicePluginContext devicePluginContext) {
        addApi(mPostDevicePairing);
        addApi(mDeleteDevicePairing);
        mDevicePluginContext = devicePluginContext;
    }
    @Override
    public String getProfileName() {
        return "device";
    }
    /**
     * 外部ディスプレイとマルチウィンドウとの接続を行う.
     */
    private PostApi mPostDevicePairing = new PostApi() {
        @Override
        public String getAttribute() {
            return "pairing";
        }
        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            DConnectServiceProvider provider = ((HostDeviceService) getContext()).getServiceProvider();
            ExternalDisplayService dService = (ExternalDisplayService) provider.getService(ExternalDisplayService.SERVICE_ID);
            if (dService == null) {
                dService = new ExternalDisplayService(mDevicePluginContext);
                provider.addService(dService);
            }
            boolean state = dService.connect();
            dService.setOnline(state);
            getHostDeviceService().setOnlineForExternalDisplay(dService.connect() ? Online : Offline);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // ユーザ補助機能が許可されているかを確認する.されていない場合は設定画面を開く.
                CapabilityUtil.checkMutiWindowCapability(getContext(), new Handler(Looper.getMainLooper()), new CapabilityUtil.Callback() {
                    @Override
                    public void onSuccess() {
                        MultiWindowService mwService = (MultiWindowService) provider.getService(MultiWindowService.SERVICE_ID);
                        if (mwService == null) {
                            mwService = new MultiWindowService(mDevicePluginContext);
                            provider.addService(mwService);
                        }
                        mwService.setOnline(true);
                        getHostDeviceService().setOnlineForMultiWindow(Online);
                        setResult(response, DConnectMessage.RESULT_OK);
                        sendResponse(response);
                    }

                    @Override
                    public void onFail() {
                        MessageUtils.setIllegalServerStateError(response,
                                "AvailabilityService permission not granted.");
                        sendResponse(response);
                    }
                });
                return false;
            }
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        }
    };

    /**
     * 外部ディスプレイとマルチウィンドウとの接続を解除する.
     */
    private DeleteApi mDeleteDevicePairing = new DeleteApi() {
        @Override
        public String getAttribute() {
            return "pairing";
        }
        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            DConnectServiceProvider provider = ((HostDeviceService) getContext()).getServiceProvider();
            ExternalDisplayService dService = (ExternalDisplayService) provider.getService(ExternalDisplayService.SERVICE_ID);
            boolean state = !dService.disconnectCanvasDisplay();
            dService.setOnline(state);
            getHostDeviceService().setOnlineForExternalDisplay(state ? Offline : Online);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                MultiWindowService mwService = (MultiWindowService) provider.getService(MultiWindowService.SERVICE_ID);
                mwService.setOnline(false);
                getHostDeviceService().setOnlineForMultiWindow(Offline);
            }
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        }
    };

    // HostDeviceServiceを取得する.
    private HostDeviceService getHostDeviceService() {
        return (HostDeviceService) getContext();
    }
}
