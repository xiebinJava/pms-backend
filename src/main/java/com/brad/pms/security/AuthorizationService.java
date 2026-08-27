package com.brad.pms.security;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.PermissionDO;
import com.brad.pms.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthorizationService {
    private final PermissionMapper permissionMapper;

    public boolean has(String permissionCode) {
        LoginUser current = UserContext.get();
        if (current == null) return false;
        if (UserContext.isAdministrator()) return true;
        return permissionMapper.findLiveByUserId(current.getId()).stream()
                .anyMatch(permission -> Objects.equals(permission.getCode(), permissionCode));
    }

    public void require(String permissionCode) {
        if (!has(permissionCode)) throw BusinessException.forbidden("无权执行此操作");
    }

    public List<String> effectivePermissionCodes(Long userId) {
        return permissionMapper.findLiveByUserId(userId).stream()
                .map(PermissionDO::getCode)
                .distinct()
                .collect(Collectors.toList());
    }
}
