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
 * broadcast receiver which receives the list of characteristics from a service.
 */
public abstract class CharactertisticListReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String deviceAddress = intent.getStringExtra(DeviceService.EXTRA_DEVICE_ADDRESS);
		ArrayList<BTCharacteristicProfile> charProfileList = (ArrayList<BTCharacteristicProfile>) intent.getSerializableExtra(DeviceService.EXTRA_CHARACTERISTICS);
		List<BluetoothGattCharacteristic> charList = BTCharacteristicProfile.getCharacteristicList(charProfileList);
		BTServiceProfile serviceProfile = intent.getParcelableExtra(DeviceService.EXTRA_SERVICE);
		onCharacteristicList(deviceAddress, serviceProfile.getService(), charList);
	}
	
	/**
	 * implement this to receive the characteristic that was read/written, or notified
	 * @param deviceAdress String of the device MAC address
	 * @param service GATT service the characteristics are being listed for
	 * @param charList list of bluetooth characteristics
	 */
	public abstract void onCharacteristicList(String 							deviceAdress, 
											  BluetoothGattService				service,
											  List<BluetoothGattCharacteristic> charList);

}
