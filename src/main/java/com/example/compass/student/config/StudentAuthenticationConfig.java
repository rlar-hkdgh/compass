package com.example.compass.student.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
@Configuration public class StudentAuthenticationConfig {
    @Bean UserDetailsService studentAccounts(JdbcTemplate db) {
        return schoolEmail -> db.query("select id,password_hash from student where school_email=?",    (r,n)->User.withUsername(r.getString("id")).password(r.getString("password_hash")).authorities(List.of()).build(),schoolEmail)    .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("Unknown student"));
    }
    @Bean AuthenticationManager studentAuthentication(UserDetailsService accounts,PasswordEncoder encoder) {
        var provider=new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
