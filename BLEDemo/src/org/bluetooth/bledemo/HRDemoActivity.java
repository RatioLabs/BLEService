package org.bluetooth.bledemo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.support.v4.app.NavUtils;

/* this activity's purpose is to show how to use particular type of devices in easy and fast way */
public class HRDemoActivity extends Activity {
	
	private Handler mHandler = null;
	private BluetoothManager mBTManager = null;
	private BluetoothAdapter mBTAdapter = null;
	private BluetoothDevice  mBTDevice = null;
	private BluetoothGatt    mBTGatt = null;
	private BluetoothGattService        mBTService = null;
	private BluetoothGattCharacteristic mBTValueCharacteristic = null;
	// UUDI od Heart Rate service:
	final static private UUID mHeartRateServiceUuid = BleDefinedUUIDs.Service.HEART_RATE;
	final static private UUID mHeartRateCharacteristicUuid = BleDefinedUUIDs.Characteristic.HEART_RATE_MEASUREMENT;
	
	private EditText mConsole = null;
	private TextView mTextView  = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_hrdemo);
		mConsole = (EditText) findViewById(R.id.hr_console_item);
		log("Creating activity");
		
		// Show the Up button in the action bar.
		getActionBar().setDisplayHomeAsUpEnabled(true);
		setTitle("Heart Rate Demo");
		mConsole = (EditText) findViewById(R.id.hr_console_item);
		mTextView = (TextView) findViewById(R.id.hr_text_view);

		mHandler = new Handler();
		log("Activity created");
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		super.onResume();
		log("Resuming activity");
		
		// first check if BT/BLE is available and enabled
		if(initBt() == false) return;
		if(isBleAvailable() == false) return;
		if(isBtEnabled() == false) return;
		
		// then start discovering devices around
		startSearchingForHr();
		
		log("Activity resumed");
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		disableNotificationForHr();
		disconnectFromDevice();
		closeGatt();
	};

	private boolean initBt() {
		mBTManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		if(mBTManager != null) mBTAdapter = mBTManager.getAdapter();
		
		return (mBTManager != null) && (mBTAdapter != null);
	}
	
	private boolean isBleAvailable() {
		log("Checking if BLE hardware is available");
		
		boolean hasBle = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
		if(hasBle && mBTManager != null && mBTAdapter != null) {
			log("BLE hardware available");
		}
		else {
			log("BLE hardware is missing!");
			return false;
		}
		return true;
	}
	
	private boolean isBtEnabled() {
		log("Checking if BT is enabled");
		if(mBTAdapter.isEnabled()) {
			log("BT is enabled");
		}
		else {
			log("BT is disabled. Use Setting to enable it and then come back to this app");
			return false;
		}
		return true;
	}

	private void startSearchingForHr() {
		// we define what kind of services found device needs to provide. In our case we are interested only in
		// Heart Rate service
		final UUID[] uuids = new UUID[] { mHeartRateServiceUuid };
		mBTAdapter.startLeScan(uuids, mDeviceFoundCallback);
		// results will be returned by callback
		log("Search for devices providing Heart Rate service started");
		
		// please, remember to add timeout for that scan
		Runnable timeout = new Runnable() {
            @Override
            public void run() {
				if(mBTAdapter.isDiscovering() == false) return;
				stopSearchingForHr();	
            }
        };
        mHandler.postDelayed(timeout, 10000); //10 seconds		
	}

	private void stopSearchingForHr() {
		mBTAdapter.stopLeScan(mDeviceFoundCallback);
		log("Searching for devices with Heart Rate service stopped");
	}
	
	private void connectToDevice() {
		log("Connecting to the device NAME: " + mBTDevice.getName() + " HWADDR: " + mBTDevice.getAddress());
		mBTGatt = mBTDevice.connectGatt(this, true, mGattCallback);
	}
	
	private void disconnectFromDevice() {
		log("Disconnecting from device");
		if(mBTGatt != null) mBTGatt.disconnect();
	}
	
	private void closeGatt() {
		if(mBTGatt != null) mBTGatt.close();
		mBTGatt = null;
	}
	
	private void discoverServices() {
		log("Starting discovering services");
		mBTGatt.discoverServices();
	}
	
	private void getHrService() {
		log("Getting Heart Rate Service");
		mBTService = mBTGatt.getService(mHeartRateServiceUuid);
		
		if(mBTService == null) {
			log("Could not get Heart Rate Service");
		}
		else {
			log("Heart Rate Service successfully retrieved");
			getHrCharacteristic();
		}
	}
	
	private void getHrCharacteristic() {
		log("Getting Heart Rate Measurement characteristic");
		mBTValueCharacteristic = mBTService.getCharacteristic(mHeartRateCharacteristicUuid);
		
		if(mBTValueCharacteristic == null) {
			log("Could not find Heart Rate Measurement Characteristic");
		}
		else {
			log("Heart Rate Measurement characteristic retrieved properly");
			enableNotificationForHr();
		}
	}
	
	private void enableNotificationForHr() {
		log("Enabling notification for Heart Rate");
        boolean success = mBTGatt.setCharacteristicNotification(mBTValueCharacteristic, true);
        if(!success) {
        	log("Enabling notification failed!");
        	return;
        }

        BluetoothGattDescriptor descriptor = mBTValueCharacteristic.getDescriptor(BleDefinedUUIDs.Descriptor.CHAR_CLIENT_CONFIG);
        if(descriptor != null) {
	        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
	        mBTGatt.writeDescriptor(descriptor);
	        log("Notification enabled");
        }		
        else {
        	log("Could not get descriptor for characteristic! Notification are not enabled.");
        }
	}

	private void disableNotificationForHr() {
		log("Disabling notification for Heart Rate");
        boolean success = mBTGatt.setCharacteristicNotification(mBTValueCharacteristic, false);
        if(!success) {
        	log("Disabling notification failed!");
        	return;
        }

        BluetoothGattDescriptor descriptor = mBTValueCharacteristic.getDescriptor(BleDefinedUUIDs.Descriptor.CHAR_CLIENT_CONFIG);
        if(descriptor != null) {
	        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
	        mBTGatt.writeDescriptor(descriptor);
	        log("Notification disabled");
        }		
        else {
        	log("Could not get descriptor for characteristic! Notification could be still enabled.");
        }
	}	
	
	private void getAndDisplayHrValue() {
    	byte[] raw = mBTValueCharacteristic.getValue();
    	int index = ((raw[0] & 0x01) == 1) ? 2 : 1;
    	int format = (index == 1) ? BluetoothGattCharacteristic.FORMAT_UINT8 : BluetoothGattCharacteristic.FORMAT_UINT16;
    	int value = mBTValueCharacteristic.getIntValue(format, index);
    	final String description = value + " bpm";

    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mTextView.setText(description);
			}
    	});
	}
	
    private BluetoothAdapter.LeScanCallback mDeviceFoundCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        	// here we found some device with heart rate service, lets save it:
        	HRDemoActivity.this.mBTDevice = device;
        	log("Device with Heart Rate service discovered. HW Address: "  + device.getAddress());
        	stopSearchingForHr();
        	
        	connectToDevice();
        }
    };	
	
    /* callbacks called for any action on HR Device */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
            	log("Device connected");
            	discoverServices();
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            	log("Device disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        	if(status == BluetoothGatt.GATT_SUCCESS) {
        		log("Services discovered");
        		getHrService();
        	}
        	else {
        		log("Unable to discover services");
        	}
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic)
        {
        	if(characteristic.equals(mBTValueCharacteristic)) {
        		getAndDisplayHrValue();
        	}
        }       
        
        /* the rest of callbacks are not interested for us */
        
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {}


        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {};
        
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {};
    };
     
	
	// put new logs into the UI console
	private void log(final String txt) {
		if(mConsole == null) return;
		
		final String timestamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS").format(new Date());
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mConsole.setText(timestamp + " : " + txt + "\n" + mConsole.getText());
			}		
		});
	}
}
