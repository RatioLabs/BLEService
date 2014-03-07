package com.ratio.deviceService;

// interface to the DeviceService
interface IDeviceCommand {
	void scanDevices(in String[] advertisedServiceUUIDList, int periodMsec);
	void startDeviceScan();
	void stopDeviceScan();
	boolean isScanning();
	void connectDevice(String deviceAddress, long timeoutMsec);
	void disconnectDevice(String deviceAddress);
	void discoverServices(String deviceAddress);
	void getServices(String deviceAddress);
	void getCharacteristics(String deviceAddress, String serviceUUID);
	void setCharacteristicNotification(String deviceAddress, String serviceUUID, String characteristicUUID, boolean enabled);
	void readCharacteristic(String deviceAddress, String serviceUUID, String characteristicUUID);
	void writeCharacteristicString(String deviceAddress, String serviceUUID, String characteristicUUID, String value);
	void writeCharacteristicByteArray(String deviceAddress, String serviceUUID, String characteristicUUID, in byte[] value);
	void writeCharacteristicInt(String deviceAddress, String serviceUUID, String characteristicUUID, int value, int format, int offset);
	void writeDescriptor(String deviceAddress, String serviceUUID, String characteristicUUID, String descriptorUUID, in byte[] value);
	void readDescriptor(String deviceAddress, String serviceUUID, String characteristicUUID, String descriptorUUID);
	boolean isRetrying(String deviceAddress);
	int getConnectionState(String deviceAddress);
	void readRemoteRSSI(String deviceAddress);
}