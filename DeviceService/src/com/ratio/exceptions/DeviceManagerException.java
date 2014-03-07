package com.ratio.exceptions;


// exceptions thrown by the BTLE device manager
public class DeviceManagerException extends Exception { 
	static final long serialVersionUID = 0;
	
	public DeviceManagerException(String s) {
		super(s);
	}
	public DeviceManagerException(String error, String errorValue) {
    	super(String.format("%s %s", error, errorValue));
	}
}
