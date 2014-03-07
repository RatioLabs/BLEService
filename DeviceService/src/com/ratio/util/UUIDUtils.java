package com.ratio.util;

import java.util.UUID;

import android.os.Parcel;

// utilities for UUIDs, mostly to read and write to parcels for services.
public class UUIDUtils {
	
	// if the UUID is null, then we write 0L,0L
	public static void writeToParcel(UUID uuid, Parcel parcel) {
		if (uuid == null) {
			parcel.writeLong(0L);
			parcel.writeLong(0L);
		} else {
			parcel.writeLong(uuid.getLeastSignificantBits());
			parcel.writeLong(uuid.getMostSignificantBits());
		}
	}
	
	// if the uuid is 0L,0L, then we assume that it is null
	public static UUID readFromParcel(Parcel parcel) {
		long lsb = parcel.readLong();
		long msb = parcel.readLong();
		if ((lsb == 0L) && (msb == 0L)) {
			return null;
		} else {
			return new UUID(msb, lsb);
		}
	}
	
	// turn B6981800756211E2B50D00163E46F8FE into B6981800-7562-11E2-B50D-00163E46F8FE
	public static UUID fromByteArray(byte[] byteArray, int offset) {
		String format = "%s-%s-%s-%s-%s";
		String p1 = StringUtil.toHexCode(byteArray, offset + 0, 4);
		String p2 = StringUtil.toHexCode(byteArray, offset + 4, 2);
		String p3 = StringUtil.toHexCode(byteArray, offset + 6, 2);
		String p4 = StringUtil.toHexCode(byteArray, offset + 8, 2);
		String p5 = StringUtil.toHexCode(byteArray, offset + 10, 6);
		String uuidString = String.format(format, p1, p2, p3, p4, p5);
		return UUID.fromString(uuidString);		
	}
}
