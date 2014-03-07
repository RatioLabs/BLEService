package org.bluetooth.bledemo;

import java.util.List;
import java.util.Locale;
import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class PeripheralActivity extends Activity implements BleWrapperUiCallbacks {	
    public static final String EXTRAS_DEVICE_NAME    = "BLE_DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "BLE_DEVICE_ADDRESS";
    public static final String EXTRAS_DEVICE_RSSI    = "BLE_DEVICE_RSSI";

    
    public enum ListType {
    	GATT_SERVICES,
    	GATT_CHARACTERISTICS,
    	GATT_CHARACTERISTIC_DETAILS
    }
    
    private ListType mListType = ListType.GATT_SERVICES;
    private String mDeviceName;
    private String mDeviceAddress;
    private String mDeviceRSSI;

    private BleWrapper mBleWrapper;
    
    private TextView mDeviceNameView;
    private TextView mDeviceAddressView;
    private TextView mDeviceRssiView;
    private TextView mDeviceStatus;
    private ListView mListView;
    private View     mListViewHeader;
    private TextView mHeaderTitle;
    private TextView mHeaderBackButton;
    private ServicesListAdapter mServicesListAdapter = null;
    private CharacteristicsListAdapter mCharacteristicsListAdapter = null; 
    private CharacteristicDetailsAdapter mCharDetailsAdapter = null;  
    
    public void uiDeviceConnected(final BluetoothGatt gatt,
			                      final BluetoothDevice device)
    {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mDeviceStatus.setText("connected");
				invalidateOptionsMenu();
			}
    	});
    }
    
    public void uiDeviceDisconnected(final BluetoothGatt gatt,
			                         final BluetoothDevice device)
    {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mDeviceStatus.setText("disconnected");
				mServicesListAdapter.clearList();
				mCharacteristicsListAdapter.clearList();
				mCharDetailsAdapter.clearCharacteristic();
				
				invalidateOptionsMenu();
				
				mHeaderTitle.setText("");
				mHeaderBackButton.setVisibility(View.INVISIBLE);
				mListType = ListType.GATT_SERVICES;
				mListView.setAdapter(mServicesListAdapter);
			}
    	});    	
    }
    
    public void uiNewRssiAvailable(final BluetoothGatt gatt,
    							   final BluetoothDevice device,
    							   final int rssi)
    {
    	runOnUiThread(new Runnable() {
	    	@Override
			public void run() {
				mDeviceRSSI = rssi + " db";
				mDeviceRssiView.setText(mDeviceRSSI);
			}
		});    	
    }
    
    public void uiAvailableServices(final BluetoothGatt gatt,
    						        final BluetoothDevice device,
    							    final List<BluetoothGattService> services)
    {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mServicesListAdapter.clearList();
				mListType = ListType.GATT_SERVICES;
				mListView.setAdapter(mServicesListAdapter);
				mHeaderTitle.setText(mDeviceName + "\'s services:");
				mHeaderBackButton.setVisibility(View.INVISIBLE);
				
    			for(BluetoothGattService service : mBleWrapper.getCachedServices()) {
            		mServicesListAdapter.addService(service);
            	}				
    			mServicesListAdapter.notifyDataSetChanged();
			}    		
    	});
    }
   
    public void uiCharacteristicForService(final BluetoothGatt gatt,
    				 					   final BluetoothDevice device,
    									   final BluetoothGattService service,
    									   final List<BluetoothGattCharacteristic> chars)
    {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mCharacteristicsListAdapter.clearList();
		    	mListType = ListType.GATT_CHARACTERISTICS;
		    	mListView.setAdapter(mCharacteristicsListAdapter);
		    	mHeaderTitle.setText(BleNamesResolver.resolveServiceName(service.getUuid().toString().toLowerCase(Locale.getDefault())) + "\'s characteristics:");
		    	mHeaderBackButton.setVisibility(View.VISIBLE);
		    	
		    	for(BluetoothGattCharacteristic ch : chars) {
		    		mCharacteristicsListAdapter.addCharacteristic(ch);
		    	}
		    	mCharacteristicsListAdapter.notifyDataSetChanged();
			}
    	});
    }
    
    public void uiCharacteristicsDetails(final BluetoothGatt gatt,
					 					 final BluetoothDevice device,
										 final BluetoothGattService service,
										 final BluetoothGattCharacteristic characteristic)
    {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mListType = ListType.GATT_CHARACTERISTIC_DETAILS;
				mListView.setAdapter(mCharDetailsAdapter);
		    	mHeaderTitle.setText(BleNamesResolver.resolveCharacteristicName(characteristic.getUuid().toString().toLowerCase(Locale.getDefault())) + "\'s details:");
		    	mHeaderBackButton.setVisibility(View.VISIBLE);
		    	
		    	mCharDetailsAdapter.setCharacteristic(characteristic);
		    	mCharDetailsAdapter.notifyDataSetChanged();
			}
    	});
    }

    public void uiNewValueForCharacteristic(final BluetoothGatt gatt,
											final BluetoothDevice device,
											final BluetoothGattService service,
											final BluetoothGattCharacteristic characteristic,
											final String strValue,
											final int intValue,
											final byte[] rawValue,
											final String timestamp)
    {
    	if(mCharDetailsAdapter == null || mCharDetailsAdapter.getCharacteristic(0) == null) return;
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mCharDetailsAdapter.newValueForCharacteristic(characteristic, strValue, intValue, rawValue, timestamp);
				mCharDetailsAdapter.notifyDataSetChanged();
			}
    	});
    }
 
	public void uiSuccessfulWrite(final BluetoothGatt gatt,
            					  final BluetoothDevice device,
            					  final BluetoothGattService service,
            					  final BluetoothGattCharacteristic ch,
            					  final String description)
	{
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), "Writing to " + description + " was finished successfully!", Toast.LENGTH_LONG).show();
			}
		});
	}
	
	public void uiFailedWrite(final BluetoothGatt gatt,
							  final BluetoothDevice device,
							  final BluetoothGattService service,
							  final BluetoothGattCharacteristic ch,
							  final String description)
	{
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), "Writing to " + description + " FAILED!", Toast.LENGTH_LONG).show();
			}
		});	
	}

	
	public void uiGotNotification(final BluetoothGatt gatt,
								  final BluetoothDevice device,
								  final BluetoothGattService service,
								  final BluetoothGattCharacteristic ch)
	{
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// at this moment we only need to send this "signal" do characteristic's details view
				mCharDetailsAdapter.setNotificationEnabledForService(ch);
			}			
		});
	}

	@Override
	public void uiDeviceFound(BluetoothDevice device, int rssi, byte[] record) {
		// no need to handle that in this Activity (here, we are not scanning)
	}  	
	
    private AdapterView.OnItemClickListener listClickListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			--position; // we have header so we need to handle this by decrementing by one
			if(position < 0) { // user have clicked on the header - action: BACK
				if(mListType.equals(ListType.GATT_SERVICES)) return;
				if(mListType.equals(ListType.GATT_CHARACTERISTICS)) {
					uiAvailableServices(mBleWrapper.getGatt(), mBleWrapper.getDevice(), mBleWrapper.getCachedServices());
					mCharacteristicsListAdapter.clearList();
					return;
				}
				if(mListType.equals(ListType.GATT_CHARACTERISTIC_DETAILS)) {
					mBleWrapper.getCharacteristicsForService(mBleWrapper.getCachedService());
					mCharDetailsAdapter.clearCharacteristic();
					return;
				}
			}
			else { // user is going deeper into the tree (device -> services -> characteristics -> characteristic's details) 
				if(mListType.equals(ListType.GATT_SERVICES)) {
					BluetoothGattService service = mServicesListAdapter.getService(position);
					mBleWrapper.getCharacteristicsForService(service);
				}
				else if(mListType.equals(ListType.GATT_CHARACTERISTICS)) {
					BluetoothGattCharacteristic ch = mCharacteristicsListAdapter.getCharacteristic(position);
					uiCharacteristicsDetails(mBleWrapper.getGatt(), mBleWrapper.getDevice(), mBleWrapper.getCachedService(), ch);
				} 
			}
		}     	
	};  
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_peripheral);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		mListViewHeader = (View) getLayoutInflater().inflate(R.layout.peripheral_list_services_header, null, false);
		
		connectViewsVariables();
		
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mDeviceRSSI = intent.getIntExtra(EXTRAS_DEVICE_RSSI, 0) + " db";
        mDeviceNameView.setText(mDeviceName);
        mDeviceAddressView.setText(mDeviceAddress);
        mDeviceRssiView.setText(mDeviceRSSI);
        getActionBar().setTitle(mDeviceName);
        
        mListView.addHeaderView(mListViewHeader);
        mListView.setOnItemClickListener(listClickListener);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if(mBleWrapper == null) mBleWrapper = new BleWrapper(this, this);
		
		if(mBleWrapper.initialize() == false) {
			finish();
		}
		
		if(mServicesListAdapter == null) mServicesListAdapter = new ServicesListAdapter(this);
		if(mCharacteristicsListAdapter == null) mCharacteristicsListAdapter = new CharacteristicsListAdapter(this);
		if(mCharDetailsAdapter == null) mCharDetailsAdapter = new CharacteristicDetailsAdapter(this, mBleWrapper);
		
		mListView.setAdapter(mServicesListAdapter);
		mListType = ListType.GATT_SERVICES;
		mHeaderBackButton.setVisibility(View.INVISIBLE);
		mHeaderTitle.setText("");
		
		// start automatically connecting to the device
    	mDeviceStatus.setText("connecting ...");
    	mBleWrapper.connect(mDeviceAddress);
	};
	
	@Override
	protected void onPause() {
		super.onPause();
		
		mServicesListAdapter.clearList();
		mCharacteristicsListAdapter.clearList();
		mCharDetailsAdapter.clearCharacteristic();
		
		
		mBleWrapper.stopMonitoringRssiValue();
		mBleWrapper.diconnect();
		mBleWrapper.close();
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.peripheral, menu);
		if (mBleWrapper.isConnected()) {
	        menu.findItem(R.id.device_connect).setVisible(false);
	        menu.findItem(R.id.device_disconnect).setVisible(true);
	    } else {
	        menu.findItem(R.id.device_connect).setVisible(true);
	        menu.findItem(R.id.device_disconnect).setVisible(false);
	    }		
		return true;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.device_connect:
            	mDeviceStatus.setText("connecting ...");
            	mBleWrapper.connect(mDeviceAddress);
                return true;
            case R.id.device_disconnect:
            	mBleWrapper.diconnect();
                return true;
            case android.R.id.home:
            	mBleWrapper.diconnect();
            	mBleWrapper.close();
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }	

    
    private void connectViewsVariables() {
    	mDeviceNameView = (TextView) findViewById(R.id.peripheral_name);
		mDeviceAddressView = (TextView) findViewById(R.id.peripheral_address);
		mDeviceRssiView = (TextView) findViewById(R.id.peripheral_rssi);
		mDeviceStatus = (TextView) findViewById(R.id.peripheral_status);
		mListView = (ListView) findViewById(R.id.listView);
		mHeaderTitle = (TextView) mListViewHeader.findViewById(R.id.peripheral_service_list_title);
		mHeaderBackButton = (TextView) mListViewHeader.findViewById(R.id.peripheral_list_service_back);
    }

}
