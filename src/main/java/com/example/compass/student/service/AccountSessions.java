package com.example.compass.student.service;

import jakarta.servlet.http.*;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
/** Withdrawal also erases in-memory transcript previews in all authenticated sessions. */
@Component
public class AccountSessions implements HttpSessionListener {
    private final ConcurrentHashMap<String,HttpSession> sessions=new ConcurrentHashMap<>();
    private final SessionRegistry registry;
    public AccountSessions(SessionRegistry registry) { this.registry=registry; }
    public void track(HttpSession session) { sessions.put(session.getId(),session); }
    @Override public void sessionDestroyed(HttpSessionEvent event) { sessions.remove(event.getSession().getId()); }
    public void revoke(String principal) {
        for(var info:registry.getAllSessions(principal,true)) {
            info.expireNow();
            var session=sessions.remove(info.getSessionId());
            if(session!=null) try {session.invalidate();} catch(IllegalStateException ignored) {}
            // An untracked session remains marked expired until its next request.
            if(session!=null) registry.removeSessionInformation(info.getSessionId());
        }
    }
}
