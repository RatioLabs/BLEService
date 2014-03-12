package com.ratio.deviceService.receivers;

import java.util.ArrayList;
import java.util.List;

import com.ratio.deviceService.BTServiceProfile;
import com.ratio.deviceService.DeviceService;

import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * broadcast receiver which returns the response from IDeviceCommand.discoverServices()
 * It de-parcels the device address and the BluetoothGattService list.
 *
 */
public abstract class ServicesDiscoveredReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String deviceAddress = intent.getStringExtra(DeviceService.EXTRA_DEVICE_ADDRESS);
   		ArrayList<BTServiceProfile> serviceProfileList = (ArrayList<BTServiceProfile>) intent.getSerializableExtra(DeviceService.EXTRA_SERVICES);
   		List<BluetoothGattService> serviceList = BTServiceProfile.getServiceList(serviceProfileList);
   		onServicesDiscovered(deviceAddress, serviceList);
	}
	
	public abstract void onServicesDiscovered(String deviceAdress, List<BluetoothGattService> serviceList);

}
