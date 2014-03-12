package com.ratio.btdemo;

import java.util.UUID;

import com.ratio.deviceService.BTDeviceProfile;
import com.ratio.deviceService.DeviceService;
import com.ratio.deviceService.IDeviceCommand;

import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ScanActivity extends Activity {
	protected final static String 			TAG = ScanActivity.class.getSimpleName();
    public static final String 				PREFS = "BT_PREFS";
	public static final String 				SCAN_TIMEOUT = "scan_timeout";
	public static final int 				SCAN_TIMEOUT_DEFAULT_SEC = 20;
	public static final String 				RECONNECT_DELAY = "reconnect_delay";
	public static final int 				RECONNECT_DELAY_DEFAULT = 10;
	public static final String 				CONNECT_TIMEOUT = "connect_timeout";
	public static final String				EXTRA_DEVICE = "device";
	protected boolean						mfScanning = false;
	protected IDeviceCommand	 			mService;						// service for device interface.
	protected DeviceServiceConnection 		mConnection;					// connection to the device service.
	protected DeviceDiscoveredReceiver		mDeviceDiscoveredReceiver;
	protected ScanStopReceiver				mScanStopReceiver;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scan);
		initializeDeviceService();
	}	
	
	
	// set up the broadcast receivers for service and device discovery
	@Override
	public void onResume() {
		super.onResume();
		mDeviceDiscoveredReceiver = new DeviceDiscoveredReceiver();
		addReceiver(mDeviceDiscoveredReceiver, DeviceService.ACTION_DEVICE_DISCOVERED);
		mScanStopReceiver = new ScanStopReceiver();
		addReceiver(mScanStopReceiver, DeviceService.ACTION_STOP_DEVICE_SCAN);
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
	
	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(mDeviceDiscoveredReceiver);
		unregisterReceiver(mScanStopReceiver);
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
            
            // set up the click listener to enable the scan
            final Button scanButton = (Button) findViewById(R.id.scan_button);
            scanButton.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					if (mfScanning) {
						stopScan();
					} else {
						scanDevices();	
					}
				}
            });
        }
            
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            Log.d(TAG, "onServiceDisconnected() disconnected");
        }
    }

    // scan with a predetermined timeout
    public void scanDevices() {
    	LinearLayout deviceContainer = (LinearLayout) findViewById(R.id.device_container);
    	deviceContainer.removeAllViews();
    	Button scanButton = (Button) findViewById(R.id.scan_button);
		scanButton.setText(R.string.stop_device_scan);
		mfScanning = true;
	    try {
	        SharedPreferences settings = getSharedPreferences(PREFS, 0);
	        int scanTimeoutSeconds = settings.getInt(SCAN_TIMEOUT, SCAN_TIMEOUT_DEFAULT_SEC);
	        mService.scanDevices(null, scanTimeoutSeconds*1000);
	    } catch (RemoteException rex) {
	            rex.printStackTrace();
	    }
	}
    
    public void stopScan() {
    	Button scanButton = (Button) findViewById(R.id.scan_button);
		scanButton.setText(R.string.scan_devices);
		mfScanning = false;
		try {
			mService.stopDeviceScan();
		} catch (RemoteException rex) {
			rex.printStackTrace();
		}
    }
     
    // when the device has been discovered, add a layout showing the device name and UUID.  Save the
    // device in the layout, so when we click on it, we can examine the services that the device advertised.
    public class DeviceDiscoveredReceiver extends com.ratio.deviceService.receivers.DeviceDiscoveredRecevier {
    	
    	
    	@Override 
    	public void onDeviceDiscovered(BluetoothDevice device) {
        	Log.d(TAG, "DeviceDiscoveredReceiver address = " + device.getAddress());
        	View deviceLayout = getLayoutInflater().inflate(R.layout.device_layout, null);
        	deviceLayout.setTag(device);
           	TextView deviceNameText = (TextView) deviceLayout.findViewById(R.id.device_name);
           	String deviceName = device.getName();
        	if (deviceName == null) {
        		deviceNameText.setText(R.string.no_name);
        	} else {
        		deviceNameText.setText(deviceName);
        	}
        	TextView deviceAddrText = (TextView) deviceLayout.findViewById(R.id.device_address);
        	deviceAddrText.setText(device.getAddress().toString());
        	deviceLayout.setOnClickListener(new DeviceClickListener());
        	LinearLayout deviceContainer = (LinearLayout) findViewById(R.id.device_container);
        	deviceContainer.addView(deviceLayout);
        }

   }
    
    // launch the activity to query services.
    public class DeviceClickListener implements View.OnClickListener {

		@Override
		public void onClick(View v) {
			stopScan();
			BluetoothDevice device = (BluetoothDevice) v.getTag();
			Intent servicesActivityIntent = new Intent(ScanActivity.this, ServicesActivity.class);
			servicesActivityIntent.putExtra(EXTRA_DEVICE, device);
			startActivity(servicesActivityIntent);
		}
    	
    }
    
    // when the scanning has been stopped, update the scan button
   public class ScanStopReceiver extends BroadcastReceiver {
    	
        @Override
        public void onReceive(Context context, Intent intent) {
            Button scanButton = (Button) findViewById(R.id.scan_button);
			scanButton.setText(R.string.scan_devices);
			mfScanning = false;
        }
   }
}
