/*
 ThetaOmnidirectionalImageProfile.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import org.deviceconnect.android.deviceplugin.theta.ThetaDeviceService;
import org.deviceconnect.android.deviceplugin.theta.opengl.SphereRenderer;
import org.deviceconnect.android.deviceplugin.theta.opengl.SphericalView;
import org.deviceconnect.android.deviceplugin.theta.roi.OmnidirectionalImage;
import org.deviceconnect.android.deviceplugin.theta.roi.RoiDeliveryContext;
import org.deviceconnect.android.deviceplugin.theta.utils.MixedReplaceMediaServer;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.OmnidirectionalImageProfile;
import org.deviceconnect.message.DConnectMessage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Theta Omnidirectional Image Profile.
 *
 * @author NTT DOCOMO, INC.
 */
public class ThetaOmnidirectionalImageProfile extends OmnidirectionalImageProfile
    implements RoiDeliveryContext.OnChangeListener, MixedReplaceMediaServer.ServerEventListener,
        SphericalView.EventListener {

    /**
     * The service ID of ROI Image Service.
     */
    public static final String SERVICE_ID = "roi";

    /**
     * The name of ROI Image Service.
     */
    public static final String SERVICE_NAME = "ROI Image Service";

    private static final List<ParamDefinition> ROI_PARAM_DEFINITIONS;

    private final Object lockObj = new Object();

    private MixedReplaceMediaServer mServer;

    private Map<String, OmnidirectionalImage> mOmniImages =
        Collections.synchronizedMap(new HashMap<String, OmnidirectionalImage>());

    private Map<String, RoiDeliveryContext> mRoiContexts =
        Collections.synchronizedMap(new HashMap<String, RoiDeliveryContext>());

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SphericalView mSphericalView;

    public ThetaOmnidirectionalImageProfile(final SphericalView view) {
        mSphericalView = view;
    }

    static {
        List<ParamDefinition> def = new ArrayList<ParamDefinition>();
        def.add(new DoubleParamDefinition(PARAM_X, null));
        def.add(new DoubleParamDefinition(PARAM_Y, null));
        def.add(new DoubleParamDefinition(PARAM_Z, null));
        def.add(new DoubleParamDefinition(PARAM_ROLL, new DoubleParamRange() {
            @Override
            public boolean validate(final double v) {
                return 0.0 <= v && v < 360.0;
            }
        }));
        def.add(new DoubleParamDefinition(PARAM_YAW, new DoubleParamRange() {
            @Override
            public boolean validate(final double v) {
                return 0.0 <= v && v < 360.0;
            }
        }));
        def.add(new DoubleParamDefinition(PARAM_PITCH, new DoubleParamRange() {
            @Override
            public boolean validate(final double v) {
                return 0.0 <= v && v < 360.0;
            }
        }));
        def.add(new DoubleParamDefinition(PARAM_FOV, new DoubleParamRange() {
            @Override
            public boolean validate(final double v) {
                return 0.0 < v && v < 180.0;
            }
        }));
        def.add(new DoubleParamDefinition(PARAM_SPHERE_SIZE, new DoubleParamRange() {
            @Override
            public boolean validate(final double v) {
                return SphereRenderer.Z_NEAR < v && v < SphereRenderer.Z_FAR;
            }
        }));
        def.add(new DoubleParamDefinition(PARAM_WIDTH, new DoubleParamRange() {
            @Override
            public boolean validate(final double v) {
                return 0.0 < v;
            }
        }));
        def.add(new DoubleParamDefinition(PARAM_HEIGHT, new DoubleParamRange() {
            @Override
            public boolean validate(final double v) {
                return 0.0 < v;
            }
        }));
        def.add(new BooleanParamDefinition(PARAM_STEREO));
        def.add(new BooleanParamDefinition(PARAM_VR));
        ROI_PARAM_DEFINITIONS = def;
    }

    @Override
    protected boolean onGetView(final Intent request, final Intent response, final String serviceId,
                                final String source) {
        requestView(request, response, serviceId, source, true);
        return false;
    }

    @Override
    protected boolean onPutView(final Intent request, final Intent response, final String serviceId,
                                final String source) {
        requestView(request, response, serviceId, source, false);
        return false;
    }

    private void requestView(final Intent request, final Intent response, final String serviceId,
                             final String source, final boolean isGet) {

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (!checkServiceId(serviceId)) {
                    MessageUtils.setNotFoundServiceError(response);
                    ((ThetaDeviceService) getContext()).sendResponse(response);
                    return;
                }

                final Integer sourceWidth = getSourceWidth(request);
                final Integer sourceHeight = getSourceHeight(request);
                if (sourceWidth != null && sourceWidth < 0) {
                    MessageUtils.setInvalidRequestParameterError(response, "sourceWidth is negative.");
                    return;
                }
                if (sourceHeight != null && sourceHeight < 0) {
                    MessageUtils.setInvalidRequestParameterError(response, "sourceHeight is negative.");
                    return;
                }

                try {
                    synchronized (lockObj) {
                        if (mServer == null) {
                            mServer = new MixedReplaceMediaServer();
                            mServer.setServerName("ThetaDevicePlugin Server");
                            mServer.setContentType("image/jpeg");
                            mServer.setServerEventListener(ThetaOmnidirectionalImageProfile.this);
                            mServer.start();
                        }
                    }

                    mSphericalView.resetCamera();
                    mSphericalView.setFrameRate(10.0);
                    mSphericalView.setEventListener(ThetaOmnidirectionalImageProfile.this);

                    OmnidirectionalImage omniImage = mOmniImages.get(source);
                    if (omniImage == null) {
                        String origin = getContext().getPackageName();
                        omniImage = new OmnidirectionalImage(source, origin, sourceWidth, sourceHeight);
                        mOmniImages.put(source, omniImage);
                    }

                    RoiDeliveryContext roiContext = new RoiDeliveryContext(getContext(), omniImage);
                    String segment = UUID.randomUUID().toString();
                    String uri = mServer.getUrl() + "/" + segment;
                    mSphericalView.setKey(segment);

                    roiContext.setUri(uri);
                    roiContext.setSphericalView(mSphericalView);
                    roiContext.setOnChangeListener(ThetaOmnidirectionalImageProfile.this);
                    roiContext.changeRendererParam(RoiDeliveryContext.DEFAULT_PARAM, true);
                    roiContext.startExpireTimer();
                    mServer.createMediaQueue(segment);
                    mRoiContexts.put(uri, roiContext);

                    setResult(response, DConnectMessage.RESULT_OK);
                    if (isGet) {
                        setURI(response, uri + "?snapshot");
                    } else {
                        setURI(response, uri);
                    }
                } catch (MalformedURLException e) {
                    MessageUtils.setInvalidRequestParameterError(response, "uri is malformed: " + source);
                } catch (FileNotFoundException e) {
                    MessageUtils.setInvalidRequestParameterError(response, "Image is not found: " + source);
                } catch (IOException e) {
                    MessageUtils.setUnknownError(response, e.getMessage());
                } catch (Throwable e) {
                    e.printStackTrace();
                    MessageUtils.setUnknownError(response, e.getMessage());
                }
                ((ThetaDeviceService) getContext()).sendResponse(response);
            }
        });
    }

    @Override
    protected boolean onDeleteView(final Intent request, final Intent response, final String serviceId,
                                   final String uri) {
        if (!checkServiceId(serviceId)) {
            MessageUtils.setNotFoundServiceError(response);
            return true;
        }
        if (uri == null) {
            MessageUtils.setInvalidRequestParameterError(response, "uri is not specified.");
            return true;
        }
        RoiDeliveryContext roiContext = mRoiContexts.remove(omitParameters(uri));
        if (roiContext != null) {
            roiContext.destroy();
            mServer.stopMedia(roiContext.getSegment());

            mSphericalView.setEventListener(null);
        }
        setResult(response, DConnectMessage.RESULT_OK);
        return true;
    }

    @Override
    protected boolean onPutSettings(final Intent request, final Intent response, final String serviceId,
                                    final String uri) {
        if (!checkServiceId(serviceId)) {
            MessageUtils.setNotFoundServiceError(response);
            return true;
        }
        if (uri == null) {
            MessageUtils.setInvalidRequestParameterError(response, "uri is not specified.");
            return true;
        }
        if (!validateRequest(request, response)) {
            return true;
        }
        final RoiDeliveryContext roiContext = mRoiContexts.get(omitParameters(uri));
        if (roiContext == null) {
            MessageUtils.setInvalidRequestParameterError(response, "The specified media is not found.");
            return true;
        }
        setResult(response, DConnectMessage.RESULT_OK);

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                RoiDeliveryContext.Param param = parseParam(request);
                roiContext.changeRendererParam(param, true);
            }
        });
        return true;
    }

    @Override
    public byte[] onConnect(final MixedReplaceMediaServer.Request request) {
        final String uri = request.getUri();
        final RoiDeliveryContext target = mRoiContexts.get(uri);
        if (target == null) {
            return null;
        }
        if (request.isGet()) {
            target.restartExpireTimer();
        } else {
            target.stopExpireTimer();
        }
        return null;
    }

    @Override
    public void onDisconnect(final MixedReplaceMediaServer.Request request) {
        if (!request.isGet()) {
            RoiDeliveryContext roiContext = mRoiContexts.remove(request.getUri());
            if (roiContext != null) {
                roiContext.destroy();
            }
        }
    }

    @Override
    public void onCloseServer() {
        mRoiContexts.clear();
    }

    @Override
    public void onUpdate(final String key, final byte[] roi) {
        mServer.offerMedia(key, roi);
    }

    @Override
    public void onExpire(final RoiDeliveryContext roiContext) {
        mServer.stopMedia(roiContext.getSegment());
        mRoiContexts.remove(roiContext.getUri());
        roiContext.destroy();
    }

    private boolean checkServiceId(final String serviceId) {
        if (TextUtils.isEmpty(serviceId)) {
            return false;
        }
        return serviceId.equals(SERVICE_ID);
    }

    private String omitParameters(final String uri) {
        if (uri == null) {
            return null;
        }
        int index = uri.indexOf("?");
        if (index >= 0) {
            return uri.substring(0, index);
        }
        return uri;
    }

    private RoiDeliveryContext.Param parseParam(final Intent request) {
        Double x = getX(request);
        Double y = getY(request);
        Double z = getZ(request);
        Double roll = getRoll(request);
        Double pitch = getPitch(request);
        Double yaw = getYaw(request);
        Double fov = getFOV(request);
        Double sphereSize = getSphereSize(request);
        Integer width = getWidth(request);
        Integer height = getHeight(request);
        Boolean stereo = getStereo(request);
        Boolean vr = getVR(request);

        RoiDeliveryContext.Param param = new RoiDeliveryContext.Param();
        if (x != null) {
            param.setCameraX(x);
        }
        if (y != null) {
            param.setCameraY(y);
        }
        if (z != null) {
            param.setCameraZ(z);
        }
        if (roll != null && !param.isVrMode()) {
            param.setCameraRoll(roll);
        }
        if (pitch != null && !param.isVrMode()) {
            param.setCameraPitch(pitch);
        }
        if (yaw != null && !param.isVrMode()) {
            param.setCameraYaw(yaw);
        }
        if (fov != null) {
            param.setCameraFov(fov);
        }
        if (sphereSize != null) {
            param.setSphereSize(sphereSize);
        }
        if (width != null) {
            param.setImageWidth(width);
        }
        if (height != null) {
            param.setImageHeight(height);
        }
        if (stereo != null) {
            param.setStereoMode(stereo);
        }
        if (vr != null) {
            param.setVrMode(vr);
        }
        return param;
    }

    private static boolean validateRequest(final Intent request, final Intent response) {
        Bundle extras = request.getExtras();
        if (extras == null) {
            MessageUtils.setUnknownError(response, "request has no parameter.");
            return false;
        }
        for (ParamDefinition definition : ROI_PARAM_DEFINITIONS) {
            if (!definition.validate(extras, response)) {
                return false;
            }
        }
        return true;
    }

    private static abstract class ParamDefinition {

        protected final String mName;

        protected final boolean mIsOptional;

        protected ParamDefinition(final String name, final boolean isOptional) {
            mName = name;
            mIsOptional = isOptional;
        }

        public abstract boolean validate(final Bundle extras, final Intent response);
    }

    private static class BooleanParamDefinition extends ParamDefinition {

        private static final String TRUE = "true";
        private static final String FALSE = "false";

        public BooleanParamDefinition(final String name, final boolean isOptional) {
            super(name, isOptional);
        }

        public BooleanParamDefinition(final String name) {
            this(name, true);
        }

        @Override
        public boolean validate(final Bundle extras, final Intent response) {
            Object value = extras.get(mName);
            if (value == null) {
                if (mIsOptional) {
                    return true;
                } else {
                    MessageUtils.setInvalidRequestParameterError(response, mName + " is not specified.");
                    return false;
                }
            }
            if (value instanceof Boolean) {
                return true;
            } else if (value instanceof String) {
                String stringValue = (String) value;
                if (!TRUE.equals(stringValue) && !FALSE.equals(stringValue)) {
                    MessageUtils.setInvalidRequestParameterError(response, "Format of " + mName + " is invalid.");
                    return false;
                }
                try {
                    Boolean.parseBoolean(stringValue);
                    return true;
                } catch (NumberFormatException e) {
                    // Nothing to do.
                }
            }
            MessageUtils.setInvalidRequestParameterError(response, "Format of " + mName + " is invalid.");
            return false;
        }
    }

    private static class DoubleParamDefinition extends ParamDefinition {

        private DoubleParamRange mRange;

        public DoubleParamDefinition(final String name, final boolean isOptional,
                                     final DoubleParamRange range) {
            super(name, isOptional);
            mRange = range;
        }

        public DoubleParamDefinition(final String name, final DoubleParamRange range) {
            this(name, true, range);
        }

        @Override
        public boolean validate(final Bundle extras, final Intent response) {
            Object value = extras.get(mName);
            if (value == null) {
                if (mIsOptional) {
                    return true;
                } else {
                    MessageUtils.setInvalidRequestParameterError(response, mName + " is not specified.");
                    return false;
                }
            }
            if (value instanceof Double) {
                if (validateRange(((Double) value).doubleValue())) {
                    return true;
                } else {
                    MessageUtils.setInvalidRequestParameterError(response, mName + " is out of range.");
                    return false;
                }
            } else if (value instanceof String) {
                try {
                    double doubleValue = Double.parseDouble((String) value);
                    if (validateRange(doubleValue)) {
                        return true;
                    } else {
                        MessageUtils.setInvalidRequestParameterError(response, mName + " is out of range.");
                        return false;
                    }
                } catch (NumberFormatException e) {
                    // Nothing to do.
                }
            }
            MessageUtils.setInvalidRequestParameterError(response, "Format of " + mName + " is invalid.");
            return false;
        }

        private boolean validateRange(double value) {
            if (mRange == null) {
                return true;
            }
            return mRange.validate(value);
        }
    }

    private interface DoubleParamRange {
        boolean validate(double value);
    }
}
