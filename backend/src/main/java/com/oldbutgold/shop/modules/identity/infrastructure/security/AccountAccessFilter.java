package com.oldbutgold.shop.modules.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.application.AccountAccessService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

/** Checks signed users on new-activity endpoints, never blocks old-order responses. */
public class AccountAccessFilter extends OncePerRequestFilter {
    private final AccountAccessService access; private final ObjectMapper json;
    public AccountAccessFilter(AccountAccessService access,ObjectMapper json) { this.access=access;this.json=json; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException {
        String path=request.getRequestURI(),method=request.getMethod();
        String scope=null;
        if(path.startsWith("/api/v1/commerce/checkout")) scope="BUY_ALL";
        else if(!method.equals("GET") && path.startsWith("/api/v1/seller/products/") && !path.endsWith("/hide")) scope="NEW_LISTINGS";
        else if(!method.equals("GET") && path.equals("/api/v1/seller/products")) scope="NEW_LISTINGS";
        else if(!method.equals("GET") && (path.startsWith("/api/v1/messages") || path.startsWith("/api/v1/conversations"))) scope="CHAT";
        else if(!method.equals("GET") && (path.startsWith("/api/v1/ekyc/") || path.startsWith("/api/v1/seller-verification/"))) scope="NEW_TRANSACTIONS";
        var auth=SecurityContextHolder.getContext().getAuthentication();
        // Production decoder requires this issuer. Unsigned test principals do
        // not represent a session; integration tests exercise the signed path.
        if(scope!=null && auth instanceof JwtAuthenticationToken token
            && "og-shop".equals(token.getToken().getClaims().get("iss"))) {
            try {
                if(access.restricted(Long.parseLong(token.getToken().getSubject()),scope)) {
                    problem(response,403,"ACCOUNT_ACTION_RESTRICTED","Thao tác đang bị hạn chế. Xem tình trạng tài khoản; quyền xem và phản hồi đơn cũ được giữ.");return;
                }
            } catch(Exception error) {
                problem(response,503,"ACCOUNT_ACCESS_UNAVAILABLE","Chưa kiểm tra được quyền thao tác. Vui lòng thử lại.");return;
            }
        }
        chain.doFilter(request,response);
    }
    private void problem(HttpServletResponse response,int status,String code,String message)throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(),Map.of("code",code,"message",message));
    }
}
