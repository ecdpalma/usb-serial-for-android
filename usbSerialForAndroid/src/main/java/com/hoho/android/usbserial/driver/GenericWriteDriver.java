/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A driver for simply sending data to a USB device.
 *
 * It does not implement serial configuration, nor reads.
 */
public class GenericWriteDriver implements UsbSerialDriver {

    private final String TAG = GenericWriteDriver.class.getSimpleName();

    private final UsbDevice mDevice;
    private final UsbSerialPort mPort;

    public GenericWriteDriver(UsbDevice device) {
        mDevice = device;
        mPort = new GenericWritePort(device, 0);
    }

    @Override
    public UsbDevice getDevice() {
        return mDevice;
    }

    @Override
    public List<UsbSerialPort> getPorts() {
        return Collections.singletonList(mPort);
    }

    class GenericWritePort extends CommonUsbSerialPort {

        private UsbInterface mDataInterface;

        private UsbEndpoint mWriteEndpoint;

        public GenericWritePort(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        @Override
        public UsbSerialDriver getDriver() {
            return GenericWriteDriver.this;
        }

        @Override
        public void open(UsbDeviceConnection connection) throws IOException {
            if (mConnection != null) {
                throw new IOException("Already open");
            }

            mConnection = connection;
            boolean opened = false;
            try {
                Log.d(TAG, "claiming interfaces, count=" + mDevice.getInterfaceCount());

                for (int i = 0; i < mDevice.getInterfaceCount(); i++) {
                    Log.d(TAG, "Claiming data interface.");
                    UsbInterface usbInterface = mDevice.getInterface(i);
                    Log.d(TAG, "data iface=" + usbInterface);
                    if (!mConnection.claimInterface(usbInterface, true)) {
                        throw new IOException("Could not claim data interface.");
                    }
                    else {
                        mDataInterface = usbInterface;
                    }
                }

                for (int i = 0; i < mDataInterface.getEndpointCount(); i++) {
                    UsbEndpoint endpoint = mDataInterface.getEndpoint(i);
                    if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                            mWriteEndpoint = endpoint;
                        }
                    }
                }

                opened = true;
            } finally {
                if (!opened) {
                    mConnection = null;
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (mConnection == null) {
                throw new IOException("Already closed");
            }
            mConnection.close();
            mConnection = null;
        }

        @Override
        public int read(byte[] dest, int timeoutMillis) throws IOException {
            return 0;
        }

        @Override
        public int write(byte[] src, int timeoutMillis) throws IOException {
            // TODO(mikey): Nearly identical to FtdiSerial write. Refactor.
            int offset = 0;

            while (offset < src.length) {
                final int writeLength;
                final int amtWritten;

                synchronized (mWriteBufferLock) {
                    final byte[] writeBuffer;

                    writeLength = Math.min(src.length - offset, mWriteBuffer.length);
                    if (offset == 0) {
                        writeBuffer = src;
                    } else {
                        // bulkTransfer does not support offsets, make a copy.
                        System.arraycopy(src, offset, mWriteBuffer, 0, writeLength);
                        writeBuffer = mWriteBuffer;
                    }

                    amtWritten = mConnection.bulkTransfer(mWriteEndpoint, writeBuffer, writeLength,
                            timeoutMillis);
                }
                if (amtWritten <= 0) {
                    throw new IOException("Error writing " + writeLength
                            + " bytes at offset " + offset + " length=" + src.length);
                }

                Log.d(TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
                offset += amtWritten;
            }
            return offset;
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits, int parity) {
        }

        @Override
        public boolean getCD() throws IOException {
            return false;
        }

        @Override
        public boolean getCTS() throws IOException {
            return false;
        }

        @Override
        public boolean getDSR() throws IOException {
            return false;
        }

        @Override
        public boolean getDTR() throws IOException {
            return false;
        }

        @Override
        public void setDTR(boolean value) throws IOException {
        }

        @Override
        public boolean getRI() throws IOException {
            return false;
        }

        @Override
        public boolean getRTS() throws IOException {
            return false;
        }

        @Override
        public void setRTS(boolean value) throws IOException {
        }
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        return new LinkedHashMap<Integer, int[]>();
    }

}
