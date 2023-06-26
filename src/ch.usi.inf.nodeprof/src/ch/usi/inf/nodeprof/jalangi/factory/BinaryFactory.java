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

import ch.usi.inf.nodeprof.handlers.BaseEventHandlerNode;
import ch.usi.inf.nodeprof.handlers.BinaryEventHandler;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;

public class BinaryFactory extends AbstractFactory {

    public BinaryFactory(Object jalangiAnalysis, JSDynamicObject pre,
                         JSDynamicObject post, JSDynamicObject enter) {
        super("binary", jalangiAnalysis, pre, post, null, null, enter);
    }

    @Override
    public BaseEventHandlerNode create(EventContext context) {
        return new BinaryEventHandler(context) {
            @Child
            CallbackNode cbNode = new CallbackNode();

            @Override
            public Object executePre(VirtualFrame frame, Object[] inputs) throws InteropException {
                return pre != null
                        ? cbNode.preCall(this, jalangiAnalysis, pre, getSourceIID(), getOp(), getLeft(inputs), getRight(inputs), isLogic())
                        : null;
            }

            @Override
            public Object executePost(VirtualFrame frame, Object result,
                                      Object[] inputs) throws InteropException {
                return post != null
                        ? cbNode.postCall(this, jalangiAnalysis, post, getSourceIID(), getOp(), getLeft(inputs), getRight(inputs), convertResult(result), isLogic())
                        : null;
            }

//            @Override
//            public Object executeOnInput(VirtualFrame frame, int inputIndex, Object input) throws Exception {
//                return onInput != null
//                        ? cbNode.onInputCall(this, jalangiAnalysis, post, getSourceIID(), getOp(), inputIndex, input)
//                        : null;
//            }

            @Override
            public void executeEnter(VirtualFrame frame) {
                if (enter == null) return;

                cbNode.enterCall(this, jalangiAnalysis, enter, getSourceIID(), getOp());
            }
        };
    }
}
