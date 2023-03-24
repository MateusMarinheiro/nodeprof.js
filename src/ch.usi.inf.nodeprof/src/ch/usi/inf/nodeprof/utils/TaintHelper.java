package ch.usi.inf.nodeprof.utils;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.array.ScriptArray;
import com.oracle.truffle.js.runtime.builtins.JSArrayObject;
import com.oracle.truffle.js.runtime.builtins.JSMapObject;
import com.oracle.truffle.js.runtime.builtins.JSPromiseObject;
import com.oracle.truffle.js.runtime.builtins.JSProxyObject;
import com.oracle.truffle.js.runtime.builtins.JSSetObject;
import com.oracle.truffle.js.runtime.interop.InteropArray;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.Undefined;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class TaintHelper {
    @CompilerDirectives.TruffleBoundary
    public static boolean hasTaint(JSDynamicObject obj, int depth) {
        if (obj instanceof JSProxyObject) {
            return ((JSProxyObject) obj).getProxyHandler().hasOwnProperty(Strings.fromJavaString("__taint"));
        }

        if (depth == 0) return false;

        if (obj instanceof JSArrayObject) {
            Object[] elems = ((JSArrayObject) obj).getArrayType().toArray(obj);
            for (Object elem : elems) {
                if (!(elem instanceof JSDynamicObject)) continue;

                if (hasTaint((JSDynamicObject) elem, depth - 1)) {
                    return true;
                }
            }
            // ToDo - properly handle internal objects
        } else if (obj instanceof JSMapObject) {
            return false;
        } else if (obj instanceof JSSetObject) {
            return false;
        } else if (obj instanceof JSPromiseObject) {
            return false;
        } else {
            for (Object key : obj.getOwnPropertyKeys(true, false)) {
                Object prop = obj.getOwnProperty(key).getValue();

                if (!(prop instanceof JSDynamicObject)) continue;

                if (hasTaint((JSDynamicObject) prop, depth - 1)) {
                    return true;
                }
            }
        }

//        System.out.println(Arrays.toString(getPropertyArray(obj)));

        return false;
    }

    @CompilerDirectives.TruffleBoundary
    public static boolean hasTaint(Object obj, int depth) {
        return obj instanceof JSDynamicObject && hasTaint((JSDynamicObject) obj, depth);
    }

    @CompilerDirectives.TruffleBoundary
    public static Object checkTaints(JSDynamicObject obj, int depth) {
        List<JSDynamicObject> taints = new ArrayList<>();
        getTaints(obj, depth, taints);
        return InteropArray.create(taints);
    }

    @CompilerDirectives.TruffleBoundary
    public static Object checkTaints(Object obj, int depth) {
        return obj instanceof JSDynamicObject ? checkTaints((JSDynamicObject) obj, depth) : Undefined.instance;
    }

    @CompilerDirectives.TruffleBoundary
    public static void getTaints(JSDynamicObject obj, int depth, List<JSDynamicObject> taints) {
        if (obj instanceof JSProxyObject && ((JSProxyObject) obj).getProxyHandler().hasOwnProperty(Strings.fromJavaString("__taint"))) {
            taints.add(obj);
            return;
        }

        if (depth == 0) return;

        if (obj instanceof JSArrayObject) {
            Object[] elems = ((JSArrayObject) obj).getArrayType().toArray(obj);
            for (Object elem : elems) {
                if (!(elem instanceof JSDynamicObject)) continue;
                getTaints((JSDynamicObject) elem, depth - 1, taints);
            }
            // ToDo - properly handle internal objects
        } else if (obj instanceof JSMapObject) {
            // ToDo
        } else if (obj instanceof JSSetObject) {
            // ToDo
        } else if (obj instanceof JSPromiseObject) {
            // ToDo
        } else {
            for (Object key : obj.getOwnPropertyKeys(true, false)) {
                Object prop = obj.getOwnProperty(key).getValue();

                if (!(prop instanceof JSDynamicObject)) continue;

                getTaints((JSDynamicObject) prop, depth - 1, taints);
            }
        }
    }
}
