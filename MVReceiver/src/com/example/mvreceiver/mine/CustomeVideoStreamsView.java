package com.example.mvreceiver.mine;

import android.content.Context;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.intel.webrtc.base.EglBase;
import com.intel.webrtc.base.EglBase.ConfigType;
import com.intel.webrtc.base.RendererCommon;
import com.intel.webrtc.base.RendererCommon.RendererEvents;
import com.intel.webrtc.base.RendererCommon.ScalingType;
import com.intel.webrtc.base.Stream;

import org.webrtc.GlRectDrawer;
import org.webrtc.GlUtil;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRenderer.I420Frame;

import java.nio.ByteBuffer;

import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.params.BlackLevelPattern;

import javax.microedition.khronos.egl.EGLContext;

import static android.util.Log.*;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
/**
 * Created by mrq on 16-4-27.
 */
public class CustomeVideoStreamsView extends SurfaceView implements SurfaceHolder.Callback, Stream.VideoRendererInterface {

    private static final String TAG = "WooGeen-CustomeVideoStreamsView";
    private HandlerThread renderThread;
    private final Object handlerLock = new Object();
    private Handler renderThreadHandler;
    private MineEglBase eglBase;
    private GlRectDrawer drawer;
    private int[] yuvTextures = null;
    private final Object frameLock = new Object();
    private I420Frame pendingFrame;
    private final Object layoutLock = new Object();
    private int widthSpec;
    private int heightSpec;
    private int layoutWidth;
    private int layoutHeight;
    private int surfaceWidth;
    private int surfaceHeight;
    private int frameWidth;
    private int frameHeight;
    private int frameRotation;
    private ScalingType scalingType;
    private boolean mirror;
    private RendererEvents rendererEvents;
    private final Object statisticsLock;
    private int framesReceived;
    private int framesDropped;
    private int framesRendered;
    private long firstFrameTimeNs;
    private long renderTimeNs;
    private boolean blackFrame;
    private final Runnable renderFrameRunnable;

    public CustomeVideoStreamsView(Context context) {
        super(context);
        this.scalingType = ScalingType.SCALE_ASPECT_BALANCED;
        this.statisticsLock = new Object();
        this.blackFrame = false;
        this.renderFrameRunnable = new Runnable() {
            public void run() {
                CustomeVideoStreamsView.this.renderFrameOnRenderThread();
            }
        };
    }

    public CustomeVideoStreamsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.scalingType = ScalingType.SCALE_ASPECT_BALANCED;
        this.statisticsLock = new Object();
        this.blackFrame = false;
        this.renderFrameRunnable = new Runnable() {
            public void run() {
                CustomeVideoStreamsView.this.renderFrameOnRenderThread();
            }
        };
    }

    public void init(EGLContext sharedContext, RendererEvents rendererEvents) {
        if(this.renderThreadHandler != null) {
            throw new IllegalStateException("Already initialized");
        } else {
            Log.d("WooGeen-CustomeVideoStreamsView", "Initializing");
            this.rendererEvents = rendererEvents;
            this.renderThread = new HandlerThread("WooGeen-CustomeVideoStreamsView");
            this.renderThread.start();
            this.renderThreadHandler = new Handler(this.renderThread.getLooper());
            this.eglBase = new MineEglBase(sharedContext, MineEglBase.ConfigType.PLAIN);
            this.drawer = new GlRectDrawer();
            this.getHolder().addCallback(this);
        }
    }

    public void release() {
        Object var1 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler == null) {
                Log.d("WooGeen-CustomeVideoStreamsView", "Already released");
                return;
            }

            this.renderThreadHandler.postAtFrontOfQueue(new Runnable() {
                public void run() {
                    CustomeVideoStreamsView.this.drawer.release();
                    CustomeVideoStreamsView.this.drawer = null;
                    if(CustomeVideoStreamsView.this.yuvTextures != null) {
                        GLES20.glDeleteTextures(3, CustomeVideoStreamsView.this.yuvTextures, 0);
                        CustomeVideoStreamsView.this.yuvTextures = null;
                    }

                    CustomeVideoStreamsView.this.eglBase.release();
                    CustomeVideoStreamsView.this.eglBase = null;
                }
            });
            this.renderThreadHandler = null;
        }

        this.renderThread.quit();
        var1 = this.frameLock;
        synchronized(this.frameLock) {
            if(this.pendingFrame != null) {
                VideoRenderer.renderFrameDone(this.pendingFrame);
                this.pendingFrame = null;
            }
        }

        MineThreadUtils.joinUninterruptibly(this.renderThread);
        this.renderThread = null;
    }

    public void setMirror(boolean mirror) {
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.mirror = mirror;
        }
    }

    public void setScalingType(ScalingType scalingType) {
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.scalingType = scalingType;
        }
    }

    public void renderFrame(I420Frame frame) {
        Object var2 = this.statisticsLock;
        synchronized(this.statisticsLock) {
            ++this.framesReceived;
        }

        var2 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler == null) {
                Log.d("WooGeen-CustomeVideoStreamsView", "Dropping frame - SurfaceViewRenderer not initialized or already released.");
            } else {
                label66: {
                    Object var3 = this.frameLock;
                    synchronized(this.frameLock) {
                        if(this.pendingFrame != null) {
                            break label66;
                        }

                        this.updateFrameDimensionsAndReportEvents(frame);
                        this.pendingFrame = frame;
                        this.renderThreadHandler.post(this.renderFrameRunnable);
                    }

                    return;
                }
            }
        }

        var2 = this.statisticsLock;
        synchronized(this.statisticsLock) {
            ++this.framesDropped;
        }

        VideoRenderer.renderFrameDone(frame);
    }

    private Point getDesiredLayoutSize() {
        Object var1 = this.layoutLock;
        synchronized(this.layoutLock) {
            int maxWidth = getDefaultSize(2147483647, this.widthSpec);
            int maxHeight = getDefaultSize(2147483647, this.heightSpec);
            Point size = RendererCommon.getDisplaySize(this.scalingType, this.frameAspectRatio(), maxWidth, maxHeight);
            if(MeasureSpec.getMode(this.widthSpec) == 1073741824) {
                size.x = maxWidth;
            }

            if(MeasureSpec.getMode(this.heightSpec) == 1073741824) {
                size.y = maxHeight;
            }

            return size;
        }
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        Object size = this.layoutLock;
        synchronized(this.layoutLock) {
            this.widthSpec = widthSpec;
            this.heightSpec = heightSpec;
        }

        Point size1 = this.getDesiredLayoutSize();
        this.setMeasuredDimension(size1.x, size1.y);
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        Object var6 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.layoutWidth = right - left;
            this.layoutHeight = bottom - top;
        }

        this.runOnRenderThread(this.renderFrameRunnable);
    }

    public void surfaceCreated(final SurfaceHolder holder) {
        Log.d("WooGeen-CustomeVideoStreamsView", "Surface created");
        this.runOnRenderThread(new Runnable() {
            public void run() {
                CustomeVideoStreamsView.this.eglBase.createSurface(holder.getSurface());
                CustomeVideoStreamsView.this.eglBase.makeCurrent();
                GLES20.glPixelStorei(3317, 1);
            }
        });
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("WooGeen-CustomeVideoStreamsView", "Surface destroyed");
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.surfaceWidth = 0;
            this.surfaceHeight = 0;
        }

        this.runOnRenderThread(new Runnable() {
            public void run() {
                CustomeVideoStreamsView.this.eglBase.releaseSurface();
            }
        });
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("WooGeen-CustomeVideoStreamsView", "Surface changed: " + width + "x" + height);
        Object var5 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.surfaceWidth = width;
            this.surfaceHeight = height;
        }

        this.runOnRenderThread(this.renderFrameRunnable);
    }

    private void runOnRenderThread(Runnable runnable) {
        Object var2 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler != null) {
                this.renderThreadHandler.post(runnable);
            }

        }
    }

    private boolean checkConsistentLayout() {
        Object var1 = this.layoutLock;
        synchronized(this.layoutLock) {
            Point desiredLayoutSize = this.getDesiredLayoutSize();
            if(desiredLayoutSize.x == this.layoutWidth && desiredLayoutSize.y == this.layoutHeight) {
                return this.surfaceWidth == this.layoutWidth && this.surfaceHeight == this.layoutHeight;
            } else {
                Log.d("WooGeen-CustomeVideoStreamsView", "Requesting new layout with size: " + desiredLayoutSize.x + "x" + desiredLayoutSize.y);
                this.post(new Runnable() {
                    public void run() {
                        CustomeVideoStreamsView.this.requestLayout();
                    }
                });
                return false;
            }
        }
    }

    private void renderFrameOnRenderThread() {
        if(this.eglBase != null && this.eglBase.hasSurface()) {
            if(!this.checkConsistentLayout()) {
                GLES20.glClear(16384);
                this.eglBase.swapBuffers();
            } else {
                Object frame = this.layoutLock;
                synchronized(this.layoutLock) {
                    if(this.eglBase.surfaceWidth() != this.surfaceWidth || this.eglBase.surfaceHeight() != this.surfaceHeight) {
                        GLES20.glClear(16384);
                        this.eglBase.swapBuffers();
                    }
                }

                if(this.blackFrame) {
                    this.blackFrame = false;
                    GLES20.glClear(16384);
                    this.eglBase.swapBuffers();
                } else {
                    Object startTimeNs = this.frameLock;
                    I420Frame var15;
                    synchronized(this.frameLock) {
                        if(this.pendingFrame == null) {
                            return;
                        }

                        var15 = this.pendingFrame;
                        this.pendingFrame = null;
                    }

                    long var16 = System.nanoTime();
                    float[] samplingMatrix;
                    if(var15.yuvFrame) {
                        samplingMatrix = RendererCommon.verticalFlipMatrix();
                    } else {
                        SurfaceTexture texMatrix = (SurfaceTexture)var15.textureObject;
                        texMatrix.updateTexImage();
                        samplingMatrix = new float[16];
                        texMatrix.getTransformMatrix(samplingMatrix);
                    }

                    Object i = this.layoutLock;
                    float[] var17;
                    synchronized(this.layoutLock) {
                        float[] rotatedSamplingMatrix = RendererCommon.rotateTextureMatrix(samplingMatrix, (float)var15.rotationDegree);
                        float[] layoutMatrix = RendererCommon.getLayoutMatrix(this.mirror, this.frameAspectRatio(), (float)this.layoutWidth / (float)this.layoutHeight);
                        var17 = RendererCommon.multiplyMatrices(rotatedSamplingMatrix, layoutMatrix);
                    }

                    GLES20.glViewport(0, 0, this.surfaceWidth, this.surfaceHeight);
                    if(var15.yuvFrame) {
                        if(this.yuvTextures == null) {
                            this.yuvTextures = new int[3];

                            for(int var18 = 0; var18 < 3; ++var18) {
                                this.yuvTextures[var18] = GlUtil.generateTexture(3553);
                            }
                        }

                        this.drawer.uploadYuvData(this.yuvTextures, var15.width, var15.height, var15.yuvStrides, var15.yuvPlanes);
                        this.drawer.drawYuv(this.yuvTextures, var17);
                    } else {
                        this.drawer.drawOes(var15.textureId, var17);
                    }

                    this.eglBase.swapBuffers();
                    VideoRenderer.renderFrameDone(var15);
                    i = this.statisticsLock;
                    synchronized(this.statisticsLock) {
                        if(this.framesRendered == 0) {
                            this.firstFrameTimeNs = var16;
                        }

                        ++this.framesRendered;
                        this.renderTimeNs += System.nanoTime() - var16;
                        if(this.framesRendered % 300 == 0) {
                            this.logStatistics();
                        }

                    }
                }
            }
        } else {
            Log.d("WooGeen-CustomeVideoStreamsView", "No surface to draw on");
        }
    }

    private float frameAspectRatio() {
        Object var1 = this.layoutLock;
        synchronized(this.layoutLock) {
            return this.frameWidth != 0 && this.frameHeight != 0?(this.frameRotation % 180 == 0?(float)this.frameWidth / (float)this.frameHeight:(float)this.frameHeight / (float)this.frameWidth):0.0F;
        }
    }

    private void updateFrameDimensionsAndReportEvents(I420Frame frame) {
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            if(this.frameWidth != frame.width || this.frameHeight != frame.height || this.frameRotation != frame.rotationDegree) {
                if(this.rendererEvents != null) {
                    String id = this.getResources().getResourceEntryName(this.getId());
                    if(this.frameWidth == 0 || this.frameHeight == 0) {
                        Log.d("WooGeen-CustomeVideoStreamsView", "ID: " + id + ". Reporting first rendered frame.");
                        this.rendererEvents.onFirstFrameRendered();
                    }

                    Log.d("WooGeen-CustomeVideoStreamsView", "ID: " + id + ". Reporting frame resolution changed to " + frame.width + "x" + frame.height + " with rotation " + frame.rotationDegree);
                    this.rendererEvents.onFrameResolutionChanged(frame.width, frame.height, frame.rotationDegree);
                }

                this.frameWidth = frame.width;
                this.frameHeight = frame.height;
                this.frameRotation = frame.rotationDegree;
            }

        }
    }

    private void logStatistics() {
        Object var1 = this.statisticsLock;
        synchronized(this.statisticsLock) {
            Log.d("WooGeen-CustomeVideoStreamsView", "ID: " + this.getResources().getResourceEntryName(this.getId()) + ". Frames received: " + this.framesReceived + ". Dropped: " + this.framesDropped + ". Rendered: " + this.framesRendered);
            if(this.framesReceived > 0 && this.framesRendered > 0) {
                long timeSinceFirstFrameNs = System.nanoTime() - this.firstFrameTimeNs;
                Log.d("WooGeen-CustomeVideoStreamsView", "Duration: " + (int)((double)timeSinceFirstFrameNs / 1000000.0D) + " ms. FPS: " + (double)((float)this.framesRendered) * 1.0E9D / (double)timeSinceFirstFrameNs);
                Log.d("WooGeen-CustomeVideoStreamsView", "Average render time: " + (int)(this.renderTimeNs / (long)(1000 * this.framesRendered)) + " us.");
            }

        }
    }

    public void cleanFrame() {
        this.blackFrame = true;
        this.renderThreadHandler.post(this.renderFrameRunnable);
    }
}
