package com.example.mvreceiver.mine;

import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
public class MineEglBase {
	private static final String TAG = "WooGeen-EglBase";
	private static final int EGL_OPENGL_ES2_BIT = 4;
	private static final int EGL_CONTEXT_CLIENT_VERSION = 12440;
	private static final int EGL_RECORDABLE_ANDROID = 12610;
	private final EGL10 egl;
	private EGLContext eglContext;
	private MineEglBase.ConfigType configType;
	private EGLConfig eglConfig;
	private EGLDisplay eglDisplay;
	private EGLSurface eglSurface;

	public MineEglBase() {
		this(EGL10.EGL_NO_CONTEXT, MineEglBase.ConfigType.PLAIN);
	}

	public MineEglBase(EGLContext sharedContext, MineEglBase.ConfigType configType) {
		this.eglSurface = EGL10.EGL_NO_SURFACE;
		this.egl = (EGL10)EGLContext.getEGL();
		this.configType = configType;
		this.eglDisplay = this.getEglDisplay();
		this.eglConfig = this.getEglConfig(this.eglDisplay, configType);
		this.eglContext = this.createEglContext(sharedContext, this.eglDisplay, this.eglConfig);
	}

	void createSurface(Surface surface) {
		this.createSurfaceInternal(surface);
	}

	void createSurface(SurfaceTexture surfaceTexture) {
		this.createSurfaceInternal(surfaceTexture);
	}

	private void createSurfaceInternal(Object surface) {
		if(!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)) {
			throw new IllegalStateException("Input must be either a Surface or SurfaceTexture");
		} else {
			this.checkIsNotReleased();
			if(this.configType == MineEglBase.ConfigType.PIXEL_BUFFER) {
				Log.e("WooGeen-EglBase", "This EGL context is configured for PIXEL_BUFFER, but uses regular Surface");
			}

			if(this.eglSurface != EGL10.EGL_NO_SURFACE) {
				throw new RuntimeException("Already has an EGLSurface");
			} else {
				int[] surfaceAttribs = new int[]{12344};
				this.eglSurface = this.egl.eglCreateWindowSurface(this.eglDisplay, this.eglConfig, surface, surfaceAttribs);
				if(this.eglSurface == EGL10.EGL_NO_SURFACE) {
					throw new RuntimeException("Failed to create window surface");
				}
			}
		}
	}

	void createDummyPbufferSurface() {
		this.createPbufferSurface(1, 1);
	}

	void createPbufferSurface(int width, int height) {
		this.checkIsNotReleased();
		if(this.configType != MineEglBase.ConfigType.PIXEL_BUFFER) {
			throw new RuntimeException("This EGL context is not configured to use a pixel buffer: " + this.configType);
		} else if(this.eglSurface != EGL10.EGL_NO_SURFACE) {
			throw new RuntimeException("Already has an EGLSurface");
		} else {
			int[] surfaceAttribs = new int[]{12375, width, 12374, height, 12344};
			this.eglSurface = this.egl.eglCreatePbufferSurface(this.eglDisplay, this.eglConfig, surfaceAttribs);
			if(this.eglSurface == EGL10.EGL_NO_SURFACE) {
				throw new RuntimeException("Failed to create pixel buffer surface");
			}
		}
	}

	public EGLContext getContext() {
		return this.eglContext;
	}

	boolean hasSurface() {
		return this.eglSurface != EGL10.EGL_NO_SURFACE;
	}

	int surfaceWidth() {
		int[] widthArray = new int[1];
		this.egl.eglQuerySurface(this.eglDisplay, this.eglSurface, 12375, widthArray);
		return widthArray[0];
	}

	int surfaceHeight() {
		int[] heightArray = new int[1];
		this.egl.eglQuerySurface(this.eglDisplay, this.eglSurface, 12374, heightArray);
		return heightArray[0];
	}

	void releaseSurface() {
		if(this.eglSurface != EGL10.EGL_NO_SURFACE) {
			this.egl.eglDestroySurface(this.eglDisplay, this.eglSurface);
			this.eglSurface = EGL10.EGL_NO_SURFACE;
		}

	}

	void checkIsNotReleased() {
		if(this.eglDisplay == EGL10.EGL_NO_DISPLAY || this.eglContext == EGL10.EGL_NO_CONTEXT || this.eglConfig == null) {
			throw new RuntimeException("This object has been released");
		}
	}

	void release() {
		this.checkIsNotReleased();
		this.releaseSurface();
		this.detachCurrent();
		this.egl.eglDestroyContext(this.eglDisplay, this.eglContext);
		this.egl.eglTerminate(this.eglDisplay);
		this.eglContext = EGL10.EGL_NO_CONTEXT;
		this.eglDisplay = EGL10.EGL_NO_DISPLAY;
		this.eglConfig = null;
	}

	void makeCurrent() {
		this.checkIsNotReleased();
		if(this.eglSurface == EGL10.EGL_NO_SURFACE) {
			throw new RuntimeException("No EGLSurface - can\'t make current");
		} else if(!this.egl.eglMakeCurrent(this.eglDisplay, this.eglSurface, this.eglSurface, this.eglContext)) {
			throw new RuntimeException("eglMakeCurrent failed");
		}
	}

	void detachCurrent() {
		if(!this.egl.eglMakeCurrent(this.eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)) {
			throw new RuntimeException("eglMakeCurrent failed");
		}
	}

	void swapBuffers() {
		this.checkIsNotReleased();
		if(this.eglSurface == EGL10.EGL_NO_SURFACE) {
			throw new RuntimeException("No EGLSurface - can\'t swap buffers");
		} else {
			this.egl.eglSwapBuffers(this.eglDisplay, this.eglSurface);
		}
	}

	private EGLDisplay getEglDisplay() {
		EGLDisplay eglDisplay = this.egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
		if(eglDisplay == EGL10.EGL_NO_DISPLAY) {
			throw new RuntimeException("Unable to get EGL10 display");
		} else {
			int[] version = new int[2];
			if(!this.egl.eglInitialize(eglDisplay, version)) {
				throw new RuntimeException("Unable to initialize EGL10");
			} else {
				return eglDisplay;
			}
		}
	}

	private EGLConfig getEglConfig(EGLDisplay eglDisplay, MineEglBase.ConfigType configType) {
		int[] configAttributes = new int[]{12324, 8, 12323, 8, 12322, 8, 12352, 4, 12344, 0, 12344};
//		switch(MineEglBase.SyntheticClass_1.$SwitchMap$com$intel$webrtc$base$EglBase$ConfigType[configType.ordinal()]) {
//		case 1:
//			break;
//		case 2:
//			configAttributes[configAttributes.length - 3] = 12339;
//			configAttributes[configAttributes.length - 2] = 1;
//			break;
//		case 3:
//			configAttributes[configAttributes.length - 3] = 12610;
//			configAttributes[configAttributes.length - 2] = 1;
//			break;
//		default:
//			throw new IllegalArgumentException();
//		}

		EGLConfig[] configs = new EGLConfig[1];
		int[] numConfigs = new int[1];
		if(!this.egl.eglChooseConfig(eglDisplay, configAttributes, configs, configs.length, numConfigs)) {
			throw new RuntimeException("Unable to find RGB888 " + configType + " EGL config");
		} else {
			return configs[0];
		}
	}

	private EGLContext createEglContext(EGLContext sharedContext, EGLDisplay eglDisplay, EGLConfig eglConfig) {
		int[] contextAttributes = new int[]{12440, 2, 12344};
		EGLContext eglContext = this.egl.eglCreateContext(eglDisplay, eglConfig, sharedContext, contextAttributes);
		if(eglContext == EGL10.EGL_NO_CONTEXT) {
			throw new RuntimeException("Failed to create EGL context");
		} else {
			return eglContext;
		}
	}

	public static enum ConfigType {
		PLAIN,
		PIXEL_BUFFER,
		RECORDABLE;

		private ConfigType() {
		}
	}
}
