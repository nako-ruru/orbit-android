package com.orbit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 动态代理工具类：拦截 Provider 接口中抛出的异常，
 * 将其包装为带有完整 StackTrace（堆栈信息）作为 Message 的 RuntimeException。
 */
public class ExceptionProxyUtils {

    /**
     * 为给定的 Provider 接口对象创建一个动态代理，拦截所有方法调用。
     * 当方法抛出异常时，捕获该异常并将其堆栈信息转换为 String，
     * 包装进一个新的 RuntimeException（其 message 即为 stacktrace）。
     *
     * @param targetInterface 接口类型
     * @param target          真实的实现对象
     * @param <T>             接口泛型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrapWithStackMessageException(Class<T> targetInterface, T target) {
        if (!targetInterface.isInterface()) {
            throw new IllegalArgumentException("Target must be an interface");
        }
        String interfaceName = targetInterface.getName();
        android.util.Log.d("ExceptionProxy", "Wrapping interface: " + interfaceName);

        return (T) Proxy.newProxyInstance(
                targetInterface.getClassLoader(),
                new Class<?>[]{targetInterface},
                (proxy, method, args) -> {
                    android.util.Log.d("ExceptionProxy", "Calling method: " + interfaceName + "#" + method.getName());
                    try {
                        return method.invoke(target, args);
                    } catch (Throwable t) {
                        Throwable cause = t;
                        if(t instanceof InvocationTargetException) {
                            cause = ((InvocationTargetException)t).getTargetException();
                        }
                        String stackTraceString = getStackTraceAsString(cause);
                        throw new RuntimeException(stackTraceString, cause);
                    }
                }
        );
    }

    /**
     * 将 Throwable 的堆栈信息转换为 String
     */
    private static String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
