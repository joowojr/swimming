package com.swimming.backend.agentwork.interfaces.router.security;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.agentwork.infra.persistence.entity.AgentAccessTokenEntity;
import com.swimming.backend.agentwork.application.service.AgentAccessTokenService;
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

    /** 토큰 관리 API는 PAT로 인증하지 않는다. 유출된 PAT가 새 PAT를 발급하거나 다른 토큰을 폐기하지 못하게 한다. */
    private boolean isAgentPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/api/agent-work/tokens") || path.startsWith("/api/agent-work/tokens/")) {
            return false;
        }
        return path.equals("/mcp") || path.startsWith("/mcp/") || path.startsWith("/api/agent-work/");
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }
}
