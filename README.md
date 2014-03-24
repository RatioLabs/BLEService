BLEService
==========

Android Bluetooth Low Energy Service by Ratio Labs (http://labs.weareratio.com).

Main features:

- Implements BLE as an android service
- Requests done via AIDL with a service connection
- Responses sent via BroadcastReceivers
- Allows persistent device interface cross-activity.
- Hides all the nasty stuff
- Wraps Bluetooth classes in parcelable classes so they can be passed between activities.
- Less than 20 calls on a single object
- Supports connection retries
