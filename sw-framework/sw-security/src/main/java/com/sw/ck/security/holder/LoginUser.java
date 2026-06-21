package com.sw.ck.security.holder;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class LoginUser implements Serializable {

    private Long userId;
    private String username;
    private List<String> roles;
    private List<String> permissions;
}
