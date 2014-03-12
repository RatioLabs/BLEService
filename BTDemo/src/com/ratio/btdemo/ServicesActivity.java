package com.ratio.btdemo;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import com.ratio.btdemo.adapters.ServicesListAdapter;
import com.ratio.deviceService.BTDeviceProfile;
import com.ratio.deviceService.BTServiceProfile;
import com.ratio.deviceService.DeviceService;
import com.ratio.deviceService.IDeviceCommand;

import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class ServicesActivity extends Activity {
	protected final static String 			TAG = ServicesActivity.class.getSimpleName();
    public static final String 				PREFS = "BT_PREFS";
	public static final String 				CONNECT_TIMEOUT = "connect_timeout";
	public static final int 				CONNECT_TIMEOUT_DEFAULT_SEC = 20;
	public static final String 				RSSI_REFRESH_MSEC = "rssi_refresh_msec";
	public static final int					RSSI_REFRESH_MSEC_DEFAULT = 2000;
	
	protected boolean						mfScanning = false;
	protected IDeviceCommand	 			mService;						// service for device interface.
	protected DeviceServiceConnection 		mConnection;					// connection to the device service.
	protected BluetoothDevice				mDevice;
	protected ServicesListAdapter			mAdapter;
	protected ServicesDiscoveredReceiver	mServicesDiscoveredReceiver;
	protected RSSIReceiver					mRSSIReceiver;
	protected ConnectionStateReceiver		mConnectionStateReceiver;
	protected Timer							mRSSITimer;
	protected TimerTask						mRSSITimerTask;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mDevice = getIntent().getParcelableExtra(ScanActivity.EXTRA_DEVICE);
		setContentView(R.layout.activity_services);
		initializeDeviceService();
		mRSSITimer = new Timer();
	}	
	
	
	@Override
	public void onContentChanged() {
		TextView tvName = (TextView) findViewById(R.id.peripheral_name);
		tvName.setText(mDevice.getName());
		TextView tvAddress = (TextView) findViewById(R.id.peripheral_address);
		tvAddress.setText(mDevice.getAddress().toString());
	}
	// set up the broadcast receivers for service and device discovery
	@Override
	public void onResume() {
		super.onResume();
		mServicesDiscoveredReceiver = new ServicesDiscoveredReceiver();
		addReceiver(mServicesDiscoveredReceiver, DeviceService.ACTION_SERVICES_DISCOVERED);
		mRSSIReceiver = new RSSIReceiver();
		addReceiver(mRSSIReceiver, DeviceService.ACTION_READ_RSSI);
		mConnectionStateReceiver = new ConnectionStateReceiver();
		addReceiver(mConnectionStateReceiver, DeviceService.ACTION_CONNECTION_STATE);
		
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mConnection);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(mServicesDiscoveredReceiver);
		unregisterReceiver(mRSSIReceiver);
		unregisterReceiver(mConnectionStateReceiver);
	}

	protected void addReceiver(BroadcastReceiver receiver, String action) {
		IntentFilter filter = new IntentFilter();
		filter.addAction(action);
		registerReceiver(receiver, filter);
	}
	
	protected class CharacteristicsClickListener implements ListView.OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> adapter, View v, int position ,long i) {
			BluetoothGattService service = mAdapter.getServiceProfile(position);
			Intent characteristicActivityIntent = new Intent(ServicesActivity.this, CharacteristicsActivity.class);
			characteristicActivityIntent.putExtra(CharacteristicsActivity.EXTRA_DEVICE, mDevice);
			BTServiceProfile serviceProfile = new BTServiceProfile(service);
			characteristicActivityIntent.putExtra(CharacteristicsActivity.EXTRA_SERVICE, serviceProfile);
			startActivity(characteristicActivityIntent);
		}
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
            try {
    	        SharedPreferences settings = getSharedPreferences(PREFS, 0);
    	        long connectTimeoutSeconds = settings.getLong(CONNECT_TIMEOUT, CONNECT_TIMEOUT_DEFAULT_SEC);
            	mService.connectDevice(mDevice.getAddress(), connectTimeoutSeconds*1000); 
            } catch (RemoteException rex) {
            	rex.printStackTrace();
            }
        }
            
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            Log.d(TAG, "onServiceDisconnected() disconnected");
        }
    }
   
    public class ServicesDiscoveredReceiver extends BroadcastReceiver {
    	
        @Override
        public void onReceive(Context context, Intent intent) {
    		String deviceAddress = intent.getStringExtra(DeviceService.EXTRA_DEVICE_ADDRESS);
    		Log.d(TAG, "ServicesDiscoveredReceiver address = " + deviceAddress);
       		ArrayList<BTServiceProfile> serviceProfileList = (ArrayList<BTServiceProfile>) intent.getSerializableExtra(DeviceService.EXTRA_SERVICES);
       		List<BluetoothGattService> serviceList = BTServiceProfile.getServiceList(serviceProfileList);
       		mAdapter = new ServicesListAdapter(ServicesActivity.this, serviceList);
       		ListView servicesListView = (ListView) ServicesActivity.this.findViewById(R.id.services_list);
       		servicesListView.setAdapter(mAdapter);
       		servicesListView.setOnItemClickListener(new CharacteristicsClickListener());
   	        SharedPreferences settings = getSharedPreferences(PREFS, 0);
   	        mRSSITimerTask = new RSSITimerTask();
   	        long rssiRefreshMsec = settings.getLong(RSSI_REFRESH_MSEC, RSSI_REFRESH_MSEC_DEFAULT);
            mRSSITimer.schedule(mRSSITimerTask, rssiRefreshMsec*5, rssiRefreshMsec);
       		
        }
    }
 
    public class RSSIReceiver extends BroadcastReceiver {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		int rssi = intent.getIntExtra(DeviceService.EXTRA_RSSI, 0);
    		int status = intent.getIntExtra(DeviceService.EXTRA_STATUS, 0);
    		String deviceAddress = intent.getStringExtra(DeviceService.EXTRA_DEVICE_ADDRESS);
    		TextView tvRssi = (TextView) findViewById(R.id.peripheral_rssi);
    		tvRssi.setText(Integer.toString(rssi) + " db");   		
   		
    	}
    }
    
    public class ConnectionStateReceiver extends BroadcastReceiver {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		String deviceAddress = intent.getStringExtra(DeviceService.EXTRA_DEVICE_ADDRESS);
    		int state = intent.getIntExtra(DeviceService.EXTRA_STATE, 0);
    		if (state == BluetoothProfile.STATE_CONNECTED) {
    			try {
    				mService.discoverServices(mDevice.getAddress());
    			} catch (RemoteException rex) {
    				rex.printStackTrace();
    			}

    		}
       		TextView tvState = (TextView) ServicesActivity.this.findViewById(R.id.peripheral_status);
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
 }
