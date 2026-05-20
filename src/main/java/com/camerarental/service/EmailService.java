package com.camerarental.service;

public interface EmailService {

    void sendPasswordResetEmail(String to, String resetLink);
}
