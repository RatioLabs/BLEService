package com.ratio.deviceService.command;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import com.ratio.deviceService.BTLEDeviceManager;
import com.ratio.exceptions.DeviceManagerException;

/**
 * Queued command to read a character from the BTLE device
 */
public class BTLECommandDiscoverServices extends BTLECommand {
	
	public BTLECommandDiscoverServices(BTLEDeviceManager.BTDeviceInfo	deviceInfo) {
		super(deviceInfo);
	}
	
	public boolean execute(BTLEDeviceManager deviceManager) throws DeviceManagerException {
		boolean fSuccess = mDeviceInfo.getGatt().discoverServices();
        Log.i(TAG, "Attempting to start service discovery:" + fSuccess);
        return fSuccess;
	}
	
	public String toString() {
		return BTLECommandDiscoverServices.class.getSimpleName() + " addr: " + mDeviceInfo.getDeviceAddress();
	}
}
