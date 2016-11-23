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
import hudson.model.Result;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import hudson.node_monitors.AbstractNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import jenkins.security.MasterToSlaveCallable;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class FailureListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(FailureListener.class.getName());

    @Override
    public void onFinalized(Run<?, ?> r) {
        if (!Result.FAILURE.equals(r.getResult())) return;

        Computer computer = Computer.currentComputer();
        if (computer == null) {
            LOGGER.info("Unable to detect computer for " + r);
            return;
        }

        LOGGER.log(Level.INFO, "Monitoring {0} after failed run of {1}",
                new String[] {computer.getName(), r.getFullDisplayName()}
        );

        for (Entry<Descriptor<NodeMonitor>, NodeMonitor> pair: ComputerSet.getNonIgnoredMonitors().entrySet()) {
            AbstractNodeMonitorDescriptor<NodeMonitor> descriptor = (AbstractNodeMonitorDescriptor<NodeMonitor>) pair.getKey();
            Updater.trigger(descriptor, pair.getValue(), computer);
        }

        if (computer.isOffline()) {
            final String message = String.format(
                    "%s taken offline after failed run of %s",
                    computer.getName(), r.getFullDisplayName()
            );

            LOGGER.log(Level.WARNING, message);

            try {
                final VirtualChannel channel = computer.getChannel();
                // Channel might not be available if slave crashed causing build to fail
                if (channel == null) return;

                channel.call(new LogToSlave(message));
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static class LogToSlave extends MasterToSlaveCallable<Void, IOException> {
        private static final long serialVersionUID = 1L;
        private String message;
        public LogToSlave(String message) {
            this.message = message;
        }
        public Void call() throws IOException {
            LOGGER.log(Level.WARNING, message);
            return null;
        }
    }
}
