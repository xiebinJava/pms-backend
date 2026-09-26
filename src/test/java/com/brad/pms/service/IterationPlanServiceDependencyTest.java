package com.brad.pms.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

class IterationPlanServiceDependencyTest {

    @Test
    void projectServiceDependencyIsDeferredToAvoidTheProjectNodeServiceCycle() throws Exception {
        Constructor<?> constructor = IterationPlanService.class.getDeclaredConstructors()[0];
        Parameter projectService = java.util.Arrays.stream(constructor.getParameters())
                .filter(parameter -> parameter.getType().equals(ProjectService.class))
                .findFirst()
                .orElseThrow();

        assertThat(projectService.isAnnotationPresent(Lazy.class))
                .as("ProjectService must be injected lazily to break the ProjectService/NodeService cycle")
                .isTrue();
    }
}
