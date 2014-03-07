package com.ratio.deviceService;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * IMPORTANT NOTE: this receiver needs to know that the disable request was actually sent by the device service
 * rather than being initiated by the user, otherwise it will enable bluetooth and kick off a device scan if the
 * user disables bluetooth
 * This is part of a workaround where we have to disable and re-enable bluetooth to be able scan devices
 * see http://stackoverflow.com/questions/17870189/android-4-3-bluetooth-low-energy-unstable
 * I hope that in the future that this is unnecessary
 * @author matt2
 *
 */
public class BluetoothBroadcastReceiver extends BroadcastReceiver {
	private static String TAG = BluetoothBroadcastReceiver.class.getName();
    private static BluetoothManager 		mBluetoothManager = null;
    private static BluetoothAdapter 		mBluetoothAdapter = null;
    private static boolean 					mfAdapterReset = false;
    
	@Override
	public void onReceive(Context context, Intent intent) {
	    // For API level 18 and above, get a reference to BluetoothAdapter through bluetoothManager.
	    if (mBluetoothManager == null) {
	        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
	        if (mBluetoothManager == null) {
	            Log.e(TAG, "Unable to initialize BluetoothManager.");
	            return;
	        } 
		    mBluetoothAdapter = mBluetoothManager.getAdapter();
		    if (mBluetoothAdapter == null) {
		        Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
		        return;
		    }
	    }
	    
		String action = intent.getAction();
		
		// ensure that this was initiated by the service.
		if (action.equals(DeviceService.RESET_ADAPTER)) {
			mfAdapterReset = true;
		} else if (mfAdapterReset) {

			// re-enable the bluetooth adapter if it was disabled by our service, and only by our service
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				int adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				if (adapterState == BluetoothAdapter.STATE_OFF) {
					mBluetoothAdapter.enable();

				} else if (adapterState == BluetoothAdapter.STATE_ON) {
					// NOTE: I'd love to be able to pass the service class here, but since it's a top-level
					// broadcast receiver, I can't do that.
					Intent serviceIntent = new Intent(context, DeviceService.class);
					serviceIntent.setAction(DeviceService.ACTION_PERFORM_SCAN);
					context.startService(serviceIntent);
					mfAdapterReset = false;
				}
			}
		}
	}	
}
