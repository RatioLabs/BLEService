package com.ratio.deviceService.receivers;

import java.util.ArrayList;
import java.util.List;

import com.ratio.deviceService.BTCharacteristicProfile;
import com.ratio.deviceService.BTDescriptorProfile;
import com.ratio.deviceService.BTServiceProfile;
import com.ratio.deviceService.DeviceService;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


/**
 * broadcast receiver which receives the response from IDeviceCommand.readDescriptor()
 * @author matt2
 *
 */
public abstract class DescriptorReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String deviceAddress = intent.getStringExtra(DeviceService.EXTRA_DEVICE_ADDRESS);
		BTServiceProfile serviceProfile = intent.getParcelableExtra(DeviceService.EXTRA_SERVICE);
		BTCharacteristicProfile chProfile = intent.getParcelableExtra(DeviceService.EXTRA_CHARACTERISTIC);
		BTDescriptorProfile chDescriptor = intent.getParcelableExtra(DeviceService.EXTRA_DESCRIPTOR);
		int status = intent.getIntExtra(DeviceService.EXTRA_STATUS, 0);
		byte[] value = intent.getByteArrayExtra(DeviceService.EXTRA_VALUE);
		onDescriptor(deviceAddress, serviceProfile.getService(), chProfile.getCharacteristic(),
				     chDescriptor.getDescriptor(),
   					 chProfile.getCharacteristic().getValue(), status);
	}
	
	/**
	 * implement this to receive the decsriptor that was read/written.
	 * @param deviceAdress String of the device MAC address
	 * @param service bluetooth service for the characteristic containing the descriptor
	 * @param characteristic characteristic for the descriptor
	 * @param descriptor descriptor read/written
	 * @param chValue byte[] value of the descriptor (also stored in the descriptor itself
	 * @param status status code 0: good, otherwise bad things happened
	 */
	
	public abstract void onDescriptor(String 						deviceAdress, 
									  BluetoothGattService 			service,
									  BluetoothGattCharacteristic	characteristic,
									  BluetoothGattDescriptor		descriptor,
									  byte[]						chValue,
									  int							status);

}
