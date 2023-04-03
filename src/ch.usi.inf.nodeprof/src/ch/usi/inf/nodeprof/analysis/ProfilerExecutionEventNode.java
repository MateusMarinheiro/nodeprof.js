/* *****************************************************************************
 * Copyright 2018 Dynamic Analysis Group, Università della Svizzera Italiana (USI)
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package ch.usi.inf.nodeprof.analysis;

import ch.usi.inf.nodeprof.ProfiledTagEnum;
import ch.usi.inf.nodeprof.handlers.BaseEventHandlerNode;
import ch.usi.inf.nodeprof.handlers.BinaryEventHandler;
import ch.usi.inf.nodeprof.handlers.FunctionCallEventHandler;
import ch.usi.inf.nodeprof.utils.GlobalConfiguration;
import ch.usi.inf.nodeprof.utils.Logger;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.runtime.GraalJSException;
import com.oracle.truffle.js.runtime.Strings;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;

public class ProfilerExecutionEventNode extends ExecutionEventNode {
    protected final EventContext context;
    protected final ProfiledTagEnum cb;

    /**
     * Store the changed input when a callback returns a new result
     * It is set when onResult returns another result and is unset onUnwind
     * <p>
     * This is used to overwrite the input with the changed value in the parent nodes onInputValue
     * The reason for this is that unwind works for the actual program, but if it is an input for
     * another operation then onInputValue is called before onUnwind which leads to the instrumentation not receiving the right value
     * e.g. 1 + 2 + 3 - where 1 is replaced with 5 by the callback - returns 10, but the onInputValue of the second '+' operations still gets 3 and 3 as inputs
     * </p>
     */
    protected static Object returnInput = null;
    protected static int waitingForUnwind = 0;

    @Child
    BaseEventHandlerNode child;
    int hasOnEnter = 0;
    /**
     * A flag to switch on/off the profiling analysis: true => enabled, false => disabled
     * <p>
     * by default the instrumentation is on. It can be updated with
     * ProfilerExecutionEventNode.updateEnabled.
     * <p>
     * After disabled, this class acts as an empty ExecutionEventNode which can be fully optimized
     * out by the compiler
     */
    @CompilationFinal
    private static boolean profilerEnabled = true;

    public static boolean getEnabled() {
        return profilerEnabled;
    }

    /**
     * @param value true to enable the profiler or false to disable
     */
    public static void updateEnabled(boolean value) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        profilerEnabled = value;
    }

    public ProfilerExecutionEventNode(ProfiledTagEnum cb, EventContext context,
                                      BaseEventHandlerNode child) {
        this.context = context;
        this.cb = cb;
        this.cb.nodeCount++;
        this.child = child;
    }

    public EventContext getContext() {
        return context;
    }

    @Override
    protected void onInputValue(VirtualFrame frame, EventContext inputContext,
                                int inputIndex, Object inputValue) {
        if (!profilerEnabled) {
            return;
        }

        Object input = returnInput != null ? returnInput : inputValue;

        Object newResult = null;

        if (child.expectedNumInputs() < 0 || inputIndex < child.expectedNumInputs()) {
            // save input only necessary
            // note that we save the returned result if available - that means that all callbacks get the changed input

            try {
                newResult = this.child.executeOnInput(frame, inputIndex, input);
                if (newResult != null) {
                    input = newResult;
                }
            } catch (Throwable e) {
                reportError(null, e);
            }

            saveInputValue(frame, inputIndex, input);
        }

        if (this.child.isLastIndex(getInputCount(), inputIndex)) {
            this.cb.preHitCount++;
            try {
                this.child.executePre(frame, child.expectedNumInputs() != 0 ? getSavedInputValues(frame) : null);

                // allow for handler changes after executePre/Post
                checkHandlerChanges();
            } catch (Throwable e) {
                reportError(null, e);
            }
        }

        /* Note that this won't always work because onUnwind of a child is caught after onInputValue is called
         * That means that if a child node unwinds it will stop the unwinding and onUnwind of this EventNode will never be called
         * However, it can still be useful in cases in which it is known that no child will unwind
         */
        if (newResult != null) {
            CompilerDirectives.transferToInterpreter();
//            returnInput = newResult;
            throw context.createUnwind(newResult);
        }
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        if (!profilerEnabled) {
            return;
        }

//        System.out.println("Enter " + frame);

//        if (this.child instanceof BinaryEventHandler && ((BinaryEventHandler) this.child).getOp().toString().equals("+")) {
//            CompilerDirectives.transferToInterpreter();
//            throw context.createUnwind(Strings.fromJavaString("enter"));
//        }

        hasOnEnter++;
//        Object newResult = null;
        try {
            this.child.enter(frame);

            if (this.child.isLastIndex(getInputCount(), -1)) {
                this.cb.preHitCount++;

                this.child.executePre(frame, null);

                // allow for handler changes after executePre/Post
                checkHandlerChanges();
            }
        } catch (Throwable e) {
            reportError(null, e);
        }
//        if (newResult != null) {
//            CompilerDirectives.transferToInterpreter();
//            throw context.createUnwind(newResult);
//        }
    }


    @Override
    protected void onReturnValue(VirtualFrame frame, Object result) {
        if (!profilerEnabled) {
            return;
        }

        Object[] inputs = null;

        Object newResult = null;

        try {
            if (hasOnEnter > 0) {  // not sure what hasOnEnter is needed for (is it possible to enter more often then return?)
                hasOnEnter--;
                this.cb.postHitCount++;
                inputs = child.expectedNumInputs() != 0 ? getSavedInputValues(frame) : null;
                newResult = this.child.executePost(frame, result, inputs);

                // allow for handler changes after executePre/Post
                checkHandlerChanges();
            }
        } catch (Throwable e) {
            reportError(inputs, e);
        }

        if (newResult != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            returnInput = newResult;
            throw context.createUnwind(newResult);
        }
    }

    @Override
    protected Object onUnwind(VirtualFrame frame, Object info) {
        hasOnEnter = 0;
        // ToDo - some debug output?
        returnInput = null;
        return info;
    }

    @TruffleBoundary
    private void reportError(Object[] inputs, Throwable e) {
        if (e instanceof GraalJSException) {
            /*
             * Dump JS exception messages in the analysis callback and avoid dumping full Graal.js
             * stack trace. This helps to avoid showing the Graal.js internals when debugging a new
             * dynamic analysis.
             */
            Logger.reportJSException((GraalJSException) e);
            return;
        }

        Logger.error(context.getInstrumentedSourceSection(), this.cb + " inputs: " + (inputs == null ? "null" : inputs.length) + " exception: " + e.getMessage());
        if (inputs != null) {
            for (int i = 0; i < inputs.length; i++) {
                Logger.error(context.getInstrumentedSourceSection(),
                        "\targ[" + i + "]: " + inputs[i]);
            }
        }
        if (!GlobalConfiguration.IGNORE_JALANGI_EXCEPTION) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    @Override
    protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        if (!profilerEnabled) {
            return;
        }

        Object[] inputs = child.expectedNumInputs() != 0 ? getSavedInputValues(frame) : null;
        Object newResult = null;
        try {
            if (hasOnEnter > 0) {
                hasOnEnter--;

                // it can be that one of the input threw an error
                // thus the throwing and its following inputs where not saved and are null
                if (inputs != null) {
                    inputs = Arrays.stream(inputs).filter(Objects::nonNull).toArray();
                }

                this.cb.exceptionHitCount++;
                if (exception instanceof ControlFlowException) {
                    // ToDo - look into this
                    this.child.executeExceptionalCtrlFlow(frame, exception, inputs);
                } else if (exception instanceof GraalJSException) {
                    newResult = this.child.executeExceptional(frame, exception, inputs);
                }
            }
        } catch (Throwable e) {
            reportError(inputs, e);
        }

        if (newResult != null) {
            returnInput = newResult;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw context.createUnwind(newResult);
        }
    }

    public ProfiledTagEnum getType() {
        return this.cb;
    }

    private void checkHandlerChanges() {
        // check for handler changes
        BaseEventHandlerNode newChild = this.child.wantsToUpdateHandler();
        if (newChild == null) {
            removeInstrumentation();
        } else if (newChild != this.child) {
            updateChild(newChild);
        }
    }

    private void updateChild(BaseEventHandlerNode newChild) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.child = insert(newChild);
    }

    private void removeInstrumentation() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        Logger.debug("Removing instrumentation for " + this.child.getClass().getTypeName() + " / " + this + " @ " + context.getInstrumentedNode());
        this.replace(new ExecutionEventNode() {
        }, "NodeProf instrumentation handler removed");
        this.cb.deactivatedCount++;
    }
}
