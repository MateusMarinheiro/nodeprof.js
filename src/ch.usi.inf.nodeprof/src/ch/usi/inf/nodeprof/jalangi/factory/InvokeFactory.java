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
import com.oracle.truffle.api.object.DynamicObject;

import ch.usi.inf.nodeprof.ProfiledTagEnum;
import ch.usi.inf.nodeprof.handlers.BaseEventHandlerNode;
import ch.usi.inf.nodeprof.handlers.FunctionCallEventHandler;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.runtime.GraalJSException;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.Undefined;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

public class InvokeFactory extends AbstractFactory {
    private final ProfiledTagEnum tag; // can be INVOKE or NEW

    public InvokeFactory(Object jalangiAnalysis, ProfiledTagEnum tag, JSDynamicObject pre,
                         JSDynamicObject post, JSDynamicObject onInput, JSDynamicObject onException) {
        super("invokeFun", jalangiAnalysis, pre, post, onInput, onException);
        this.tag = tag;
    }

    @Override
    public BaseEventHandlerNode create(EventContext context) {
        return new FunctionCallEventHandler(context, tag) {

            @Child
            MakeArgumentArrayNode makeArgs = MakeArgumentArrayNodeGen.create(pre == null ? post : pre, getOffSet(), 0);
            @Child
            CallbackNode cbNode = new CallbackNode();

            // cache the function to pass on input to not have to fetch all inputs every time
            Object function = null;

            @Override
            public Object executePre(VirtualFrame frame, Object[] inputs) throws InteropException {
                if (pre != null) {
                    // TODO Jalangi's function iid/sid are set to be 0/0
                    return cbNode.preCall(this, jalangiAnalysis, pre, getSourceIID(), getFunction(inputs), getReceiver(inputs), makeArgs.executeArguments(inputs), isNew(), isInvoke(), 0, 0);
                }
                return null;
            }

            @Override
            public Object executePost(VirtualFrame frame, Object result,
                                      Object[] inputs) throws InteropException {
                if (post != null) {
                    // TODO Jalangi's function iid/sid are set to be 0/0
                    return cbNode.postCall(this, jalangiAnalysis, post, getSourceIID(), getFunction(inputs), getReceiver(inputs), makeArgs.executeArguments(inputs), convertResult(result), isNew(),
                            isInvoke(), 0, 0);
                }
                return null;
            }

            @Override
            public Object executeOnInput(VirtualFrame frame, int inputIndex, Object input) throws InteropException {
                // only call input call when the function is read
                // additionally check if input is actual function - in invalid code the function could be e.g. undefined
                if (onInput == null || inputIndex != getOffSet() - 1 || !(input instanceof JSFunctionObject))
                    return null;

                Source src = ((JSFunctionObject) input).getSourceLocation().getSource();
                boolean isBuiltin = src.isInternal();

                // get node module from src uri
                String nodeModule = null;
                if (!isBuiltin) {
                    String[] uriParts = src.getURI().toString().split("/");
                    nodeModule = uriParts.length > 1 && uriParts[1].startsWith("node:") ? uriParts[1].split(":")[1] : null;
                }

                return cbNode.onInputCall(
                        this,
                        jalangiAnalysis,
                        onInput,
                        getSourceIID(),
                        input,
                        nodeModule != null ? Strings.fromJavaString(nodeModule) : Undefined.instance,
                        isBuiltin,
                        inputIndex
                );
            }

            @Override
            public Object executeExceptional(VirtualFrame frame, Throwable exception, Object[] inputs) throws InteropException {
                if (onException == null) return null;

                Object function = inputs.length >= this.getOffSet() - 1 ? inputs[this.getOffSet() - 1] : null;
                Object jsErrorObject = exception instanceof GraalJSException ? ((GraalJSException) exception).getErrorObject() : null; // get it eager, else it is null --> check performance implications
                return cbNode.onExceptionCall(this, jalangiAnalysis, onException, getSourceIID(), jsErrorObject != null ? jsErrorObject : Undefined.instance, function != null ? function : Undefined.instance);
            }
        };
    }
}
