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
public class BTLECommandWriteDescriptor extends BTLECommand {
	protected BluetoothGattDescriptor mDescriptor;
	
	public BTLECommandWriteDescriptor(BTLEDeviceManager.BTDeviceInfo	deviceInfo,
			 						  BluetoothGattDescriptor 			descriptor) {
		super(deviceInfo);
		mDescriptor = descriptor;
	}
	public boolean execute(BTLEDeviceManager deviceManager) throws DeviceManagerException {
		if (!mDeviceInfo.getGatt().writeDescriptor(mDescriptor)) {
			throw new DeviceManagerException(String.format("%s %s %s", deviceManager.getContext().getString(R.string.write_descriptor_failed), 
																	   mDeviceInfo.getDeviceAddress(), mDescriptor.getUuid()));
		}
		return true;
	}
	
	public String toString() {
		return BTLECommandWriteDescriptor.class.getSimpleName() + " addr:" + mDeviceInfo.getDeviceAddress() +
				   " characteristic:" + mDescriptor.getCharacteristic().getUuid() + 
				   " descriptor:" + mDescriptor.getUuid();
	}

}
