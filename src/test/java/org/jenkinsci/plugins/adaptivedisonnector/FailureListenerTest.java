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

import static org.junit.Assert.assertTrue;
import hudson.Extension;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.FreeStyleProject;
import hudson.node_monitors.AbstractNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.tasks.Builder;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.FailureBuilder;
import org.kohsuke.stapler.StaplerRequest;

public class FailureListenerTest {

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Test
    public void doNothingIfSuccessfull() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedNode(slave);

        j.buildAndAssertSuccess(project);

        Thread.sleep(1000);

        assertTrue(slave.getComputer().isOnline());
    }

    @Test
    public void disconnectSlave() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new FailureBuilder());
        project.setAssignedNode(slave);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);

        Thread.sleep(10000);

        assertTrue(slave.getComputer().isOffline());
    }

    @Test
    public void avoidNpeWhenDisconnected() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new SlaveKiller());
        project.setAssignedNode(slave);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);

        assertTrue(slave.getComputer().isOffline());
    }

    private static final OfflineCause OFFLINE_CAUSE = new OfflineCause() {};

    private static final class SlaveKiller extends Builder {
        @Override
        public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
            build.getBuiltOn().toComputer().disconnect(null);
            return true;
        }
    }

    public static final class TestNodeMonitor extends NodeMonitor {

        @Override
        public Object data(Computer c) {
            if (!(Thread.currentThread() instanceof Executor)) return null;

            c.setTemporarilyOffline(true, OFFLINE_CAUSE);
            return this;
        }

        @Extension(ordinal = Double.MIN_VALUE)
        public static final class Descriptor extends AbstractNodeMonitorDescriptor<Void> {

            @Override
            protected Void monitor(Computer c) throws IOException, InterruptedException {
                return null;
            }

            @Override
            public String getDisplayName() {
                return "test-node-monitor";
            }

            @Override
            public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                return new TestNodeMonitor();
            }
        }
    }
}
