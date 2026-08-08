package com.isg.backend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TempHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode("123456"));
    }
}