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

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.binary.InNode;
import com.oracle.truffle.js.nodes.binary.InstanceofNode;
import com.oracle.truffle.js.nodes.binary.JSLogicalNode;
import com.oracle.truffle.js.nodes.binary.JSOrNode;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.objects.Undefined;

import ch.usi.inf.nodeprof.ProfiledTagEnum;

/**
 * Abstract event handler for binary events
 */
public abstract class BinaryEventHandler extends BaseSingleTagEventHandler {
    private final TruffleString op;
    private final boolean isLogic;

    public BinaryEventHandler(EventContext context) {
        super(context, ProfiledTagEnum.BINARY);
        String internalOp = parseInternalOp();
        isLogic = internalOp.equals("||") || internalOp.equals("&&");
        op = Strings.fromJavaString(internalOp);
    }

    private String parseInternalOp() {
        Node node = context.getInstrumentedNode();

        String op;
        if (node instanceof InstanceofNode) {
            op = "instanceof";
        } else if (node instanceof InNode) {
            op = "in";
        } else {
            op = getAttributeInternalString("operator");
        }

        if (op == null) {
            // UndefinedOrNode is not public, so we can't check instanceof
            // However, it's the only private logical node without an operation
            if (node instanceof JSLogicalNode) {
                op = "undefinedor";
            }
        }
        return op;
    }

    /**
     * @return the operator
     */
    public Object getOp() {
        return this.op;
    }

    /**
     * @return the left operand from inputs[0]
     */
    public Object getLeft(Object[] inputs) {
//        return assertGetInput(0, inputs, "left");
        Object leftVal = inputs.length > 0 ? inputs[0] : null;
        return leftVal != null ? leftVal : Undefined.instance;
    }

    /**
     * @return the right operand from inputs[1]
     */
    public Object getRight(Object[] inputs) {
        /**
         * TODO
         *
         * remove the check after bug fix
         */
        if (inputs.length < 2) {
            return Undefined.instance;
        }

        // if there is no right just pass undefined and let the analysis decide what to do
        // right can be e.g. null for the '??' operator
//        return assertGetInput(1, inputs, "right");
        Object rightVal = inputs[1];
        return rightVal != null ? rightVal : Undefined.instance;
    }

    /**
     * the logic operator '||' and '&&' are two special binary operations.
     * <p>
     * e.g., true || right, false && right would only evaluate the left operand
     *
     * @return true if the operator is '||' or '&&'
     */
    public boolean isLogic() {
        return this.isLogic;
    }

    /**
     * @return 0 for logical operations and 2 for others
     */
    @Override
    public int expectedNumInputs() {
        return 2;
    }
}
