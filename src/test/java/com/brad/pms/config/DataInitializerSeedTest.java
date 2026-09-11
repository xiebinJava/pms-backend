package com.brad.pms.config;

import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DataInitializerSeedTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void localDemoSeedProvidesTwentyNaturalNameUsers() {
        List<UserDO> users = userMapper.selectList(null);

        assertThat(users).hasSizeGreaterThanOrEqualTo(21);
        assertThat(users).anyMatch(user -> "张伟".equals(user.getNameZh()) && "Alex.Zhang".equals(user.getUsername()));
        assertThat(users).noneMatch(user -> List.of("张三", "李四", "王五").contains(user.getNameZh()));
    }
}
