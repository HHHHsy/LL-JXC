package com.jxc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jxc.common.Result;
import com.jxc.entity.User;
import com.jxc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class LoginController {

    // 简单的内存Token存储
    public static final Map<String, User> TOKEN_MAP = new ConcurrentHashMap<>();

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isEmpty()) {
            return Result.error("请输入用户名");
        }
        if (password == null || password.isEmpty()) {
            return Result.error("请输入密码");
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        wrapper.eq("password", password);
        User user = userService.getOne(wrapper);

        if (user == null) {
            return Result.error("用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        TOKEN_MAP.put(token, user);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("realName", user.getRealName() != null ? user.getRealName() : user.getUsername());
        return Result.success(result);
    }

    @PostMapping("/logout")
    public Result logout(@RequestHeader("Authorization") String token) {
        if (token != null) {
            TOKEN_MAP.remove(token);
        }
        return Result.success();
    }

    @GetMapping("/current-user")
    public Result currentUser(@RequestHeader("Authorization") String token) {
        User user = TOKEN_MAP.get(token);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName() != null ? user.getRealName() : user.getUsername());
        return Result.success(result);
    }
}
