package com.ratio.deviceService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ratio.util.UUIDUtils;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * external description of a device profile, so once we query the device services and characteristics
 * we can send it back from the service to the activity in a bundle, which can be received by the BroadcastReceiver
 * @author mreynolds
 *
 */


public class BTServiceProfile implements Parcelable {
	public BluetoothGattService					mService;
	
	public BTServiceProfile(BluetoothGattService service) {
		mService = service;
	}

	public BluetoothGattService getService() {
		return mService;
	}
	
	
	public static List<BluetoothGattService> getServiceList(List<BTServiceProfile> profileList) {
		List<BluetoothGattService> serviceList = new ArrayList<BluetoothGattService>(profileList.size());
		for (BTServiceProfile profile : profileList) {
			serviceList.add(profile.getService());
		}
		return serviceList;
	}
	
	public BTServiceProfile(Parcel in) {
		UUID serviceUUID = UUIDUtils.readFromParcel(in);
		int type = in.readInt();
		int numCharacteristics = in.readInt();
		mService = new BluetoothGattService(serviceUUID, type);
		for (int i = 0; i < numCharacteristics; i++) {
			BTCharacteristicProfile profile = new BTCharacteristicProfile(in);
			mService.addCharacteristic(profile.mCharacteristic);
		}
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		UUIDUtils.writeToParcel(mService.getUuid(), dest);
		dest.writeInt(mService.getType());
		dest.writeInt(mService.getCharacteristics().size());
		for (BluetoothGattCharacteristic characteristic : mService.getCharacteristics()) {
			BTCharacteristicProfile profile = new BTCharacteristicProfile(characteristic);
			profile.writeToParcel(dest, flags);
		}
	}
	
	public static final Parcelable.Creator<BTServiceProfile> CREATOR = 
        new Parcelable.Creator<BTServiceProfile>() {

            @Override
            public BTServiceProfile createFromParcel(Parcel source) {
                return new BTServiceProfile(source);
            }

            @Override
            public BTServiceProfile[] newArray(int size) {
                return new BTServiceProfile[size];
            }
        };
        
}

