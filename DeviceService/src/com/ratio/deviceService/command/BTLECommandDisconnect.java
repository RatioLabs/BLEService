package com.ratio.deviceService.command;

import android.util.Log;

import com.ratio.deviceService.BTLEDeviceManager;
import com.ratio.exceptions.DeviceManagerException;

/**
 * Queued command to read a character from the BTLE device
 */
public class BTLECommandDisconnect extends BTLECommand {
	
	public BTLECommandDisconnect(BTLEDeviceManager.BTDeviceInfo	deviceInfo) {
		super(deviceInfo);
	}
	
	public boolean execute(BTLEDeviceManager deviceManager) throws DeviceManagerException {
		mDeviceInfo.setDisconnectRequest(true);
		mDeviceInfo.getGatt().disconnect();
        mDeviceInfo.getGatt().close();
		mDeviceInfo.setGatt(null);
		Log.i(TAG, "disconnecting from device");
        return true;
	}
	
	public String toString() {
		return BTLECommandDisconnect.class.getSimpleName() + " addr: " + mDeviceInfo.getDeviceAddress();
	}
}
