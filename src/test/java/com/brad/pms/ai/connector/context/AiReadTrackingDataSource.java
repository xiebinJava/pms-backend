package com.brad.pms.ai.connector.context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * Test-scoped JDBC proxy: forwards every call verbatim while classifying
 * prepareStatement/prepareCall/createStatement plus execute/executeBatch activity into the
 * mutation tracker. Only installed from the isolated test class.
 */
public final class AiReadTrackingDataSource implements DataSource {

    private final DataSource delegate;
    private final AiReadMutationTracker tracker;

    public AiReadTrackingDataSource(DataSource delegate, AiReadMutationTracker tracker) {
        this.delegate = delegate;
        this.tracker = tracker;
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            Connection raw = delegate.getConnection();
            ClassLoader loader = AiReadTrackingDataSource.class.getClassLoader();
            return (Connection) Proxy.newProxyInstance(loader, new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        Object result = invoke(raw, method, args);
                        if (result instanceof Statement statement) {
                            return tracked(statement, method.getName(), (String) first(args));
                        }
                        return result;
                    });
        } catch (SQLException thrown) {
            throw thrown;
        } catch (RuntimeException propagated) {
            throw propagated;
        } catch (Exception neverHere) {
            throw new IllegalStateException(neverHere);
        }
    }

    private Object tracked(Statement statement, String methodName, String trackedSql)
            throws SQLException {
        ClassLoader loader = AiReadTrackingDataSource.class.getClassLoader();
        Class<?>[] interfaces = statement instanceof PreparedStatement
                ? new Class<?>[] {PreparedStatement.class, Statement.class}
                : new Class<?>[] {Statement.class};
        return Proxy.newProxyInstance(loader, interfaces, (proxy, method, args) -> {
            String name = method.getName();
            if (name.startsWith("execute")) {
                tracker.recordSql(trackedSql == null ? "statement" : trackedSql);
            }
            return invoke(statement, method, args);
        });
    }

    private static Object first(Object[] args) {
        return args == null || args.length == 0 ? null : args[0];
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException unwrapped) {
            throw unwrapped.getCause();
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        try {
            Connection raw = delegate.getConnection(username, password);
            ClassLoader loader = AiReadTrackingDataSource.class.getClassLoader();
            return (Connection) Proxy.newProxyInstance(loader, new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        Object result = invoke(raw, method, args);
                        if (result instanceof Statement statement) {
                            return tracked(statement, method.getName(), (String) first(args));
                        }
                        return result;
                    });
        } catch (SQLException thrown) {
            throw thrown;
        } catch (RuntimeException propagated) {
            throw propagated;
        } catch (Exception neverHere) {
            throw new IllegalStateException(neverHere);
        }
    }

    @Override
    public java.io.PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }

    @Override
    public void setLogWriter(java.io.PrintWriter out) throws SQLException { delegate.setLogWriter(out); }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }

    @Override
    public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }

    @Override
    public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
}
