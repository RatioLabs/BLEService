package com.ratio.deviceService.command;

import android.bluetooth.BluetoothGattCharacteristic;

import com.ratio.deviceService.BTLEDeviceManager;
import com.ratio.exceptions.DeviceManagerException;

/**
 * Queued command to read a character from the BTLE device
 */
public class BTLECommandReadCharacteristic extends BTLECommand {
	protected BluetoothGattCharacteristic mCharacteristic;
	
	public BTLECommandReadCharacteristic(BTLEDeviceManager.BTDeviceInfo	deviceInfo,
										 BluetoothGattCharacteristic 	characteristic) {
		super(deviceInfo);
		mCharacteristic = characteristic;
	}
	
	public boolean execute(BTLEDeviceManager deviceManager) throws DeviceManagerException {
		return mDeviceInfo.getGatt().readCharacteristic(mCharacteristic);
	}
	
	public String toString() {
		return BTLECommandReadCharacteristic.class.getSimpleName() + " addr:" + mDeviceInfo.getDeviceAddress() +
			   " characteristic:" + mCharacteristic.getUuid();
	}
}
