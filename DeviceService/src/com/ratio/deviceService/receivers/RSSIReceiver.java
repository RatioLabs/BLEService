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
public abstract class RSSIReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String deviceAddress = intent.getStringExtra(DeviceService.EXTRA_DEVICE_ADDRESS);
		int rssi = intent.getIntExtra(DeviceService.EXTRA_RSSI, 0);
		int status = intent.getIntExtra(DeviceService.EXTRA_STATUS, 0);
		onRSSI(deviceAddress, rssi, status);
	}
	
	/**
	 * implement this to receive the rssi from the device
	 * @param deviceAdress String of the device MAC address
	 * @param status status code 0: good, otherwise bad things happened
	 */
	
	public abstract void onRSSI(String 	deviceAdress, 
								int		rssi,
								int		status);
}
