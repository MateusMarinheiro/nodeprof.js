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
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;

import ch.usi.inf.nodeprof.handlers.BaseEventHandlerNode;
import ch.usi.inf.nodeprof.handlers.PropertyReadEventHandler;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.nodes.function.JSFunctionCallNode;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Undefined;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

public class GetFieldFactory extends AbstractFactory {

    public GetFieldFactory(Object jalangiAnalysis, JSDynamicObject pre,
                           JSDynamicObject post) {
        super("getField", jalangiAnalysis, pre, post);
        // TODO
    }

    @Override
    public BaseEventHandlerNode create(EventContext context) {
        return new PropertyReadEventHandler(context) {
            @Child
            CallbackNode cbNode = new CallbackNode();

            @Override
            public Object executePre(VirtualFrame frame, Object[] inputs) throws InteropException {
                if (pre != null && !this.isGlobal(inputs)) {
                    return cbNode.preCall(this, jalangiAnalysis, pre, getSourceIID(), getReceiver(inputs), getProperty(), false, isOpAssign(), isMethodCall());
                }
                return null;
            }

            @Override
            public Object executePost(VirtualFrame frame, Object result,
                                      Object[] inputs) throws InteropException {
                if (post != null && !this.isGlobal(inputs)) {
                    // Only fetch scope when we have an undefine prop read -> this is specific to our case to improve performance
                    // To generalize remove
                    Object scope = result == Undefined.instance ? getScope() : Undefined.instance;
                    return cbNode.postCall(this, jalangiAnalysis, post, getSourceIID(), getReceiver(inputs), getProperty(), convertResult(result), false, isOpAssign(), isMethodCall(), scope);
                }
                return null;
            }
        };
    }

}
