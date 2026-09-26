package com.jxc.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器。
 *
 * <p>说明：实体上的 {@code @TableField(fill = FieldFill.INSERT / INSERT_UPDATE)} 只有在容器中
 * 存在 MetaObjectHandler 时才会真正生效。MyBatis-Plus 对于带填充注解的字段会「无条件」拼进
 * INSERT / UPDATE 语句（不带 &lt;if&gt; 判断），因此一旦缺少本处理器，create_time / update_time
 * 会被显式写成 NULL，数据库的 DEFAULT CURRENT_TIMESTAMP 与 ON UPDATE CURRENT_TIMESTAMP 都不会生效。</p>
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
