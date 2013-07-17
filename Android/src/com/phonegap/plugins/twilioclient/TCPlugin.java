package com.phonegap.plugins.twilioclient;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaPlugin;
import org.apache.cordova.api.PluginResult;
import org.apache.cordova.api.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.twilio.client.Connection;
import com.twilio.client.ConnectionListener;
import com.twilio.client.Device;
import com.twilio.client.DeviceListener;
import com.twilio.client.PresenceEvent;
import com.twilio.client.Twilio;
import com.twilio.client.Twilio.InitListener;

/**
 * Twilio Client Plugin for Cordova/PhoneGap Targeted at version 2.9 for
 * compatibility
 * 
 * 
 * 
 * @see https://github.com/stevegraham/twilio_client_phonegap
 * @author Jeff Linwood, https://github.com/jefflinwood
 * 
 */
public class TCPlugin extends CordovaPlugin implements DeviceListener,
		InitListener, ConnectionListener {

	private final static String TAG = "TCPlugin";

	private Device mDevice;
	private Connection mConnection;
	private CallbackContext mInitCallbackContext;
	private JSONArray mInitDeviceSetupArgs;

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// mDevice = intent.getParcelableExtra(Device.EXTRA_DEVICE);
			mConnection = intent.getParcelableExtra(Device.EXTRA_CONNECTION);
			Log.d(TAG, "incoming intent received with connection: "
					+ mConnection.getState().name());
			fireDocumentEvent("onincoming");
		}
	};

	/**
	 * Android Cordova Action Router
	 * 
	 * Executes the request.
	 * 
	 * This method is called from the WebView thread. To do a non-trivial amount
	 * of work, use: cordova.getThreadPool().execute(runnable);
	 * 
	 * To run on the UI thread, use:
	 * cordova.getActivity().runOnUiThread(runnable);
	 * 
	 * @param action
	 *            The action to execute.
	 * @param args
	 *            The exec() arguments in JSON form.
	 * @param callbackContext
	 *            The callback context used when calling back into JavaScript.
	 * @return Whether the action was valid.
	 */
	@Override
	public boolean execute(final String action, final JSONArray args,
			final CallbackContext callbackContext) throws JSONException {
		if ("deviceSetup".equals(action)) {
			if (Twilio.isInitialized()) {
				deviceSetup(args, callbackContext);
			} else {
				mInitDeviceSetupArgs = args;
				initTwilio(callbackContext);
			}
			return true;

		} else if ("connect".equals(action)) {
			connect(args, callbackContext);
			return true;
		} else if ("disconnectAll".equals(action)) {
			disconnectAll(args, callbackContext);
			return true;
		} else if ("acceptConnection".equals(action)) {
			acceptConnection(args, callbackContext);
			return true;
		} else if ("disconnectConnection".equals(action)) {
			disconnectConnection(args, callbackContext);
			return true;
		}

		return false; 
	}

	/**
	 * Initialize Twilio's client library - this is only necessary on Android,
	 * 
	 */
	private void initTwilio(CallbackContext callbackContext) {
		mInitCallbackContext = callbackContext;
		Twilio.initialize(cordova.getActivity().getApplicationContext(), this);
	}

	/**
	 * Set up the Twilio device with a capability token
	 * 
	 * @param arguments
	 *            JSONArray with a callback and a Twilio capability token
	 */
	private void deviceSetup(JSONArray arguments,
			CallbackContext callbackContext) {
		Log.d(TAG, arguments.optString(0));
		if (arguments == null || arguments.length() < 1) {
			callbackContext.sendPluginResult(new PluginResult(
					PluginResult.Status.ERROR));
			return;
		}
		mDevice = Twilio.createDevice(arguments.optString(0), this);

		// handle incoming phone requests
		// 1) configure Twilio
		Intent intent = new Intent(this.cordova.getActivity(),
				IncomingConnectionActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(
				this.cordova.getActivity(), 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT);
		mDevice.setIncomingIntent(pendingIntent);
		// 2) configure the local broadcast manager
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(cordova
				.getActivity());
		lbm.registerReceiver(mBroadcastReceiver, new IntentFilter(
				IncomingConnectionActivity.ACTION_NAME));
		javascriptCallback("onready", callbackContext);

	}

	private void connect(JSONArray arguments, CallbackContext callbackContext) {
		JSONObject options = arguments.optJSONObject(0);
		Map<String, String> map = getMap(options);
		mConnection = mDevice.connect(map, this);
		Log.d(TAG, "Twilio device.connect() called: "
				+ mConnection.getState().name());
	}

	// helper method to get a map of strings from a JSONObject
	public Map<String, String> getMap(JSONObject object) {
		if (object == null) {
			return null;
		}

		Map<String, String> map = new HashMap<String, String>();

		@SuppressWarnings("rawtypes")
		Iterator keys = object.keys();
		while (keys.hasNext()) {
			String key = (String) keys.next();
			map.put(key, object.optString(key));
		}
		return map;
	}
	
	private void disconnectAll(JSONArray arguments, CallbackContext callbackContext) {
		mDevice.disconnectAll();
		callbackContext.success();
	}
	
	private void acceptConnection(JSONArray arguments, CallbackContext callbackContext) {
		mConnection.accept();
		callbackContext.success();
	}
	
	private void disconnectConnection(JSONArray arguments, CallbackContext callbackContext) {
		mConnection.disconnect();
		callbackContext.success();
	}

	// DeviceListener methods

	@Override
	public void onPresenceChanged(Device device, PresenceEvent presenceEvent) {
		/*
		 * JSONObject object = new JSONObject(); try { object.put("from",
		 * presenceEvent.getName()); object.put("available",
		 * presenceEvent.isAvailable()); } catch (JSONException e) {
		 * callbackContext.sendPluginResult(new
		 * PluginResult(PluginResult.Status.JSON_EXCEPTION)); return; }
		 * javascriptCallback("onpresence", object,callbackContext);
		 */
	}

	@Override
	public void onStartListening(Device device) {
		// What to do here? The JS library doesn't have an event for this.

	}

	@Override
	public void onStopListening(Device device) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStopListening(Device device, int errorCode,
			String errorMessage) {
		// this.javascriptErrorback(errorCode, errorMessage);

	}

	@Override
	public boolean receivePresenceEvents(Device device) {
		// TODO Auto-generated method stub
		return false;
	}

	// Twilio Init Listener methods
	@Override
	public void onError(Exception ex) {
		Log.e(TAG, "Error Initializing Twilio: " + ex.getMessage(), ex);
		mInitCallbackContext = null;

	}

	@Override
	public void onInitialized() {
		Log.d(TAG, "Twilio Initialized");
		deviceSetup(mInitDeviceSetupArgs, mInitCallbackContext);
		mInitCallbackContext = null;
	}

	// Twilio Connection Listener methods
	@Override
	public void onConnected(Connection connection) {
		Log.d(TAG, "onConnected()");
		fireDocumentEvent("onconnect");
		if (connection.isIncoming()) {
			fireDocumentEvent("onaccept");
		}

	}

	@Override
	public void onConnecting(Connection connection) {
		Log.d(TAG, "onConnecting()");
		// What to do here? The JS library doesn't have an event for connection
		// negotiation.

	}

	@Override
	public void onDisconnected(Connection connection) {
		Log.d(TAG, "onDisconnected()");
		fireDocumentEvent("ondevicedisconnect");
		fireDocumentEvent("onconnectiondisconnect");

	}

	@Override
	public void onDisconnected(Connection connection, int errorCode,
			String errorMessage) {
		// TODO: Pass error back
		Log.d(TAG, "onDisconnected(): " + errorMessage);
		onDisconnected(connection);
	}

	// Plugin-to-Javascript communication methods
	private void javascriptCallback(String event, JSONObject arguments,
			CallbackContext callbackContext) {
		JSONObject options = new JSONObject();
		try {
			options.putOpt("callback", event);
			options.putOpt("arguments", arguments);
		} catch (JSONException e) {
			callbackContext.sendPluginResult(new PluginResult(
					PluginResult.Status.JSON_EXCEPTION));
			return;
		}
		PluginResult result = new PluginResult(Status.OK, options);
		result.setKeepCallback(true);
		callbackContext.sendPluginResult(result);

	}

	private void javascriptCallback(String event,
			CallbackContext callbackContext) {
		javascriptCallback(event, null, callbackContext);
	}

	private void javascriptErrorback(int errorCode, String errorMessage,
			CallbackContext callbackContext) {
		JSONObject object = new JSONObject();
		try {
			object.putOpt("message", errorMessage);
		} catch (JSONException e) {
			callbackContext.sendPluginResult(new PluginResult(
					PluginResult.Status.JSON_EXCEPTION));
			return;
		}
		PluginResult result = new PluginResult(Status.ERROR, object);
		result.setKeepCallback(true);
		callbackContext.sendPluginResult(result);
	}

	private void fireDocumentEvent(String eventName) {
		if (eventName != null) {
			webView.sendJavascript("cordova.fireDocumentEvent('"
					+ eventName + "');");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		//lifecycle events
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(cordova
				.getActivity());
		lbm.unregisterReceiver(mBroadcastReceiver);
	}
	

	

}
