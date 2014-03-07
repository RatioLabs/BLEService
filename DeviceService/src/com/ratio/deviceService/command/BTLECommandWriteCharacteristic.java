package com.ratio.deviceService.command;

import android.bluetooth.BluetoothGattCharacteristic;

import com.ratio.deviceService.BTLEDeviceManager;
import com.ratio.exceptions.DeviceManagerException;

/**
 * queued command to write a characteristic
 * @author mreynolds
 *
 */
public class BTLECommandWriteCharacteristic extends BTLECommand {
	protected BluetoothGattCharacteristic mCharacteristic;
	
	public BTLECommandWriteCharacteristic(BTLEDeviceManager.BTDeviceInfo	deviceInfo,
										  BluetoothGattCharacteristic 		characteristic) {
		super(deviceInfo);
		mCharacteristic = characteristic;
	}
	
	public boolean execute(BTLEDeviceManager deviceManager) throws DeviceManagerException {
		return mDeviceInfo.getGatt().writeCharacteristic(mCharacteristic);
	}
	
	public String toString() {
		return BTLECommandWriteCharacteristic.class.getSimpleName() + " addr:" + mDeviceInfo.getDeviceAddress() +
			   " characteristic:" + mCharacteristic.getUuid();
	}
}
