package com.ghostcommunity.backend.config;

import com.ghostcommunity.backend.security.UserDetailsImpl;
import com.ghostcommunity.backend.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        if (event.getUser() instanceof UsernamePasswordAuthenticationToken auth &&
                auth.getPrincipal() instanceof UserDetailsImpl udi) {
            presenceService.updateActivity(udi.getId(), udi.getSectionId());
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        if (event.getUser() instanceof UsernamePasswordAuthenticationToken auth &&
                auth.getPrincipal() instanceof UserDetailsImpl udi) {
            presenceService.removeUser(udi.getId());
        }
    }
}
