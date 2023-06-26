/* *****************************************************************************
 * Copyright 2018 Dynamic Analysis Group, Università della Svizzera Italiana (USI)
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * *****************************************************************************/
package ch.usi.inf.nodeprof.jalangi.factory;

import ch.usi.inf.nodeprof.utils.SourceMapping;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCallTarget;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;

import ch.usi.inf.nodeprof.ProfiledTagEnum;
import ch.usi.inf.nodeprof.handlers.BaseEventHandlerNode;
import ch.usi.inf.nodeprof.handlers.FunctionCallEventHandler;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.runtime.GraalJSException;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.JSArray;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.builtins.JSProxyObject;
import com.oracle.truffle.js.runtime.interop.InteropBoundFunction;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSOrdinaryObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.sun.org.apache.xpath.internal.operations.Bool;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

public class InvokeFactory extends AbstractFactory {
    private final ProfiledTagEnum tag; // can be INVOKE or NEW

    public InvokeFactory(Object jalangiAnalysis, ProfiledTagEnum tag, JSDynamicObject pre,
                         JSDynamicObject post, JSDynamicObject onInput, JSDynamicObject onException) {
        super("invokeFun", jalangiAnalysis, pre, post, onInput, onException, null);
        this.tag = tag;
    }

    @Override
    public BaseEventHandlerNode create(EventContext context) {
        return new FunctionCallEventHandler(context, tag) {

            @Child
            MakeArgumentArrayNode makeArgs = MakeArgumentArrayNodeGen.create(pre == null ? post : pre, getOffSet(), 0);
            @Child
            CallbackNode cbNode = new CallbackNode();

            // cache the function receiver pass on input to not have to fetch all inputs every time
            private Object receiver = Undefined.instance;
            private Object originalFun = Undefined.instance;

            @Override
            public Object executePre(VirtualFrame frame, Object[] inputs) throws InteropException {
                if (pre != null) {
                    Object funInput = getFunction(inputs);
                    Object receiver = getReceiver(inputs);

                    Object fun = null;

                    Object proxy = Undefined.instance;

                    if (funInput instanceof JSFunctionObject) {
                        fun = funInput;
                    } else if (funInput instanceof InteropBoundFunction) {
                        fun = ((InteropBoundFunction) funInput).getFunction();
                        receiver = ((InteropBoundFunction) funInput).getReceiver();
                    } else if (funInput instanceof JSProxyObject) {
                        // if it is a proxy extract the target but also send the proxy itself
                        // note that fun can again be a proxy object
//                        do {
                        fun = ((JSProxyObject) funInput).getProxyTarget();
//                        } while (fun instanceof JSProxyObject);

                        proxy = funInput;
                    }
                    Source src = fun instanceof JSFunctionObject ? ((JSFunctionObject) fun).getSourceLocation().getSource() : null;

                    // TODO Jalangi's function iid/sid are set to be 0/0
                    return cbNode.preCall(this, jalangiAnalysis, pre, getSourceIID(), fun, receiver, makeArgs.executeArguments(inputs), isNew(), isInvoke(), getScopeOf(src), proxy, originalFun, 0, 0);
                }
                return null;
            }

            @Override
            public Object executePost(VirtualFrame frame, Object result,
                                      Object[] inputs) throws InteropException {
                if (post != null) {
                    Object funInput = getFunction(inputs);
                    Object receiver = getReceiver(inputs);

                    Object fun = null;

                    Object proxy = Undefined.instance;

                    if (funInput instanceof JSFunctionObject) {
                        fun = funInput;
                    } else if (funInput instanceof InteropBoundFunction) {
                        fun = ((InteropBoundFunction) funInput).getFunction();
                        receiver = ((InteropBoundFunction) funInput).getReceiver();
                    } else if (funInput instanceof JSProxyObject) {
                        fun = ((JSProxyObject) funInput).getProxyTarget();
                        proxy = funInput;
                    }
                    Source src = fun instanceof JSFunctionObject ? ((JSFunctionObject) fun).getSourceLocation().getSource() : null;
                    // TODO Jalangi's function iid/sid are set to be 0/0
                    return cbNode.postCall(this, jalangiAnalysis, post, getSourceIID(), fun, receiver, makeArgs.executeArguments(inputs), convertResult(result), isNew(),
                            isInvoke(), getScopeOf(src), 0, 0);
                }
                return null;
            }

            @Override
            public Object executeOnInput(VirtualFrame frame, int inputIndex, Object input) throws InteropException {
                if (onInput == null) return null;

                // set receiver
                if (inputIndex == 0 && !isNew()) {
                    this.receiver = input;
                    return null;
                }

                // only call input call when the function is read
                if (inputIndex != getOffSet() - 1) return null;

                /* Most of the time the function object is a JSFunctionObject, but sometimes it's an InteropBoundFunction
                   I'm not sure when/why this is the case - it differs sometime even for the same program
                   ToDo - look into this */

                Object fun = input;
                if (input instanceof InteropBoundFunction) {
                    fun = ((InteropBoundFunction) input).getFunction();
                    this.receiver = ((InteropBoundFunction) input).getReceiver();
                }

                boolean isAsync = fun instanceof JSFunctionObject && ((JSFunctionObject) fun).getFunctionData().isAsync();
                Object scope = fun instanceof JSFunctionObject ? getScopeOf(((JSFunctionObject) fun).getSourceLocation().getSource()) : Undefined.instance;

                Object newFun = cbNode.onInputCall(
                        this,
                        jalangiAnalysis,
                        onInput,
                        getSourceIID(),
                        fun,
                        this.receiver,
                        inputIndex,
                        isNew(),
                        isAsync,
                        scope
                );

                // store original function for later use
//                if (newFun != null) {
//                    originalFun = fun;
//                }
                return newFun;
            }

            @Override
            public Object executeExceptional(VirtualFrame frame, Throwable exception, Object[] inputs) throws InteropException {
                if (onException == null) return null;

                // It is possible that function and receiver are not set (if e.g. receiver throws)
                Object function = inputs.length >= this.getOffSet() && inputs[this.getOffSet() - 1] != null ? inputs[this.getOffSet() - 1] : Undefined.instance;
                Object receiver = !this.isNew() && inputs.length > 0 && inputs[0] != null ? inputs[0] : Undefined.instance;
                Object args = inputs.length >= this.getOffSet() ? makeArgs.executeArguments(inputs) : Undefined.instance;
                Object jsErrorObject = exception instanceof GraalJSException ? ((GraalJSException) exception).getErrorObject() : null; // get it eager, else it is null --> check performance implications
                return cbNode.onExceptionCall(this, jalangiAnalysis, onException, getSourceIID(), jsErrorObject != null ? jsErrorObject : Undefined.instance, function, receiver, args);
            }
        };
    }
}
