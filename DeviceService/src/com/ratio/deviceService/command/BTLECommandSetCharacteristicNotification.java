package com.ratio.deviceService.command;

import java.util.UUID;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.ratio.BTDeviceService.R;
import com.ratio.deviceService.BTLEDeviceManager;
import com.ratio.deviceService.BTLEDeviceManager.BTDeviceInfo;
import com.ratio.deviceService.BTUUID;
import com.ratio.exceptions.DeviceManagerException;
import com.ratio.exceptions.DeviceNameNotFoundException;

/**
 * command queue to set a characteristic on the device
 * @author matt2
 *
 */
public class BTLECommandSetCharacteristicNotification extends BTLECommand {
	protected BluetoothGattCharacteristic mCharacteristic;
	protected boolean					  mfEnable;
	
	public BTLECommandSetCharacteristicNotification(BTLEDeviceManager.BTDeviceInfo	deviceInfo,
													BluetoothGattCharacteristic 	characteristic,
													boolean							fEnable) {
		super(deviceInfo);
		mCharacteristic = characteristic;
		mfEnable = fEnable;
	}
	
	public boolean execute(BTLEDeviceManager deviceManager) throws DeviceManagerException {
        boolean f = mDeviceInfo.getGatt().setCharacteristicNotification(mCharacteristic, mfEnable);
        BluetoothGattDescriptor descriptor = mCharacteristic.getDescriptor(UUID.fromString(BTUUID.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID));
        descriptor.setValue(mfEnable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[] { 0x00, 0x00 });
        f &= mDeviceInfo.getGatt().writeDescriptor(descriptor);
        return f;
	}
	
	public String toString() {
		return BTLECommandSetCharacteristicNotification.class.getSimpleName() + " addr:" + mDeviceInfo.getDeviceAddress() +
			   " characteristic:" + mCharacteristic.getUuid();
	}
}
