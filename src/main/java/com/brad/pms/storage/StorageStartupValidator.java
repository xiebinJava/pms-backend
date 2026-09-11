package com.brad.pms.storage;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class StorageStartupValidator implements InitializingBean {

    private final StorageProperties properties;

    public StorageStartupValidator(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        properties.validate();
    }
}
