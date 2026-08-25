package com.brad.pms.service;

import com.brad.pms.convertor.Convertors;
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
}
