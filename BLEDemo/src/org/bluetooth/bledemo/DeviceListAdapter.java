package org.bluetooth.bledemo;

import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class DeviceListAdapter extends BaseAdapter {
	
	private ArrayList<BluetoothDevice> mDevices;
	private ArrayList<byte[]> mRecords;
	private ArrayList<Integer> mRSSIs;
	private LayoutInflater mInflater;
	
	public DeviceListAdapter(Activity par) {
		super();
		mDevices  = new ArrayList<BluetoothDevice>();
		mRecords = new ArrayList<byte[]>();
		mRSSIs = new ArrayList<Integer>();
		mInflater = par.getLayoutInflater();
	}
	
	public void addDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
		if(mDevices.contains(device) == false) {
			mDevices.add(device);
			mRSSIs.add(rssi);
			mRecords.add(scanRecord);
		}
	}
	
	public BluetoothDevice getDevice(int index) {
		return mDevices.get(index);
	}
	
	public int getRssi(int index) {
		return mRSSIs.get(index);
	}
	
	public void clearList() {
		mDevices.clear();
		mRSSIs.clear();
		mRecords.clear();
	}
	
	@Override
	public int getCount() {
		return mDevices.size();
	}

	@Override
	public Object getItem(int position) {
		return getDevice(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// get already available view or create new if necessary
		FieldReferences fields;
        if (convertView == null) {
        	convertView = mInflater.inflate(R.layout.activity_scanning_item, null);
        	fields = new FieldReferences();
        	fields.deviceAddress = (TextView)convertView.findViewById(R.id.deviceAddress);
        	fields.deviceName    = (TextView)convertView.findViewById(R.id.deviceName);
        	fields.deviceRssi    = (TextView)convertView.findViewById(R.id.deviceRssi);
            convertView.setTag(fields);
        } else {
            fields = (FieldReferences) convertView.getTag();
        }			
		
        // set proper values into the view
        BluetoothDevice device = mDevices.get(position);
        int rssi = mRSSIs.get(position);
        String rssiString = (rssi == 0) ? "N/A" : rssi + " db";
        String name = device.getName();
        String address = device.getAddress();
        if(name == null || name.length() <= 0) name = "Unknown Device";
        
        fields.deviceName.setText(name);
        fields.deviceAddress.setText(address);
        fields.deviceRssi.setText(rssiString);

		return convertView;
	}
	
	private class FieldReferences {
		TextView deviceName;
		TextView deviceAddress;
		TextView deviceRssi;
	}
}
