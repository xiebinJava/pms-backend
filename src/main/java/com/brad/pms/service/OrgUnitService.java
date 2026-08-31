package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.OrgUnitCreateCmd;
import com.brad.pms.dto.request.OrgUnitMoveCmd;
import com.brad.pms.dto.request.OrgUnitUpdateCmd;
import com.brad.pms.dto.response.OrgUnitTreeDTO;
import com.brad.pms.entity.OrgUnitDO;
import com.brad.pms.entity.OrgUnitTypeDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.OrgUnitMapper;
import com.brad.pms.mapper.OrgUnitTypeMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.mapper.UserPositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgUnitService {
    private final OrgUnitMapper orgUnitMapper;
    private final OrgUnitTypeMapper orgUnitTypeMapper;
    private final UserMapper userMapper;
    private final UserPositionMapper userPositionMapper;
    private final ProjectMapper projectMapper;
    private final OperationLogService operationLogService;

    public List<OrgUnitTreeDTO> tree() {
        List<OrgUnitDO> units = orgUnitMapper.findActiveTree();
        Map<Long, OrgUnitTreeDTO> mapped = units.stream().collect(Collectors.toMap(OrgUnitDO::getId, this::toDto));
        for (OrgUnitDO unit : units) {
            if (unit.getParentId() != null && mapped.containsKey(unit.getParentId())) {
                mapped.get(unit.getParentId()).getChildren().add(mapped.get(unit.getId()));
            }
        }
        return units.stream().filter(unit -> unit.getParentId() == null || !mapped.containsKey(unit.getParentId()))
                .map(unit -> mapped.get(unit.getId())).collect(Collectors.toList());
    }

    @Transactional
    public OrgUnitTreeDTO create(OrgUnitCreateCmd cmd) {
        if (orgUnitMapper.findByCode(cmd.getCode().trim()) != null) {
            throw BusinessException.error("组织编码已存在");
        }
        OrgUnitTypeDO type = orgUnitTypeMapper.selectOne(new LambdaQueryWrapper<OrgUnitTypeDO>()
                .eq(OrgUnitTypeDO::getCode, cmd.getTypeCode().trim()));
        if (type == null || !Boolean.TRUE.equals(type.getEnabled())) throw BusinessException.error("组织类型不存在或已停用");
        OrgUnitDO parent = cmd.getParentId() == null ? null : requireActive(cmd.getParentId());
        OrgUnitDO unit = new OrgUnitDO();
        unit.setParentId(parent == null ? null : parent.getId());
        unit.setTypeId(type.getId());
        unit.setCode(cmd.getCode().trim());
        unit.setName(cmd.getName().trim());
        unit.setLeaderUserId(cmd.getLeaderUserId());
        unit.setSort(cmd.getSort() == null ? 0 : cmd.getSort());
        unit.setStatus("ACTIVE");
        unit.setPath("/");
        orgUnitMapper.insert(unit);
        unit.setPath(parent == null ? "/" + unit.getId() + "/" : parent.getPath() + unit.getId() + "/");
        validateLeader(unit, unit.getLeaderUserId());
        orgUnitMapper.updateById(unit);
        operationLogService.record("ORG_CREATED", "ORG_UNIT", unit.getId(), null, Map.of("code", unit.getCode(), "name", unit.getName()));
        return toDto(unit);
    }

    @Transactional
    public OrgUnitTreeDTO move(Long id, OrgUnitMoveCmd cmd) {
        OrgUnitDO unit = requireActive(id);
        OrgUnitDO parent = cmd.getParentId() == null ? null : requireActive(cmd.getParentId());
        if (parent != null && (Objects.equals(parent.getId(), id) || parent.getPath().startsWith(unit.getPath()))) {
            throw BusinessException.error("不能移动到自身或下级组织");
        }
        String oldPrefix = unit.getPath();
        String newPrefix = parent == null ? "/" + unit.getId() + "/" : parent.getPath() + unit.getId() + "/";
        unit.setParentId(parent == null ? null : parent.getId());
        unit.setPath(newPrefix);
        orgUnitMapper.updateById(unit);
        for (OrgUnitDO descendant : orgUnitMapper.selectList(null)) {
            if (!Objects.equals(descendant.getId(), id) && descendant.getPath().startsWith(oldPrefix)) {
                descendant.setPath(newPrefix + descendant.getPath().substring(oldPrefix.length()));
                orgUnitMapper.updateById(descendant);
            }
        }
        operationLogService.record("ORG_MOVED", "ORG_UNIT", id, Map.of("path", oldPrefix), Map.of("path", newPrefix));
        return toDto(unit);
    }

    @Transactional
    public OrgUnitTreeDTO update(Long id, OrgUnitUpdateCmd cmd) {
        OrgUnitDO unit = requireActive(id);
        String beforeName = unit.getName();
        String beforeType = unit.getTypeId() == null ? null : String.valueOf(unit.getTypeId());
        if (cmd.getTypeCode() != null && !cmd.getTypeCode().isBlank()) {
            OrgUnitTypeDO type = orgUnitTypeMapper.selectOne(new LambdaQueryWrapper<OrgUnitTypeDO>()
                    .eq(OrgUnitTypeDO::getCode, cmd.getTypeCode().trim()));
            if (type == null || !Boolean.TRUE.equals(type.getEnabled())) {
                throw BusinessException.error("组织类型不存在或已停用");
            }
            unit.setTypeId(type.getId());
        }
        Long leaderUserId = Boolean.TRUE.equals(cmd.getClearLeader())
                ? null
                : (cmd.getLeaderUserId() == null ? unit.getLeaderUserId() : cmd.getLeaderUserId());
        validateLeader(unit, leaderUserId);
        unit.setName(cmd.getName().trim());
        unit.setLeaderUserId(leaderUserId);
        if (cmd.getSort() != null) unit.setSort(cmd.getSort());
        orgUnitMapper.updateById(unit);
        operationLogService.record("ORG_UPDATED", "ORG_UNIT", id,
                Map.of("name", beforeName, "typeId", beforeType == null ? "" : beforeType),
                Map.of("name", unit.getName(), "typeId", String.valueOf(unit.getTypeId()),
                        "leaderUserId", unit.getLeaderUserId() == null ? "" : unit.getLeaderUserId()));
        return toDto(unit);
    }

    @Transactional
    public void deactivate(Long id) {
        OrgUnitDO unit = requireActive(id);
        if (unit.getParentId() == null) throw BusinessException.error("公司总部不能停用");
        long children = orgUnitMapper.selectCount(new LambdaQueryWrapper<OrgUnitDO>()
                .eq(OrgUnitDO::getParentId, id).eq(OrgUnitDO::getStatus, "ACTIVE"));
        if (children > 0) throw BusinessException.error("请先处理下级组织");
        long positions = userPositionMapper.selectCount(new LambdaQueryWrapper<com.brad.pms.entity.UserPositionDO>()
                .eq(com.brad.pms.entity.UserPositionDO::getOrgUnitId, id)
                .eq(com.brad.pms.entity.UserPositionDO::getStatus, "ACTIVE"));
        if (positions > 0) throw BusinessException.error("请先转移该组织下的在职人员");
        long projects = projectMapper.selectCount(new LambdaQueryWrapper<com.brad.pms.entity.ProjectDO>()
                .eq(com.brad.pms.entity.ProjectDO::getOrgUnitId, id)
                .ne(com.brad.pms.entity.ProjectDO::getStatus, com.brad.pms.common.enums.ProjectStatus.DELETED.getCode()));
        if (projects > 0) throw BusinessException.error("请先处理该组织下的项目");
        unit.setStatus("INACTIVE");
        orgUnitMapper.updateById(unit);
        operationLogService.record("ORG_DEACTIVATED", "ORG_UNIT", id, Map.of("status", "ACTIVE"), Map.of("status", "INACTIVE"));
    }

    private OrgUnitDO requireActive(Long id) {
        OrgUnitDO unit = orgUnitMapper.selectById(id);
        if (unit == null || !"ACTIVE".equals(unit.getStatus())) throw BusinessException.error("组织不存在或已停用");
        return unit;
    }

    private void validateLeader(OrgUnitDO unit, Long leaderUserId) {
        if (leaderUserId == null) return;
        UserDO leader = userMapper.selectById(leaderUserId);
        if (leader == null || !"ACTIVE".equals(leader.getStatus())) {
            throw BusinessException.error("负责人不存在或已停用");
        }
        // A leader may be appointed from a parent organization (for example a
        // BG owner overseeing a department). We only require an active
        // appointment so disabled or unassigned accounts can never be leaders.
        if (userPositionMapper.findActiveByUserId(leaderUserId).isEmpty()) {
            throw BusinessException.error("负责人必须有在职任职记录");
        }
    }

    private OrgUnitTreeDTO toDto(OrgUnitDO unit) {
        OrgUnitTreeDTO dto = new OrgUnitTreeDTO();
        dto.setId(unit.getId());
        dto.setParentId(unit.getParentId());
        dto.setCode(unit.getCode());
        dto.setName(unit.getName());
        dto.setStatus(unit.getStatus());
        dto.setLeaderUserId(unit.getLeaderUserId());
        dto.setSort(unit.getSort());
        dto.setMemberCount(userPositionMapper.selectCount(new LambdaQueryWrapper<com.brad.pms.entity.UserPositionDO>()
                .eq(com.brad.pms.entity.UserPositionDO::getOrgUnitId, unit.getId())
                .eq(com.brad.pms.entity.UserPositionDO::getStatus, "ACTIVE")));
        if (unit.getLeaderUserId() != null) {
            UserDO leader = userMapper.selectById(unit.getLeaderUserId());
            dto.setLeaderDisplayName(com.brad.pms.convertor.Convertors.userDisplayName(leader));
        }
        OrgUnitTypeDO type = orgUnitTypeMapper.selectById(unit.getTypeId());
        dto.setTypeCode(type == null ? null : type.getCode());
        return dto;
    }
}
