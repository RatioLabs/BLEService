package com.ratio.deviceService;

import java.util.ArrayList;
import java.util.UUID;

import com.ratio.exceptions.DeviceManagerException;
import com.ratio.exceptions.DeviceNameNotFoundException;
import com.ratio.deviceService.IDeviceCommand;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * the device service is a than wrapper around the BTLEDeviceManager.  It provides an AIDL interface to call into the
 * BTLEDeviceManager, sets up a callback interface to respond to bluetooth device events, and forwards the events
 * via broadcast message.
 * @author mreynolds
 *
 */
public class DeviceService extends Service implements BTLEDeviceManager.DeviceManagerCallback {
	private final static String TAG = DeviceService.class.getSimpleName();
 
    // broadcast messages
    public final static String ACTION_PERFORM_SCAN =DeviceService.class.getName() + ".ACTION_PERFORM_SCAN";
    public final static String ACTION_GET_SERVICES =DeviceService.class.getName() + ".ACTION_GET_SERVICES";
    public final static String ACTION_GET_CHARACTERISTICS =DeviceService.class.getName() + ".ACTION_GET_CHARACTERISTICS";
    public final static String ACTION_RETRY_RECONNECT =DeviceService.class.getName() + ".ACTION_RETRY_RECONNECT";
    public final static String ACTION_RECONNECT_FAILED =DeviceService.class.getName() + ".ACTION_RECONNECT_FAILED";
    public final static String ACTION_STOP_DEVICE_SCAN = DeviceService.class.getName() + ".ACTION_STOP_DEVICE_SCAN";
    public final static String ACTION_START_DEVICE_SCAN = DeviceService.class.getName() + ".ACTION_START_DEVICE_SCAN";
    public final static String ACTION_DEVICE_DISCOVERED = DeviceService.class.getName() + ".ACTION_DEVICE_DISCOVERED";
    public final static String ACTION_DEVICE_CONNECT = DeviceService.class.getName() + ".ACTION_DEVICE_CONNECT";
    public final static String ACTION_ERROR = DeviceService.class.getName() + ".ACTION_ERROR";
    public final static String ACTION_CONNECTION_STATE = DeviceService.class.getName() + ".ACTION_GATT_CONNECTION_STATE";
    public final static String ACTION_SERVICES_DISCOVERED = DeviceService.class.getName() + ".ACTION_SERVICES_DISCOVERED";
    public final static String ACTION_CHARACTERISTIC_READ = DeviceService.class.getName() + ".ACTION_CHARACTERISTIC_READ";
    public final static String ACTION_CHARACTERISTIC_WRITE = DeviceService.class.getName() + ".ACTION_CHARACTERISTIC_WRITE";
    public final static String ACTION_CHARACTERISTIC_CHANGED = DeviceService.class.getName() + ".ACTION_CHARACTERISTIC_CHANGED";
    public final static String ACTION_DESCRIPTOR_READ = DeviceService.class.getName() + ".ACTION_DESCRIPTOR_READ";
    public final static String ACTION_DESCRIPTOR_WRITE = DeviceService.class.getName() + ".ACTION_DESCRIPTOR_WRITE";
    public final static String RESET_ADAPTER = DeviceService.class.getName() + ".RESET_ADAPTER";
    public final static String ACTION_READ_RSSI = DeviceService.class.getName() + ".ACTION_READ_RSSI";
   
    // additional data sent along with intents in broadcast messages.
    public final static String EXTRA_DATA = DeviceService.class.getName() + ".EXTRA_DATA";
    public final static String EXTRA_SCAN_PERIOD_MSEC = DeviceService.class.getName() + ".EXTRA_SCAN_PERIOD";
    public final static String EXTRA_ENABLE = DeviceService.class.getName() + ".EXTRA_ENABLE";
    public final static String EXTRA_DEVICE_ADDRESS = DeviceService.class.getName() + ".EXTRA_DEVICE_ADDRESS";
    public final static String EXTRA_DEVICE = DeviceService.class.getName() + ".EXTRA_DEVICE";
    public final static String EXTRA_ERROR_MESSAGE = DeviceService.class.getName() + ".EXTRA_ERROR_MESSAGE";
    public final static String EXTRA_ERROR_CODE = DeviceService.class.getName() + ".EXTRA_ERROR_CODE";
    public final static String EXTRA_CHARACTERISTICS = DeviceService.class.getName() + ".EXTRA_CHARACTERISTICS";
    public final static String EXTRA_SERVICES = DeviceService.class.getName() + ".EXTRA_SERVICES";
    public final static String EXTRA_STATE = DeviceService.class.getName() + ".EXTRA_STATE";
    public final static String EXTRA_SERVICE = DeviceService.class.getName() + ".EXTRA_SERVICE";
    public final static String EXTRA_CHARACTERISTIC = DeviceService.class.getName() + ".EXTRA_CHARACTERISTIC";
    public final static String EXTRA_DESCRIPTOR = DeviceService.class.getName() + ".EXTRA_DESCRIPTOR";
    public final static String EXTRA_VALUE = DeviceService.class.getName() + ".EXTRA_VALUE";
    public final static String EXTRA_RSSI = DeviceService.class.getName() + ".EXTRA_RSSI";
    public final static String EXTRA_STATUS = DeviceService.class.getName() + ".EXTRA_STATUS";
    public final static String EXTRA_RETRIES_LEFT = DeviceService.class.getName() + ".EXTRA_RETRIES_LEFT";
       
    protected static BTLEDeviceManager	mDeviceManager;					// device manager actually does device interface
    protected static int 				mPeriodMsec;					// how many msec should we poll for?
    protected static UUID[]				mAdvertisedServices;			// advertised services to scan for
    protected static boolean			mfInitialized = false;
    protected static boolean			mfScanning = false;				// scanning for BTLE devices, don't start another scan
		
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ((intent != null) && (intent.getAction() != null)) {
			// this is because we get the message from the broadcast receiver that toggles the bluetooth adapter.
			if (intent.getAction().equals(ACTION_PERFORM_SCAN)) {
				mfScanning = true;
				mDeviceManager.scanLeDevice(mAdvertisedServices, mPeriodMsec);
			}
		}
		return START_STICKY;
	}
   /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
    	try {
    		mDeviceManager = new BTLEDeviceManager(this, this);
    	} catch (DeviceManagerException dmex) {
    		broadcastError(DeviceErrorCodes.ERROR_INIITIALIZATION, dmex.getMessage(), null);
    	}
    	return true;
    }

    public boolean shutdown() {
    	return true;
    }
    
    public void onDestroy() {
    	shutdown();
    }
    
    // these are the callback interfaces for BTLEDeviceManager. They pretty much take their parameters and broadcast them.
    // we broadcast the fact that we have stopped scanning because other people may want to know that.
	public void onDiscoveryStopped() {
		Intent broadcastIntent = new Intent(ACTION_STOP_DEVICE_SCAN);
		sendBroadcast(broadcastIntent);
	}
	
	// we send the broadcast immediately when we have scanned a bluetooth device.  Happily, a bluetooth device is parcelable
	public void onDeviceDiscovered(BluetoothDevice device) {
		Intent broadcastIntent = new Intent(ACTION_DEVICE_DISCOVERED);
		broadcastIntent.putExtra(EXTRA_DEVICE, device);
		sendBroadcast(broadcastIntent);
	}
	

   	// broadcast the fact that we have started scanning, since other people want to know
	public void onDiscoveryStarted() {
		Intent broadcastIntent = new Intent(ACTION_START_DEVICE_SCAN);
		sendBroadcast(broadcastIntent);
	}	

	// report on the connection state for a device
	public void onGattConnectionState(BluetoothDevice device, BluetoothGatt gatt, int connState) {
		Intent broadcastIntent = new Intent(ACTION_CONNECTION_STATE);
		broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
		broadcastIntent.putExtra(EXTRA_STATE, connState);
		sendBroadcast(broadcastIntent);
	}
	
	// report that we are attempting to reconnect to a device
	public void onRetryReconnect(BluetoothDevice device, int retriesLeft) {
		Intent broadcastIntent = new Intent(ACTION_RETRY_RECONNECT);
		broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
		broadcastIntent.putExtra(EXTRA_RETRIES_LEFT, retriesLeft);
		sendBroadcast(broadcastIntent);
		
	}
	
	// report that we have failed in our attempts to reconnect to a device
	public void onReconnectFailed(BluetoothDevice device) {
		Intent broadcastIntent = new Intent(ACTION_RECONNECT_FAILED);
		broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
		sendBroadcast(broadcastIntent);	
	}
	
	// report that we have discovered the services published by a device.
	public void onServicesDiscovered(BluetoothDevice device, BluetoothGatt gatt) {
		try {
			BTDeviceProfile profile = mDeviceManager.getDeviceProfile(device.getAddress());
			Intent broadcastIntent = new Intent(ACTION_SERVICES_DISCOVERED);
			broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
			broadcastIntent.putExtra(EXTRA_SERVICES, profile.mServiceProfileList);
			sendBroadcast(broadcastIntent);	
		} catch (DeviceNameNotFoundException dnnfex) {
			onError(DeviceErrorCodes.ERROR_SERVICES_DISCOVERED, dnnfex.getMessage(), device.getAddress());
		}
	}
	
	// broadcast the response from a readCharacteristic call
	public void onCharacteristicRead(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
		Intent broadcastIntent = new Intent(ACTION_CHARACTERISTIC_READ);
		broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
		broadcastIntent.putExtra(EXTRA_SERVICE, new BTServiceProfile(characteristic.getService()));
		broadcastIntent.putExtra(EXTRA_CHARACTERISTIC, new BTCharacteristicProfile(characteristic));
		broadcastIntent.putExtra(EXTRA_VALUE, characteristic.getValue());
		sendBroadcast(broadcastIntent);	
	}
	
	// broadcast the response from a writeCharacteristic call
	@Override
	public void onCharacteristicWrite(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
		Intent broadcastIntent = new Intent(ACTION_CHARACTERISTIC_WRITE);
		broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
		broadcastIntent.putExtra(EXTRA_SERVICE,  new BTServiceProfile(characteristic.getService()));
		broadcastIntent.putExtra(EXTRA_CHARACTERISTIC, new BTCharacteristicProfile(characteristic));
		broadcastIntent.putExtra(EXTRA_VALUE, characteristic.getValue());
		broadcastIntent.putExtra(EXTRA_STATUS, status);
		sendBroadcast(broadcastIntent);
		
	}

	// broadcast the notification for write descriptor
	public void onDescriptorRead(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
		Intent broadcastIntent = new Intent(ACTION_DESCRIPTOR_READ);
		broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
		broadcastIntent.putExtra(EXTRA_SERVICE,  new BTServiceProfile(descriptor.getCharacteristic().getService()));
		broadcastIntent.putExtra(EXTRA_CHARACTERISTIC, new BTCharacteristicProfile(descriptor.getCharacteristic()));
		broadcastIntent.putExtra(EXTRA_DESCRIPTOR, new BTDescriptorProfile(descriptor));
		broadcastIntent.putExtra(EXTRA_VALUE, descriptor.getValue());
		sendBroadcast(broadcastIntent);	
	}

	@Override
	public void onDescriptorWrite(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
		Intent broadcastIntent = new Intent(ACTION_DESCRIPTOR_WRITE);
		broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
		broadcastIntent.putExtra(EXTRA_SERVICE,  new BTServiceProfile(descriptor.getCharacteristic().getService()));
		broadcastIntent.putExtra(EXTRA_CHARACTERISTIC, new BTCharacteristicProfile(descriptor.getCharacteristic()));
		broadcastIntent.putExtra(EXTRA_DESCRIPTOR, new BTDescriptorProfile(descriptor));
		broadcastIntent.putExtra(EXTRA_VALUE, descriptor.getValue());
		sendBroadcast(broadcastIntent);	
		
	}
	
	// broadcast the notification for onCharacteristicChanged()
	public void onCharacteristicChanged(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
		Intent broadcastIntent = new Intent(ACTION_CHARACTERISTIC_CHANGED);
		broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
		broadcastIntent.putExtra(EXTRA_SERVICE,  new BTServiceProfile(characteristic.getService()));
		broadcastIntent.putExtra(EXTRA_CHARACTERISTIC, new BTCharacteristicProfile(characteristic));
		broadcastIntent.putExtra(EXTRA_VALUE, characteristic.getValue());
		sendBroadcast(broadcastIntent);	
	}
	
	// brodcast the Received Signal Strength Indicator for a device
	public void onReadRemoteRssi(BluetoothDevice device, int rssi, int status) {
		Intent broadcastIntent = new Intent(ACTION_READ_RSSI);
		broadcastIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
		broadcastIntent.putExtra(EXTRA_RSSI, rssi);
		broadcastIntent.putExtra(EXTRA_STATUS, status);
		sendBroadcast(broadcastIntent);	
	}
	
	// broadcast an error message.
	public void onError(int errorCode, String error, String deviceAddress) {
		broadcastError(errorCode, error, deviceAddress);
	}
    
    protected void broadcastError(int errorCode, final String error, String deviceAddress) {
    	final Intent intent = new Intent(ACTION_ERROR);
    	intent.putExtra(EXTRA_ERROR_MESSAGE, error);
    	intent.putExtra(EXTRA_ERROR_CODE, errorCode);
    	intent.putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress);
    	sendBroadcast(intent);
    }

    // implementation of the AIDL interface.  Pretty much just a pass-through to the BTLDeviceManager.
    public class DeviceCommandImpl extends IDeviceCommand.Stub {

		@Override
		public void scanDevices(String[] advertisedServiceUUIDList, int periodMsec) throws RemoteException {
			// broadcast to the receiver responsible for resetting the adapter that we have initiated disabling
			// the bluetooth adapter.
			Intent broadcastIntent = new Intent(RESET_ADAPTER);
			sendBroadcast(broadcastIntent);
			DeviceService.mPeriodMsec = periodMsec;
			if (advertisedServiceUUIDList != null) {
				DeviceService.mAdvertisedServices = new UUID[advertisedServiceUUIDList.length];
				for (int i = 0; i < advertisedServiceUUIDList.length; i++) {
					DeviceService.mAdvertisedServices[i] = UUID.fromString(advertisedServiceUUIDList[i]);
				}
			} else {
				DeviceService.mAdvertisedServices = null;
			}
			
			// if the bluetooth adapter is disabled, we can just re-enable it again, and we don't have to go
			// through the broadcast receiver round-trip.
			if (!mDeviceManager.disableBluetoothAdapter()) {
				mfScanning = true;
				mDeviceManager.scanLeDevice(mAdvertisedServices, DeviceService.mPeriodMsec);
			}
		}

		@Override
		public void startDeviceScan() throws RemoteException {
			mfScanning = true;
			mDeviceManager.scanLeDevice(mAdvertisedServices, mPeriodMsec);
		}

		@Override
		public void stopDeviceScan() throws RemoteException {
			mfScanning = false;
			mDeviceManager.stopLeScan();			
		}
		
		@Override
		public boolean isScanning() throws RemoteException {
			return mfScanning;
		}

		@Override
		public void connectDevice(String deviceAddress, long timeoutMsec) throws RemoteException {
			try {
				mDeviceManager.connect(deviceAddress, timeoutMsec);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_CONNECT, ex.getMessage(), deviceAddress);
			}
		}
		
		@Override
		public void disconnectDevice(String deviceAddress) throws RemoteException {
			try {
				mDeviceManager.disconnect(deviceAddress);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_DISCONNECT, ex.getMessage(), deviceAddress);
			}
		}

		
		@Override
		public void readRemoteRSSI(String deviceAddress) throws RemoteException {
			try {
				mDeviceManager.readRemoteRSSI(deviceAddress);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_READ_RSSI, ex.getMessage(), deviceAddress);
			}
		}
		// this assumes that onServicesDiscovered() has already happened for the device
		@Override
		public void getServices(String deviceAddress) throws RemoteException {
			try {
				ArrayList<BluetoothGattService> services = new ArrayList<BluetoothGattService>(mDeviceManager.getSupportedGattServices(deviceAddress));	
				ArrayList<BTServiceProfile> btServices = new ArrayList<BTServiceProfile>(services.size());
				for (BluetoothGattService service : services) {
					btServices.add(new BTServiceProfile(service));
				}
				Intent broadcastIntent = new Intent(ACTION_GET_SERVICES);
				broadcastIntent.putExtra(EXTRA_SERVICES, btServices);
				sendBroadcast(broadcastIntent);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_SERVICES_DISCOVERED, ex.getMessage(), deviceAddress);
			}
		}
		
		@Override 
		public void discoverServices(String deviceAddress) throws RemoteException {
			try {
				mDeviceManager.discoverServices(deviceAddress);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_DISCOVER_SERVICES, ex.getMessage(), deviceAddress);
			}
		}
		
		// this assumes that onServicesDiscovered() has already happened for the device
		@Override
		public void getCharacteristics(String deviceAddress, String serviceUUID)
				throws RemoteException {
			try {
				ArrayList<BluetoothGattCharacteristic> characteristics = new ArrayList<BluetoothGattCharacteristic>(mDeviceManager.getCharacteristics(deviceAddress, UUID.fromString(serviceUUID)));
				ArrayList<BTCharacteristicProfile> characteristicProfiles = new ArrayList<BTCharacteristicProfile>(characteristics.size());
				for (BluetoothGattCharacteristic btChar : characteristics) {
					characteristicProfiles.add(new BTCharacteristicProfile(btChar));
				}
				Intent broadcastIntent = new Intent(ACTION_GET_CHARACTERISTICS);
				broadcastIntent.putExtra(EXTRA_CHARACTERISTICS, characteristicProfiles);
				sendBroadcast(broadcastIntent);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_GET_CHARACTERISTICS, ex.getMessage(), deviceAddress);
			}
			
		}

		// enable characteristic for this characteristic of this service of this device
		@Override
		public void setCharacteristicNotification(String deviceAddress,
												  String serviceUUID, 
												  String characteristicUUID, 
												  boolean enabled) throws RemoteException {
			try {
				mDeviceManager.setCharacteristicNotification(deviceAddress, UUID.fromString(serviceUUID), UUID.fromString(characteristicUUID), enabled);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_SET_CHARACTERISTIC_NOTIFICATION, ex.getMessage(), deviceAddress);
			}
		}
		
		// readCharacteristic request.
		@Override
		public void readCharacteristic(String deviceAddress, String serviceUUID, String characteristicUUID) 
				throws RemoteException {
			try {
				mDeviceManager.readCharacteristic(deviceAddress, UUID.fromString(serviceUUID), UUID.fromString(characteristicUUID));
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_READ_CHARACTERISTIC, ex.getMessage(), deviceAddress);
			}
		}

		// readCharacteristic request.
		@Override
		public void writeDescriptor(String deviceAddress, 
								    String serviceUUID, 
								    String characteristicUUID, 
								    String descriptorUUID,
								    byte[] value) 
				throws RemoteException {
			try {
				mDeviceManager.writeDescriptor(deviceAddress, UUID.fromString(serviceUUID), UUID.fromString(characteristicUUID), UUID.fromString(descriptorUUID), value);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_WRITE_DESCRIPTOR, ex.getMessage(), deviceAddress);
			}
		}

		// readCharacteristic request.
		@Override
		public void readDescriptor(String deviceAddress, 
								   String serviceUUID, 
								   String characteristicUUID, 
								   String descriptorUUID) 
				throws RemoteException {
			try {
				mDeviceManager.readDescriptor(deviceAddress, UUID.fromString(serviceUUID), UUID.fromString(characteristicUUID), UUID.fromString(descriptorUUID));
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_READ_DESCRIPTOR, ex.getMessage(), deviceAddress);
			}
		}
		
		@Override
		public void writeCharacteristicString(String deviceAddress, String serviceUUID, String characteristicUUID, String value) {
			try {
				mDeviceManager.writeCharacteristic(deviceAddress, UUID.fromString(serviceUUID), UUID.fromString(characteristicUUID), value);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_WRITE_CHARACTERISTIC, ex.getMessage(), deviceAddress);
			}
		}
		
		@Override
		public void writeCharacteristicByteArray(String deviceAddress, String serviceUUID, String characteristicUUID, byte[] value) {
			try {
				mDeviceManager.writeCharacteristic(deviceAddress, UUID.fromString(serviceUUID), UUID.fromString(characteristicUUID), value);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_WRITE_CHARACTERISTIC, ex.getMessage(), deviceAddress);
			}
			
		}
		@Override
		public void writeCharacteristicInt(String deviceAddress, String serviceUUID, String characteristicUUID, int value, int format, int offset) {
			try {
				mDeviceManager.writeCharacteristic(deviceAddress, UUID.fromString(serviceUUID), UUID.fromString(characteristicUUID), value, format, offset);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_WRITE_CHARACTERISTIC, ex.getMessage(), deviceAddress);
			}
		}

		@Override
		public boolean isRetrying(String deviceAddress) {
			try {
				return mDeviceManager.isRetrying(deviceAddress);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_DEVICE_NOT_FOUND, ex.getMessage(), deviceAddress);
				return false;
			}
		}
		
		@Override
		public int getConnectionState(String deviceAddress) {
			try {
				return mDeviceManager.getConnectionState(deviceAddress);
			} catch (Exception ex) {
				broadcastError(DeviceErrorCodes.ERROR_DEVICE_NOT_FOUND, ex.getMessage(), deviceAddress);
				return BluetoothProfile.STATE_DISCONNECTED;
			}
		}
    }

    // standard service onBind() call
    @Override
    public IBinder onBind(Intent intent) {
		if (!mfInitialized) {
			initialize();
			mfInitialized = true;
		}
        return new DeviceCommandImpl();
    }
    
    // shutdown.  Don't call rebind.
    @Override 
    public boolean onUnbind(Intent intent) {
    	//mDeviceManager.shutdown();
    	mfInitialized = false;
    	return false;
    }
}
