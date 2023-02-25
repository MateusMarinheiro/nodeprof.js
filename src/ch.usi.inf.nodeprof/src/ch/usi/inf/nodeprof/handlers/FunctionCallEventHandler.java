/* *****************************************************************************
 * Copyright 2018 Dynamic Analysis Group, Università della Svizzera Italiana (USI)
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package ch.usi.inf.nodeprof.handlers;

import ch.usi.inf.nodeprof.utils.SourceMapping;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.objects.Undefined;

import ch.usi.inf.nodeprof.ProfiledTagEnum;

/**
 * Abstract event handler for function call events
 */
public abstract class FunctionCallEventHandler extends BaseSingleTagEventHandler {
    private final boolean isInvoke;

    // INVOKE node can also be "new"
    private final boolean isNew;

    public FunctionCallEventHandler(EventContext context, ProfiledTagEnum tag) {
        super(context, tag);
        // tag should be either NEW or INVOKE
        assert tag == ProfiledTagEnum.INVOKE || tag == ProfiledTagEnum.NEW;
        this.isInvoke = (boolean) getAttribute("isInvoke");
        this.isNew = (boolean) getAttribute("isNew");
    }

    public Object getFunction(Object[] inputs) {
        int funIndex = isNew ? 0 : 1;
        Object result = assertGetInput(funIndex, inputs, "func");

        // it's possible the user invoke an non-function object raising a runtime exception
        // In nodeprof, we allow the function object to be passed to the dynamic analysis as it is
        // i.e., result might not be a JS function object
        assert (result != null);
        return result;
    }

    public String getFunctionName(Object[] inputs) {
        Object result = getFunction(inputs);
        // TODO cache function name TruffleString
        return JSFunction.getName((DynamicObject) result).toJavaStringUncached();
    }

    public Object getReceiver(Object[] inputs) {
        Object result = isNew ? Undefined.instance : assertGetInput(0, inputs, "receiver");
        // TODO, in some cases in module.js we got null, to be fixed
        if (result == null) {
            result = Undefined.instance;
        }
        return result;
    }

    public Object getArgument(Object[] inputs, int index) {
        return assertGetInput(getOffSet() + index, inputs, "arg");
    }

    /**
     * Checks if a function is internally defined (i.e. js internal or nodejs)
     *
     * @param fun - the function object
     * @throws UnsupportedMessageException
     */
    public boolean isInternal(Object fun) throws UnsupportedMessageException {
        return fun instanceof JSFunctionObject
                && SourceMapping.isInternal(((JSFunctionObject) fun).getSourceLocation().getSource());
    }

    public boolean isInternal(Object[] inputs) throws UnsupportedMessageException {
        return this.isInternal(getFunction(inputs));
    }

    /**
     * @return the index of the first argument
     */
    public int getOffSet() {
        return isNew ? 1 : 2;
    }

    public boolean isNew() {
        return this.isNew;
    }

    public boolean isInvoke() {
        return this.isInvoke;
    }
}
