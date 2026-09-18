package com.swimming.backend.common.security;

import com.swimming.backend.agentwork.repository.entity.AgentAccessTokenEntity;
import com.swimming.backend.agentwork.service.AgentAccessTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Arrays;

@Component
@ConditionalOnBean(AgentAccessTokenService.class)
@RequiredArgsConstructor
public class AgentAccessTokenFilter extends OncePerRequestFilter {
    private static final String TOKEN_PREFIX = "swm_pat_";
    private final AgentAccessTokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null && isAgentPath(request)) {
            String token = bearerToken(request);
            if (token != null && token.startsWith(TOKEN_PREFIX)) {
                try {
                    AgentAccessTokenEntity accessToken = tokenService.authenticate(token);
                    var authorities = Arrays.stream(accessToken.getScopes().split(","))
                            .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList();
                    var authentication = new PreAuthenticatedAuthenticationToken(new AuthUser(accessToken.getUserId(), null), token, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (RuntimeException ignored) { }
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAgentPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/mcp") || path.startsWith("/mcp/") || path.startsWith("/api/agent-work/");
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }
}
