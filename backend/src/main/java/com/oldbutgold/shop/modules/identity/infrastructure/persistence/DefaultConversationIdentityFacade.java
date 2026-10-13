package com.oldbutgold.shop.modules.identity.infrastructure.persistence;
import com.oldbutgold.shop.modules.identity.application.ConversationIdentityFacade;
import com.oldbutgold.shop.modules.identity.application.AccountAccessService;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import com.oldbutgold.shop.modules.identity.application.RegistrationException;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
@Service
public class DefaultConversationIdentityFacade implements ConversationIdentityFacade {
    private final JdbcTemplate jdbc; private final AccountAccessService access; private final IdentityCommerceFacade commerce;
    public DefaultConversationIdentityFacade(JdbcTemplate jdbc,AccountAccessService access,IdentityCommerceFacade commerce) {this.jdbc=jdbc;this.access=access;this.commerce=commerce;}
    public Person customer(long id) {
        var values=jdbc.query("""
            SELECT u.user_id,u.full_name,public.og_has_role(u.user_id,'SELLER') AS seller,(public.og_has_role(u.user_id,'BUYER') OR public.og_has_role(u.user_id,'SELLER')) AS buyer FROM public.users u
            WHERE u.record_type='users' AND u.user_id=? AND u.deleted_at IS NULL
              AND (public.og_has_role(u.user_id,'BUYER') OR public.og_has_role(u.user_id,'SELLER'))
              AND NOT public.og_has_role(u.user_id,'ADMIN') AND NOT public.og_has_role(u.user_id,'KTV')
            """,(r,n)->new Person(r.getLong(1),r.getString(2),r.getBoolean(3),r.getBoolean(4)),id);
        return values.stream().findFirst().orElseThrow(()->new AccessDeniedException("Tài khoản không thuộc phạm vi trao đổi của khách hàng."));
    }
    public void lockPair(long buyer,long seller) {jdbc.query("SELECT user_id FROM public.users WHERE record_type='users' AND user_id IN (?,?) ORDER BY user_id FOR UPDATE",(r,n)->r.getLong(1),buyer,seller);}
    public String displayName(long id) {return jdbc.query("SELECT full_name FROM public.users WHERE record_type='users' AND user_id=?",(r,n)->r.getString(1),id).stream().findFirst().orElse("Tài khoản đã ngừng hoạt động");}
    public void requireChat(long id) {customer(id);access.requireAllowed(id,"CHAT");}
    public boolean chatAllowed(long id) {return !access.restricted(id,"CHAT");}
    public void requireNewConversation(long buyer,long seller) {access.requireAllowed(buyer,"NEW_TRANSACTIONS");access.requireAllowed(seller,"NEW_TRANSACTIONS");}
    public void requireBlockManager(long seller) {if(!customer(seller).seller())throw new AccessDeniedException("Chỉ người bán quản lý danh sách chặn.");access.requireAllowed(seller,"NEW_TRANSACTIONS");}
    public void requireTrade(long buyer,long seller,boolean ekyc) {customer(buyer);if(!customer(seller).seller())throw new AccessDeniedException("Đối tác không có quyền bán.");commerce.requirePurchaseAllowed(buyer,seller,ekyc);if(ekyc&&!commerce.isUserVerified(buyer))throw new RegistrationException("BUYER_EKYC_REQUIRED","Sản phẩm yêu cầu người mua xác thực eKYC.",403);}
}
