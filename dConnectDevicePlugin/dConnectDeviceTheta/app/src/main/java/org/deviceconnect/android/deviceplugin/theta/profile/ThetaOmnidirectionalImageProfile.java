/*
 ThetaOmnidirectionalImageProfile.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.profile;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Surface;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.theta.opengl.SphereRenderer;
import org.deviceconnect.android.deviceplugin.theta.roi.OmnidirectionalImage;
import org.deviceconnect.android.deviceplugin.theta.roi.RoiDeliveryContext;
import org.deviceconnect.android.deviceplugin.theta.utils.MixedReplaceMediaServer;
import org.deviceconnect.android.deviceplugin.theta.utils.Quaternion;
import org.deviceconnect.android.deviceplugin.theta.utils.Vector3D;
import org.deviceconnect.android.deviceplugin.theta.walkthrough.Overlay;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.OmnidirectionalImageProfile;
import org.deviceconnect.message.DConnectMessage;

import java.io.FileNotFoundException;
import java.io.IOException;
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
public class ThetaOmnidirectionalImageProfile extends OmnidirectionalImageProfile
    implements RoiDeliveryContext.OnChangeListener,
               MixedReplaceMediaServer.ServerEventListener,
               SensorEventListener {

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

    private Overlay mOverlay;

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
        requestView(response, serviceId, source, true);
        return true;
    }

    @Override
    protected boolean onPutView(final Intent request, final Intent response, final String serviceId,
                                final String source) {
        requestView(response, serviceId, source, false);
        return true;
    }

    private void requestView(final Intent response, final String serviceId,
                             final String source, final boolean isGet) {

        try {
            stopVrMode();
            startVrMode();

            OmnidirectionalImage omniImage = mOmniImages.get(source);
            if (omniImage == null) {
                String origin = getContext().getPackageName();
                omniImage = new OmnidirectionalImage(source, origin);
                mOmniImages.put(source, omniImage);
            }
            if (mOverlay == null) {
                mOverlay = new Overlay(getContext());
            }
            if (!mOverlay.isShow()) {
                mOverlay.show(omniImage.getData());
            }
            setResult(response, DConnectMessage.RESULT_OK);
        } catch (FileNotFoundException e) {
            MessageUtils.setInvalidRequestParameterError(response, "Image is not found: " + source);
        } catch (IOException e) {
            MessageUtils.setUnknownError(response, e.getMessage());
        }

//        mExecutor.execute(new Runnable() {
//            @Override
//            public void run() {
//                if (!checkServiceId(serviceId)) {
//                    MessageUtils.setNotFoundServiceError(response);
//                    ((ThetaDeviceService) getContext()).sendResponse(response);
//                    return;
//                }
//                try {
//                    synchronized (lockObj) {
//                        if (mServer == null) {
//                            mServer = new MixedReplaceMediaServer();
//                            mServer.setServerName("ThetaDevicePlugin Server");
//                            mServer.setContentType("image/jpeg");
//                            mServer.setServerEventListener(ThetaOmnidirectionalImageProfile.this);
//                            mServer.start();
//                        }
//                    }
//
//                    OmnidirectionalImage omniImage = mOmniImages.get(source);
//                    if (omniImage == null) {
//                        String origin = getContext().getPackageName();
//                        omniImage = new OmnidirectionalImage(source, origin);
//                        mOmniImages.put(source, omniImage);
//                    }
//
//                    RoiDeliveryContext roiContext = new RoiDeliveryContext(getContext(), omniImage);
//                    String segment = UUID.randomUUID().toString();
//                    String uri = mServer.getUrl() + "/" + segment;
//                    roiContext.setUri(uri);
//                    roiContext.setOnChangeListener(ThetaOmnidirectionalImageProfile.this);
//                    roiContext.changeRendererParam(RoiDeliveryContext.DEFAULT_PARAM, true);
//                    roiContext.renderWithBlocking();
//                    roiContext.startExpireTimer();
//                    mServer.createMediaQueue(segment);
//                    mRoiContexts.put(uri, roiContext);
//
//                    setResult(response, DConnectMessage.RESULT_OK);
//                    if (isGet) {
//                        setURI(response, uri + "?snapshot");
//                    } else {
//                        setURI(response, uri);
//                    }
//                } catch (MalformedURLException e) {
//                    MessageUtils.setInvalidRequestParameterError(response, "uri is malformed: " + source);
//                } catch (FileNotFoundException e) {
//                    MessageUtils.setInvalidRequestParameterError(response, "Image is not found: " + source);
//                } catch (IOException e) {
//                    MessageUtils.setUnknownError(response, e.getMessage());
//                } catch (Throwable e) {
//                    e.printStackTrace();
//                    MessageUtils.setUnknownError(response, e.getMessage());
//                }
//                ((ThetaDeviceService) getContext()).sendResponse(response);
//            }
//        });
    }

    private boolean startVrMode() {
        Context context = getContext();
        // Reset current rotation.
        mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));

        WindowManager windowMgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplayRotation = windowMgr.getDefaultDisplay().getRotation();

        SensorManager mSensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = mSensorMgr.getSensorList(Sensor.TYPE_ALL);
        if (sensors.size() == 0) {
            mLogger.warning("Failed to start VR mode: any sensor is NOT found.");
            return false;
        }
        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                mLogger.info("Started VR mode: GYROSCOPE sensor is found.");
                mSensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
                return true;
            }
        }
        mLogger.warning("Failed to start VR mode: GYROSCOPE sensor is NOT found.");
        return false;
    }

    private void stopVrMode() {
        Context context = getContext();
        SensorManager mSensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensorMgr.unregisterListener(this);
    }

    private static final float NS2S = 1.0f / 1000000000.0f;
    private long mLastEventTimestamp;
    private int mDisplayRotation;
    private Quaternion mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));

    @Override
    public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        // Nothing to do.
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        if (mLastEventTimestamp != 0) {
            float EPSILON = 0.000000001f;
            float[] vGyroscope = new float[3];
            float[] deltaVGyroscope = new float[4];
            Quaternion qGyroscopeDelta;
            float dT = (event.timestamp - mLastEventTimestamp) * NS2S;

            System.arraycopy(event.values, 0, vGyroscope, 0, vGyroscope.length);
            float tmp = vGyroscope[2];
            vGyroscope[2] = vGyroscope[0] * +1;
            vGyroscope[0] = tmp;

            float magnitude = (float) Math.sqrt(Math.pow(vGyroscope[0], 2)
                + Math.pow(vGyroscope[1], 2) + Math.pow(vGyroscope[2], 2));
            if (magnitude > EPSILON) {
                vGyroscope[0] /= magnitude;
                vGyroscope[1] /= magnitude;
                vGyroscope[2] /= magnitude;
            }

            float thetaOverTwo = magnitude * dT / 2.0f;
            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

            deltaVGyroscope[0] = sinThetaOverTwo * vGyroscope[0];
            deltaVGyroscope[1] = sinThetaOverTwo * vGyroscope[1];
            deltaVGyroscope[2] = sinThetaOverTwo * vGyroscope[2];
            deltaVGyroscope[3] = cosThetaOverTwo;

            float[] delta = new float[3];
            switch (mDisplayRotation) {
                case Surface.ROTATION_0:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[1];
                    delta[2] = deltaVGyroscope[2];
                    break;
                case Surface.ROTATION_90:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[2] * -1;
                    delta[2] = deltaVGyroscope[1];
                    break;
                case Surface.ROTATION_180:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[1] * -1;
                    delta[2] = deltaVGyroscope[2];
                    break;
                case Surface.ROTATION_270:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[2];
                    delta[2] = deltaVGyroscope[1] * -1;
                    break;
                default:
                    break;
            }

            qGyroscopeDelta = new Quaternion(deltaVGyroscope[3], new Vector3D(delta));

            mCurrentRotation = qGyroscopeDelta.multiply(mCurrentRotation);

            float[] qvOrientation = new float[4];
            qvOrientation[0] = mCurrentRotation.imaginary().x();
            qvOrientation[1] = mCurrentRotation.imaginary().y();
            qvOrientation[2] = mCurrentRotation.imaginary().z();
            qvOrientation[3] = mCurrentRotation.real();

            float[] rmGyroscope = new float[9];
            SensorManager.getRotationMatrixFromVector(rmGyroscope,
                qvOrientation);

            float[] vOrientation = new float[3];
            SensorManager.getOrientation(rmGyroscope, vOrientation);

            if (mOverlay != null && mOverlay.isShow()) {
                SphereRenderer renderer = mOverlay.getRenderer();
                SphereRenderer.Camera currentCamera = renderer.getCamera();
                SphereRenderer.CameraBuilder newCamera = new SphereRenderer.CameraBuilder(currentCamera);
                newCamera.rotate(mCurrentRotation);
                renderer.setCamera(newCamera.create());
            }

        }
        mLastEventTimestamp = event.timestamp;
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
                byte[] roi = roiContext.renderWithBlocking();
                mServer.offerMedia(roiContext.getSegment(), roi);
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
            target.startDeliveryTimer();
        }
        return target.getRoi();
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
    public void onUpdate(final RoiDeliveryContext roiContext, final byte[] roi) {
        mServer.offerMedia(roiContext.getSegment(), roi);
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

    private static interface DoubleParamRange {
        boolean validate(double value);
    }
}
