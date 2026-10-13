package com.oldbutgold.shop.modules.identity.infrastructure.persistence;
import com.oldbutgold.shop.modules.identity.application.VoucherIdentityFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

@Component
public class DefaultVoucherIdentityFacade implements VoucherIdentityFacade {
    private final JdbcTemplate jdbc;
    public DefaultVoucherIdentityFacade(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static final String CUSTOMERS="u.record_type='users' AND u.deleted_at IS NULL AND (public.og_has_role(u.user_id,'BUYER') OR public.og_has_role(u.user_id,'SELLER')) AND NOT public.og_has_role(u.user_id,'ADMIN') AND NOT public.og_has_role(u.user_id,'KTV')";
    private static final String SELECT="SELECT u.user_id,u.full_name,u.email,CASE WHEN public.og_has_role(u.user_id,'SELLER') THEN 'SELLER' ELSE 'BUYER' END AS role,CASE WHEN public.og28_effective_state(u.user_id)<>'ACTIVE' THEN public.og28_effective_state(u.user_id) ELSE u.status END AS state FROM public.users u WHERE ";
    private Customer map(java.sql.ResultSet r,int n)throws java.sql.SQLException {String s=r.getString("state");return new Customer(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),s,"ACTIVE".equals(s));}
    public void staff(long id,String role){if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM public.users WHERE record_type='users' AND user_id=? AND deleted_at IS NULL AND status='ACTIVE' AND public.og_has_role(user_id,?))",Boolean.class,id,role)))throw new AccessDeniedException("Không có quyền quản lý voucher.");}
    public Customer customer(long id,boolean lock){return jdbc.query(SELECT+CUSTOMERS+" AND u.user_id=?"+(lock?" FOR UPDATE OF u":""),this::map,id).stream().findFirst().orElseThrow(()->new AccessDeniedException("Chỉ khách hàng được nhận voucher."));}
    public Customers search(String query,String role,String status,int page,int size,Set<Long> include,Set<Long> exclude){String filter=" AND (u.full_name ILIKE '%'||?||'%' OR u.email ILIKE '%'||?||'%') AND (?='ALL' OR public.og_has_role(u.user_id,?)) AND (?='ALL' OR CASE WHEN public.og28_effective_state(u.user_id)<>'ACTIVE' THEN public.og28_effective_state(u.user_id) ELSE u.status END=?) AND (?::bigint[] IS NULL OR u.user_id=ANY(?::bigint[])) AND NOT(u.user_id=ANY(?::bigint[]))";Object[] args={query,query,role,role,status,status,array(include),array(include),array(exclude)};var all=new ArrayList<Object>(Arrays.asList(args));all.add(size);all.add((long)page*size);return new Customers(jdbc.query(SELECT+CUSTOMERS+filter+" ORDER BY u.user_id LIMIT ? OFFSET ?",this::map,all.toArray()),jdbc.queryForObject("SELECT count(*) FROM public.users u WHERE "+CUSTOMERS+filter,Long.class,args),page,size);}
    private String array(Set<Long> ids){return ids==null?null:"{"+ids.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+"}";}
    public void lockCustomers(Set<Long> ids){jdbc.query("SELECT user_id FROM public.users WHERE record_type='users' AND user_id=ANY(?::bigint[]) ORDER BY user_id FOR UPDATE",(r,n)->r.getLong(1),array(ids));}
}
