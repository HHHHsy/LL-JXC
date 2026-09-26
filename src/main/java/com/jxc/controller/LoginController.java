package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.PasswordUtils;
import com.jxc.common.Result;
import com.jxc.config.TokenStore;
import com.jxc.entity.User;
import com.jxc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginController {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenStore tokenStore;

    @PostMapping("/login")
    public Result login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.trim().isEmpty()) {
            return Result.error("请输入用户名");
        }
        if (password == null || password.isEmpty()) {
            return Result.error("请输入密码");
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username.trim());
        User user = userService.getOne(wrapper);

        if (user == null || !PasswordUtils.matches(password, user.getPassword())) {
            return Result.error("用户名或密码错误");
        }

        // 历史明文口令：登录成功后自动升级为 PBKDF2 散列
        if (PasswordUtils.needsUpgrade(user.getPassword())) {
            User update = new User();
            update.setId(user.getId());
            update.setPassword(PasswordUtils.encode(password));
            userService.updateById(update);
        }

        String token = tokenStore.create(user);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("realName", displayName(user));
        return Result.success(result);
    }

    /** 登出：服务端立即作废令牌（前端也会清理本地存储） */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "Authorization", required = false) String token) {
        tokenStore.remove(token);
        return Result.success();
    }

    @GetMapping("/current-user")
    public Result currentUser(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = tokenStore.get(token);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("username", user.getUsername());
        result.put("realName", displayName(user));
        return Result.success(result);
    }

    private String displayName(User user) {
        return user.getRealName() != null && !user.getRealName().isEmpty() ? user.getRealName() : user.getUsername();
    }
}
