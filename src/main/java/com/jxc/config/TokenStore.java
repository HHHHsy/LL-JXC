package com.jxc.config;

import com.jxc.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带过期时间的登录令牌存储。
 *
 * <p>替代原先"永不失效的静态 Map"：令牌有过期时间、登出即失效、过期条目会被清理，
 * 避免长期运行下的内存泄漏与令牌无法撤销的问题。</p>
 */
@Component
public class TokenStore {

    private static final class Entry {
        private final User user;
        private final long expireAt;

        private Entry(User user, long expireAt) {
            this.user = user;
            this.expireAt = expireAt;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 令牌有效期（分钟），默认 8 小时，可通过 jxc.token.expire-minutes 配置 */
    @Value("${jxc.token.expire-minutes:480}")
    private long expireMinutes = 480;

    public String create(User user) {
        cleanup();
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, new Entry(user, System.currentTimeMillis() + expireMinutes * 60_000L));
        return token;
    }

    /** 返回有效令牌对应的用户；不存在或已过期返回 null */
    public User get(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Entry entry = store.get(token);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            store.remove(token);
            return null;
        }
        return entry.user;
    }

    public void remove(String token) {
        if (token != null) {
            store.remove(token);
        }
    }

    public int size() {
        return store.size();
    }

    /** 清理过期令牌，防止内存无限增长 */
    public void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expireAt < now) {
                it.remove();
            }
        }
    }
}
