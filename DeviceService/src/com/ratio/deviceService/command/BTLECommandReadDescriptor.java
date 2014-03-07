package com.ratio.deviceService.command;

import android.bluetooth.BluetoothGattDescriptor;

import com.ratio.BTDeviceService.R;
import com.ratio.deviceService.BTLEDeviceManager;
import com.ratio.exceptions.DeviceManagerException;

/**
 * queued command to write to a BTLE descriptor
 * @author mreynolds
 *
 */
public class BTLECommandReadDescriptor extends BTLECommand {
	protected BluetoothGattDescriptor mDescriptor;
	
	public BTLECommandReadDescriptor(BTLEDeviceManager.BTDeviceInfo	deviceInfo,
			 						  BluetoothGattDescriptor 			descriptor) {
		super(deviceInfo);
		mDescriptor = descriptor;
	}
	public boolean execute(BTLEDeviceManager deviceManager) throws DeviceManagerException {
		if (!mDeviceInfo.getGatt().readDescriptor(mDescriptor)) {
			throw new DeviceManagerException(String.format("%s %s %s", deviceManager.getContext().getString(R.string.read_descriptor_failed), 
																	   mDeviceInfo.getDeviceAddress(), mDescriptor.getUuid()));
		}
		return true;
	}
	
	public String toString() {
		return BTLECommandReadDescriptor.class.getSimpleName() + " addr:" + mDeviceInfo.getDeviceAddress() +
			   " characteristic:" + mDescriptor.getCharacteristic().getUuid() + 
			   " descriptor:" + mDescriptor.getUuid();
	}

}
