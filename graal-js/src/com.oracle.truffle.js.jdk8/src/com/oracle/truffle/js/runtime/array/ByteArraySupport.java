/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.runtime.array;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

import com.oracle.truffle.api.CompilerDirectives;

import sun.misc.Unsafe;

final class ByteArraySupport {
    private ByteArraySupport() {
    }

    static final ByteArrayAccess LITTLE_ENDIAN_ORDER = new LittleEndianByteArrayAccess();
    static final ByteArrayAccess BIG_ENDIAN_ORDER = new BigEndianByteArrayAccess();
    static final ByteArrayAccess NATIVE_ORDER = new SunMiscUnsafeNativeOrderByteArrayAccess();
}

final class SunMiscUnsafeNativeOrderByteArrayAccess extends ByteArrayAccess {
    @Override
    public int getInt8(byte[] buffer, int offset, int index, int bytesPerElement) {
        return UNSAFE.getByte(buffer, offset(offset, index, bytesPerElement, buffer, Byte.BYTES));
    }

    @Override
    public int getInt16(byte[] buffer, int offset, int index, int bytesPerElement) {
        return UNSAFE.getShort(buffer, offset(offset, index, bytesPerElement, buffer, Short.BYTES));
    }

    @Override
    public int getInt32(byte[] buffer, int offset, int index, int bytesPerElement) {
        return UNSAFE.getInt(buffer, offset(offset, index, bytesPerElement, buffer, Integer.BYTES));
    }

    @Override
    public long getInt64(byte[] buffer, int offset, int index, int bytesPerElement) {
        return UNSAFE.getLong(buffer, offset(offset, index, bytesPerElement, buffer, Long.BYTES));
    }

    @Override
    public float getFloat(byte[] buffer, int offset, int index, int bytesPerElement) {
        return UNSAFE.getFloat(buffer, offset(offset, index, bytesPerElement, buffer, Float.BYTES));
    }

    @Override
    public double getDouble(byte[] buffer, int offset, int index, int bytesPerElement) {
        return UNSAFE.getDouble(buffer, offset(offset, index, bytesPerElement, buffer, Double.BYTES));
    }

    @Override
    public void putInt8(byte[] buffer, int offset, int index, int bytesPerElement, int value) {
        UNSAFE.putByte(buffer, offset(offset, index, bytesPerElement, buffer, Byte.BYTES), (byte) value);
    }

    @Override
    public void putInt16(byte[] buffer, int offset, int index, int bytesPerElement, int value) {
        UNSAFE.putShort(buffer, offset(offset, index, bytesPerElement, buffer, Short.BYTES), (short) value);
    }

    @Override
    public void putInt32(byte[] buffer, int offset, int index, int bytesPerElement, int value) {
        UNSAFE.putInt(buffer, offset(offset, index, bytesPerElement, buffer, Integer.BYTES), value);
    }

    @Override
    public void putInt64(byte[] buffer, int offset, int index, int bytesPerElement, long value) {
        UNSAFE.putLong(buffer, offset(offset, index, bytesPerElement, buffer, Long.BYTES), value);
    }

    @Override
    public void putFloat(byte[] buffer, int offset, int index, int bytesPerElement, float value) {
        UNSAFE.putFloat(buffer, offset(offset, index, bytesPerElement, buffer, Float.BYTES), value);
    }

    @Override
    public void putDouble(byte[] buffer, int offset, int index, int bytesPerElement, double value) {
        UNSAFE.putDouble(buffer, offset(offset, index, bytesPerElement, buffer, Double.BYTES), value);
    }

    private static long offset(int offset, int index, int bytesPerElement, byte[] buffer, int elementSize) {
        int byteIndex = offset + index * bytesPerElement;
        if (byteIndex < 0 || byteIndex > buffer.length - elementSize) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IndexOutOfBoundsException();
        }
        return (long) byteIndex * Unsafe.ARRAY_BYTE_INDEX_SCALE + Unsafe.ARRAY_BYTE_BASE_OFFSET;
    }

    private static final Unsafe UNSAFE = AccessController.doPrivileged(new PrivilegedAction<Unsafe>() {
        @Override
        public Unsafe run() {
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                return (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
            }
        }
    });
}
