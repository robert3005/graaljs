/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.runtime.builtins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.objects.Accessor;
import com.oracle.truffle.js.runtime.objects.JSAttributes;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.JSProperty;
import com.oracle.truffle.js.runtime.objects.JSShape;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.objects.PropertyDescriptor;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.DefinePropertyUtil;
import com.oracle.truffle.js.runtime.util.IteratorUtil;

public abstract class JSBuiltinObject extends JSClass {

    protected JSBuiltinObject() {
    }

    @TruffleBoundary
    @Override
    public boolean defineOwnProperty(DynamicObject thisObj, Object key, PropertyDescriptor desc, boolean doThrow) {
        return DefinePropertyUtil.ordinaryDefineOwnProperty(thisObj, key, desc, doThrow);
    }

    /**
     * Like getOwnProperty, but returns {@code null} if value is not present instead of undefined.
     */
    @TruffleBoundary
    @Override
    public Object getOwnHelper(DynamicObject store, Object thisObj, Object key) {
        Property entry = DefinePropertyUtil.getPropertyByKey(store, key);
        if (entry != null) {
            return JSProperty.getValue(entry, store, thisObj, false);
        } else {
            return null;
        }
    }

    /**
     * Like getOwnProperty, but returns {@code null} if value is not present instead of undefined.
     */
    @TruffleBoundary
    @Override
    public Object getOwnHelper(DynamicObject store, Object thisObj, long index) {
        return getOwnHelper(store, thisObj, String.valueOf(index));
    }

    @TruffleBoundary
    @Override
    public Object getHelper(DynamicObject store, Object thisObj, Object key) {
        Object value = getOwnHelper(store, thisObj, key);
        if (value != null) {
            return value;
        } else {
            return getPropertyHelperGeneric(thisObj, store, key);
        }
    }

    @TruffleBoundary
    private static Object getPropertyHelperGeneric(Object thisObj, DynamicObject store, Object key) {
        DynamicObject prototype = JSObject.getPrototype(store);
        if (prototype != Null.instance) {
            return JSObject.getJSClass(prototype).getHelper(prototype, thisObj, key);
        }
        return null;
    }

    @TruffleBoundary
    @Override
    public Object getHelper(DynamicObject store, Object thisObj, long index) {
        Object value = getOwnHelper(store, thisObj, index);
        if (value != null) {
            return value;
        } else {
            return getPropertyHelperGeneric(thisObj, store, index);
        }
    }

    @TruffleBoundary
    private static Object getPropertyHelperGeneric(Object thisObj, DynamicObject store, long index) {
        DynamicObject prototype = JSObject.getPrototype(store);
        if (prototype != Null.instance) {
            return JSObject.getJSClass(prototype).getHelper(prototype, thisObj, index);
        }
        return null;
    }

    @Override
    public Object getMethodHelper(DynamicObject store, Object thisObj, Object name) {
        return getHelper(store, thisObj, name);
    }

    @Override
    public List<Object> getOwnPropertyKeys(DynamicObject thisObj, boolean strings, boolean symbols) {
        return ordinaryOwnPropertyKeys(thisObj, strings, symbols);
    }

    protected static List<Object> ordinaryOwnPropertyKeys(DynamicObject thisObj) {
        return ordinaryOwnPropertyKeys(thisObj, true, true);
    }

    @TruffleBoundary
    protected static List<Object> ordinaryOwnPropertyKeys(DynamicObject thisObj, boolean strings, boolean symbols) {
        if (JSConfig.FastOwnKeys) {
            List<Object> all = IteratorUtil.convertList(JSShape.getProperties(thisObj.getShape()), Property::getKey);
            return filterOwnPropertyKeys(all, strings, symbols);
        } else {
            return ordinaryOwnPropertyKeysSlow(thisObj, strings, symbols);
        }
    }

    protected static List<Object> ordinaryOwnPropertyKeysSlow(DynamicObject thisObj, boolean strings, boolean symbols) {
        CompilerAsserts.neverPartOfCompilation();
        List<Object> keyList = thisObj.getShape().getKeyList();
        List<Object> list = new ArrayList<>(keyList.size());
        for (Object key : keyList) {
            if ((!symbols && key instanceof Symbol) || (!strings && key instanceof String)) {
                continue;
            }
            list.add(key);
        }
        Collections.sort(list, JSRuntime::comparePropertyKeys);
        return list;
    }

    @Override
    public boolean hasOnlyShapeProperties(DynamicObject obj) {
        return false;
    }

    @TruffleBoundary
    @Override
    public boolean delete(DynamicObject thisObj, Object key, boolean isStrict) {
        return deletePropertyDefault(thisObj, key, isStrict);
    }

    protected static boolean deletePropertyDefault(DynamicObject object, Object key, boolean isStrict) {
        Property foundProperty = object.getShape().getProperty(key);
        if (foundProperty != null) {
            if (!JSProperty.isConfigurable(foundProperty)) {
                if (isStrict) {
                    throw Errors.createTypeErrorNotConfigurableProperty(key);
                }
                return false;
            }
            return object.delete(key);
        } else {
            /* the prototype might have a property with that name, but we don't care */
            return true;
        }
    }

    @TruffleBoundary
    @Override
    public boolean delete(DynamicObject thisObj, long index, boolean isStrict) {
        return deletePropertyDefault(thisObj, String.valueOf(index), isStrict);
    }

    @TruffleBoundary
    @Override
    public boolean hasOwnProperty(DynamicObject thisObj, Object key) {
        return thisObj.getShape().hasProperty(key);
    }

    @TruffleBoundary
    @Override
    public boolean hasOwnProperty(DynamicObject thisObj, long index) {
        return hasOwnProperty(thisObj, String.valueOf(index));
    }

    @TruffleBoundary
    @Override
    public boolean hasProperty(DynamicObject thisObj, long index) {
        if (hasOwnProperty(thisObj, index)) {
            return true;
        }
        // shape does not call the object's overwritten function
        if (JSObject.getPrototype(thisObj) != Null.instance) {
            return JSObject.hasProperty(JSObject.getPrototype(thisObj), index);
        }
        return false;
    }

    @TruffleBoundary
    @Override
    public boolean hasProperty(DynamicObject thisObj, Object key) {
        if (hasOwnProperty(thisObj, key)) {
            return true;
        }
        // shape does not call the object's overwritten function
        DynamicObject prototype = JSObject.getPrototype(thisObj);
        if (prototype != Null.instance) {
            return JSObject.hasProperty(prototype, key);
        }
        return false;
    }

    @TruffleBoundary
    @Override
    public boolean set(DynamicObject thisObj, long index, Object value, Object receiver, boolean isStrict) {
        return ordinarySetIndex(thisObj, index, value, receiver, isStrict);
    }

    @TruffleBoundary
    @Override
    public boolean set(DynamicObject thisObj, Object key, Object value, Object receiver, boolean isStrict) {
        return ordinarySet(thisObj, key, value, receiver, isStrict);
    }

    protected static boolean ordinarySetIndex(DynamicObject thisObj, long index, Object value, Object receiver, boolean isStrict) {
        Object key = Boundaries.stringValueOf(index);
        if (receiver != thisObj) {
            // OrdinarySet: set the property on the receiver instead
            return ordinarySetWithReceiver(thisObj, key, value, receiver, isStrict);
        }
        Property entry = DefinePropertyUtil.getPropertyByKey(thisObj, key);
        if (entry != null) {
            return JSProperty.setValue(entry, thisObj, receiver, value, isStrict);
        }
        return setPropertySlow(thisObj, key, value, receiver, isStrict, true);
    }

    protected static boolean ordinarySet(DynamicObject thisObj, Object key, Object value, Object receiver, boolean isStrict) {
        if (receiver != thisObj) {
            // OrdinarySet: set the property on the receiver instead
            return ordinarySetWithReceiver(thisObj, key, value, receiver, isStrict);
        }
        Property entry = DefinePropertyUtil.getPropertyByKey(thisObj, key);
        if (entry != null) {
            return JSProperty.setValue(entry, thisObj, receiver, value, isStrict);
        }
        return setPropertySlow(thisObj, key, value, receiver, isStrict, false);
    }

    protected static boolean ordinarySetWithReceiver(DynamicObject target, Object key, Object value, Object receiver, boolean isStrict) {
        assert JSRuntime.isPropertyKey(key);
        PropertyDescriptor descriptor = JSObject.getOwnProperty(target, key);
        boolean result = performOrdinarySetWithOwnDescriptor(target, key, value, receiver, descriptor, isStrict);
        assert !isStrict || result : "should have thrown";
        return result;
    }

    @TruffleBoundary
    protected static boolean performOrdinarySetWithOwnDescriptor(DynamicObject target, Object key, Object value, Object receiver, PropertyDescriptor desc, boolean isStrict) {
        PropertyDescriptor descriptor = desc;
        if (descriptor == null) {
            DynamicObject parent = JSObject.getPrototype(target);
            if (parent != Null.instance) {
                return JSObject.setWithReceiver(parent, key, value, receiver, isStrict);
            } else {
                descriptor = PropertyDescriptor.undefinedDataDesc;
            }
        }
        if (descriptor.isDataDescriptor()) {
            if (!descriptor.getWritable()) {
                if (isStrict) {
                    throw Errors.createTypeErrorNotWritableProperty(key, target);
                }
                return false;
            }
            if (!JSRuntime.isObject(receiver)) {
                if (isStrict) {
                    throw Errors.createTypeErrorSetNonObjectReceiver(receiver, key);
                }
                return false;
            }
            DynamicObject receiverObj = (DynamicObject) receiver;
            PropertyDescriptor existingDesc = JSObject.getOwnProperty(receiverObj, key);
            if (existingDesc != null) {
                if (existingDesc.isAccessorDescriptor()) {
                    if (isStrict) {
                        throw Errors.createTypeErrorCannotRedefineProperty(key);
                    }
                    return false;
                }
                if (!existingDesc.getWritable()) {
                    if (isStrict) {
                        throw Errors.createTypeErrorNotWritableProperty(key, receiverObj);
                    }
                    return false;
                }
                PropertyDescriptor valueDesc = PropertyDescriptor.createData(value);
                return JSObject.defineOwnProperty(receiverObj, key, valueDesc, isStrict);
            } else {
                return JSRuntime.createDataProperty(receiverObj, key, value, isStrict);
            }
        } else {
            assert descriptor.isAccessorDescriptor();
            Object setter = descriptor.getSet();
            if (setter == Undefined.instance || setter == null) {
                if (isStrict) {
                    throw Errors.createTypeErrorCannotSetAccessorProperty(key, target);
                }
                return false;
            }
            JSRuntime.call(setter, receiver, new Object[]{value});
            return true;
        }
    }

    @TruffleBoundary
    protected static boolean setPropertySlow(DynamicObject thisObj, Object key, Object value, Object receiver, boolean isStrict, boolean isIndex) {
        // check prototype chain for accessors
        assert JSRuntime.isPropertyKey(key);
        DynamicObject current = JSObject.getPrototype(thisObj);
        while (current != Null.instance) {
            if (JSProxy.isProxy(current)) {
                return JSObject.setWithReceiver(current, key, value, receiver, isStrict);
            } else {
                PropertyDescriptor desc = JSObject.getOwnProperty(current, key);
                if (desc != null) {
                    if (desc.isDataDescriptor() && !desc.getWritable()) {
                        if (isStrict) {
                            throw Errors.createTypeErrorNotWritableProperty(key, current);
                        }
                        return false;
                    } else if (desc.isAccessorDescriptor()) {
                        return invokeAccessorPropertySetter(desc, thisObj, key, value, receiver, isStrict);
                    } else {
                        break;
                    }
                }
            }
            current = JSObject.getPrototype(current);
        }

        assert thisObj == receiver;
        DynamicObject receiverObj = (DynamicObject) receiver;
        if (!JSObject.isExtensible(receiverObj)) {
            if (isStrict) {
                throw Errors.createTypeErrorNotExtensible(receiverObj, key);
            }
            return false;
        }

        if (JSConfig.DictionaryObject) {
            boolean isDictionaryObject = JSDictionaryObject.isJSDictionaryObject(thisObj);
            if (!isDictionaryObject && isDictionaryObjectCandidate(thisObj, isIndex)) {
                JSDictionaryObject.makeDictionaryObject(thisObj, "set");
                isDictionaryObject = true;
            }
            if (isDictionaryObject) {
                JSDictionaryObject.getHashMap(thisObj).put(key, PropertyDescriptor.createDataDefault(value));
                return true;
            }
        }

        // add it here
        JSContext context = JSObject.getJSContext(thisObj);
        JSObjectUtil.putDataProperty(context, thisObj, key, value, JSAttributes.getDefault());
        return true;
    }

    protected static boolean invokeAccessorPropertySetter(PropertyDescriptor desc, DynamicObject thisObj, Object key, Object value, Object receiver, boolean isStrict) {
        CompilerAsserts.neverPartOfCompilation();
        assert desc.isAccessorDescriptor();
        DynamicObject setter = (DynamicObject) desc.getSet();
        if (setter != Undefined.instance) {
            JSRuntime.call(setter, receiver, new Object[]{value});
            return true;
        } else if (isStrict) {
            throw Errors.createTypeErrorCannotSetAccessorProperty(key, thisObj);
        } else {
            return false;
        }
    }

    private static boolean isDictionaryObjectCandidate(DynamicObject thisObj, boolean isIndex) {
        if (!JSConfig.DictionaryObject) {
            return false;
        }

        if (!JSUserObject.isJSUserObject(thisObj)) {
            return false;
        }

        int count = thisObj.getShape().getPropertyCount();
        return (count == 0 && isIndex) || (count == JSConfig.DictionaryObjectTransitionThreshold);
    }

    @Override
    public PropertyDescriptor getOwnProperty(DynamicObject thisObj, Object key) {
        return ordinaryGetOwnProperty(thisObj, key);
    }

    /**
     * 9.1.5.1 OrdinaryGetOwnProperty (O, P).
     */
    public static PropertyDescriptor ordinaryGetOwnProperty(DynamicObject thisObj, Object key) {
        assert JSRuntime.isPropertyKey(key);
        Property prop = thisObj.getShape().getProperty(key);
        if (prop == null) {
            return null;
        }
        return ordinaryGetOwnPropertyIntl(thisObj, key, prop);
    }

    @TruffleBoundary
    public static PropertyDescriptor ordinaryGetOwnPropertyIntl(DynamicObject thisObj, Object key, Property prop) {
        PropertyDescriptor desc;
        if (JSProperty.isData(prop)) {
            desc = PropertyDescriptor.createData(JSObject.get(thisObj, key));
            desc.setWritable(JSProperty.isWritable(prop));
        } else if (JSProperty.isAccessor(prop)) {
            Accessor acc = (Accessor) prop.get(thisObj, false);
            desc = PropertyDescriptor.createAccessor(acc.getGetter(), acc.getSetter());
        } else {
            desc = PropertyDescriptor.createEmpty();
        }
        desc.setEnumerable(JSProperty.isEnumerable(prop));
        desc.setConfigurable(JSProperty.isConfigurable(prop));
        return desc;
    }

    @Override
    @TruffleBoundary
    public boolean setIntegrityLevel(DynamicObject thisObj, boolean freeze, boolean doThrow) {
        Shape shape = thisObj.getShape();
        if (thisObj.updateShape()) {
            shape = thisObj.getShape();
        }
        Shape newShape = freeze ? JSShape.freeze(shape) : JSShape.seal(shape);
        if (shape != newShape) {
            thisObj.setShapeAndGrow(shape, newShape);
            thisObj.updateShape();
        }
        return JSObject.preventExtensions(thisObj, doThrow);
    }

    @TruffleBoundary
    @Override
    public boolean preventExtensions(DynamicObject thisObj, boolean doThrow) {
        Shape shape = thisObj.getShape();
        if (thisObj.updateShape()) {
            shape = thisObj.getShape();
        }
        Shape newShape = JSShape.makeNotExtensible(shape);
        if (shape != newShape) {
            thisObj.setShapeAndGrow(shape, newShape);
            thisObj.updateShape();
        }
        return true;
    }

    @Override
    public final boolean isExtensible(DynamicObject thisObj) {
        return JSShape.isExtensible(thisObj.getShape());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public String toDisplayStringImpl(DynamicObject obj, int depth, boolean allowSideEffects, JSContext context) {
        if (context.isOptionNashornCompatibilityMode()) {
            return defaultToString(obj);
        } else {
            return JSRuntime.objectToConsoleString(obj, getClassName(obj), depth, allowSideEffects);
        }
    }

    @TruffleBoundary
    @Override
    public final DynamicObject getPrototypeOf(DynamicObject thisObj) {
        return (DynamicObject) JSShape.getPrototypeProperty(thisObj.getShape()).get(thisObj, false);
    }

    @Override
    public boolean setPrototypeOf(DynamicObject thisObj, DynamicObject newPrototype) {
        return setPrototypeStatic(thisObj, newPrototype);
    }

    @TruffleBoundary
    static boolean setPrototypeStatic(DynamicObject thisObj, DynamicObject newPrototype) {
        if (!checkProtoCycle(thisObj, newPrototype)) {
            return false;
        }
        Shape shape = thisObj.getShape();
        Object oldPrototype = JSObject.getPrototype(thisObj);
        if (oldPrototype == newPrototype) {
            return true;
        }
        if (!JSShape.isExtensible(shape)) {
            return false;
        }
        if (JSShape.isPrototypeInShape(shape)) {
            JSObjectUtil.setPrototype(thisObj, newPrototype);
        } else {
            JSShape.getPrototypeProperty(shape).setSafe(thisObj, newPrototype, null);
        }
        return true;
    }

    public static boolean checkProtoCycle(DynamicObject thisObj, DynamicObject newPrototype) {
        DynamicObject check = newPrototype;
        while (check != Null.instance) {
            if (check == thisObj) {
                return false;
            }
            // 9.1.2.1 If p.[[GetPrototypeOf]] is not the ordinary object internal method
            if (JSProxy.isProxy(check)) {
                return true;
            }
            check = JSObject.getPrototype(check);
        }
        return true;
    }

    protected static void putConstructorSpeciesGetter(JSRealm realm, DynamicObject constructor) {
        JSObjectUtil.putConstantAccessorProperty(realm.getContext(), constructor, Symbol.SYMBOL_SPECIES, createSymbolSpeciesGetterFunction(realm), Undefined.instance);
    }

    protected static DynamicObject createSymbolSpeciesGetterFunction(JSRealm realm) {
        return JSFunction.create(realm, JSFunctionData.createCallOnly(realm.getContext(), realm.getContext().getSpeciesGetterFunctionCallTarget(), 0, "get [Symbol.species]"));
    }

    @Override
    public String getBuiltinToStringTag(DynamicObject object) {
        return "Object";
    }

    @Override
    public boolean usesOrdinaryGetOwnProperty() {
        return true;
    }

    @Override
    public boolean usesOrdinaryIsExtensible() {
        return true;
    }
}
