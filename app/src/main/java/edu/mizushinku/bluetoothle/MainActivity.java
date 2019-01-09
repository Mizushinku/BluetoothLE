package edu.mizushinku.bluetoothle;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@TargetApi(21)
public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;

    private static UUID UART_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static UUID TX_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings scanSettings;
    private List<ScanFilter> filters;
    private Handler mHandler = new Handler();

    private ImageButton bt_btn;
    private TextView bleMessage;
    private Button btn_PREV, btn_OFF;
    private ListView leDeviceListView;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private AlertDialog alertDialog;

    private static int scancnt = 0;
    private static boolean isconnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        bt_btn = findViewById(R.id.bt_btn);
        bt_btn.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ble_disconnect));
        bt_btn.setOnClickListener(this);

        bleMessage = findViewById(R.id.bleMessage);

        btn_PREV = findViewById(R.id.btn_PREV);
        btn_PREV.setOnClickListener(this);
        btn_OFF = findViewById(R.id.btn_OFF);
        btn_OFF.setOnClickListener(this);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View mView = inflater.inflate(R.layout.le_device_list, null);
        leDeviceListView = mView.findViewById(R.id.LeDeviceList);
        mLeDeviceListAdapter = new LeDeviceListAdapter(this);
        leDeviceListView.setAdapter(mLeDeviceListAdapter);

        alertDialog = new AlertDialog.Builder(this)
                .setTitle("Devices")
                .setView(mView)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        alertDialog.cancel();
                        if(Build.VERSION.SDK_INT < 21) {
                            mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        } else {
                            mLEScanner.stopScan(mScanCallback);
                        }
                    }
                }).create();
        alertDialog.setCanceledOnTouchOutside(true);
        leDeviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                alertDialog.dismiss();
                if(Build.VERSION.SDK_INT < 21) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                } else {
                    mLEScanner.stopScan(mScanCallback);
                }
                connectToDevice(device);
            }
        });
    }

    @Override
    public void onClick(View v) {
        if(v == bt_btn) {
            if(!isconnected) {
                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    scanThings();
                }
            }else {
                disconnectGatt();
            }
        } else if(v == btn_PREV) {
            sendTx("1");
        } else if(v == btn_OFF) {
            sendTx("5");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(Build.VERSION.SDK_INT >= 21) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            filters = new ArrayList<>();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        disconnectGatt();
    }

    private void disconnectGatt() {
        if(mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
            mGatt = null;
            tx = null;
            isconnected = false;
            bt_btn.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ble_disconnect));
        }
    }

    private void scanThings() {
        if(Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        } else {
            mLeDeviceListAdapter.clear();
            scanLeDevice(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT) {
            if(resultCode == Activity.RESULT_OK) {
                scanThings();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if(requestCode == PERMISSION_REQUEST_COARSE_LOCATION) {
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
            }
        }
    }

    private void scanLeDevice(final boolean enable) {

        final long SCAN_PERIOD = 5000;
        if (enable) {
            alertDialog.show();
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);
                    }
                }
            }, SCAN_PERIOD);

            if(Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                Toast.makeText(MainActivity.this, "start scan", Toast.LENGTH_SHORT).show();
                mLEScanner.startScan(filters, scanSettings, mScanCallback);
            }
        } else {
            if(Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }


    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            final BluetoothDevice device = result.getDevice();
            if(result.getScanRecord() != null) {
                List<ParcelUuid> parcelUuids = result.getScanRecord().getServiceUuids();
                if(parcelUuids != null) {
                    for (int i = 0; i < parcelUuids.size(); ++i) {
                        if (parcelUuids.get(i).getUuid().equals(UART_UUID)) {
                            Log.d("BLE_Scan", "Find UART_UUID------- " + scancnt);
                            if(device.getName() != null) {
                                ++scancnt;
                                Log.d("BLE_Scan", device.getName() + "------- " + scancnt);
                            }
                        }
                    }
                }
            }

            mLeDeviceListAdapter.addDevice(device);
            mLeDeviceListAdapter.notifyDataSetChanged();
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLeDeviceListAdapter.addDevice(device);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    private void print(final CharSequence message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bleMessage.append(message);
                bleMessage.append("\n");
            }
        });
    }

    private void connectToDevice(BluetoothDevice device) {
        Toast.makeText(MainActivity.this, "connecting "+device.getName(), Toast.LENGTH_LONG).show();
        mGatt = device.connectGatt(getApplicationContext(), false, leGattCallback);
    }

    private BluetoothGattCallback leGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if(newState == BluetoothGatt.STATE_CONNECTED) {
                print("connected :D");
                if(!gatt.discoverServices()) {
                    print("fail discovering services!");
                }
            }else if(newState == BluetoothGatt.STATE_DISCONNECTED) {
                print("disconnected");
            }else {
                print("new state : " + newState);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if(status == BluetoothGatt.GATT_SUCCESS) {
                print("discovering success");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bt_btn.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ble_connect));
                    }
                });
                isconnected = true;
            } else {
                print("Service discovery failed with status: " + status);
            }
            tx = mGatt.getService(UART_UUID).getCharacteristic(TX_UUID);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            print("receive : " + characteristic.getStringValue(0));
        }
    };

    private void sendTx(String mod) {
        if(tx == null) {
            return;
        }
        tx.setValue(mod.getBytes(Charset.forName("UTF-8")));
        if(mGatt.writeCharacteristic(tx)) {
            print("Send : " + mod);
        } else {
            print("connot write TX characteristic!");
        }
    }
}
