package edu.mizushinku.bluetoothle;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class LeDeviceListAdapter extends BaseAdapter {
    private ArrayList<BluetoothDevice> mLeDevices = null;
    private LayoutInflater inflater = null;

    public LeDeviceListAdapter(Context context) {
        super();
        mLeDevices = new ArrayList<>();
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void addDevice(BluetoothDevice device) {
        if(!mLeDevices.contains(device)) {
            mLeDevices.add(device);
        }
    }

    public BluetoothDevice getDevice(int pos) {
        return mLeDevices.get(pos);
    }

    public void clear() {
        mLeDevices.clear();
    }

    @Override
    public int getCount() {
        return mLeDevices.size();
    }

    @Override
    public Object getItem(int i) {
        return mLeDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        if(view == null) {
            view = inflater.inflate(R.layout.le_device_item, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceName = view.findViewById(R.id.device_name);
            viewHolder.deviceAddr = view.findViewById(R.id.device_addr);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        BluetoothDevice device = mLeDevices.get(i);
        final String deviceName = device.getName();
        if(deviceName != null && deviceName.length() > 0) {
            viewHolder.deviceName.setText(deviceName);
        } else {
            viewHolder.deviceName.setText(R.string.unknown_device);
        }
        viewHolder.deviceAddr.setText(device.getAddress());

        return view;
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddr;
    }
}
