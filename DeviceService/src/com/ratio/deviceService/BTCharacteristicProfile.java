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

public class BTCharacteristicProfile implements Parcelable {
	protected BluetoothGattCharacteristic mCharacteristic;
	
	public BTCharacteristicProfile(BluetoothGattCharacteristic characteristic) {
		mCharacteristic = characteristic;
	}
	
	public BTCharacteristicProfile(UUID uuid, int properties, int permissions) {
		mCharacteristic = new BluetoothGattCharacteristic(uuid, properties, permissions);
	}
	
	public BluetoothGattCharacteristic getCharacteristic() {
		return mCharacteristic;
	}
	
	
	public static List<BluetoothGattCharacteristic> getCharacteristicList(List<BTCharacteristicProfile> characteristicProfileList) {
		ArrayList<BluetoothGattCharacteristic> charList = new ArrayList<BluetoothGattCharacteristic>(characteristicProfileList.size());
		for (BTCharacteristicProfile profile : characteristicProfileList) {
			charList.add(profile.getCharacteristic());
		}
		return charList;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		UUIDUtils.writeToParcel(mCharacteristic.getUuid(), dest);
		dest.writeInt(mCharacteristic.getProperties());
		dest.writeInt(mCharacteristic.getPermissions());
		dest.writeByteArray(mCharacteristic.getValue());
		
	}
	
	public BTCharacteristicProfile(Parcel in) {
		UUID uuid = UUIDUtils.readFromParcel(in);
		int properties = in.readInt();
		int permissions = in.readInt();
		byte[] value = in.createByteArray();
		mCharacteristic = new BluetoothGattCharacteristic(uuid, properties, permissions);
		mCharacteristic.setValue(value);
	}
	
	public static final Parcelable.Creator<BTCharacteristicProfile> CREATOR = 
        new Parcelable.Creator<BTCharacteristicProfile>() {

            @Override
            public BTCharacteristicProfile createFromParcel(Parcel source) {
                return new BTCharacteristicProfile(source);
            }

            @Override
            public BTCharacteristicProfile[] newArray(int size) {
                return new BTCharacteristicProfile[size];
            }
        };
}

