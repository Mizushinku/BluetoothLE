package edu.mizushinku.bluetoothle;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


@TargetApi(21)
public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;

    private ImageButton bt_btn;
    private BluetoothAdapter mBluetoothAdapter;
    private ListView leDeviceListView;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings scanSettings;
    private List<ScanFilter> filters;
    private Handler mHandler = new Handler();
    private AlertDialog alertDialog;

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
            if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                scanThings();
            }
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

        final long SCAN_PERIOD = 10000;
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
            if(device.getName() != null) {
                Log.d("BLE_Scan", device.getName());
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

    public void connectToDevice(BluetoothDevice device) {
        Toast.makeText(MainActivity.this, "connecting "+device.getName(), Toast.LENGTH_LONG).show();
    }
}
