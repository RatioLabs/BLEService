package com.ratio.btdemo.adapters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ratio.btdemo.BleNamesResolver;
import com.ratio.btdemo.R;
import com.ratio.deviceService.BTCharacteristicProfile;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class CharacteristicsListAdapter extends BaseAdapter {
    	private List<BluetoothGattCharacteristic> mCharacteristics;
    	private LayoutInflater mInflater;
    	
    	public CharacteristicsListAdapter(Activity parent, List<BluetoothGattCharacteristic> characterstics) {
    		super();
    		mCharacteristics = characterstics;
    		mInflater = parent.getLayoutInflater();
    	}
    	
    	public BluetoothGattCharacteristic getCharacteristic(int index) {
    		return mCharacteristics.get(index);
    	}

    	public void clearList() {
    		mCharacteristics.clear();
    	}
    	
		@Override
		public int getCount() {
			return mCharacteristics.size();
		}

		@Override
		public Object getItem(int position) {
			return getCharacteristic(position);
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
            	convertView = mInflater.inflate(R.layout.peripheral_list_characteristic_item, null);
            	fields = new FieldReferences();
            	fields.charName = (TextView)convertView.findViewById(R.id.peripheral_list_characteristic_name);
            	fields.charUuid = (TextView)convertView.findViewById(R.id.peripheral_list_characteristic_uuid);
                convertView.setTag(fields);
            } else {
                fields = (FieldReferences) convertView.getTag();
            }			
			
            // set proper values into the view
            BluetoothGattCharacteristic ch = getCharacteristic(position);
            String uuid = ch.getUuid().toString().toLowerCase(Locale.getDefault());
            String name = BleNamesResolver.resolveCharacteristicName(uuid);
            
            fields.charName.setText(name);
            fields.charUuid.setText(uuid);
   
			return convertView;
		}
    	
		private class FieldReferences {
			TextView charName;
			TextView charUuid;
		}
}
