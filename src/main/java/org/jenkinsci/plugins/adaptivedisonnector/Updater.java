package org.jenkinsci.plugins.adaptivedisonnector;

import hudson.model.Computer;
import hudson.node_monitors.AbstractNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

final class Updater {

    private Updater() {}

    static void trigger(AbstractNodeMonitorDescriptor<?> descriptor, NodeMonitor monitor, Computer computer) {

        try {
            Field recordField = AbstractNodeMonitorDescriptor.class.getDeclaredField("record");
            recordField.setAccessible(true);

            Object record = recordField.get(descriptor);

            if (computer.getChannel() == null) {
                if (record != null) {
                    getRecordData(record).put(computer, null);
                }
                return;
            }

            Method monitorMethod = AbstractNodeMonitorDescriptor.class.getDeclaredMethod("monitor", Computer.class);
            monitorMethod.setAccessible(true);

            Object result = monitorMethod.invoke(descriptor, computer);

            if (record != null) {
                getRecordData(record).put(computer, result);
            }

            monitor.data(computer);

        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new IllegalStateException("Failed to update node monitor for " + computer.getName(), cause);

        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to access Jenkins node monitor internals", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Computer, Object> getRecordData(Object record) throws ReflectiveOperationException {

        Field dataField = record.getClass().getDeclaredField("data");
        dataField.setAccessible(true);

        return (Map<Computer, Object>) dataField.get(record);
    }
}
