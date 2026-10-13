package com.oldbutgold.shop.modules.platform.infrastructure.persistence;

import com.oldbutgold.shop.modules.platform.application.VoucherHistoryFacade;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class DefaultVoucherHistoryFacade implements VoucherHistoryFacade {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public DefaultVoucherHistoryFacade(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public Optional<Receipt> find(String key){return jdbc.query("SELECT user_id,command_request::text,new_values::text FROM public.audit_logs WHERE entity_type='VOUCHER' AND command_key=?",(r,n)->new Receipt(r.getLong(1),decode(r.getString(2)),decode(r.getString(3))),key).stream().findFirst();}
    public void record(long actor,long voucher,String action,String key,Map<String,Object> request,Map<String,Object> result){jdbc.update("INSERT INTO public.audit_logs(user_id,entity_type,entity_id,action,command_key,command_request,old_values,new_values) VALUES(?,'VOUCHER',?,?,?,?::jsonb,'{}',?::jsonb)",actor,voucher,action,key,encode(request),encode(result));}
    private String encode(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException("Cannot encode voucher receipt",e);}}
    private Map<String,Object> decode(String v){try{return json.readValue(v,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Cannot decode voucher receipt",e);}}
}
