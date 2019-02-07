package org.deviceconnect.android.deviceplugin.host.profile;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaRouter;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.externaldisplay.ExternalDisplayPresentation;
import org.deviceconnect.android.deviceplugin.host.externaldisplay.ExternalDisplayService;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.profile.api.PostApi;
import org.deviceconnect.android.service.DConnectService;
import org.deviceconnect.android.service.DConnectServiceProvider;
import org.deviceconnect.message.DConnectMessage;

import java.util.Map;

public class HostDeviceProfile extends DConnectProfile {

    /**
     * コンストラクタ.
     */
    public HostDeviceProfile() {
        addApi(mPostDevicePairing);
        addApi(mDeleteDevicePairing);
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
            ExternalDisplayService dService = (ExternalDisplayService)
                                provider.getService(ExternalDisplayService.SERVICE_ID);
            if (dService == null) {
                dService = new ExternalDisplayService(getContext());
                provider.addService(dService);
            }
            dService.setOnline(dService.connect());
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
            dService.setOnline(!dService.disconnect());
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        }
    };


}
