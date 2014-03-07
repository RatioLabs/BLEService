package com.ratio.deviceService;

import java.util.UUID;

import com.ratio.util.UUIDUtils;

import android.bluetooth.BluetoothGattDescriptor;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * external description of a device profile, so once we query the device services and descriptors
 * we can send it back from the service to the activity in a bundle, which can be received by the BroadcastReceiver
 * @author mreynolds
 *
 */

public class BTDescriptorProfile implements Parcelable {
	protected BluetoothGattDescriptor mDescriptor;
	
	public BTDescriptorProfile(BluetoothGattDescriptor descriptor) {
		mDescriptor = descriptor;
	}
	
	public BTDescriptorProfile(UUID uuid, int permissions) {
		mDescriptor = new BluetoothGattDescriptor(uuid, permissions);
	}
	
	public BluetoothGattDescriptor getDescriptor() {
		return mDescriptor;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		UUIDUtils.writeToParcel(mDescriptor.getUuid(), dest);
		dest.writeInt(mDescriptor.getPermissions());
		dest.writeByteArray(mDescriptor.getValue());	
	}
	
	public BTDescriptorProfile(Parcel in) {
		UUID uuid = UUIDUtils.readFromParcel(in);
		int permissions = in.readInt();
		byte[] value = in.createByteArray();
		mDescriptor = new BluetoothGattDescriptor(uuid, permissions);
		mDescriptor.setValue(value);
	}
	
	public static final Parcelable.Creator<BTDescriptorProfile> CREATOR = 
        new Parcelable.Creator<BTDescriptorProfile>() {

            @Override
            public BTDescriptorProfile createFromParcel(Parcel source) {
                return new BTDescriptorProfile(source);
            }

            @Override
            public BTDescriptorProfile[] newArray(int size) {
                return new BTDescriptorProfile[size];
            }
        };
}

