/* *****************************************************************************
 * Copyright 2018 Dynamic Analysis Group, Università della Svizzera Italiana (USI)
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;

import ch.usi.inf.nodeprof.handlers.BaseEventHandlerNode;
import ch.usi.inf.nodeprof.handlers.CFRootEventHandler;
import com.oracle.truffle.js.nodes.control.WhileNode;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;

import java.util.Arrays;

public class ControlFlowRootFactory extends AbstractFactory {
    public ControlFlowRootFactory(Object jalangiAnalysis, JSDynamicObject pre, JSDynamicObject post) {
        super("loop", jalangiAnalysis, pre, post);
    }

    @Override
    public BaseEventHandlerNode create(EventContext context) {
        return new CFRootEventHandler(context) {
            @Child
            CallbackNode cbNode = new CallbackNode();

            @Override
            public Object executePre(VirtualFrame frame, Object[] inputs) throws InteropException {
                if (pre != null) {
                    return cbNode.preCall(this, jalangiAnalysis, pre, getSourceIID(), getType(), getCondition(inputs));
                }
                return null;
            }

            @Override
            public Object executePost(VirtualFrame frame, Object result, Object[] inputs) throws InteropException {
                if (post != null) {
                    return cbNode.postCall(this, jalangiAnalysis, post, getSourceIID(), getType(), getCondition(inputs));
                }
                return null;
            }
        };
    }

}
