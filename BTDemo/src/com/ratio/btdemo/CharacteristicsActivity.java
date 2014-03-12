package com.ratio.btdemo;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import com.ratio.btdemo.adapters.CharacteristicsListAdapter;
import com.ratio.deviceService.BTCharacteristicProfile;
import com.ratio.deviceService.BTServiceProfile;
import com.ratio.deviceService.DeviceService;
import com.ratio.deviceService.IDeviceCommand;

import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public class CharacteristicsActivity extends Activity {
	protected final static String 			TAG = CharacteristicsActivity.class.getSimpleName();
	public static final String				EXTRA_DEVICE = "device";
	public static final String				EXTRA_SERVICE = "service";
    public static final String 				PREFS = "BT_PREFS";
	public static final String 				RSSI_REFRESH_MSEC = "rssi_refresh_msec";
	public static final int					RSSI_REFRESH_MSEC_DEFAULT = 2000;
	
	protected boolean						mfScanning = false;
	protected IDeviceCommand	 			mService;						// service for device interface.
	protected DeviceServiceConnection 		mConnection;					// connection to the device service.
	protected BluetoothDevice				mDevice;
	protected BTServiceProfile				mServiceProfile;
	protected CharacteristicsListAdapter	mAdapter;
	protected RSSIReceiver					mRSSIReceiver;
	protected ConnectionStateReceiver		mConnectionStateReceiver;
	protected CharactersticsReceiver		mCharacteristicsReceiver;
	protected Timer							mRSSITimer;
	protected TimerTask						mRSSITimerTask;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mDevice = getIntent().getParcelableExtra(EXTRA_DEVICE);
		mServiceProfile = getIntent().getParcelableExtra(EXTRA_SERVICE);
		setContentView(R.layout.activity_characteristics);
		initializeDeviceService();
		mRSSITimer = new Timer();
	}	
	
	
	@Override
	public void onContentChanged() {
		TextView tvName = (TextView) findViewById(R.id.peripheral_name);
		tvName.setText(mDevice.getName());
		TextView tvAddress = (TextView) findViewById(R.id.peripheral_address);
		tvAddress.setText(mDevice.getAddress().toString());
   		mAdapter = new CharacteristicsListAdapter(CharacteristicsActivity.this, mServiceProfile.getService().getCharacteristics());
   		ListView characteristicsListView = (ListView) CharacteristicsActivity.this.findViewById(R.id.characteristics_list);
   		characteristicsListView.setAdapter(mAdapter);
   		characteristicsListView.setOnItemClickListener(new CharacteristicItemClickListener());
	}
	
	// set up the broadcast receivers for service and device discovery
	@Override
	public void onResume() {
		super.onResume();
		mRSSIReceiver = new RSSIReceiver();
		addReceiver(mRSSIReceiver, DeviceService.ACTION_READ_RSSI);
		mConnectionStateReceiver = new ConnectionStateReceiver();
		addReceiver(mConnectionStateReceiver, DeviceService.ACTION_CONNECTION_STATE);
		mCharacteristicsReceiver = new CharactersticsReceiver();
		addReceiver(mCharacteristicsReceiver, DeviceService.ACTION_GET_CHARACTERISTICS);
		
	}

	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(mConnectionStateReceiver);
		unregisterReceiver(mRSSIReceiver);
		unregisterReceiver(mCharacteristicsReceiver);
	}
		
	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mConnection);
	}
	
	protected void addReceiver(BroadcastReceiver receiver, String action) {
		IntentFilter filter = new IntentFilter();
		filter.addAction(action);
		registerReceiver(receiver, filter);
	}
	
	private void initializeDeviceService() {
	    mConnection = new DeviceServiceConnection();
	    Intent i = new Intent(this, DeviceService.class);
	    boolean ret = bindService(i, mConnection, Context.BIND_AUTO_CREATE);
	    Log.d(TAG, "initService() bound with " + ret);
	}

	// interface to make requests from the device service. data is returned via broadcast receivers.
    private class DeviceServiceConnection implements ServiceConnection {

        public void onServiceConnected(ComponentName name, IBinder boundService) {
            mService = IDeviceCommand.Stub.asInterface((IBinder) boundService);
            Log.d(TAG, "onServiceConnected() connected");

        	//mService.getCharacteristics(mDevice.getAddress(), mServiceProfile.mServiceUUID.toString());
	        SharedPreferences settings = getSharedPreferences(PREFS, 0);
        	mRSSITimerTask = new RSSITimerTask();
   	        long rssiRefreshMsec = settings.getLong(RSSI_REFRESH_MSEC, RSSI_REFRESH_MSEC_DEFAULT);
            mRSSITimer.schedule(mRSSITimerTask, 0, rssiRefreshMsec);
        }
            
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            Log.d(TAG, "onServiceDisconnected() disconnected");
        }
    }
   
    public class CharactersticsReceiver extends com.ratio.deviceService.receivers.CharactertisticListReceiver {
    	@Override
    	public void onCharacteristicList(String 							deviceAddress, 
    									 BluetoothGattService				service,
				  						 List<BluetoothGattCharacteristic> 	charList) {
    		Log.d(TAG, "ServicesDiscoveredReceiver address = " + deviceAddress);
       		mAdapter = new CharacteristicsListAdapter(CharacteristicsActivity.this, charList);
       		ListView characteristicsListView = (ListView) CharacteristicsActivity.this.findViewById(R.id.services_list);
       		characteristicsListView.setAdapter(mAdapter);
       		characteristicsListView.setOnItemClickListener(new CharacteristicItemClickListener());     		
        }
    }
 
    public class RSSIReceiver extends com.ratio.deviceService.receivers.RSSIReceiver {
    	@Override
    	public void onRSSI(String deviceAddress, int rssi, int status) {
     		TextView tvRssi = (TextView) findViewById(R.id.peripheral_rssi);
    		tvRssi.setText(Integer.toString(rssi) + " db");   		
   		
    	}
    }
    
    public class ConnectionStateReceiver extends com.ratio.deviceService.receivers.ConnectionStateReceiver {
    	@Override
    	public void onConnectionState(String deviceAddress, int state) {
       		TextView tvState = (TextView) CharacteristicsActivity.this.findViewById(R.id.peripheral_status);
       		tvState.setText(Integer.toString(state));  		

    	}
    }
    
    protected class RSSITimerTask extends TimerTask {
		@Override
		public void run() {
			try {
				mService.readRemoteRSSI(mDevice.getAddress());	
			} catch (RemoteException rex) {
				rex.printStackTrace();
			}
		}

    }
    
    public class CharacteristicItemClickListener implements ListView.OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> adapterView, View v, int position, long id) {
			Intent i = new Intent(CharacteristicsActivity.this, CharacteristicDetailsActivity.class);
			i.putExtra(CharacteristicDetailsActivity.EXTRA_DEVICE, mDevice);
			i.putExtra(CharacteristicDetailsActivity.EXTRA_SERVICE, mServiceProfile);
			BTCharacteristicProfile btCharProfile = new BTCharacteristicProfile(mAdapter.getCharacteristic(position));
			i.putExtra(CharacteristicDetailsActivity.EXTRA_CHARACTERISTIC, btCharProfile);
			startActivity(i);
		}
    	
    }

  }
