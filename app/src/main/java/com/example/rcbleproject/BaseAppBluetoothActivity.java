package com.example.rcbleproject;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.rcbleproject.Database.DatabaseAdapterForHubs;
import com.example.rcbleproject.databinding.ActivityAddingDevicesBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class BaseAppBluetoothActivity extends BaseAppActivity{
    private IListViewAdapterForHubs lvAdapterConnectedDevices;
    private IListViewAdapterForHubs lvAdapterFoundHubs;

    protected boolean bluetoothRequested;
    protected boolean locationRequested;
    protected boolean permissionLocationRequested;
    protected boolean permissionScanRequested;
    protected boolean permissionConnectRequested;
    protected ActivityResultLauncher launcher;

    protected BluetoothAdapter bluetoothAdapter;
    protected BluetoothLeScanner BLEScanner;
    protected ActivityAddingDevicesBinding binding;

    protected DatabaseAdapterForHubs dbHubsAdapter;

    protected final HashMap<String, BluetoothGatt> gatts = Container.getGatts();

    protected void setLvAdapterConnectedDevices(IListViewAdapterForHubs adapter){
        lvAdapterConnectedDevices = adapter;
    }

    protected void setLvAdapterFoundHubs(IListViewAdapterForHubs adapter){
        lvAdapterFoundHubs = adapter;
    }

    @Override
    protected void onCreate(Bundle savedInstance){
        super.onCreate(savedInstance);
        bluetoothRequested = false;
        locationRequested = false;
        permissionLocationRequested = false;
        permissionScanRequested = false;
        permissionConnectRequested = false;

        bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> checkBluetoothPeripherals());
    }

    public boolean checkBluetoothPeripherals(){
        if (bluetoothAdapter == null) return false;
        if (bluetoothAdapter.isEnabled()
                && checkPermissionLocation()
                && checkLocation()
                && checkPermissionBLE_SCAN()
                && checkPermissionBLE_CONNECT()) return true;
        if (!bluetoothAdapter.isEnabled() && !bluetoothRequested) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            launcher.launch(intent);
            bluetoothRequested = true;
            return false;
        }

        if (bluetoothAdapter.isEnabled() && !permissionLocationRequested && !checkPermissionLocation()) {
            permissionLocationRequested = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return false;
        }

        if (bluetoothAdapter.isEnabled() && !permissionScanRequested && !checkPermissionBLE_SCAN()){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, 2);
            permissionScanRequested = true;
            return false;
        }

        if (bluetoothAdapter.isEnabled() && !permissionConnectRequested && !checkPermissionBLE_CONNECT()){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 3);
            permissionConnectRequested = true;
            return false;
        }

        if (bluetoothAdapter.isEnabled() && !locationRequested && !checkLocation()){
            Intent enableLocationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            launcher.launch(enableLocationIntent);
            locationRequested = true;
            return false;
        }

        return false;
    }

    protected boolean checkPermissionLocation(){
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    protected boolean checkPermissionBLE_SCAN(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    protected boolean checkPermissionBLE_CONNECT(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    protected boolean checkLocation(){
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            return enabled;
        } catch (NullPointerException e) {
            Toast.makeText(this, "Пожалуйста, предоствавьте доступ к геолокации в настройках телефона!", Toast.LENGTH_LONG).show();
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    protected void startLEScan(){
        if (!checkBluetoothPeripherals()) return;
        if (bluetoothAdapter.isEnabled()) {
            BLEScanner = bluetoothAdapter.getBluetoothLeScanner();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT).build();
            ArrayList<ScanFilter> filters = getScanFilters();
            BLEScanner.startScan(filters, settings, scanCallback);
            if (BuildConfig.DEBUG) Log.v("APP_TAG", "Start scan!");
        }
        Log.v("APP_TAG22", "gatts size: " + gatts.size());
    }

    protected ArrayList<ScanFilter> getScanFilters(){
        ArrayList<ScanFilter> filters = new ArrayList<>();
        for (UUID uuid : Container.getServiceUUIDs(getApplicationContext()).values()){
            filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(uuid)).build());
        }
        return filters;
    }

    @SuppressLint("MissingPermission")
    protected void stopLEScan(){
        if (BLEScanner == null) return;
        BLEScanner.stopScan(scanCallback);
    }

    @SuppressLint("MissingPermission")
    protected void startLEScan(String deviceAddress){
        if (!checkBluetoothPeripherals()) return;
        if (bluetoothAdapter.isEnabled()) {
            BLEScanner = bluetoothAdapter.getBluetoothLeScanner();
            ScanFilter filter = new ScanFilter.Builder().setDeviceAddress(deviceAddress).build();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
            ArrayList<ScanFilter> filters = new ArrayList<>();
            filters.add(filter);
            BLEScanner.startScan(filters, settings, scanCallback);
            if (BuildConfig.DEBUG) Log.v("APP_TAG", "Start scan!");
        }
        Log.v("APP_TAG22", "gatts size: " + gatts.size());
    }

    @SuppressLint("MissingPermission")
    public void connectDevice(BluetoothDevice device){
        if (BuildConfig.DEBUG) Log.v("APP_TAG22", "try to connect");
        if (!checkBluetoothPeripherals()) return;
        new Thread(() -> {
            device.connectGatt(this, true, gattCallback, TRANSPORT_LE);
        }).start();
    }

    @SuppressLint("MissingPermission")
    public void connectDevice(String deviceAddress){
        connectDevice(bluetoothAdapter.getRemoteDevice(deviceAddress));
    }

    @SuppressLint("MissingPermission")
    public void disconnectDevice(BluetoothGatt gatt){
        if (BuildConfig.DEBUG) Log.v("APP_TAG22", "try to disconnect");
        if (!checkBluetoothPeripherals() || gatt == null) return;
        if (BuildConfig.DEBUG){
            Log.v("APP_TAG22", "start disconnecting, addr = " + gatt.getDevice().getAddress());
            Log.v("APP_TAG22", "gatt = " + gatt);
        }
        new Thread(() -> {
            gatt.disconnect();
            runOnUiThread(() -> {
                gatts.remove(gatt.getDevice().getAddress());
                if (lvAdapterConnectedDevices != null)
                    lvAdapterConnectedDevices.removeHub(gatt.getDevice().getAddress());
                Log.v("APP_TAG22", "gatts size: " + gatts.size());
            });
        }).start();
    }

    @SuppressLint("MissingPermission")
    public void disconnectDevice(String deviceAddress){
        disconnectDevice(gatts.get(deviceAddress));
    }

    protected final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (device != null) {
                if (dbHubsAdapter != null &&
                        dbHubsAdapter.getHubStateConnection(device.getAddress()) == 1){
                    if (!gatts.containsKey(device.getAddress()))
                        connectDevice(device);
                    return;
                }
                if (lvAdapterFoundHubs != null)
                    lvAdapterFoundHubs.addHub(new BluetoothHub(result, getApplicationContext()));
            }
        }
    };

    protected final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (BuildConfig.DEBUG) {
                Log.v("APP_TAG22", "state changed: " + gatt.getDevice().getAddress());
                Log.v("APP_TAG22", "status: " + status + "; newState: " + newState);
            }
            if (status == GATT_SUCCESS){
                Log.v("APP_TAG2", "GATT_SUCCESS");
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    int bondState = gatt.getDevice().getBondState();
                    Log.v("APP_TAG2", "STATE_CONNECTED. bondState: " + bondState);
                    int delay = 0;
                    switch (bondState){
                        case BOND_BONDED:
                            delay = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N? 1000 : 0;
                            break;
                        case BOND_BONDING:
                            return;
                    }

                    if (!gatts.containsKey(gatt.getDevice().getAddress()))
                        runOnUiThread(() -> {
                            if (lvAdapterConnectedDevices == null) return;
                            if (lvAdapterFoundHubs == null) return;
                            BluetoothHub hub = lvAdapterFoundHubs.removeHub(gatt.getDevice().getAddress());
                            if (hub != null)
                                lvAdapterConnectedDevices.addHub(hub);
                        });
                    runOnUiThread(() -> {
                        gatts.put(gatt.getDevice().getAddress(), gatt);
                        lvAdapterConnectedDevices.setAvailability(true, gatt.getDevice());
                    });
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (BuildConfig.DEBUG) Log.v("APP_TAG2", "Discover services");
                        boolean res = gatt.discoverServices();
                        if (!res && BuildConfig.DEBUG)
                            Log.v("APP_TAG2", "Discovering services was failed");
                            }, delay);
                }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                    String addr = gatt.getDevice().getAddress();
                    gatt.close();
                    Log.v("APP_TAG22", "gatt is closed; addr = " + addr);
                    Log.v("APP_TAG22", "gatt = " + gatt + "; Conn state: " + newState);
                }
            }
            else if (status == 8){
                if (BuildConfig.DEBUG) Log.e("APP_TAG", "Error. status = " + status);
                runOnUiThread(() -> {
                    gatts.remove(gatt.getDevice().getAddress());
                    if (lvAdapterConnectedDevices != null){
                        lvAdapterConnectedDevices.setAvailability(false, gatt.getDevice());
                    }
                });
                gatt.close();
            }
            else {
                if (BuildConfig.DEBUG) Log.e("APP_TAG", "Error. status = " + status);
                runOnUiThread(() -> {
                    gatts.remove(gatt.getDevice().getAddress());
                    if (lvAdapterConnectedDevices != null){
                        lvAdapterConnectedDevices.setAvailability(false, gatt.getDevice());
                    }
                });
                gatt.close();
            }
        }
    };

    @SuppressLint("MissingPermission")
    public void writeCharacteristic(BluetoothHub hub, byte[] message){
        new Thread(() -> {
            if (!checkBluetoothPeripherals()){
                runOnUiThread(this::alarmNoPermissions);
                return;
            }
            if (hub == null) return;
            BluetoothGatt bluetoothGatt = gatts.get(hub.address);
            if (bluetoothGatt == null) return;
            BluetoothGattService service = bluetoothGatt.getService(hub.serviceUuid);
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(hub.characteristicUuid);

        /*if((characteristic.getProperties() & PROPERTY_WRITE_NO_RESPONSE) == 0 ) {
            Log.e("APP_TAG22", "proterties " + (characteristic.getProperties() & PROPERTY_WRITE_NO_RESPONSE));
            Log.e("APP_TAG22", "ERROR: Characteristic does not support writeType '" + characteristic.getWriteType() + "'");
            return false;
        }*/
            characteristic.setWriteType(WRITE_TYPE_NO_RESPONSE);
            characteristic.setValue(message);
            bluetoothGatt.writeCharacteristic(characteristic);
        }).start();
    }

    public void alarmNoPermissions(){
        Toast.makeText(this, "", Toast.LENGTH_LONG).show();
    }
}
