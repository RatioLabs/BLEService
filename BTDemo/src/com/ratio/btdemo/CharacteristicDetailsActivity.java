package com.ratio.btdemo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import com.ratio.btdemo.ServicesActivity.RSSIReceiver;
import com.ratio.btdemo.adapters.CharacteristicsListAdapter;
import com.ratio.btdemo.adapters.ServicesListAdapter;
import com.ratio.deviceService.BTCharacteristicProfile;
import com.ratio.deviceService.BTDeviceProfile;
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
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

public class CharacteristicDetailsActivity extends Activity {
	protected final static String 			TAG = CharacteristicDetailsActivity.class.getSimpleName();
	public static final String				EXTRA_DEVICE = "device";
	public static final String				EXTRA_SERVICE = "service";
	public static final String				EXTRA_CHARACTERISTIC = "characteristic";
    public static final String 				PREFS = "BT_PREFS";
	public static final String 				RSSI_REFRESH_MSEC = "rssi_refresh_msec";
	public static final int					RSSI_REFRESH_MSEC_DEFAULT = 2000;
	
	protected boolean						mNotificationEnabled = false;
	protected IDeviceCommand	 			mService;						// service for device interface.
	protected DeviceServiceConnection 		mConnection;					// connection to the device service.
	protected BluetoothDevice				mDevice;
	protected BTServiceProfile				mServiceProfile;
	protected BTCharacteristicProfile		mCharacteristicProfile;
	protected CharacteristicsListAdapter	mAdapter;
	protected RSSIReceiver					mRSSIReceiver;
	protected ConnectionStateReceiver		mConnectionStateReceiver;
	protected CharacteristicReadReceiver	mCharacteristicReadReceiver;
	protected CharacteristicChangedReceiver	mCharacteristicChangedReceiver;
	protected CharacteristicWriteReceiver	mCharacteristicWriteReceiver;
	
	protected Timer							mRSSITimer;
	protected TimerTask						mRSSITimerTask;
	private byte[] 							mRawValue = null;
	private int 							mIntValue = 0;
	private String 							mAsciiValue = "";
	private String 							mStrValue = "";
	private String 							mLastUpdateTime = "";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mDevice = getIntent().getParcelableExtra(EXTRA_DEVICE);
		mServiceProfile = getIntent().getParcelableExtra(EXTRA_SERVICE);
		mCharacteristicProfile = getIntent().getParcelableExtra(EXTRA_CHARACTERISTIC);
		setContentView(R.layout.peripheral_details_characteristic);
		initializeDeviceService();
		mRSSITimer = new Timer();
	}	
	
	
	@Override
	public void onContentChanged() {
		updateUI();
	}

	public void updateUI() {
		final BluetoothGattService service = mServiceProfile.getService();
		final BluetoothGattCharacteristic characteristic = mCharacteristicProfile.getCharacteristic();
    	TextView charPeripheralName = (TextView) findViewById(R.id.char_details_peripheral_name);
    	TextView charPeripheralAddress = (TextView) findViewById(R.id.char_details_peripheral_address);
    	TextView charServiceName = (TextView) findViewById(R.id.char_details_service);
    	TextView charServiceUuid = (TextView) findViewById(R.id.char_details_service_uuid);
    	TextView charName = (TextView) findViewById(R.id.char_details_name);
    	TextView charUuid = (TextView) findViewById(R.id.char_details_uuid);
    	
    	TextView charDataType = (TextView) findViewById(R.id.char_details_type);
    	TextView charProperties = (TextView) findViewById(R.id.char_details_properties);
    	
    	TextView charStrValue = (TextView) findViewById(R.id.char_details_ascii_value);
    	TextView charDecValue = (TextView) findViewById(R.id.char_details_decimal_value);
    	final EditText charHexValue = (EditText) findViewById(R.id.char_details_hex_value);
    	charHexValue.setText("0x");
    	TextView charDateValue = (TextView) findViewById(R.id.char_details_timestamp);
    	
    	ToggleButton notificationBtn = (ToggleButton) findViewById(R.id.char_details_notification_switcher);
    	Button readBtn = (Button) findViewById(R.id.char_details_read_btn);
    	Button writeBtn = (Button) findViewById(R.id.char_details_write_btn);
    	//Button writeBtn.setTag(fields.charHexValue);
    	
    	readBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					mService.readCharacteristic(mDevice.getAddress(), service.getUuid().toString(),
							characteristic.getUuid().toString());
				} catch (RemoteException rex) {
					rex.printStackTrace();
				}
			}
		});

    	writeBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String newValue =  charHexValue.getText().toString().toLowerCase(Locale.getDefault());
				byte[] dataToWrite = parseHexStringToBytes(newValue);
				try {
					mService.writeCharacteristicByteArray(mDevice.getAddress(), service.getUuid().toString(), 
														  characteristic.getUuid().toString(), dataToWrite);
				} catch (RemoteException rex) {
					rex.printStackTrace();
				}
			}
		});          	
    	
    	notificationBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked == mNotificationEnabled) return; // no need to update anything
				try {
					mService.setCharacteristicNotification(mDevice.getAddress(), service.getUuid().toString(), 
														  characteristic.getUuid().toString(), isChecked);
				} catch (RemoteException rex) {
					rex.printStackTrace();
				}
				mNotificationEnabled = isChecked;
			}
		} );
 	
	    // set proper values into the view
	    charPeripheralName.setText(mDevice.getName());
	    charPeripheralAddress.setText(mDevice.getAddress());
	    
	    String tmp = characteristic.getUuid().toString().toLowerCase(Locale.getDefault());
	    charServiceUuid.setText(tmp);
	    charServiceName.setText(BleNamesResolver.resolveServiceName(tmp));
	    
	    String uuid = characteristic.getUuid().toString().toLowerCase(Locale.getDefault());
	    String name = BleNamesResolver.resolveCharacteristicName(uuid);
	    
	    charName.setText(name);
	    charUuid.setText(uuid);
	    
	    int format = getValueFormat(characteristic);
	    charDataType.setText(BleNamesResolver.resolveValueTypeDescription(format));
	    int props = characteristic.getProperties();
	    String propertiesString = getCharacteristicPropertiesString(props);
	    charProperties.setText(propertiesString);
	    
	    notificationBtn.setEnabled((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0);
	    notificationBtn.setChecked(mNotificationEnabled);
	    readBtn.setEnabled((props & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
	    writeBtn.setEnabled((props & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0);
	    charHexValue.setEnabled(writeBtn.isEnabled());
	    
	    charHexValue.setText(mAsciiValue);
	    charStrValue.setText(mStrValue);
	    charDecValue.setText(String.format("%d", mIntValue));
	    charDateValue.setText(mLastUpdateTime);
	}
	
	// set up the broadcast receivers for service and device discovery
	@Override
	public void onResume() {
		super.onResume();
		mConnectionStateReceiver = new ConnectionStateReceiver();
		addReceiver(mConnectionStateReceiver, DeviceService.ACTION_CONNECTION_STATE);
		mRSSIReceiver = new RSSIReceiver();
		addReceiver(mRSSIReceiver, DeviceService.ACTION_READ_RSSI);
		mCharacteristicChangedReceiver = new CharacteristicChangedReceiver();
		addReceiver(mCharacteristicChangedReceiver, DeviceService.ACTION_CHARACTERISTIC_CHANGED);
		mCharacteristicReadReceiver = new CharacteristicReadReceiver();
		addReceiver(mCharacteristicReadReceiver, DeviceService.ACTION_CHARACTERISTIC_READ);
		mCharacteristicWriteReceiver = new CharacteristicWriteReceiver();
		addReceiver(mCharacteristicWriteReceiver, DeviceService.ACTION_CHARACTERISTIC_WRITE);
		
	}

	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(mConnectionStateReceiver);
		unregisterReceiver(mCharacteristicChangedReceiver);
		unregisterReceiver(mCharacteristicWriteReceiver);
		unregisterReceiver(mCharacteristicReadReceiver);
		unregisterReceiver(mRSSIReceiver);
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
        }
            
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            Log.d(TAG, "onServiceDisconnected() disconnected");
        }
    }
     
    public class ConnectionStateReceiver extends com.ratio.deviceService.receivers.ConnectionStateReceiver {

		@Override
		public void onConnectionState(String deviceAdress, int state) {
      		TextView tvState = (TextView) CharacteristicDetailsActivity.this.findViewById(R.id.peripheral_status);
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
    
	
	public static byte[] parseHexStringToBytes(final String hex) {
		String tmp = null;
		if (hex.startsWith("0x")) {
			tmp = hex.substring(2).replaceAll("[^[0-9][a-f]]", "");
		} else {
			tmp = hex;
		}
		byte[] bytes = new byte[tmp.length() / 2]; // every two letters in the string are one byte finally
		
		String part = "";
		
		for(int i = 0; i < bytes.length; ++i) {
			part = "0x" + tmp.substring(i*2, i*2+2);
			bytes[i] = Long.decode(part).byteValue();
		}
		
		return bytes;
	}
	
	public static String getHexString(byte[] rawValue) {
       if (rawValue != null && rawValue.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(rawValue.length);
            for (byte byteChar : rawValue) {
                stringBuilder.append(String.format("%02X", byteChar));
            }
            return "0x" + stringBuilder.toString();
        } else {
        	return "";
        } 
	}
	
	   /* reads and return what what FORMAT is indicated by characteristic's properties
     * seems that value makes no sense in most cases */
    public int getValueFormat(BluetoothGattCharacteristic ch) {
    	int properties = ch.getProperties();
    	
    	if((BluetoothGattCharacteristic.FORMAT_FLOAT & properties) != 0) return BluetoothGattCharacteristic.FORMAT_FLOAT;
    	if((BluetoothGattCharacteristic.FORMAT_SFLOAT & properties) != 0) return BluetoothGattCharacteristic.FORMAT_SFLOAT;
    	if((BluetoothGattCharacteristic.FORMAT_SINT16 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_SINT16;
    	if((BluetoothGattCharacteristic.FORMAT_SINT32 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_SINT32;
    	if((BluetoothGattCharacteristic.FORMAT_SINT8 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_SINT8;
    	if((BluetoothGattCharacteristic.FORMAT_UINT16 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_UINT16;
    	if((BluetoothGattCharacteristic.FORMAT_UINT32 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_UINT32;
    	if((BluetoothGattCharacteristic.FORMAT_UINT8 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_UINT8;
    	
    	return 0;
    }
    
    public static String getCharacteristicPropertiesString(int props) {
	    String propertiesString = String.format("0x%04X [", props);
	    if((props & BluetoothGattCharacteristic.PROPERTY_READ) != 0) propertiesString += "read ";
	    if((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) propertiesString += "write ";
	    if((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) propertiesString += "notify ";
	    if((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) propertiesString += "indicate ";
	    if((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) propertiesString += "write_no_response ";
	    propertiesString += "]";
	    return propertiesString;

    }

    /* get characteristic's value (and parse it for some types of characteristics) 
     * before calling this You should always update the value by calling requestCharacteristicValue() */
    public void getCharacteristicValue(BluetoothGattCharacteristic ch) {   
        byte[] rawValue = ch.getValue();
        String strValue = null;
        int intValue = 0;
        
        // lets read and do real parsing of some characteristic to get meaningful value from it 
        UUID uuid = ch.getUuid();
        
        if(uuid.equals(BleDefinedUUIDs.Characteristic.HEART_RATE_MEASUREMENT)) { // heart rate
        	// follow https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        	// first check format used by the device - it is specified in bit 0 and tells us if we should ask for index 1 (and uint8) or index 2 (and uint16)
        	int index = ((rawValue[0] & 0x01) == 1) ? 2 : 1;
        	// also we need to define format
        	int format = (index == 1) ? BluetoothGattCharacteristic.FORMAT_UINT8 : BluetoothGattCharacteristic.FORMAT_UINT16;
        	// now we have everything, get the value
        	intValue = ch.getIntValue(format, index);
        	strValue = intValue + " bpm"; // it is always in bpm units
        }
        else if (uuid.equals(BleDefinedUUIDs.Characteristic.HEART_RATE_MEASUREMENT) || // manufacturer name string
        		 uuid.equals(BleDefinedUUIDs.Characteristic.MODEL_NUMBER_STRING) || // model number string)
        		 uuid.equals(BleDefinedUUIDs.Characteristic.FIRMWARE_REVISION_STRING)) // firmware revision string
        {
        	// follow https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.manufacturer_name_string.xml etc.
        	// string value are usually simple utf8s string at index 0
        	strValue = ch.getStringValue(0);
        }
        else if(uuid.equals(BleDefinedUUIDs.Characteristic.APPEARANCE)) { // appearance
        	// follow: https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.gap.appearance.xml
        	intValue  = ((int)rawValue[1]) << 8;
        	intValue += rawValue[0];
        	strValue = BleNamesResolver.resolveAppearance(intValue);
        }
        else if(uuid.equals(BleDefinedUUIDs.Characteristic.BODY_SENSOR_LOCATION)) { // body sensor location
        	// follow: https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.body_sensor_location.xml
        	intValue = rawValue[0];
        	strValue = BleNamesResolver.resolveHeartRateSensorLocation(intValue);
        }
        else if(uuid.equals(BleDefinedUUIDs.Characteristic.BATTERY_LEVEL)) { // battery level
        	// follow: https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.battery_level.xml
        	intValue = rawValue[0];
        	strValue = "" + intValue + "% battery level";
        }        
        else {
        	// not known type of characteristic, so we need to handle this in "general" way
        	// get first four bytes and transform it to integer
        	intValue = 0;
        	if(rawValue.length > 0) intValue = (int)rawValue[0];
        	if(rawValue.length > 1) intValue = intValue + ((int)rawValue[1] << 8); 
        	if(rawValue.length > 2) intValue = intValue + ((int)rawValue[2] << 8); 
        	if(rawValue.length > 3) intValue = intValue + ((int)rawValue[3] << 8); 
        	
            if (rawValue.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(rawValue.length);
                for(byte byteChar : rawValue) {
                	try {
                		stringBuilder.append(String.format("%c", byteChar));
                	} catch (Exception ex) {
                		// swallow illegal characters
                	}
                }
                strValue = stringBuilder.toString();
            }
        }
        
        String timestamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS").format(new Date());
    	mRawValue = rawValue;
    	mIntValue = intValue;
    	mAsciiValue = getHexString(rawValue);
    	mStrValue = strValue;
    	mLastUpdateTime = timestamp;
    }
    
    public class CharacteristicReadReceiver extends com.ratio.deviceService.receivers.CharacteristicReceiver {
    	@Override
		public void onCharacteristic(String deviceAddress,
				BluetoothGattService service,
				BluetoothGattCharacteristic characteristic, 
				byte[] chValue,
				int status) {
    		getCharacteristicValue(characteristic);
    		updateUI();
    	}
    }
    
    public class CharacteristicWriteReceiver extends com.ratio.deviceService.receivers.CharacteristicReceiver {
    	@Override
		public void onCharacteristic(String deviceAddress,
				BluetoothGattService service,
				BluetoothGattCharacteristic characteristic, 
				byte[] chValue,
				int status) {
     		getCharacteristicValue(characteristic);
    		updateUI();
    	}
    }
    
    public class CharacteristicChangedReceiver extends com.ratio.deviceService.receivers.CharacteristicReceiver {
		@Override
		public void onCharacteristic(String deviceAddress,
									BluetoothGattService service,
									BluetoothGattCharacteristic characteristic, 
									byte[] chValue,
									int status) {
    		if (deviceAddress.equals(mDevice.getAddress()) && 
    			service.getUuid().equals(mServiceProfile.getService().getUuid()) &&
    			characteristic.getUuid().equals(mCharacteristicProfile.getCharacteristic().getUuid())) {
	    		getCharacteristicValue(characteristic);
	    		updateUI();
    		}
    	}
    }
    
    
    public class RSSIReceiver extends com.ratio.deviceService.receivers.RSSIReceiver {

		@Override
		public void onRSSI(String deviceAdress, int rssi, int status) {
    		TextView tvRssi = (TextView) findViewById(R.id.peripheral_rssi);
    		tvRssi.setText(Integer.toString(rssi) + " db");   		
			
		}
    }

 }
