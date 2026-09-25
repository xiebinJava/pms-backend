package com.brad.pms.service;

import com.brad.pms.convertor.Convertors;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.UserDTO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public List<UserDTO> search(String keyword) {
        return Convertors.toUsers(userMapper.search(keyword == null ? "" : keyword));
    }

    public List<UserDO> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userMapper.selectBatchIds(ids);
    }

    public List<UserDO> listByIdsIncludingDeleted(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userMapper.selectByIdsIncludingDeleted(ids);
    }

    public UserDO requireActiveUser(Long userId) {
        if (userId == null) throw BusinessException.error("账号不能为空");
        UserDO user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw BusinessException.notFound("账号不存在");
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw BusinessException.error("只能选择已激活的账号");
        }
        return user;
    }
}
