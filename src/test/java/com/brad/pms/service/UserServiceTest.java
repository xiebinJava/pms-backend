package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.UserMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;

    @Test
    void requireActiveUserAcceptsOnlyActiveNonDeletedAccount() {
        UserService service = new UserService(userMapper);
        UserDO active = user(88L, "ACTIVE", false);
        when(userMapper.selectById(88L)).thenReturn(active);

        assertThat(service.requireActiveUser(88L)).isSameAs(active);

        UserDO disabled = user(89L, "DISABLED", false);
        when(userMapper.selectById(89L)).thenReturn(disabled);
        assertThatThrownBy(() -> service.requireActiveUser(89L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("激活");
    }

    @Test
    void requireActiveUserRejectsMissingAndDeletedAccount() {
        UserService service = new UserService(userMapper);
        when(userMapper.selectById(88L)).thenReturn(null);
        assertThatThrownBy(() -> service.requireActiveUser(88L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");

        when(userMapper.selectById(89L)).thenReturn(user(89L, "ACTIVE", true));
        assertThatThrownBy(() -> service.requireActiveUser(89L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void candidateSearchOnlyReturnsActiveAccounts() throws Exception {
        Method search = UserMapper.class.getMethod("search", String.class);
        String sql = String.join(" ", search.getAnnotation(Select.class).value());
        assertThat(sql).contains("status = 'ACTIVE'").contains("deleted = FALSE");
    }

    private static UserDO user(Long id, String status, boolean deleted) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setStatus(status);
        user.setDeleted(deleted);
        return user;
    }
}
