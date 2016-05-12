package com.example.mvreceiver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.webrtc.PeerConnection.IceServer;

import com.example.mvreceiver.mine.CustomeVideoStreamsView;
import com.intel.webrtc.base.ActionCallback;
import com.intel.webrtc.base.ClientContext;
import com.intel.webrtc.base.EglBase;
import com.intel.webrtc.base.RemoteScreenStream;
import com.intel.webrtc.base.RemoteStream;
import com.intel.webrtc.base.VideoStreamsView;
import com.intel.webrtc.base.WoogeenException;
import com.intel.webrtc.base.MediaCodec.VideoCodec;
import com.intel.webrtc.base.RendererCommon.ScalingType;
import com.intel.webrtc.conference.ConferenceClient;
import com.intel.webrtc.conference.ConferenceClientConfiguration;
import com.intel.webrtc.conference.RemoteMixedStream;
import com.intel.webrtc.conference.SubscribeOptions;
import com.intel.webrtc.conference.User;
import com.intel.webrtc.conference.ConferenceClient.ConferenceClientObserver;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;



public class MainActivity extends Activity implements ConferenceClientObserver{
	private String roomId = "88888888888888";
	private static final String TAG = "WooGeen-Activity";
	private ConferenceClient mRoom;
	private EglBase rootEglBase;
	private String basicServerString = "http://192.168.16.65:3001/";
	private static final String stunAddr = "";
	private CustomeVideoStreamsView leftRemoteView;
	private CustomeVideoStreamsView rightRemoteView;
	private RemoteStream currentRemoteStream;
	//presenter
	//viewerWithData
	//viewer
	private String role="viewer";
	private String userName="master";

	private String tokenString = "";
	public static final int MSG_ROOM_DISCONNECTED = 98;
	public static final int MSG_PUBLISH = 99;
	public static final int MSG_LOGIN = 100;
	public static final int MSG_SUBSCRIBE = 101;
	public static final int MSG_UNSUBSCRIBE = 102;
	public static final int MSG_UNPUBLISH = 103;
	private HandlerThread roomThread;
	private RoomHandler roomHandler;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				Log.d(TAG, "uncaughtexception");
				e.printStackTrace();
				System.exit(-1);
			}
		});
		AudioManager audioManager = ((AudioManager) getSystemService(AUDIO_SERVICE));
		audioManager.setSpeakerphoneOn(true);
		ConferenceClientConfiguration config=new ConferenceClientConfiguration();
		List<IceServer> iceServers=new ArrayList<IceServer>();
		iceServers.add(new IceServer(stunAddr));
		//iceServers.add(new IceServer(turnAddrTCP, "woogeen", "master"));
		//iceServers.add(new IceServer(turnAddrUDP, "woogeen", "master"));
		try {
			config.setIceServers(iceServers);
		} catch (WoogeenException e1) {
			e1.printStackTrace();
		}
		mRoom = new ConferenceClient(config);
		mRoom.addObserver(this);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams. FLAG_FULLSCREEN , WindowManager.LayoutParams. FLAG_FULLSCREEN);  
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_main);
		initVideoStreamsViews();
		initAudioControl();
		roomThread = new HandlerThread("Room Thread");
		roomThread.start();
		roomHandler = new RoomHandler(roomThread.getLooper());
		new Handler().postDelayed(new Runnable(){   
			public void run() {   
				command(MSG_LOGIN); 
			}   
		}, 1000);  
	}
	@Override
	protected void onPause() {
		super.onPause();
	}
	@Override
	protected void onResume() {
		super.onResume();
	}
	public void command(int sender) {
		Message msg = new Message();
		msg.what = sender;                
		roomHandler.sendMessage(msg);
	}
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK)
			android.os.Process.killProcess(android.os.Process.myPid());
		return super.onKeyDown(keyCode, event);
	}
	@Override
	public void onServerDisconnected() {
		Log.d(TAG, "onRoomDisconnected");
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				//TODO prepare send
				Toast.makeText(MainActivity.this, "Room DisConnected",
						Toast.LENGTH_SHORT).show();
			}
		});
		currentRemoteStream = null;
	}
	@Override
	public void onStreamAdded(RemoteStream remoteStream) {
		Log.d(TAG, "onStreamAdded: streamId = " + remoteStream.getId()
				+ ", from " + remoteStream.getRemoteUserId());
		/*
		 * we only subscribe the mix stream in default.
		 */
		if ((remoteStream instanceof RemoteMixedStream)
				|| (remoteStream instanceof RemoteScreenStream)) {
			Message msg = new Message();
			msg.what = MSG_SUBSCRIBE;
			msg.obj = remoteStream;
			roomHandler.sendMessage(msg);
		}
	}
	@Override
	public void onStreamRemoved(RemoteStream remoteStream) {
		Log.d(TAG, "onStreamRemoved: streamId = " + remoteStream.getId());
		// If there is another remote stream subscribed, render it.
		if (currentRemoteStream != null
				&& currentRemoteStream.getId().equals(remoteStream.getId())
				&& mRoom.getRemoteStreams().size() > 0) {
			currentRemoteStream = mRoom.getRemoteStreams().get(0);
		} else {
			remoteStream.detach();
			leftRemoteView.cleanFrame();
			rightRemoteView.cleanFrame();
		}
		Message msg = new Message();
		msg.what = MSG_UNSUBSCRIBE;
		msg.obj = remoteStream;
		roomHandler.sendMessage(msg);
	}
	@Override
	public void onUserJoined(final User user) {
		runOnUiThread(new Runnable() {
			@Override
			public void run(){
				Toast.makeText(MainActivity.this,
						"A client named " + user.getName() + " has joined this room.",
						Toast.LENGTH_SHORT).show();
			}
		});
	}
	@Override
	public void onUserLeft(final User user) {
		runOnUiThread(new Runnable() {
			@Override
			public void run(){
				Toast.makeText(MainActivity.this,
						"A client named " + user.getName() + " has left this room.",
						Toast.LENGTH_SHORT).show();
			}
		});
	}
	@Override
	public void onMessageReceived(final String sender, final String message, final boolean broadcast){
		runOnUiThread(new Runnable() {
			public void run() {
				String userName = sender;
				for(User user : mRoom.getUsers()){
					if(user.getId().equals(sender)){
						userName = user.getName();
						break;
					}
				}
				Toast.makeText(MainActivity.this,
						(broadcast ? "[Broadcast message]" : "[Private message]")
						+ userName + ":" + message,
						Toast.LENGTH_SHORT).show();
			}
		});
	}
	@Override
	public void onRecorderAdded(String recorderId) {
		Log.d(TAG, "onRecorderAdded " + recorderId);
	}
	@Override
	public void onRecorderRemoved(String recorderId) {
		Log.d(TAG, "onRecorderRemoved" + recorderId);
	}
	@Override
	public void onRecorderContinued(String recorderId) {
		Log.d(TAG, "onRecorderReused" + recorderId);
	}
	private void initVideoStreamsViews() {
		leftRemoteView = (CustomeVideoStreamsView) findViewById(R.id.remote_video_view_left);
		rightRemoteView= (CustomeVideoStreamsView) findViewById(R.id.remote_video_view_right);
		leftRemoteView.setMirror(true);
		rightRemoteView.setMirror(true);
		leftRemoteView.setScalingType(ScalingType.SCALE_ASPECT_FILL);
		rightRemoteView.setScalingType(ScalingType.SCALE_ASPECT_FILL);
		rootEglBase = new EglBase();
		leftRemoteView.init(rootEglBase.getContext(), null);
		rightRemoteView.init(rootEglBase.getContext(), null);
		ClientContext.setApplicationContext(this, rootEglBase.getContext());
	}
	private void initAudioControl(){
		try {
			Properties p = new Properties();
			InputStream s = this.getAssets().open("audio_control.properties");
			p.load(s);
			ClientContext.setAudioControlEnabled(Boolean.parseBoolean(p.getProperty("enable_audio_control")));
			ClientContext.setAudioLevelOverloud(Integer.parseInt(p.getProperty("audio_level_overloud")));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@SuppressWarnings("deprecation")
	String getToken(String basicServer, String roomId) {
		HttpPost httpPost = null;
		String token = "";
		try {
			BasicHttpParams httpParams = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParams, 2000);
			HttpConnectionParams.setSoTimeout(httpParams, 5000);
			HttpClient httpClient = new DefaultHttpClient(httpParams);
			httpPost = new HttpPost(basicServer + "createToken/");
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-Type", "application/json");
			JSONObject jsonBody = new JSONObject();
			if (!roomId.equals("")) {
				jsonBody.put("room", roomId);
			}
			else {
				jsonBody.put("room", "");
			}
			jsonBody.put("role", this.role);
			jsonBody.put("username", this.userName);
			httpPost.setEntity(new StringEntity(jsonBody.toString(), HTTP.UTF_8));
			Toast.makeText(MainActivity.this,
					jsonBody.toString(),
					Toast.LENGTH_SHORT).show();
			HttpResponse httpResponse = httpClient.execute(httpPost);
			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				token = EntityUtils.toString(httpResponse.getEntity());
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (httpPost != null) {
				httpPost.abort();
			}
		}
		return token;
	}
	class RoomHandler extends Handler {
		public RoomHandler(Looper looper) {
			super(looper);
		}
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_LOGIN:
				Log.d(TAG,"log in . . . . . .");
				tokenString = getToken(basicServerString, roomId);
				Log.d(TAG, "token is " + tokenString);
				mRoom.join(tokenString, new ActionCallback<User>() {
					@Override
					public void onSuccess(User myself) {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(MainActivity.this,
										"Room Connected",
										Toast.LENGTH_SHORT).show();
							}
						});
						Log.d(TAG, "My client Id: " + myself.getId());
					}
					@Override
					public void onFailure(final WoogeenException e) {
						runOnUiThread(new Runnable() {
							public void run() {
								Toast.makeText(MainActivity.this,
										e.getMessage(), Toast.LENGTH_SHORT)
										.show();
								//TODO need modify
								new Handler().postDelayed(new Runnable(){   
									public void run() {   
										command(MSG_LOGIN); 
									}   
								}, 1000); 
							}
						});
					}
				});
				break;
			case MSG_SUBSCRIBE:
				SubscribeOptions option = new SubscribeOptions();
				option.setVideoCodec(VideoCodec.VP8);
				RemoteStream remoteStream = (RemoteStream)msg.obj;
				if(remoteStream instanceof RemoteMixedStream){
					List<Hashtable<String, Integer>> list = ((RemoteMixedStream) remoteStream).getSupportedResolutions();
					if(list.size() != 0){
						option.setResolution(list.get(0).get("width"), list.get(0).get("height"));
					}
				}
				mRoom.subscribe(remoteStream, option,
						new ActionCallback<RemoteStream>() {
					@Override
					public void onSuccess(
							final RemoteStream remoteStream) {
						Log.d(TAG, "onStreamSubscribed");
						try{
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									Log.d(TAG, "Subscribed stream: "
											+ remoteStream.getId());
								}
							});
							currentRemoteStream = remoteStream;
							remoteStream.attach(leftRemoteView);
							remoteStream.attach(rightRemoteView);
							Log.d(TAG,
									"Remote stream is attached to a view.");
						} catch (WoogeenException e) {
							e.printStackTrace();
						}
					}
					@Override
					public void onFailure(WoogeenException e) {
						e.printStackTrace();
					}
				});
				break;
			case MSG_UNSUBSCRIBE:
				mRoom.unsubscribe((RemoteStream) msg.obj, new ActionCallback<Void>(){
					@Override
					public void onSuccess(Void result) {
					}
					@Override
					public void onFailure(WoogeenException e) {
						e.printStackTrace();
					}
				});
				break;
			case MSG_ROOM_DISCONNECTED:
				mRoom.leave(new ActionCallback<Void>() {
					@Override
					public void onSuccess(Void result) {
					}
					@Override
					public void onFailure(WoogeenException e) {
						e.printStackTrace();
					}
				});
				break;
			}
			super.handleMessage(msg);
		}
	}
}
