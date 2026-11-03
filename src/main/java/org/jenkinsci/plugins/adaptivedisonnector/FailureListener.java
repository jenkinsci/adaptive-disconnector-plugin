/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.adaptivedisonnector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.node_monitors.AbstractNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.agents.ControllerToAgentCallable;

@Extension
public class FailureListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(FailureListener.class.getName());

    /**
     * Called when a build has completed.
     *
     * Only failed builds trigger node monitoring.
     */
    @Override
    public void onCompleted(Run<?, ?> run, TaskListener listener) {

        if (!Result.FAILURE.equals(run.getResult())) {
            return;
        }

        // Resolve the computer associated with the completed build.
        Executor executor = Executor.currentExecutor();

        if (executor == null) {
            executor = run.getExecutor();
        }

        if (executor == null) {
            LOGGER.log(Level.WARNING, "Unable to identify executor for failed run {0}", run.getFullDisplayName());
            return;
        }

        Computer computer = executor.getOwner();

        LOGGER.log(Level.INFO, "Monitoring {0} after failed run of {1}", new Object[] {
            computer.getName(), run.getFullDisplayName()
        });

        // Trigger every configured node monitor that is not ignored.
        for (Map.Entry<Descriptor<NodeMonitor>, NodeMonitor> entry :
                ComputerSet.getNonIgnoredMonitors().entrySet()) {

            if (!(entry.getKey() instanceof AbstractNodeMonitorDescriptor<?> descriptor)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            AbstractNodeMonitorDescriptor<NodeMonitor> typedDescriptor =
                    (AbstractNodeMonitorDescriptor<NodeMonitor>) descriptor;

            try {
                Updater.trigger(typedDescriptor, entry.getValue(), computer);
            } catch (RuntimeException exception) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to trigger node monitor "
                                + entry.getKey().getDisplayName()
                                + " for computer "
                                + computer.getName(),
                        exception);
            }
        }

        // An offline computer may still have an active remoting channel.
        if (!computer.isOffline()) {
            return;
        }

        String message = String.format(
                "%s was taken offline after failed run of %s", computer.getName(), run.getFullDisplayName());

        LOGGER.log(Level.WARNING, message);

        VirtualChannel channel = computer.getChannel();

        // The agent may have disconnected before the build failure was reported.
        if (channel == null) {
            LOGGER.log(
                    Level.FINE,
                    "Cannot send log message because computer {0} has no active channel",
                    computer.getName());
            return;
        }

        try {
            channel.call(new LogToAgent(message));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            LOGGER.log(Level.WARNING, "Interrupted while sending log message to " + computer.getName(), exception);
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Failed to send log message to " + computer.getName(), exception);
        }
    }

    /**
     * Executes a warning log message on the agent JVM.
     *
     * ControllerToAgentCallable provides the appropriate remoting contract
     * for controller-to-agent communication.
     */
    private record LogToAgent(String message) implements ControllerToAgentCallable<Void, IOException> {

        @Override
        public Void call() {
            LOGGER.log(Level.WARNING, message);
            return null;
        }
    }
}
