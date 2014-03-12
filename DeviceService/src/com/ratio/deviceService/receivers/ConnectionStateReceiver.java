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
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


/**
 * broadcast receiver which receives the response from IDeviceCommand.readDescriptor()
 * @author matt2
 *
 */
public abstract class ConnectionStateReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String deviceAddress = intent.getStringExtra(DeviceService.EXTRA_DEVICE_ADDRESS);
		int state = intent.getIntExtra(DeviceService.EXTRA_STATE, 0);
		onConnectionState(deviceAddress, state);
	}
	
	/**
	 * implement this to receive the connection state when it changes
	 * @param deviceAdress String of the device MAC address
	 * @param BluetoothProfile.STATE_CONNECTED/BluetoothProfile.STATE_DISCONNECTED
	 */
	
	public abstract void onConnectionState(String deviceAdress, int state);
}
