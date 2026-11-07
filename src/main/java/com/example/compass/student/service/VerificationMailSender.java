package com.example.compass.student.service;

/** Only the delivery adapter sees the plaintext code. Never log it. */
public interface VerificationMailSender { void send(String recipient,String code); }
