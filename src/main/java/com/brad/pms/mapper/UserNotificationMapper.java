package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.entity.UserNotificationDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserNotificationMapper extends BaseMapper<UserNotificationDO> {

    @Select({
            "<script>",
            "SELECT n.* FROM user_notification n",
            "LEFT JOIN project p ON p.id = n.project_id",
            "WHERE n.user_id = #{userId}",
            "  AND n.deleted = FALSE",
            "  AND (n.project_id IS NULL OR (p.id IS NOT NULL AND p.deleted = FALSE AND p.status &lt;&gt; 4))",
            "ORDER BY",
            "<choose>",
            "  <when test='unreadFirst'>CASE WHEN n.read_at IS NULL THEN 0 ELSE 1 END,</when>",
            "</choose>",
            "n.created_at DESC, n.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<UserNotificationDO> selectVisibleList(@Param("userId") Long userId,
                                                @Param("unreadFirst") boolean unreadFirst,
                                                @Param("limit") int limit);

    @Select({
            "SELECT COUNT(*) FROM user_notification n",
            "LEFT JOIN project p ON p.id = n.project_id",
            "WHERE n.user_id = #{userId}",
            "  AND n.deleted = FALSE",
            "  AND n.read_at IS NULL",
            "  AND (n.project_id IS NULL OR (p.id IS NOT NULL AND p.deleted = FALSE AND p.status <> 4))"
    })
    long countVisibleUnread(@Param("userId") Long userId);

    @Select({
            "<script>",
            "SELECT n.* FROM user_notification n",
            "LEFT JOIN project p ON p.id = n.project_id",
            "WHERE n.user_id = #{userId}",
            "  AND n.deleted = FALSE",
            "  AND (n.project_id IS NULL OR (p.id IS NOT NULL AND p.deleted = FALSE AND p.status &lt;&gt; 4))",
            "<if test='type != null and type != \"\"'>AND n.type = #{type}</if>",
            "<if test='unreadOnly'>AND n.read_at IS NULL</if>",
            "ORDER BY n.created_at DESC, n.id DESC",
            "</script>"
    })
    IPage<UserNotificationDO> selectVisiblePage(Page<UserNotificationDO> page,
                                                  @Param("userId") Long userId,
                                                  @Param("type") String type,
                                                  @Param("unreadOnly") boolean unreadOnly);
}
