package org.jenkinsci.plugins.adaptivedisonnector;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import hudson.tasks.Builder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FailureListenerTest {

    @Test
    void doNothingIfSuccessful(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedNode(slave);

        j.buildAndAssertSuccess(project);

        Thread.sleep(1000);

        assertTrue(slave.getComputer().isOnline());
    }

    @Test
    void avoidNpeWhenDisconnected(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new SlaveKiller());
        project.setAssignedNode(slave);

        var build = project.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);

        assertTrue(slave.getComputer().isOffline());
    }

    private static final class SlaveKiller extends Builder {

        @Override
        public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {

            build.getBuiltOn().toComputer().disconnect(null);
            return true;
        }
    }
}
