package com.ratio.deviceService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.ratio.BTDeviceService.R;
import com.ratio.deviceService.command.BTLECommand;
import com.ratio.deviceService.command.BTLECommandDisconnect;
import com.ratio.deviceService.command.BTLECommandDiscoverServices;
import com.ratio.deviceService.command.BTLECommandReadCharacteristic;
import com.ratio.deviceService.command.BTLECommandReadDescriptor;
import com.ratio.deviceService.command.BTLECommandSetCharacteristicNotification;
import com.ratio.deviceService.command.BTLECommandWriteCharacteristic;
import com.ratio.deviceService.command.BTLECommandWriteDescriptor;
import com.ratio.exceptions.DeviceManagerException;
import com.ratio.exceptions.DeviceNameNotFoundException;
import com.ratio.util.BitUtils;
import com.ratio.util.StringUtil;
import com.ratio.util.UUIDUtils;

/**
 * the device manager controls the the devices through the Android Bluetooth interface.  It contains the bluetooth manager
 * and adapter, and the list of devices which have been discovered.  It maintains a background looper thread. which is
 * needed to receive callbacks from the bluetooth scan, which reports discovered devices through ScanCallback, and
 * through the BluetoothGattCallback, which is a per-device callback.  It uses a timer to "ping" the looper thread.  
 * TODO: determine if the timer can be disabled after the scan phase
 * @author matt2
 *
 */
public class BTLEDeviceManager {
	private final static String TAG = BTLEDeviceManager.class.getSimpleName();
	private final static int MAX_RETRY_COUNT = 5;
    
	// this is the callback interface to the device service, so the application can be notified asynchronously 
	// when events occur.
	public interface DeviceManagerCallback {
		void onDiscoveryStarted();
		void onDeviceDiscovered(BluetoothDevice device);
		void onDiscoveryStopped();
		void onGattConnectionState(BluetoothDevice device, BluetoothGatt gatt, int connState);
		void onServicesDiscovered(BluetoothDevice device, BluetoothGatt gatt);
		void onCharacteristicRead(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
      	void onCharacteristicWrite(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);
      	void onCharacteristicChanged(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
		void onDescriptorRead(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattDescriptor descriptor);
		void onDescriptorWrite(BluetoothDevice device, BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status);
		void onReadRemoteRssi(BluetoothDevice device, int rssi, int status);
		void onRetryReconnect(BluetoothDevice device, int retriesLeft);
		void onReconnectFailed(BluetoothDevice device);
		void onError(int errorCode, String error, String deviceAddress);
	}
		
	/**
	 * there is a 1:1 relationship between a bluetooth device and a bluetooth gatt.  We hold on to the connection state
	 * because there was some weird issue with actually getting it from BluetoothGatt.
	 * @author mreynolds.
	 *
	 */
	public class BTDeviceInfo {
		private BluetoothDevice			mDevice;
		private BluetoothGatt			mGatt;
		private int						mConnectionState;				// see BluetoothProfile.STATE_CONNECTED, etc
		private boolean					mfDisconnectRequest;			// to differentiate intentional vs unintentional disconnects.
		private int						mRetryCount;					// how many times have we retried to connect to this device?
		private boolean					mfRetrying;						// retrying connection.  Don't send broadcast
		private ArrayDeque<BTLECommand> mCommandQueue;					// queue of commands because BTLE doesn't queue them for us
		private TimerTask				mConnTimeout;					// connection timeout timer task
		private long					mConnTimeoutMsec;				// connection timeout in msec
		
		public BTDeviceInfo(BluetoothDevice device) {
			mDevice = device;
			mGatt = null;
			mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
			mfDisconnectRequest = false;
			mRetryCount = MAX_RETRY_COUNT;
			mCommandQueue = new ArrayDeque<BTLECommand>();
			mConnTimeout = null;
			mConnTimeoutMsec = 0;
			mfRetrying = false;
		}
		
		public void setGatt(BluetoothGatt gatt) {
			mGatt = gatt;
		}
		
		public BluetoothGatt getGatt() {
			return mGatt;
		}
		
		public BluetoothDevice getDevice() {
			return mDevice;
		}
		
		public String getDeviceAddress() {
			return mDevice.getAddress();
		}
		
		public int getConnectionState() {
			return mConnectionState;
		}
		
		public void setConnectionState(int connectionState) {
			mConnectionState = connectionState;
		}
		
		public boolean isDisconnectRequest() {
			return mfDisconnectRequest;
		}
		
		public void setDisconnectRequest(boolean f) {
			mfDisconnectRequest = f;
		}
		
		public int getRetryCount() {
			return mRetryCount;
		}
		
		public void setRetryCount(int count) {
			mRetryCount = count;
		}
		
		public void decrementRetryCount() {
			mRetryCount--;
		}
		
		public boolean isRetrying() {
			return mfRetrying;
		}
		
		public void setRetrying(boolean f) {
			mfRetrying = f;
		}
		
		public BTLECommand peekCommand() {
			return mCommandQueue.peek();	
		}
		
		public BTLECommand popCommand() {
			return mCommandQueue.pop();
		}
		
		// wipe the command queue.
		public synchronized void wipeCommandQueue() {
			mCommandQueue.clear();
		}
		
		// pop the previously enqueued command, and execute the next command in the command queue if there is one
		public synchronized boolean executeNextCommand() {
			if (!mCommandQueue.isEmpty()) {
				// pop the previously queued command
				BTLECommand command = mCommandQueue.pop();
				Log.d(TAG, "execute: popping " + command);
				if (!mCommandQueue.isEmpty()) {
					command = mCommandQueue.peek();
					try {
						Log.d(TAG, "execute: executing " + command);
						command.execute(BTLEDeviceManager.this);
					} catch (DeviceManagerException dmex) {
						BTLEDeviceManager.this.getCallback().onError(DeviceErrorCodes.ERROR_ENQUEUING_COMMAND, dmex.getMessage(), this.getDeviceAddress());					
					}
					return true;
				} else {
					Log.d(TAG, "execute: command queue is empty");
				}
			}
			return false;
		}
		
		// enqeue a command for the device.  If the command queue is empty, then execute it immediately, but push
		// the command anyway so it'll get popped before the next one is executed
		public synchronized void enqueueCommand(BTLECommand command) {
			Log.d(TAG, "enqueue: queueing " + command);
			if (mCommandQueue.isEmpty()) {
				try {
					Log.d(TAG, "enqueue: executing immediately " + command);
					command.execute(BTLEDeviceManager.this);
				} catch (DeviceManagerException dmex) {
					BTLEDeviceManager.this.getCallback().onError(DeviceErrorCodes.ERROR_ENQUEUING_COMMAND, dmex.getMessage(), this.getDeviceAddress());
				}
			} 
			mCommandQueue.addLast(command);
		}
		
		public TimerTask getConnTimeoutTimerTask() {
			return mConnTimeout;
		}
		
		public long getConnectionTimeout() {
			return mConnTimeoutMsec;
		}
		
		public void setConnectionTimeout(long msec) {
			mConnTimeoutMsec = msec;
		}
		
		public void startTimeout() {
			if (mConnTimeoutMsec != 0) {
				mConnTimeout = new ConnectionTimeoutTimerTask(this);
				BTLEDeviceManager.sTimer.schedule(mConnTimeout, mConnTimeoutMsec);
			}
		}
		
		public void killTimeout() {
			if (mConnTimeout != null) {
				mConnTimeout.cancel();
				mConnTimeout = null;
			}
		}
		
	};
	
	/**
	 * unfortunately, somtimes BTLE just doesn't want to connect, doesn't give any errors, just doesn't do a damn thing,
	 * so we have to set a timer which gives up when a specified time elapses.
	 * @author matt2
	 *
	 */
	protected class ConnectionTimeoutTimerTask extends TimerTask {
		protected BTDeviceInfo	mDeviceInfo;
		
		public ConnectionTimeoutTimerTask(BTDeviceInfo deviceInfo) {
			mDeviceInfo = deviceInfo;
		}
		
		@Override
		public void run() {
			try {
				disconnect(mDeviceInfo);
			} catch (DeviceManagerException dmex) {
				dmex.printStackTrace();
			}
			String error = BTLEDeviceManager.this.mContext.getResources().getString( R.string.connection_timeout);
			BTLEDeviceManager.this.mDeviceManagerCallback.onError(DeviceErrorCodes.ERROR_CONNECTION_TIMEOUT, error, mDeviceInfo.getDeviceAddress());
		}
	}
	
    private static final int DEVICE_SCAN_INTERVAL_MSEC = 250;		// interval between scans (pinger timer)
    private static final int HANDLER_POLL_TIMER_MSEC = 20;			// wait poll timer for handler in scan handler loop

    private DeviceManagerCallback 	mDeviceManagerCallback;			// interface for external service
    private BluetoothManager 		mBluetoothManager;				// THE bluetooth manager
    private BluetoothAdapter 		mBluetoothAdapter;				// THE bluetooth adapter
    private static Timer 			sTimer = null;					// timer to ping the BTLE scan thread and keep it lively.
    private PingTimerTask			mPingTimerTask;					// timer task to ping for discovering devices.
    private ScanDeviceHandlerThread mScanThread;					// handler thread for the BTLE scan, so timer/cancel it.
    private List<BTDeviceInfo> 		mDeviceList;					// list of devices from last scan.
    private ScanDeviceCallback 		mScanDeviceCallback;			// reports discovered devices
    private Context 				mContext;						// to obtain string resources and access system services.
	private UUID[]					mUUIDFilterList = null;			// list of UUIDs to filter on scan
	
    // constructor. Assign mmebers from parameters and call initialize
    public BTLEDeviceManager(Context context, DeviceManagerCallback deviceManagerCallback) throws DeviceManagerException {
    	mContext = context;
    	mDeviceManagerCallback = deviceManagerCallback;
		initialize();
	}
    
    // called from the service, so it can send an error if initialization fails.
    public boolean initialize() throws DeviceManagerException {

        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                throw new DeviceManagerException(mContext.getString(R.string.bluetooth_manager_initialization_failed));
            }
        }

        // get the bluetooth adapter.
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
        	throw new DeviceManagerException(mContext.getString(R.string.unable_to_obtain_adapter));
        }
        
        mScanDeviceCallback = new ScanDeviceCallback();
		mDeviceList = new ArrayList<BTDeviceInfo>();	
		getConnectedDevices(mDeviceList);
        return true;
    }
    
    /**
     * the scan looper thread is neccessary, otherwise the ScanCallback isn't called.
     */
    protected void startScanThread() {
        // set up the timer to ping the looper thread, set up the device scan callback, get the devices that are
        // already connected, and start the scan looper thread.
    	if (sTimer == null) {
    		sTimer = new Timer();
    	}
       	if (mScanThread == null) {
       		mScanThread = new ScanDeviceHandlerThread("Scan Devices");
       		mScanThread.start();
       	}
		Handler handler = null;
		
		// this is fairly ugly.  We have to wait for the HandlerThread o have a looper before we can install a handler
		while (handler == null) {
			handler = mScanThread.getHandler();
			try {
				Thread.sleep(HANDLER_POLL_TIMER_MSEC);
			} catch (Exception ex) {
				
			}
		}
		// even uglier: the timer acts a a "pinger" every 250 msec to keep the message loop looping, otherwise it just hangs.
		mPingTimerTask = new PingTimerTask(handler);
		sTimer.scheduleAtFixedRate(mPingTimerTask, 0, DEVICE_SCAN_INTERVAL_MSEC); 
   }
   
    /**
     * even though we have a handler thread, we also need this timer task to ping the message loop, otherwise
     * the scan callbacks don't get called
     *
     */
    private class PingTimerTask extends TimerTask {
    	protected Handler	 mHandler;
    	protected int 		mCount;
    	
    	public PingTimerTask(Handler handler) {
    		mHandler = handler;
    		mCount = 0;
    	}
  
    	public void run() {
    		mHandler.post(new Runnable() {
    			public void run() {
    				if (mCount++ % 20 == 0) {
    					Log.d(TAG, "ping!");
    				}
    			}
    		});
    	}
    }
   
    /**
     * this is to support the workaround where we disable, then re-enable the bluetooth adapter when we
     * rescan for devices
     */
    public boolean disableBluetoothAdapter() {
    	if (mBluetoothAdapter.isEnabled()) {
    		mBluetoothAdapter.disable();
    		return true;
    	} else {
    		mBluetoothAdapter.enable();
    		return false;
    	}
    }
    
    
    // NOTE: we need to send a broadcast of all the currently connected devices, since they won't be
    // reported by our scanning code.
    private void getConnectedDevices(List<BTDeviceInfo> deviceInfoList) {
    	List<BluetoothDevice> deviceList = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
    	for (BluetoothDevice device : deviceList) {
    		BTDeviceInfo deviceInfo = new BTDeviceInfo(device);
    		deviceInfoList.add(deviceInfo);
    	}
    }
 
    
	public void stopLeScan() {
	   	// http://developer.android.com/reference/android/bluetooth/BluetoothAdapter.html#stopLeScan(android.bluetooth.BluetoothAdapter.LeScanCallback)
	   	// callback	used to identify which scan to stop must be the same handle used to start the scan
        mBluetoothAdapter.stopLeScan(mScanDeviceCallback);
        mDeviceManagerCallback.onDiscoveryStopped();
        synchronized (this) {
            if (mPingTimerTask != null) {
                mPingTimerTask.cancel();
                mPingTimerTask = null;
            }   
        }
	}
	
	/**
	 *  scan (or stop scanning) for bluetooth LE devices. While it would be nice to apply the filter in BluetoothAdapter.startLeScan(), 
	 *  it blocks any devices from getting returned
	 * @param uuidList
	 * @param scanPeriodMsec
	 * @return
	 */
    public boolean scanLeDevice(final UUID[] uuidList, final int scanPeriodMsec) {
		startScanThread();
		mDeviceManagerCallback.onDiscoveryStarted();
    	mDeviceList = new ArrayList<BTDeviceInfo>();
    	mUUIDFilterList = uuidList;
    	// set scanning flag immediately, since the timer and other runnables query it and change state
		// post the start scan as a runnable, since it needs the looper to scan for devices.
 		mScanThread.getHandler().post(new Runnable() {
			public void run() {
				mBluetoothAdapter.startLeScan(mScanDeviceCallback);
			}
		});
		// post a delayed runnable which stops the scan.
		mScanThread.getHandler().postDelayed(new Runnable() {
			public void run() {
				stopLeScan();
			}
		}, scanPeriodMsec);
		return true;
   }
       
    
    // handler thread for the Bluetooth LE scan thread
    private class ScanDeviceHandlerThread extends HandlerThread {
    	protected Handler mHandler;
    	
    	public ScanDeviceHandlerThread(String name) {
			super(name);
			// TODO Auto-generated constructor stub
		}
    	
    	protected void onLooperPrepared() {
    		mHandler = new Handler();
    	}
    	
    	public Handler getHandler() {
    		return mHandler;
    	}
    }

    // Device scan callback.  Called when a device is discovered
    private class ScanDeviceCallback implements BluetoothAdapter.LeScanCallback {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
        	
        	// the ReadingServiceUUIDString, "B698290F-7562-11E2-B50D-00163E46F8FE" is stored in the scan record
        	// at offset 5, with bytes reversed.  This may be a bit more generic, however
            byte[] reversedUUIDbytes = new byte[16];
            for (int i = 0; i < 16; i++) {
            	reversedUUIDbytes[i] = scanRecord[i + 5];
            }
        	byte[] UUIDBytes = BitUtils.reverse(reversedUUIDbytes);
        	UUID uuidTest = UUIDUtils.fromByteArray(UUIDBytes, 0);
           	Log.d(TAG, "UUID bytes = "+ StringUtil.toHexCode(UUIDBytes) + 
           			   "\nreversed = " + StringUtil.toHexCode(reversedUUIDbytes) +
           			   "\nonLeScan UUID = " + uuidTest.toString());
        	boolean fFilterMatch = true;
        	if (mUUIDFilterList != null) {
        		fFilterMatch = false;
        		for (UUID uuidCand : mUUIDFilterList) {
        			if (uuidTest.equals(uuidCand)) {
        				fFilterMatch = true;
        			}
        		}
        	}
        	if (fFilterMatch) {
	        	
	        	// TODO: how do we know that this is adequate to prevent duplicates?  Suppose we re-scan
	        	// and the same device comes back with a newly allocated structure?  What is the definition
	        	// for BluetoothDevice.equals()?
	        	if (!BTLEDeviceManager.this.inDeviceList(device)) {
	        		mDeviceList.add(new BTDeviceInfo(device));
	        		mDeviceManagerCallback.onDeviceDiscovered(device);
	         	}
	        }
        }
    };

    /**
     * Implements callback methods for GATT events that the app cares about.  Connection change, services discovered,
     * and characteristic changed.  The GATT callback is instantiated per-device.
     * This is where most of the action is in the device manager.  As well as receiving device GATT events, and calling
     * the callback functions, it also manages the device queues.  Unfortunately, BTLE works asynchronously, and doesn't
     * provide a queue for requests, so you'll notice enqueueCommand() and executeNextCommand() at the end of most of the 
     * callbacks.  This executes the next command when the callback from the previous one has finished executing.
     *
     */
    private class BTGattCallback extends BluetoothGattCallback {
    	private BTDeviceInfo 	mDeviceInfo;				// back reference to device information for this callback
    	
    	public BTGattCallback(BTDeviceInfo deviceInfo) {
    		mDeviceInfo = deviceInfo;
    	}
    	
    	// notify the service if the device
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        	Log.i(TAG, "onConnectionStateChange status = " + status + " state = " + newState);
        	if (status != BluetoothGatt.GATT_SUCCESS) {
	            // if there was a status error, DO NOT EXECUTE ANYMORE COMMANDS ON THIS DEVICE, and disconnect from it.
        		mDeviceInfo.wipeCommandQueue();
        		try {
        			disconnect(mDeviceInfo);
        		} catch (DeviceManagerException dmex) {
    				BTLEDeviceManager.this.mDeviceManagerCallback.onError(DeviceErrorCodes.ERROR_DISCONNECT, dmex.getMessage(), mDeviceInfo.getDeviceAddress());
    			}
        		String errMsg = mContext.getResources().getString(R.string.bad_status_code) + " "+ gatt.getDevice().getAddress();
        		BTLEDeviceManager.this.mDeviceManagerCallback.onError(DeviceErrorCodes.ERROR_CONNECT_STATUS, errMsg, mDeviceInfo.getDeviceAddress());
        	} else {
	        	Log.i(TAG, "on connection state change " + gatt.getDevice().getAddress() + " status = " + status + " newState = " + newState);
	            if (newState == BluetoothProfile.STATE_CONNECTED) {
	            	
	            	// we've connected, so we can kill the timeout
	            	mDeviceInfo.killTimeout();
	              	mDeviceInfo.setRetrying(false);
	              	mDeviceInfo.setRetryCount(MAX_RETRY_COUNT);

	            	// once we've connected, then we enqueue a request to discover the device services.
	            	mDeviceInfo.setConnectionState(BluetoothProfile.STATE_CONNECTED);
	            	BTLEDeviceManager.this.mDeviceManagerCallback.onGattConnectionState(mDeviceInfo.getDevice(), gatt, BluetoothProfile.STATE_CONNECTED);
	                Log.i(TAG, "Connected to GATT server.");
	            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
	              	// close in here, otherwise, we don't get the disconnect message.
	              	mDeviceInfo.getGatt().close();
	              	mDeviceInfo.setGatt(null);
	            	if (mDeviceInfo.isDisconnectRequest()) {
		              	mDeviceInfo.setConnectionState(BluetoothProfile.STATE_DISCONNECTED);		              	
		            	BTLEDeviceManager.this.mDeviceManagerCallback.onGattConnectionState(mDeviceInfo.getDevice(), gatt,  BluetoothProfile.STATE_DISCONNECTED);
	            	} else {
	            		
	            		// attempt to reconnect if the disconnection wasn't requested by the caller.  Call the callbacks for
	            		// retry and reconnect failure if the max # of retries is exceeded.  
	            		if (!mDeviceInfo.isRetrying()) {
			            	BTLEDeviceManager.this.mDeviceManagerCallback.onGattConnectionState(mDeviceInfo.getDevice(), gatt,  BluetoothProfile.STATE_DISCONNECTED);
	            		}	
	            		if (mDeviceInfo.getRetryCount() > 0) {
	            			mDeviceInfo.setRetrying(true);
	            			mDeviceInfo.decrementRetryCount();
	            			try {
	            				BTLEDeviceManager.this.mDeviceManagerCallback.onRetryReconnect(mDeviceInfo.getDevice(), mDeviceInfo.getRetryCount());
	            				connect(mDeviceInfo, mDeviceInfo.getConnectionTimeout());
	            			} catch (DeviceManagerException dmex) {
	            				BTLEDeviceManager.this.mDeviceManagerCallback.onError(DeviceErrorCodes.ERROR_RECONNECT, dmex.getMessage(), mDeviceInfo.getDeviceAddress());
	            			}
	            		} else {
	            			mDeviceInfo.setRetrying(false);
	            			BTLEDeviceManager.this.mDeviceManagerCallback.onReconnectFailed(mDeviceInfo.getDevice());
	            		}
	            	}
	            }
	            mDeviceInfo.executeNextCommand();
        	} 
        }

        // just notify the caller that services have been discovered for the specified device.
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
           	Log.i(TAG, "on services discovered " + gatt.getDevice().getAddress() + " status = " + status);
             if (status == BluetoothGatt.GATT_SUCCESS) {
               	mDeviceInfo.setGatt(gatt);
                BTLEDeviceManager.this.mDeviceManagerCallback.onServicesDiscovered(mDeviceInfo.getDevice(), gatt);
            } else {
            	String error = String.format(mContext.getResources().getString(R.string.service_discovery_error), gatt.getDevice().getName(), status);
            	BTLEDeviceManager.this.mDeviceManagerCallback.onError(DeviceErrorCodes.ERROR_SERVICES_DISCOVERED, error, mDeviceInfo.getDeviceAddress());
            	try {
                   	mDeviceInfo.setConnectionState(BluetoothProfile.STATE_DISCONNECTED);
                    BTLEDeviceManager.this.disconnect(mDeviceInfo);
            	} catch (DeviceManagerException dmex) {
            		BTLEDeviceManager.this.mDeviceManagerCallback.onError(DeviceErrorCodes.ERROR_DISCONNECT, dmex.getMessage(), mDeviceInfo.getDeviceAddress());
            	}
            	// TODO: handle error
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
         	mDeviceInfo.executeNextCommand();
        }

        // return when a characteristic is read from the device
        @Override
        public void onCharacteristicRead(BluetoothGatt 					gatt,
                                         BluetoothGattCharacteristic 	characteristic,
                                         int 							status) {
            Log.w(TAG, "onCharacteristicRead address: " + mDeviceInfo.getDeviceAddress() + 
            		   " characteristic: " + characteristic.getUuid() + " received: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
               	BTLEDeviceManager.this.mDeviceManagerCallback.onCharacteristicRead(mDeviceInfo.getDevice(), gatt, characteristic);
            } else {
            	// TODO: handle error
                Log.w(TAG, "onCharacteristicRead received: " + status);        	
            }
            mDeviceInfo.executeNextCommand();
        }

        // callback when the characteric is changed.  This gets called when we enable notifications for a characteristic.
        @Override
        public void onCharacteristicChanged(BluetoothGatt 				gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.w(TAG, "onCharacteristicChanged device: " + gatt.getDevice().getAddress() + 
            		   " characteristic: " + characteristic.getUuid() + 
            		   " received: " + characteristic.getValue());
          	BTLEDeviceManager.this.mDeviceManagerCallback.onCharacteristicChanged(mDeviceInfo.getDevice(), gatt, characteristic);
        }
        
        // NOTE: we do NOT execute the next command on read remote RSSI, otherwise it will get lost
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        	Log.i(TAG, "onReadRemoteRssi rssi = " + rssi + " status = " + status);
        	BTLEDeviceManager.this.mDeviceManagerCallback.onReadRemoteRssi(mDeviceInfo.getDevice(), rssi, status);
        }
 
        @Override
     	public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
           	Log.i(TAG, "onCharacteristicWrite " + characteristic.getUuid()); 
           	BTLEDeviceManager.this.mDeviceManagerCallback.onCharacteristicWrite(mDeviceInfo.getDevice(), gatt, characteristic, status);
           	mDeviceInfo.executeNextCommand();
        }
     
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        	Log.i(TAG, "onDescriptorRead " + descriptor.getUuid());
           	BTLEDeviceManager.this.mDeviceManagerCallback.onDescriptorRead(mDeviceInfo.getDevice(), gatt, descriptor);
   
           	mDeviceInfo.executeNextCommand();
        }
        
        @Override
        public void	onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
           	Log.i(TAG, "onDescriptorWrite " + descriptor.getUuid());
           	BTLEDeviceManager.this.mDeviceManagerCallback.onDescriptorWrite(mDeviceInfo.getDevice(), gatt, descriptor, status);
           	mDeviceInfo.executeNextCommand();                  	
        }
    };

    /**
     * connect call which is exposed to the service.
     * @param address device MAC format address XX:XX:XX..
     * @param timeoutMsec connection timeout in milliseconds
     * @return true.  return the truth
     * @throws DeviceNameNotFoundException
     * @throws DeviceManagerException
     */
    public boolean connect(String address, final long timeoutMsec) throws DeviceNameNotFoundException, DeviceManagerException {
    	final BTDeviceInfo deviceInfo = getDeviceInfo(address);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		
    	}
        deviceInfo.setDisconnectRequest(false);
		deviceInfo.setRetryCount(MAX_RETRY_COUNT);
		deviceInfo.setRetrying(false);
    	// TODO: is this post() call neccessary?
    	mScanThread.getHandler().post(new Runnable() {
    		public void run() {
    			try {
    				deviceInfo.setConnectionTimeout(timeoutMsec);
    				connect(deviceInfo, timeoutMsec);
    			} catch (Exception ex) {
    				// TODO: add an onError to the interface and service.
    				ex.printStackTrace();
    			}
    		}
    	});
    	return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @param timeoutMsec set a timer to give up on the connection and throw an error. set 0 for "forever"
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    private boolean connect(final BTDeviceInfo deviceInfo, long timeoutMsec) throws DeviceManagerException {
        if (mBluetoothAdapter == null) {
        	throw new DeviceManagerException(mContext.getString(R.string.adapter_uninitialized));
        }
        if (deviceInfo == null) {
        	throw new DeviceManagerException(mContext.getString(R.string.device_unspecified));
        }
        if (timeoutMsec != 0) {
        	deviceInfo.startTimeout();
        }
        
        // TODO: we need to ensure that the previous GATT callback for this device is inactive.  While the docs
        // say it should be, I don't believe them
        deviceInfo.setGatt(deviceInfo.getDevice().connectGatt(mContext, false, new BTGattCallback(deviceInfo)));
        deviceInfo.setConnectionState(BluetoothProfile.STATE_CONNECTING);
        Log.d(TAG, "Trying to create a new connection." + deviceInfo.getDeviceAddress());
        return true;
    }

    /**
     * disconnect interface which is exposed to the device service.
     * @param address
     * @return
     * @throws DeviceNameNotFoundException
     * @throws DeviceManagerException
     */
    public boolean disconnect(String address) throws DeviceNameNotFoundException, DeviceManagerException {
    	final BTDeviceInfo deviceInfo = getDeviceInfo(address);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		
    	}
        return disconnect(deviceInfo);
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean disconnect(BTDeviceInfo deviceInfo) throws DeviceManagerException {
        if (mBluetoothAdapter == null) {
        	throw new DeviceManagerException(mContext.getString(R.string.adapter_uninitialized));
        }
        if (deviceInfo.getGatt() == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
        	throw new DeviceManagerException(String.format("%s %s", mContext.getString(R.string.no_gatt_info), deviceInfo.getDeviceAddress()));
        }
       	// we have to do this explicitly, since the bluetooth gatt callback doesn't always report connection state back to us.
       	deviceInfo.setConnectionState(BluetoothProfile.STATE_DISCONNECTED);
        deviceInfo.setDisconnectRequest(true);
        deviceInfo.getGatt().disconnect();
        return true;
    }
    
    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public boolean readCharacteristic(BTDeviceInfo deviceInfo, BluetoothGattCharacteristic characteristic) throws DeviceManagerException {
        if (mBluetoothAdapter == null) {
        	throw new DeviceManagerException(mContext.getString(R.string.adapter_uninitialized));
        }
        if (deviceInfo.getGatt() == null) {
            throw new DeviceManagerException(String.format("%s %s", mContext.getString(R.string.no_gatt_info), deviceInfo.getDeviceAddress()));
        }
    	deviceInfo.enqueueCommand(new BTLECommandReadCharacteristic(deviceInfo, characteristic));
    	return true;
    }
    
    /**
     * call to read a characteristic from a service published by a device. Note the actual value is returned by the callback
     * interface for onCharacteristicRead()
     * @param address device MAC address (XX:XX:XX)
     * @param serviceUUID service unique ID
     * @param characteristicUUID characteristic unique ID (within the service
     * @return BluetoothGatt.readCharacteristic() true/false
     * @throws DeviceNameNotFoundException
     * @throws DeviceManagerException
     */
    public boolean readCharacteristic(String 	address, 
									  UUID		serviceUUID,
									  UUID		characteristicUUID) throws DeviceNameNotFoundException, DeviceManagerException {
    	
    	BTDeviceInfo deviceInfo = getDeviceInfo(address);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
    	}
    	BluetoothGattService service = deviceInfo.getGatt().getService(serviceUUID);
    	if (service == null) {
    		throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.service_not_found), address, serviceUUID));
    	}
    	BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
    	if (characteristic == null) {
    		throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.characteristic_not_found), address, characteristicUUID));
    	}
    	return readCharacteristic(deviceInfo, characteristic);
    }

    /**
     * enable/disable notification for the specified characteristic in the specified service for the device referenced by address
     * @param address device address (MAC: format)
     * @param serviceUUID service UUID
     * @param characteristicUUID characteristic UUID 
     * @param enabled enabled/disabled
     * @throws DeviceNameNotFoundException
     * @throws DeviceManagerException
     */
    public void setCharacteristicNotification(String 	address,
    										  UUID		serviceUUID,
    										  UUID		characteristicUUID,
    										  boolean	enabled) throws DeviceNameNotFoundException, DeviceManagerException {
    	BTDeviceInfo deviceInfo = getDeviceInfo(address);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
    	}
    	BluetoothGattService service = deviceInfo.getGatt().getService(serviceUUID);
    	if (service == null) {
    		throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.service_not_found), address, serviceUUID));
    	}
    	BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
    	if (characteristic == null) {
    		throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.characteristic_not_found), address, characteristicUUID));
    	}
    	setCharacteristicNotification(deviceInfo, characteristic, enabled);
   }
    
   /**
     * Enables or disables notification on a give characteristic.
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     * @param immediate setNotification immediately, don't enqueue
     */
    public void setCharacteristicNotification(BTDeviceInfo 					deviceInfo, 
    										  BluetoothGattCharacteristic 	characteristic,
                                              boolean 						enabled) throws DeviceManagerException {
    	Log.d(TAG, "setCharacteristicNotification " + deviceInfo.getDeviceAddress() + " name = " + deviceInfo.getDevice().getName());
        if (mBluetoothAdapter == null) { 
           	throw new DeviceManagerException(mContext.getString(R.string.adapter_uninitialized));
        }
        if (deviceInfo.getGatt() == null) {
            throw new DeviceManagerException(String.format("%s %s", mContext.getString(R.string.no_gatt_info), deviceInfo.getDeviceAddress()));
        }
        
        // if there are no notification requests outstanding, we can issue one right away.
        deviceInfo.enqueueCommand(new BTLECommandSetCharacteristicNotification(deviceInfo, characteristic, true));
        // this is some serious "guess the magic word".  You have to write this characteristic to receive notififcations.
        // setCharacteristicNotification() isn't enough.
        // from http://stackoverflow.com/questions/17910322/android-ble-api-gatt-notification-not-received
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(BTUUID.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID));
        descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[] { 0x00, 0x00 });
		deviceInfo.enqueueCommand(new BTLECommandWriteDescriptor(deviceInfo, descriptor));
    }
    
    // write a string characterisic.
    public void writeCharacteristic(String 	address,
								    UUID	serviceUUID,
								    UUID	characteristicUUID,
								    String	value) throws DeviceNameNotFoundException, DeviceManagerException {
		BTDeviceInfo deviceInfo = getDeviceInfo(address);
		if (deviceInfo == null) {
			throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
		}
		BluetoothGattService service = deviceInfo.getGatt().getService(serviceUUID);
		if (service == null) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.service_not_found), address, serviceUUID));
		}
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
		if (characteristic == null) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.characteristic_not_found), address, characteristicUUID));
		}
		if (!writeCharacteristic(deviceInfo, characteristic, value)) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.write_characteristic_failed), address, characteristicUUID));
		}
	}

   
    // write a string characteristic
    public boolean writeCharacteristic(BTDeviceInfo 				deviceInfo, 
    							       BluetoothGattCharacteristic 	characteristic,
    							       String						value) throws DeviceManagerException {
	    if (mBluetoothAdapter == null) { 
           	throw new DeviceManagerException(mContext.getString(R.string.adapter_uninitialized));
        }
        if (deviceInfo.getGatt() == null) {
            throw new DeviceManagerException(String.format("%s %s", mContext.getString(R.string.no_gatt_info), deviceInfo.getDeviceAddress()));
        }
        characteristic.setValue(value);
		deviceInfo.enqueueCommand(new BTLECommandWriteCharacteristic(deviceInfo, characteristic));
		return true;
   }
 
    // write a byte array characteristic
    public void writeCharacteristic(String 	address,
								    UUID	serviceUUID,
								    UUID	characteristicUUID,
								    byte[]	value) throws DeviceNameNotFoundException, DeviceManagerException {
		BTDeviceInfo deviceInfo = getDeviceInfo(address);
		if (deviceInfo == null) {
			throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
		}
		BluetoothGattService service = deviceInfo.getGatt().getService(serviceUUID);
		if (service == null) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.service_not_found), address, serviceUUID));
		}
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
		if (characteristic == null) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.characteristic_not_found), address, characteristicUUID));
		}
		if (!writeCharacteristic(deviceInfo, characteristic, value)) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.write_characteristic_failed), address, characteristicUUID));
		}
	}

    // write a bytearray characteristic
    public boolean writeCharacteristic(BTDeviceInfo 				deviceInfo, 
									   BluetoothGattCharacteristic 	characteristic,
									   byte[]						value) throws DeviceManagerException {
		if (mBluetoothAdapter == null) { 
			throw new DeviceManagerException(mContext.getString(R.string.adapter_uninitialized));
		}
		if (deviceInfo.getGatt() == null) {
			throw new DeviceManagerException(String.format("%s %s", mContext.getString(R.string.no_gatt_info), deviceInfo.getDeviceAddress()));
		}
		characteristic.setValue(value);
		deviceInfo.enqueueCommand(new BTLECommandWriteCharacteristic(deviceInfo, characteristic));
		return true;
	}
    
    // write a byte array characteristic
    public void writeCharacteristic(String 	address,
								    UUID	serviceUUID,
								    UUID	characteristicUUID,
								    int		value,
								    int		format,
								    int		offset) throws DeviceNameNotFoundException, DeviceManagerException {
		BTDeviceInfo deviceInfo = getDeviceInfo(address);
		if (deviceInfo == null) {
			throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
		}
		BluetoothGattService service = deviceInfo.getGatt().getService(serviceUUID);
		if (service == null) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.service_not_found), address, serviceUUID));
		}
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
		if (characteristic == null) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.characteristic_not_found), address, characteristicUUID));
		}
		if (!writeCharacteristic(deviceInfo, characteristic, value, format, offset)) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.write_characteristic_failed), address, characteristicUUID));
		}
	}
    
    // write an integer characteristic with format and offset
    public boolean writeCharacteristic(BTDeviceInfo 				deviceInfo, 
									   BluetoothGattCharacteristic 	characteristic,
									   int							value,
									   int							format,
									   int							offset) throws DeviceManagerException {
		if (mBluetoothAdapter == null) { 
			throw new DeviceManagerException(mContext.getString(R.string.adapter_uninitialized));
		}
		if (deviceInfo.getGatt() == null) {
			throw new DeviceManagerException(String.format("%s %s", mContext.getString(R.string.no_gatt_info), deviceInfo.getDeviceAddress()));
		}
		characteristic.setValue(value, format, offset);
		deviceInfo.enqueueCommand(new BTLECommandWriteCharacteristic(deviceInfo, characteristic));
		return true;
	}
    
    public boolean writeDescriptor(String	address,
    							   UUID		serviceUUID,
    							   UUID		characteristicUUID,
    							   UUID		descriptorUUID,
								   byte[]	value) throws DeviceManagerException, DeviceNameNotFoundException {
		BTDeviceInfo deviceInfo = getDeviceInfo(address);
		if (deviceInfo == null) {
			throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
		}
		BluetoothGattService service = deviceInfo.getGatt().getService(serviceUUID);
		if (service == null) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.service_not_found), address, serviceUUID));
		}
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
		if (characteristic == null) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.characteristic_not_found), address, characteristicUUID));
		}
		BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
		if (descriptor == null) {
			throw new DeviceManagerException(String.format("%s %s %s %s", mContext.getString(R.string.descriptor_not_found), address, characteristicUUID, descriptorUUID));
		}
		return writeDescriptor(deviceInfo, characteristic, descriptor, value);
    }			   
    
    public boolean writeDescriptor(BTDeviceInfo 				deviceInfo,
			   					   BluetoothGattCharacteristic 	characteristic,
			   					   BluetoothGattDescriptor		descriptor,
			   					   byte[]						value) throws DeviceManagerException {
		if (mBluetoothAdapter == null) { 
			throw new DeviceManagerException(mContext.getString(R.string.adapter_uninitialized));
		}
		if (deviceInfo.getGatt() == null) {
			throw new DeviceManagerException(String.format("%s %s", mContext.getString(R.string.no_gatt_info), deviceInfo.getDeviceAddress()));
		}
		descriptor.setValue(value);
		deviceInfo.enqueueCommand(new BTLECommandWriteDescriptor(deviceInfo, descriptor));
		return true;
    }
    		
	public boolean readDescriptor(String 	address, 
								  UUID 		serviceUUID,
								  UUID 		characteristicUUID, 
								  UUID 		descriptorUUID)throws DeviceManagerException, DeviceNameNotFoundException {
		BTDeviceInfo deviceInfo = getDeviceInfo(address);
		if (deviceInfo == null) {
			throw new DeviceNameNotFoundException(String.format("%s %s",mContext.getString(R.string.device_not_found), address));
		}
		BluetoothGattService service = deviceInfo.getGatt().getService(serviceUUID);
		if (service == null) {
			throw new DeviceManagerException(String.format("%s %s %s",mContext.getString(R.string.service_not_found), address, serviceUUID));
		}
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
		if (characteristic == null) {
			throw new DeviceManagerException(String.format("%s %s %s", mContext.getString(R.string.characteristic_not_found),address, characteristicUUID));
		}
		BluetoothGattDescriptor descriptor = characteristic
				.getDescriptor(descriptorUUID);
		if (descriptor == null) {
			throw new DeviceManagerException(String.format("%s %s %s %s", mContext.getString(R.string.descriptor_not_found), address, characteristicUUID, descriptorUUID));
		}
		return readDescriptor(deviceInfo, characteristic, descriptor);
	}
	
	public boolean readDescriptor(BTDeviceInfo 					deviceInfo,
								  BluetoothGattCharacteristic 	characteristic,
								  BluetoothGattDescriptor		descriptor) throws DeviceManagerException {
		if (mBluetoothAdapter == null) { 
		   	throw new DeviceManagerException(mContext.getString(R.string.adapter_uninitialized));
		}
		if (deviceInfo.getGatt() == null) {
			throw new DeviceManagerException(String.format("%s %s", mContext.getString(R.string.no_gatt_info), deviceInfo.getDeviceAddress()));
		}
		deviceInfo.enqueueCommand(new BTLECommandReadDescriptor(deviceInfo, descriptor));
		return true;
	}

    // wrapper to get support Gatt Services for a device
    public List<BluetoothGattService> getSupportedGattServices(String address) throws DeviceNameNotFoundException {
    	BTDeviceInfo deviceInfo = getDeviceInfo(address);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
    	}
    	return getSupportedGattServices(deviceInfo);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    private List<BluetoothGattService> getSupportedGattServices(BTDeviceInfo deviceInfo) {
        if (deviceInfo.getGatt() == null) {
        	return null;
        }
        return deviceInfo.getGatt().getServices();
    }
    
    public void discoverServices(String address) throws DeviceNameNotFoundException {
    	BTDeviceInfo deviceInfo = getDeviceInfo(address);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
    	}
    	discoverServices(deviceInfo);
    }
    
    /**
     * explicitly request the discover services call.
     * @param deviceInfo
     */
    private void discoverServices(BTDeviceInfo deviceInfo) {
    	deviceInfo.enqueueCommand(new BTLECommandDiscoverServices(deviceInfo));
    }
    
    /**
     * read the remote RSSI from the device. The value is returned in BluetoothGattCallback.onRemoteRSSI()
     * @param address
     * @throws DeviceNameNotFoundException
     */
    public void readRemoteRSSI(String address) throws DeviceNameNotFoundException {
       	BTDeviceInfo deviceInfo = getDeviceInfo(address);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
    	}
    	deviceInfo.getGatt().readRemoteRssi();
    }
    
    /**
     * retrieve the list of characterstics for the specified device and service
     * @param address device address
     * @param serviceUUID service UUID
     * @return list of bluetooth GATT characteristics.
     * @throws DeviceNameNotFoundException
     */
    public List<BluetoothGattCharacteristic> getCharacteristics(String address, UUID serviceUUID) throws DeviceNameNotFoundException {
       	BTDeviceInfo deviceInfo = getDeviceInfo(address);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
    	}
    	return getCharacteristics(deviceInfo, serviceUUID);  	
    }
    
    private List<BluetoothGattCharacteristic> getCharacteristics(BTDeviceInfo deviceInfo, UUID serviceUUID) {
    	return deviceInfo.getGatt().getService(serviceUUID).getCharacteristics();
    }
    
    /**
     * are we trying to reconnect to this device?
     * @param deviceAddress
     * @return deviceInfo retryflag
     */
    public boolean isRetrying(String deviceAddress) throws DeviceNameNotFoundException {
       	BTDeviceInfo deviceInfo = getDeviceInfo(deviceAddress);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), deviceAddress));   		    		
    	}
    	return deviceInfo.isRetrying();
    }
    
    /**
     * how many retries are left on this device?
     * @param deviceAddress
     * @return
     * @throws DeviceNameNotFoundException
     */
    public int getRetryCount(String deviceAddress) throws DeviceNameNotFoundException {
       	BTDeviceInfo deviceInfo = getDeviceInfo(deviceAddress);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), deviceAddress));   		    		
    	}
    	return deviceInfo.getRetryCount();
    }
    
    /**
     * get the connection state for this device.
     * @param deviceAddress
     * @return BluetoothProfile
     * @throws DeviceNameNotFoundException
     */
    
    public int getConnectionState(String deviceAddress)  throws DeviceNameNotFoundException {
       	BTDeviceInfo deviceInfo = getDeviceInfo(deviceAddress);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), deviceAddress));   		    		
    	}
    	return deviceInfo.getConnectionState();
    }
    
    /**
     * get the parcelable profile for a device
     * @param address device MAC address
     * @return parcelable device profile.
     * @throws DeviceNameNotFoundException
     */
    
    public BTDeviceProfile getDeviceProfile(String address) throws DeviceNameNotFoundException {
       	BTDeviceInfo deviceInfo = getDeviceInfo(address);
    	if (deviceInfo == null) {
    		throw new DeviceNameNotFoundException(String.format("%s %s", mContext.getString(R.string.device_not_found), address));   		    		
    	}
    	return new BTDeviceProfile(deviceInfo.getDevice(), deviceInfo.getGatt(), deviceInfo.getConnectionState());   	
    }
    
    /**
     * search the list of device info for the device with the matching name.
     * @param address
     * @return
     */
    public BTDeviceInfo getDeviceInfo(String address) {
    	for (BTDeviceInfo deviceInfo : mDeviceList) {
    		if (deviceInfo.getDeviceAddress().equals(address)) {
    			return deviceInfo;
    		}
    	}
    	return null;
    }
    
    /**
     * does the MAC address of this device match any device that we have scanned previously?
     */
    public boolean inDeviceList(BluetoothDevice device) {
    	return getDeviceInfo(device.getAddress()) != null;
    }
    
    // turn off discovery, disconnect from any connected devices, kill the timer, and quit the scan thread.
    public void shutdown() {
    	if (mBluetoothAdapter != null) {
    		mBluetoothAdapter.cancelDiscovery();
    	}
    	for (BTDeviceInfo deviceInfo : mDeviceList) {
   	        deviceInfo.setDisconnectRequest(true);
   	        if (deviceInfo.getConnectionState() != BluetoothProfile.STATE_DISCONNECTED) {
   	        	if (deviceInfo.getGatt() != null) {
   	        		deviceInfo.getGatt().disconnect();  
   	        	}
    		}
    	}
    	if (sTimer != null) {
    		sTimer.purge();
    	}
    	if (mScanThread != null) {
    		mScanThread.quitSafely();
    	}
    }
     
    public Context getContext() {
    	return mContext;
    }
    
    public DeviceManagerCallback getCallback() {
    	return mDeviceManagerCallback;
    }
}
