package org.deviceconnect.android.deviceplugin.theta.profile;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.theta.ThetaDeviceService;
import org.deviceconnect.android.deviceplugin.theta.opengl.SphericalView;
import org.deviceconnect.android.deviceplugin.theta.utils.MixedReplaceMediaServer;
import org.deviceconnect.android.deviceplugin.theta.walkthrough.WalkthroughContext;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.profile.DConnectProfileConstants;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * THETA Walkthrough Profile.
 *
 */
public class ThetaWalkthroughProfile extends DConnectProfile
    implements DConnectProfileConstants, WalkthroughContext.EventListener, SphericalView.EventListener,
               MixedReplaceMediaServer.ServerEventListener {

    private static final String TAG = "Walk";
    private static final boolean DEBUG = false; // BuildConfig.DEBUG;

    public static final String PROFILE_NAME = "walkthrough";

    public static final String PARAM_SOURCE = "source";

    public static final String PARAM_WIDTH = "width";

    public static final String PARAM_HEIGHT = "height";

    public static final String PARAM_FPS = "fps";

    public static final String SERVICE_ID = "walker";

    public static final String SERVICE_NAME = "Warkthrough Service";

    private final Object lockObj = new Object();

    private MixedReplaceMediaServer mServer;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private Map<String, WalkthroughContext> mWalkContexts =
        Collections.synchronizedMap(new HashMap<String, WalkthroughContext>());

    private final SphericalView mSphericalView;

    public ThetaWalkthroughProfile(final SphericalView view) {
        mSphericalView = view;
    }

    public void destroy() {
        for (WalkthroughContext walkContext : mWalkContexts.values()) {
            mServer.stopMedia(walkContext.getSegment());
            walkContext.stop();
        }
        mWalkContexts.clear();
    }

    @Override
    public String getProfileName() {
        return PROFILE_NAME;
    }

    @Override
    public boolean onRequest(final Intent request, final Intent response) {
        String method = request.getAction();
        String interfaceName = getInterface(request);
        String attributeName = getAttribute(request);

        if (DEBUG) {
            Log.i(TAG, "onRequest: method = " + method + ", profile = " + getProfileName()
                + ", interface = " + interfaceName + ", attribute = " + attributeName);
        }

        return super.onRequest(request, response);
    }

    @Override
    protected boolean onPostRequest(final Intent request, final Intent response) {
        String interfaceName = getInterface(request);
        String attributeName = getAttribute(request);
        if (interfaceName == null || attributeName == null) {
            return onPostWalker(request, response);
        } else {
            MessageUtils.setUnknownAttributeError(response);
            return true;
        }
    }

    protected boolean onPostWalker(final Intent request, final Intent response) {
        final String source = getSource(request);
        final Integer width = getWidth(request);
        final Integer height = getHeight(request);
        final Double fps = getFps(request);
        final Double fovParam = parseDouble(request, "fov");
        final Boolean autoPlay = parseBoolean(request, "autoPlay");
        final Integer yawParam = parseInteger(request, "yaw");
        final Integer rollParam = parseInteger(request, "roll");
        final Integer pitchParam = parseInteger(request, "pitch");
        Log.d(TAG, "onPostWalker: source=" + source + " width=" + width + " height=" + height + " fps=" + fps + " fov=" + fovParam + " autoPlay=" + autoPlay);

        if (source == null) {
            MessageUtils.setInvalidRequestParameterError(response, "source is null.");
            return true;
        }
        if (width == null) {
            MessageUtils.setInvalidRequestParameterError(response, "width is null.");
            return true;
        }
        if (height == null) {
            MessageUtils.setInvalidRequestParameterError(response, "height is null.");
            return true;
        }
        if (fps == null) {
            MessageUtils.setInvalidRequestParameterError(response, "fps is null.");
            return true;
        }

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (lockObj) {
                        if (mServer == null) {
                            mServer = new MixedReplaceMediaServer();
                            mServer.setServerName("ThetaDevicePlugin Server");
                            mServer.setContentType("image/jpeg");
                            mServer.setServerEventListener(ThetaWalkthroughProfile.this);
                            mServer.start();
                        }

                        mSphericalView.resetCamera();
                        mSphericalView.setFrameRate(fps);
                        mSphericalView.setEventListener(ThetaWalkthroughProfile.this);

                        File extStore = Environment.getExternalStorageDirectory();
                        File dir = new File(extStore, source);
                        String uri;
                        String key = dir.getCanonicalPath();
                        WalkthroughContext walkContext = mWalkContexts.get(key);
                        if (walkContext == null) {

                            String segment = UUID.randomUUID().toString();
                            uri = mServer.getUrl() + "/" + segment;
                            mServer.createMediaQueue(segment);
                            mSphericalView.setKey(segment);

                            walkContext = new WalkthroughContext(getContext(), dir, fps.floatValue());
                            walkContext.setView(mSphericalView);
                            walkContext.setEventListener(ThetaWalkthroughProfile.this);
                            walkContext.setUri(uri);
                            if (fovParam != null) {
                                walkContext.setFOV(fovParam.floatValue());
                            }
                            if (autoPlay != null) {
                                walkContext.setAutoPlay(autoPlay.booleanValue());
                            }
                            int yaw = yawParam != null ? yawParam : 0;
                            int roll = rollParam != null ? rollParam : 0;
                            int pitch = pitchParam != null ? pitchParam : 0;
                            walkContext.rotate(yaw, roll, pitch);
                            walkContext.start();

                            mWalkContexts.put(key, walkContext);
                        } else {
                            uri = walkContext.getUri();
                        }

                        setResult(response, DConnectMessage.RESULT_OK);
                        response.putExtra(DConnectProfileConstants.PARAM_URI, uri);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    MessageUtils.setUnknownError(response, e.getMessage());
                }
                ((ThetaDeviceService) getContext()).sendResponse(response);
            }
        });
        return false;
    }

    @Override
    protected boolean onPutRequest(final Intent request, final Intent response) {
        String interfaceName = getInterface(request);
        String attributeName = getAttribute(request);
        if (interfaceName == null && attributeName == null) {
            return onPutWalker(request, response);
        } else {
            MessageUtils.setUnknownAttributeError(response);
            return true;
        }
    }

    protected boolean onPutWalker(final Intent request, final Intent response) {
        try {
            String uri = getURI(request);
            Integer deltaParam = parseInteger(request, "delta");
            Double fovParam = parseDouble(request, "fov");
            if (deltaParam == null) {
                MessageUtils.setInvalidRequestParameterError(request, "delta is null.");
                return true;
            }
            if (uri == null) {
                MessageUtils.setInvalidRequestParameterError(response, "uri is null.");
                return true;
            }

            WalkthroughContext walkContext = findContextByUri(uri);
            if (walkContext == null) {
                MessageUtils.setInvalidRequestParameterError(response, "the specified uri is unavailable: uri = " + uri);
                return true;
            }
            if (fovParam != null) {
                walkContext.setFOV(fovParam.floatValue());
            }
            if (Math.abs(deltaParam) > 0) {
                walkContext.seek(deltaParam);
            }
            setResult(response, DConnectMessage.RESULT_OK);
            response.putExtra("count", deltaParam);
        } catch (Throwable e) {
            Log.e(TAG, "ERROR: ", e);
        }
        return true;
    }

    @Override
    protected boolean onDeleteRequest(final Intent request, final Intent response) {
        String interfaceName = getInterface(request);
        String attributeName = getAttribute(request);
        if (interfaceName == null && attributeName == null) {
            return onDeleteWalker(request, response);
        } else {
            MessageUtils.setUnknownAttributeError(response);
            return true;
        }
    }

    protected boolean onDeleteWalker(final Intent request, final Intent response) {
        String uri = getURI(request);
        if (uri == null) {
            MessageUtils.setInvalidRequestParameterError(response, "uri is null.");
            return true;
        }
        WalkthroughContext walkContext = findContextByUri(uri);
        if (walkContext != null) {
            walkContext.stop();

            // Remove WalkThrough context.
            File dir = walkContext.getOmnidirectionalImageDirectory();
            String key = dir.getAbsolutePath();
            mWalkContexts.remove(key);

            mSphericalView.setEventListener(null);
        }
        setResult(response, DConnectMessage.RESULT_OK);
        return true;
    }

    public static String getSource(final Intent request) {
        return request.getStringExtra(PARAM_SOURCE);
    }

    public static Integer getWidth(final Intent request) {
        return parseInteger(request, PARAM_WIDTH);
    }

    public static Integer getHeight(final Intent request) {
        return parseInteger(request, PARAM_HEIGHT);
    }

    public static String getURI(final Intent request) {
        return request.getStringExtra(PARAM_URI);
    }

    public static Double getFps(final Intent request) {
        return parseDouble(request, PARAM_FPS);
    }

    @Override
    public void onUpdate(final String key, final byte[] roi) {
        if (DEBUG) {
            Log.d(TAG, "onUpdate: " + roi);
        }
        mServer.offerMedia(key, roi);
    }

    @Override
    public void onComplete(final WalkthroughContext walkContext) {
        if (DEBUG) {
            Log.d(TAG, "ThetaWalkthrough.onComplete: contexts=" + mWalkContexts.size());
        }
    }

    @Override
    public void onExpire(final WalkthroughContext walkContext) {
        if (DEBUG) {
            Log.d(TAG, "onExpire: segment=" + walkContext.getSegment());
        }
        mServer.stopMedia(walkContext.getSegment());

        walkContext.stop();

        // Remove WalkThrough context.
        File dir = walkContext.getOmnidirectionalImageDirectory();
        String key = dir.getAbsolutePath();
        mWalkContexts.remove(key);
    }

    @Override
    public byte[] onConnect(final MixedReplaceMediaServer.Request request) {
        final String uri = request.getUri();

        WalkthroughContext target = findContextByUri(uri);
        if (target != null && request.isGet()) {
            target.restartExpireTimer();
        }
        return null;
    }

    @Override
    public void onDisconnect(final MixedReplaceMediaServer.Request request) {
    }

    @Override
    public void onCloseServer() {
    }

    private WalkthroughContext findContextByUri(final String uri) {
        WalkthroughContext target = null;
        synchronized (lockObj) {
            for (Iterator<Map.Entry<String, WalkthroughContext>> it = mWalkContexts.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, WalkthroughContext> entry = it.next();
                WalkthroughContext walkContext = entry.getValue();
                if (uri.equals(walkContext.getUri())) {
                    target = walkContext;
                    break;
                }
            }
        }
        return target;
    }
}
