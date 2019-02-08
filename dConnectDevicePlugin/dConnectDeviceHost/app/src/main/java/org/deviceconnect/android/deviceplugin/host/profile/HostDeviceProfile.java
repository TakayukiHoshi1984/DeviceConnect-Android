package org.deviceconnect.android.deviceplugin.host.profile;

import android.content.Intent;
import android.os.Build;

import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.externaldisplay.ExternalDisplayService;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.MultiWindowService;
import org.deviceconnect.android.message.DevicePluginContext;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.profile.api.PostApi;
import org.deviceconnect.android.service.DConnectServiceProvider;
import org.deviceconnect.message.DConnectMessage;

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
            dService.setOnline(dService.connect());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                MultiWindowService mwService = (MultiWindowService) provider.getService(MultiWindowService.SERVICE_ID);
                if (mwService == null) {
                    mwService = new MultiWindowService(mDevicePluginContext);
                    provider.addService(mwService);
                }
                mwService.setOnline(true);
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
            dService.setOnline(!dService.disconnectCanvasDisplay());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                MultiWindowService mwService = (MultiWindowService) provider.getService(MultiWindowService.SERVICE_ID);
                mwService.setOnline(false);
            }
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        }
    };


}
