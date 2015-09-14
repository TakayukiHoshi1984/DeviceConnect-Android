package org.deviceconnect.android.deviceplugin.theta.profile;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.theta.ThetaDeviceService;
import org.deviceconnect.android.deviceplugin.theta.roi.OmnidirectionalImage;
import org.deviceconnect.android.deviceplugin.theta.roi.RoiDeliveryContext;
import org.deviceconnect.android.deviceplugin.theta.utils.MixedReplaceMediaServer;
import org.deviceconnect.android.deviceplugin.theta.walkthrough.WalkthroughContext;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.profile.DConnectProfileConstants;
import org.restlet.Message;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
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
    implements DConnectProfileConstants, WalkthroughContext.EventListener,
               MixedReplaceMediaServer.ServerEventListener {

    private static final String TAG = "Walk";

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

    @Override
    public String getProfileName() {
        return PROFILE_NAME;
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
        Log.d(TAG, "onPostWalker: source=" + source + " width=" + width + " height=" + height + " fps=" + fps);

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

                        File extStore = Environment.getExternalStorageDirectory();
                        Log.d("Walk", "extStore: " + extStore.getAbsolutePath());

                        File dir = new File(extStore, source);
                        Log.d("Walk", "dir: " + dir.getAbsolutePath() + " isDir: " + dir.isDirectory());

                        String uri;
                        String key = dir.getCanonicalPath();
                        WalkthroughContext walkContext = mWalkContexts.get(key);
                        if (walkContext == null) {


                            String segment = UUID.randomUUID().toString();
                            uri = mServer.getUrl() + "/" + segment;
                            mServer.createMediaQueue(segment);

                            walkContext = new WalkthroughContext(getContext(), dir, width, height, fps.floatValue());
                            walkContext.setEventListener(ThetaWalkthroughProfile.this);
                            walkContext.setUri(uri);
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
        String uri = getURI(request);
        Integer deltaParam = parseInteger(request, "delta");
        if (deltaParam == null) {
            MessageUtils.setInvalidRequestParameterError(request, "delta is null.");
            return true;
        }
        if (uri == null) {
            MessageUtils.setInvalidRequestParameterError(response, "uri is null.");
            return true;
        }

        WalkthroughContext walkContext = findContextByUri(uri);
        int frameCount = walkContext.seek(deltaParam);
        if (frameCount > 0) {
            setResult(response, DConnectMessage.RESULT_OK);
            response.putExtra("count", frameCount);
        } else {
            MessageUtils.setUnknownError(response, "Failed to change the position of the walk-through.");
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
    public void onUpdate(final WalkthroughContext walkContext, final byte[] roi) {
        Log.d(TAG, "onUpdate: " + roi.length + " bytes");
        mServer.offerMedia(walkContext.getSegment(), roi);
    }

    @Override
    public void onComplete(final WalkthroughContext walkContext) {
        mServer.stopMedia(walkContext.getSegment());

        walkContext.stop();

        // Remove WalkThrough context.
        File dir = walkContext.getOmnidirectionalImageDirectory();
        String key = dir.getAbsolutePath();
        mWalkContexts.remove(key);

        Log.d(TAG, "ThetaWalkthrough.onComplete: contexts=" + mWalkContexts.size());
    }

    @Override
    public void onExpire(final WalkthroughContext walkContext) {
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
        if (target != null) {
            return target.getMedia();
        } else {
            return null;
        }
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
