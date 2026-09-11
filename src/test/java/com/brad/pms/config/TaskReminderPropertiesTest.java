package com.brad.pms.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskReminderPropertiesTest {

    @Test
    void usesSafeDisabledDefaults() throws Exception {
        Class<?> type = Class.forName("com.brad.pms.config.TaskReminderProperties");
        Object properties = type.getConstructor().newInstance();

        assertThat(type.getMethod("isEnabled").invoke(properties)).isEqualTo(false);
        assertThat(type.getMethod("getCron").invoke(properties)).isEqualTo("0 0 9 * * *");
        assertThat(type.getMethod("getZone").invoke(properties)).isEqualTo("Asia/Shanghai");
        assertThat(type.getMethod("getDueSoonDays").invoke(properties)).isEqualTo(7);
    }

    @Test
    void rejectsDueSoonDaysOutsideOneToThirty() throws Exception {
        Class<?> type = Class.forName("com.brad.pms.config.TaskReminderProperties");
        Object properties = type.getConstructor().newInstance();
        Method setter = type.getMethod("setDueSoonDays", int.class);

        assertThatThrownBy(() -> invoke(setter, properties, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> invoke(setter, properties, 31))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void invoke(Method method, Object target, int value) {
        try {
            method.invoke(target, value);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException(ex.getCause());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
