package com.ratio.deviceService.command;

import com.ratio.deviceService.BTLEDeviceManager;
import com.ratio.exceptions.DeviceManagerException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;

/**
 * since commands are executed asynchronously with callbacks, but new commands "blow away" previous commands
 * if the previous command's callback has not been called, we used a queue in the BluetoothGattCallack, and 
 * requested commands are enqueued, then executed when the previous callback has completed.
 * @author matt2
 *
 */
public abstract class BTLECommand {
	protected static final String TAG = BTLECommand.class.getSimpleName();
	protected BTLEDeviceManager.BTDeviceInfo mDeviceInfo;
	
	public BTLECommand(BTLEDeviceManager.BTDeviceInfo deviceInfo) {
		mDeviceInfo = deviceInfo;
	}
	
	public abstract boolean execute(BTLEDeviceManager deviceManager) throws DeviceManagerException;
}
