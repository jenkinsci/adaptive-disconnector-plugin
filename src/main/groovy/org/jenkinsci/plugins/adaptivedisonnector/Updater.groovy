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

import hudson.model.Computer;
import hudson.node_monitors.AbstractNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;

/*package*/ class Updater {

    /*package*/ static void trigger(AbstractNodeMonitorDescriptor<NodeMonitor> descriptor, NodeMonitor monitor, Computer computer) {

        def recordField = AbstractNodeMonitorDescriptor.class.getDeclaredField("record");
        recordField.accessible = true;

        def record = recordField.get(descriptor);
        if (record == null) return;

        if(computer.getChannel() == null) {
            record.data.put(computer, null);
            return;
        }

        def monitorMethod = AbstractNodeMonitorDescriptor.class.getDeclaredMethod("monitor", Computer.class);
        monitorMethod.accessible = true;
        record.data.put(computer, monitorMethod.invoke(descriptor, computer));

        monitor.data(computer);
    }
}
    