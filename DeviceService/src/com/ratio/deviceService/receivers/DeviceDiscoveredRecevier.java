package com.ratio.deviceService.receivers;

import java.util.ArrayList;
import java.util.List;

import com.ratio.deviceService.BTCharacteristicProfile;
import com.ratio.deviceService.BTDescriptorProfile;
import com.ratio.deviceService.BTServiceProfile;
import com.ratio.deviceService.DeviceService;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


/**
 * broadcast receiver which receives the response from IDeviceCommand.readDescriptor()
 * @author matt2
 *
 */
public abstract class DeviceDiscoveredRecevier extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		BluetoothDevice device = intent.getParcelableExtra(DeviceService.EXTRA_DEVICE);
		onDeviceDiscovered(device);
	}
	
	/**
	 * implement this to receive the rssi from the device
	 * @param deviceAdress String of the device MAC address
	 */
	
	public abstract void onDeviceDiscovered(BluetoothDevice device);
}
