package com.ratio.deviceService.receivers;

import java.util.ArrayList;
import java.util.List;

import com.ratio.deviceService.BTCharacteristicProfile;
import com.ratio.deviceService.BTServiceProfile;
import com.ratio.deviceService.DeviceService;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


/**
 * broadcast receiver which receives the response from IDeviceCommand.readCharacteristic(), writeCharacteristic()
 * and for received characteristic notifications.
 *
 */
public abstract class CharacteristicReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String deviceAddress = intent.getStringExtra(DeviceService.EXTRA_DEVICE_ADDRESS);
		BTServiceProfile serviceProfile = intent.getParcelableExtra(DeviceService.EXTRA_SERVICE);
		BTCharacteristicProfile chProfile = intent.getParcelableExtra(DeviceService.EXTRA_CHARACTERISTIC);
		int status = intent.getIntExtra(DeviceService.EXTRA_STATUS, 0);
		onCharacteristic(deviceAddress, serviceProfile.getService(), chProfile.getCharacteristic(),
   							 chProfile.getCharacteristic().getValue(), status);
	}
	
	/**
	 * implement this to receive the characteristic that was read/written, or notified
	 * @param deviceAdress String of the device MAC address
	 * @param service bluetooth service for the characteristic
	 * @param characteristic characteristic that was read/written/notified
	 * @param chValue byte[] value of the characteristic (also stored in the characterstic itself
	 * @param status status code 0: good, otherwise bad things happened
	 */
	public abstract void onCharacteristic(String 						deviceAdress, 
										  BluetoothGattService 			service,
										  BluetoothGattCharacteristic	characteristic,
										  byte[]						chValue,
										  int							status);

}
