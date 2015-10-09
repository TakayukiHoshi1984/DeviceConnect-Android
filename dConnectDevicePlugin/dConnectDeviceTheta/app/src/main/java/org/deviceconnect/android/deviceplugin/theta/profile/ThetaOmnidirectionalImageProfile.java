/*
 ThetaOmnidirectionalImageProfile.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.deviceconnect.android.deviceplugin.theta.ThetaDeviceService;
import org.deviceconnect.android.deviceplugin.theta.opengl.Overlay;
import org.deviceconnect.android.deviceplugin.theta.opengl.SphereRenderer;
import org.deviceconnect.android.deviceplugin.theta.roi.OmnidirectionalImage;
import org.deviceconnect.android.deviceplugin.theta.roi.RoiDeliveryContext;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Theta Omnidirectional Image Profile.
 *
 * @author NTT DOCOMO, INC.
 */
public class ThetaOmnidirectionalImageProfile extends OmnidirectionalImageProfile {

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

    private Map<String, OmnidirectionalImage> mOmniImages =
        Collections.synchronizedMap(new HashMap<String, OmnidirectionalImage>());

    private RoiDeliveryContext mRoiContext;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private final Overlay mOverlay;

    private final Handler mHandler;

    public ThetaOmnidirectionalImageProfile(final Overlay overlay) {
        mOverlay = overlay;
        mHandler = new Handler(Looper.getMainLooper());
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
        def.add(new BooleanParamDefinition(PARAM_CALIBRATION));
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
                final Double fov = getFOV(request);
                final Boolean showZoomButton = parseBoolean(request, "showZoomButton");
                if (sourceWidth != null && sourceWidth < 0) {
                    MessageUtils.setInvalidRequestParameterError(response, "sourceWidth is negative.");
                    return;
                }
                if (sourceHeight != null && sourceHeight < 0) {
                    MessageUtils.setInvalidRequestParameterError(response, "sourceHeight is negative.");
                    return;
                }

                try {
                    OmnidirectionalImage omniImage = mOmniImages.get(source);
                    if (omniImage == null) {
                        String origin = getContext().getPackageName();
                        omniImage = new OmnidirectionalImage(source, origin, sourceWidth, sourceHeight);
                        mOmniImages.put(source, omniImage);
                    }
                    final Bitmap data = omniImage.getData();

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mOverlay.resetCamera();
                            if (mOverlay.isShow()) {
                                mOverlay.hide();
                            }
                            mOverlay.setShowZoomButton(showZoomButton != null ? showZoomButton : false);
                            mOverlay.show(data);

                            synchronized (lockObj) {
                                if (mRoiContext != null) {
                                    mRoiContext.destroy();
                                    mRoiContext = null;
                                }
                                mRoiContext = new RoiDeliveryContext(getContext(), mOverlay);
                                if (fov != null) {
                                    mRoiContext.setFOV(fov.floatValue());
                                }
                                mRoiContext.startVrMode();
                            }
                        }
                    });

                    setResult(response, DConnectMessage.RESULT_OK);
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
        synchronized (lockObj) {
            mOverlay.hide();
            if (mRoiContext != null) {
                mRoiContext.destroy();
                mRoiContext = null;
            }
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
        if (!validateRequest(request, response)) {
            return true;
        }

        synchronized (lockObj) {
            if (mRoiContext == null) {
                MessageUtils.setInvalidRequestParameterError(response, "The specified media is not found.");
                return true;
            }

            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    RoiDeliveryContext.Param param = parseParam(request);
                    synchronized (lockObj) {
                        if (mRoiContext != null) {
                            mRoiContext.changeRendererParam(param, true);
                        }
                    }
                }
            });
            setResult(response, DConnectMessage.RESULT_OK);
        }
        return true;
    }


    private boolean checkServiceId(final String serviceId) {
        if (TextUtils.isEmpty(serviceId)) {
            return false;
        }
        return serviceId.equals(SERVICE_ID);
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
        Boolean calibration = getCalibration(request);

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
        if (calibration != null) {
            param.setCalibration(calibration);
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
