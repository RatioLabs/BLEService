package com.ratio.exceptions;

/**
 * exception thrown when a device MAC address is not found by the BTLEDeviceManager
 * @author matt2
 *
 */
public class DeviceNameNotFoundException extends Exception { 
	static final long serialVersionUID = 0;
	
	public DeviceNameNotFoundException(String s) {
		super(s);
	}
}
