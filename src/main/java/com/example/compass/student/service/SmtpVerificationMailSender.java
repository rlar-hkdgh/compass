package com.example.compass.student.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
@Component
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final JavaMailSenderImpl mail=new JavaMailSenderImpl();
    private final String from;
    private final boolean enabled;
    public SmtpVerificationMailSender(
            @Value("${compass.mail.enabled:false}") boolean enabled,
            @Value("${compass.mail.host:smtp.gmail.com}") String host,
            @Value("${compass.mail.port:587}") int port,
            @Value("${compass.mail.username:}") String username,
            @Value("${compass.mail.password:}") String password,
            @Value("${compass.mail.from:}") String from) {
        this.enabled=enabled;this.from=from;
        mail.setHost(host);mail.setPort(port);mail.setUsername(username);mail.setPassword(password);
        mail.setDefaultEncoding("UTF-8");
        var p=mail.getJavaMailProperties();
        p.setProperty("mail.smtp.auth","true");
        p.setProperty("mail.smtp.starttls.enable","true");
        p.setProperty("mail.smtp.starttls.required","true");
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        p.setProperty("mail.smtp.connectiontimeout","5000");
        p.setProperty("mail.smtp.timeout","5000");
        p.setProperty("mail.smtp.writetimeout","5000");
        p.setProperty("mail.debug","false");
    }
    @Override public void send(String recipient,String code) {
        if(!enabled || from.isBlank() || mail.getUsername()==null || mail.getUsername().isBlank()
                || mail.getPassword()==null || mail.getPassword().isBlank())
            throw new IllegalStateException("Mail delivery is not configured");
        var message=new SimpleMailMessage();
        message.setFrom(from);message.setTo(recipient);
        message.setSubject("[Compass] 학교 이메일 인증번호");
        message.setText("학교 이메일 인증번호는 "+code+" 입니다.\n10분 안에 입력해 주세요.\n요청하지 않았다면 이 메일을 무시하세요.");
        mail.send(message);
    }
}
