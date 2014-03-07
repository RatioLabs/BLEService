package org.bluetooth.bledemo;

import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/* display all services for particular device */
public class ServicesListAdapter extends BaseAdapter {
	private ArrayList<BluetoothGattService> mBTServices;
	private LayoutInflater mInflater;
	
	public ServicesListAdapter(Activity parent) {
		super();
		mBTServices  = new ArrayList<BluetoothGattService>();
		mInflater = parent.getLayoutInflater();
	}
	
	public void addService(BluetoothGattService service) {
		if(mBTServices.contains(service) == false) {
			mBTServices.add(service);
		}
	}
	
	public BluetoothGattService getService(int index) {
		return mBTServices.get(index);
	}

	public void clearList() {
		mBTServices.clear();
	}
	
	@Override
	public int getCount() {
		return mBTServices.size();
	}

	@Override
	public Object getItem(int position) {
		return getService(position);
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
        	convertView = mInflater.inflate(R.layout.peripheral_list_services_item, null);
        	fields = new FieldReferences();
        	fields.serviceName = (TextView)convertView.findViewById(R.id.peripheral_list_services_name);
        	fields.serviceUuid = (TextView)convertView.findViewById(R.id.peripheral_list_services_uuid);
        	fields.serviceType = (TextView)convertView.findViewById(R.id.peripheral_list_service_type);
            convertView.setTag(fields);
        } else {
            fields = (FieldReferences) convertView.getTag();
        }			
		
        // set proper values into the view
        BluetoothGattService service = mBTServices.get(position);
        String uuid = service.getUuid().toString().toLowerCase(Locale.getDefault());
        String name = BleNamesResolver.resolveServiceName(uuid);
        String type = (service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY) ? "Primary" : "Secondary";
        
        fields.serviceName.setText(name);
        fields.serviceUuid.setText(uuid);
        fields.serviceType.setText(type);

		return convertView;
	}
	
	private class FieldReferences {
		TextView serviceName;
		TextView serviceUuid;
		TextView serviceType;
	}
}
