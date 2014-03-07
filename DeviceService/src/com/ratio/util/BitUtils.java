package com.ratio.util;
/**
 * reverse a byte array.
 */
public class BitUtils {
	
	
	// this is for ScanDeviceCallback.onLeScan(), which returns the UUID reversed and shifted by a nibble
	public static byte[] reverse(byte[] byteArray) {
		byte[] rev = new byte[byteArray.length];
		for (int i = 0; i < byteArray.length; i++) {
			rev[byteArray.length - (i + 1)] = byteArray[i];
		}
		return rev;
	}
}
