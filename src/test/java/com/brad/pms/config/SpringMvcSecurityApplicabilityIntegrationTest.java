package com.brad.pms.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.view.xslt.XsltView;
import org.springframework.web.servlet.view.xslt.XsltViewResolver;

import static org.assertj.core.api.Assertions.assertThat;

/** Invalidates the scoped CVE assessment when view or SSE endpoints are introduced. */
@SpringBootTest
@ActiveProfiles("test")
class SpringMvcSecurityApplicabilityIntegrationTest {
    @Autowired ApplicationContext context;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    @Test
    void doesNotRegisterXsltViewsOrResolvers() {
        assertThat(context.getBeansOfType(XsltView.class)).isEmpty();
        assertThat(context.getBeansOfType(XsltViewResolver.class)).isEmpty();
    }

    @Test
    void applicationEndpointsDoNotRenderViewsOrStreamSseFragments() {
        var handlers = mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getName().startsWith("com.brad.pms."))
                .toList();
        assertThat(handlers).isNotEmpty();
        for (var entry : handlers) {
            var handler = entry.getValue();
            assertThat(AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), RestController.class))
                    .as("REST controller: %s", handler).isTrue();
            assertThat(handler.getMethod().getGenericReturnType().getTypeName())
                    .as("No view or SSE response: %s", handler)
                    .doesNotContain("ModelAndView", "org.springframework.web.servlet.View",
                            "SseEmitter", "ResponseBodyEmitter", "StreamingResponseBody", "FragmentsRendering",
                            "ServerSentEvent");
            assertThat(entry.getKey().getProducesCondition().getProducibleMediaTypes())
                    .as("No declared SSE media type: %s", handler)
                    .noneMatch(type -> type.toString().startsWith("text/event-stream"));
        }
    }
}
