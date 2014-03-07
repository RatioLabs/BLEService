package com.ratio.util;

import java.util.ArrayList;
import java.util.List;

import android.database.DatabaseUtils;

// grab-bag of string utilities.
public class StringUtil {

	// generate the string with some nice deadbeef hex code for binary data
	public static String toHexCode(byte[] data) {
        final StringBuilder stringBuilder = new StringBuilder(data.length*2);
        for(byte byteChar : data) {
            stringBuilder.append(String.format("%02X", byteChar));
        }
        return stringBuilder.toString();
	}
	
	
	public static String toHexCode(byte[] data, int offset, int length) {
       final StringBuilder stringBuilder = new StringBuilder(data.length*2);
        for (int i = offset; i < offset + length; i++) {
        	byte byteChar = data[i];
            stringBuilder.append(String.format("%02X", byteChar));
        }
        return stringBuilder.toString();	
	}
}
