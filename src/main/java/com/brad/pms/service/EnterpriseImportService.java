package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.config.EnterpriseDataMigration;
import com.brad.pms.dto.response.ImportPreviewDTO;
import com.brad.pms.dto.response.ImportRowErrorDTO;
import com.brad.pms.entity.*;
import com.brad.pms.mapper.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnterpriseImportService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private final ImportJobMapper importJobMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final OrgUnitTypeMapper orgUnitTypeMapper;
    private final UserMapper userMapper;
    private final PositionMapper positionMapper;
    private final RoleMapper roleMapper;
    private final UserPositionMapper userPositionMapper;
    private final UserRoleMapper userRoleMapper;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    public ImportPreviewDTO previewOrganizations(MultipartFile file) {
        return preview(file, "ORGANIZATIONS", Arrays.asList("组织编码", "组织名称", "组织类型编码", "父组织编码", "负责人英文名", "排序"));
    }

    public ImportPreviewDTO previewUsers(MultipartFile file) {
        return preview(file, "USERS", Arrays.asList("中文名", "英文名", "邮箱", "手机号", "主组织编码", "岗位编码", "角色编码", "直属上级英文名"));
    }

    private ImportPreviewDTO preview(MultipartFile file, String type, List<String> expectedHeaders) {
        if (file == null || file.isEmpty()) throw BusinessException.error("导入文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) throw BusinessException.error("导入文件不能超过 5MB");
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("upload");
        List<Map<String, String>> rows = parse(file, expectedHeaders);
        List<ImportRowErrorDTO> errors = validate(type, rows, expectedHeaders);
        ImportJobDO job = new ImportJobDO();
        job.setId(UUID.randomUUID().toString());
        job.setImportType(type);
        job.setFilename(filename);
        job.setStatus(errors.isEmpty() ? "PREVIEWED" : "INVALID");
        job.setRowCount(rows.size());
        job.setErrorCount(errors.size());
        job.setCreatedBy(com.brad.pms.security.UserContext.userIdOrNull());
        job.setExpiresAt(LocalDateTime.now().plusHours(2));
        try {
            job.setPreviewJson(objectMapper.writeValueAsString(rows));
            job.setErrorJson(objectMapper.writeValueAsString(errors));
        } catch (Exception e) {
            throw BusinessException.error("无法保存导入预览");
        }
        importJobMapper.insert(job);
        ImportPreviewDTO dto = new ImportPreviewDTO();
        dto.setJobId(job.getId());
        dto.setImportType(type);
        dto.setFilename(filename);
        dto.setRowCount(rows.size());
        dto.setRows(rows);
        dto.setErrors(errors);
        return dto;
    }

    @Transactional
    public void commit(String jobId) {
        ImportJobDO job = importJobMapper.selectById(jobId);
        if (job == null || !"PREVIEWED".equals(job.getStatus()) || job.getExpiresAt().isBefore(LocalDateTime.now())) throw BusinessException.error("导入预览不存在、已失效或包含错误");
        if (job.getErrorCount() != null && job.getErrorCount() > 0) throw BusinessException.error("请先修正预览错误");
        try {
            List<Map<String, String>> rows = objectMapper.readValue(job.getPreviewJson(), new TypeReference<List<Map<String, String>>>() { });
            if ("ORGANIZATIONS".equals(job.getImportType())) commitOrganizations(rows);
            else if ("USERS".equals(job.getImportType())) commitUsers(rows);
            else throw BusinessException.error("不支持的导入类型");
            job.setStatus("SUCCESS");
            job.setCommittedAt(LocalDateTime.now());
            importJobMapper.updateById(job);
            operationLogService.record("IMPORT_COMMITTED", "IMPORT_JOB", null, null, Map.of("jobId", jobId, "type", job.getImportType(), "rows", job.getRowCount()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.error("导入失败，已回滚全部变更");
        }
    }

    private void commitOrganizations(List<Map<String, String>> rows) {
        Map<String, OrgUnitDO> byCode = orgUnitMapper.selectList(null).stream().collect(Collectors.toMap(OrgUnitDO::getCode, o -> o));
        for (Map<String, String> row : rows) if (byCode.containsKey(row.get("组织编码"))) throw BusinessException.error("组织编码已存在: " + row.get("组织编码"));
        List<Map<String, String>> pending = new ArrayList<>(rows);
        while (!pending.isEmpty()) {
            int inserted = 0;
            Iterator<Map<String, String>> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Map<String, String> row = iterator.next();
            OrgUnitTypeDO type = orgUnitTypeMapper.selectOne(new LambdaQueryWrapper<OrgUnitTypeDO>().eq(OrgUnitTypeDO::getCode, row.get("组织类型编码")));
            if (type == null) throw BusinessException.error("组织类型不存在: " + row.get("组织类型编码"));
            OrgUnitDO parent = row.get("父组织编码") == null || row.get("父组织编码").isBlank() ? null : byCode.get(row.get("父组织编码"));
            if (row.get("父组织编码") != null && !row.get("父组织编码").isBlank() && parent == null) continue;
            OrgUnitDO org = new OrgUnitDO();
            org.setCode(row.get("组织编码"));
            org.setName(row.get("组织名称"));
            org.setTypeId(type.getId());
            org.setParentId(parent == null ? null : parent.getId());
            org.setSort(parseInt(row.get("排序"), 0));
            org.setStatus("ACTIVE");
            org.setPath("/");
            orgUnitMapper.insert(org);
            org.setPath(parent == null ? "/" + org.getId() + "/" : parent.getPath() + org.getId() + "/");
            orgUnitMapper.updateById(org);
            byCode.put(org.getCode(), org);
                iterator.remove();
                inserted++;
            }
            if (inserted == 0) throw BusinessException.error("组织导入存在父级循环或不存在的父组织");
        }
    }

    private void commitUsers(List<Map<String, String>> rows) {
        Map<String, UserDO> usersByUsername = userMapper.selectList(null).stream()
                .filter(user -> user.getUsername() != null)
                .collect(Collectors.toMap(user -> EnterpriseDataMigration.normalizeUsername(user.getUsername()), user -> user));
        Map<String, OrgUnitDO> orgs = orgUnitMapper.selectList(null).stream().collect(Collectors.toMap(OrgUnitDO::getCode, o -> o));
        Map<String, PositionDO> positions = positionMapper.selectList(null).stream().collect(Collectors.toMap(PositionDO::getCode, p -> p));
        for (Map<String, String> row : rows) {
            String username = row.get("英文名");
            String normalized = EnterpriseDataMigration.normalizeUsername(username);
            if (usersByUsername.containsKey(normalized)) throw BusinessException.error("英文名已存在: " + username);
            UserDO user = new UserDO();
            user.setUsername(username);
            user.setUsernameNormalized(normalized);
            user.setNameZh(row.get("中文名"));
            user.setNickname(row.get("中文名"));
            user.setEmail(row.get("邮箱"));
            user.setPhone(row.get("手机号"));
            user.setPassword(encoder.encode(randomPassword()));
            user.setStatus(UserStatus.PENDING_ACTIVATION.name());
            user.setFailedLoginCount(0);
            userMapper.insert(user);
            usersByUsername.put(normalized, user);
        }
        for (Map<String, String> row : rows) {
            String normalized = EnterpriseDataMigration.normalizeUsername(row.get("英文名"));
            UserDO user = usersByUsername.get(normalized);
            OrgUnitDO org = orgs.get(row.get("主组织编码"));
            if (org == null) throw BusinessException.error("主组织不存在: " + row.get("主组织编码"));
            UserPositionDO position = new UserPositionDO();
            position.setUserId(user.getId());
            position.setOrgUnitId(org.getId());
            PositionDO pos = positions.get(row.get("岗位编码"));
            position.setPositionId(pos == null ? null : pos.getId());
            String managerUsername = EnterpriseDataMigration.normalizeUsername(row.get("直属上级英文名"));
            if (managerUsername != null && !managerUsername.isBlank()) {
                UserDO manager = usersByUsername.get(managerUsername);
                if (manager == null) throw BusinessException.error("直属上级不存在: " + row.get("直属上级英文名"));
                if (Objects.equals(manager.getId(), user.getId())) throw BusinessException.error("直属上级不能是本人: " + row.get("英文名"));
                position.setManagerUserId(manager.getId());
            }
            position.setAssignmentType("PRIMARY");
            position.setIsPrimary(true);
            position.setStartDate(LocalDate.now());
            position.setStatus("ACTIVE");
            userPositionMapper.insert(position);
            if (row.get("角色编码") != null && !row.get("角色编码").isBlank()) {
                RoleDO role = roleMapper.findByCode(row.get("角色编码"));
                if (role == null) throw BusinessException.error("角色不存在: " + row.get("角色编码"));
                UserRoleDO grant = new UserRoleDO();
                grant.setUserId(user.getId());
                grant.setRoleId(role.getId());
                grant.setStartAt(LocalDateTime.now());
                grant.setStatus("ACTIVE");
                userRoleMapper.insert(grant);
            }
        }
    }

    private List<Map<String, String>> parse(MultipartFile file, List<String> expectedHeaders) {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        try (InputStream input = file.getInputStream()) {
            if (name.endsWith(".csv")) {
                CSVParser parser = CSVParser.parse(input, StandardCharsets.UTF_8,
                        CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build());
                List<String> headers = parser.getHeaderNames();
                validateHeaders(headers, expectedHeaders);
                List<Map<String, String>> result = new ArrayList<>();
                parser.forEach(record -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String header : expectedHeaders) row.put(header, trim(record.get(header)));
                    result.add(row);
                });
                return result;
            }
            if (name.endsWith(".xlsx")) {
                try (Workbook workbook = new XSSFWorkbook(input)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    Row headerRow = sheet.getRow(0);
                    if (headerRow == null) throw BusinessException.error("Excel 缺少表头");
                    List<String> headers = new ArrayList<>();
                    for (int i = 0; i < headerRow.getLastCellNum(); i++) headers.add(cellValue(headerRow.getCell(i), true));
                    validateHeaders(headers, expectedHeaders);
                    List<Map<String, String>> result = new ArrayList<>();
                    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                        Row rowData = sheet.getRow(r);
                        if (rowData == null) continue;
                        Map<String, String> row = new LinkedHashMap<>();
                        for (int i = 0; i < expectedHeaders.size(); i++) row.put(expectedHeaders.get(i), cellValue(rowData.getCell(i), false));
                        if (row.values().stream().anyMatch(v -> v != null && !v.isBlank())) result.add(row);
                    }
                    return result;
                }
            }
            throw BusinessException.error("仅支持 .csv 或 .xlsx 文件");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.error("无法解析导入文件: " + e.getMessage());
        }
    }

    private List<ImportRowErrorDTO> validate(String type, List<Map<String, String>> rows, List<String> headers) {
        List<ImportRowErrorDTO> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int number = i + 2;
            List<String> requiredHeaders = "USERS".equals(type) ? headers.subList(0, 2) : headers.subList(0, 3);
            for (String required : requiredHeaders) if (row.get(required) == null || row.get(required).isBlank()) errors.add(new ImportRowErrorDTO(number, required, "不能为空"));
            if ("USERS".equals(type)
                    && (row.get("邮箱") == null || row.get("邮箱").isBlank())
                    && (row.get("手机号") == null || row.get("手机号").isBlank())) {
                errors.add(new ImportRowErrorDTO(number, "邮箱/手机号", "至少填写一项联系方式"));
            }
            String key = "ORGANIZATIONS".equals(type) ? row.get("组织编码") : EnterpriseDataMigration.normalizeUsername(row.get("英文名"));
            if (key != null && !key.isBlank() && !seen.add(key)) errors.add(new ImportRowErrorDTO(number, "ORGANIZATIONS".equals(type) ? "组织编码" : "英文名", "批次内重复"));
            if ("USERS".equals(type) && key != null && !key.matches("^[a-z][a-z0-9._-]{1,49}$")) errors.add(new ImportRowErrorDTO(number, "英文名", "英文名格式不正确"));
        }
        if ("ORGANIZATIONS".equals(type)) {
            Set<String> codes = rows.stream().map(row -> row.get("组织编码")).collect(Collectors.toSet());
            for (int i = 0; i < rows.size(); i++) {
                String parent = rows.get(i).get("父组织编码");
                if (parent != null && !parent.isBlank() && !codes.contains(parent) && orgUnitMapper.findByCode(parent) == null) errors.add(new ImportRowErrorDTO(i + 2, "父组织编码", "父组织不存在"));
            }
        } else {
            Set<String> existing = userMapper.selectList(null).stream()
                    .map(user -> EnterpriseDataMigration.normalizeUsername(user.getUsername()))
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<String, OrgUnitDO> orgs = orgUnitMapper.selectList(null).stream()
                    .collect(Collectors.toMap(OrgUnitDO::getCode, org -> org));
            Set<String> roleCodes = roleMapper.selectList(null).stream().map(RoleDO::getCode).collect(Collectors.toSet());
            Set<String> positionCodes = positionMapper.selectList(null).stream().map(PositionDO::getCode).collect(Collectors.toSet());
            Set<String> allUsernames = new HashSet<>(existing);
            rows.stream().map(row -> EnterpriseDataMigration.normalizeUsername(row.get("英文名")))
                    .filter(Objects::nonNull).forEach(allUsernames::add);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> row = rows.get(i);
                String username = EnterpriseDataMigration.normalizeUsername(row.get("英文名"));
                if (username != null && existing.contains(username)) errors.add(new ImportRowErrorDTO(i + 2, "英文名", "账号已存在"));
                String orgCode = row.get("主组织编码");
                if (orgCode == null || orgCode.isBlank() || !orgs.containsKey(orgCode)) errors.add(new ImportRowErrorDTO(i + 2, "主组织编码", "主组织不存在"));
                String positionCode = row.get("岗位编码");
                if (positionCode != null && !positionCode.isBlank() && !positionCodes.contains(positionCode)) errors.add(new ImportRowErrorDTO(i + 2, "岗位编码", "岗位不存在"));
                String roleCode = row.get("角色编码");
                if (roleCode != null && !roleCode.isBlank() && !roleCodes.contains(roleCode)) errors.add(new ImportRowErrorDTO(i + 2, "角色编码", "角色不存在"));
                String manager = EnterpriseDataMigration.normalizeUsername(row.get("直属上级英文名"));
                if (manager != null && !manager.isBlank() && !allUsernames.contains(manager)) errors.add(new ImportRowErrorDTO(i + 2, "直属上级英文名", "直属上级不存在"));
            }
        }
        return errors;
    }

    private void validateHeaders(List<String> actual, List<String> expected) {
        if (new HashSet<>(actual).size() != actual.size() || !actual.containsAll(expected)) throw BusinessException.error("文件表头必须包含: " + String.join(",", expected));
    }

    private String cellValue(Cell cell, boolean header) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA) throw BusinessException.error("不允许导入公式单元格");
        return trim(new DataFormatter().formatCellValue(cell));
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }
    private int parseInt(String value, int fallback) { try { return value == null || value.isBlank() ? fallback : Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
    private String randomPassword() { byte[] bytes = new byte[18]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
