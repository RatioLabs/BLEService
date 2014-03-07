package com.ratio.deviceService;

import java.util.ArrayList;
import java.util.UUID;

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

public class BTDeviceProfile implements Parcelable {
	public String						mDeviceAddress;
	public ArrayList<BTServiceProfile>	mServiceProfileList;
	public int							mConnectionState;
	
	// we pass connection state, because android throws an error if we attempt to get the connection state
	// from the BluetoothGatt, and it wants it from the BluetoothManager for the device
	public BTDeviceProfile(BluetoothDevice device, BluetoothGatt gatt, int connectionState) {
		mDeviceAddress = device.getAddress();
		mConnectionState = connectionState;
		mServiceProfileList = new ArrayList<BTServiceProfile>();
		for (BluetoothGattService service : gatt.getServices()) {
			BTServiceProfile serviceProfile = new BTServiceProfile(service);
			mServiceProfileList.add(serviceProfile);
		}
	}
	
	// construct a device profile from a parcel
	public BTDeviceProfile(Parcel in) {
		mDeviceAddress = in.readString();
		mConnectionState = in.readInt();
		mServiceProfileList = new ArrayList<BTServiceProfile>();
		in.readTypedList(mServiceProfileList, BTServiceProfile.CREATOR);
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mDeviceAddress);
		dest.writeInt(mConnectionState);
		dest.writeTypedList(mServiceProfileList);
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(mDeviceAddress);
		sb.append("\n");
		for (BTServiceProfile serviceProfile : mServiceProfileList) {
			sb.append(serviceProfile.toString());
			sb.append("\n");
		}
		return sb.toString();
	}
	
	// required for parcelable.
	public static final Parcelable.Creator<BTDeviceProfile> CREATOR = 
	        new Parcelable.Creator<BTDeviceProfile>() {

	            @Override
	            public BTDeviceProfile createFromParcel(Parcel source) {
	                return new BTDeviceProfile(source);
	            }

	            @Override
	            public BTDeviceProfile[] newArray(int size) {
	                return new BTDeviceProfile[size];
	            }
	        };
	 
}

