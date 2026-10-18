package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ListingFeeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ListingFeeRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    public void enable(long productId) {
        jdbc.update("UPDATE public.products SET manual_listing_fee_required=true WHERE product_id=?",productId);
    }
    public boolean isManaged(long productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT manual_listing_fee_required FROM public.products WHERE product_id=?",Boolean.class,productId));
    }
    public long snapshot(ProductEntity p,List<CatalogDtos.ProductMediaResponse> media) {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("contentRevision",p.getContentRevision());data.put("version",p.getVersion());
        data.put("title",p.getTitle());data.put("description",p.getDescription());
        data.put("condition",p.getCondition());data.put("usageDuration",p.getUsageDuration());
        data.put("defects",p.getDefects());data.put("repairHistory",p.getRepairHistory());
        data.put("includedAccessories",p.getIncludedAccessories());data.put("location",p.getLocation());
        data.put("categories",p.getCategoryIds());data.put("requiresBuyerEkyc",p.isRequiresBuyerEkyc());data.put("media",media);
        try {
            return jdbc.queryForObject("""
                INSERT INTO og_compat.product_revisions(product_id,revision_no,provenance,state,
                    listed_unit_price,quantity,content_snapshot,submitted_at)
                SELECT ?,COALESCE(max(revision_no),0)+1,'MANUAL_V27','SUBMITTED',?,?,?::jsonb,CURRENT_TIMESTAMP
                FROM og_compat.product_revisions WHERE product_id=? RETURNING revision_id
                """,Long.class,p.getId(),p.getListedPrice(),p.getQuantityTotal(),json.writeValueAsString(data),p.getId());
        } catch(JsonProcessingException e) { throw new IllegalStateException("Cannot snapshot manual review",e); }
    }
    public Fee assess(ProductEntity p,long decisionId,String commandKey) {
        Long policy=jdbc.queryForObject("""
            SELECT policy_id FROM og_compat.listing_fee_policies WHERE effective_from<=CURRENT_TIMESTAMP
              AND (effective_until IS NULL OR effective_until>CURRENT_TIMESTAMP)
            """,Long.class);
        long assessment=jdbc.queryForObject("""
            INSERT INTO og_compat.listing_fee_assessments(product_id,revision_id,decision_id,policy_id,
                listed_total,computed_fee,cumulative_before,amount_due,command_key)
            VALUES(?,?,?,?,1,0,0,0,?) RETURNING assessment_id
            """,Long.class,p.getId(),p.getCurrentRevisionId(),decisionId,policy,"ASSESS:"+commandKey);
        BigDecimal due=jdbc.queryForObject("SELECT amount_due FROM og_compat.listing_fee_assessments WHERE assessment_id=?",BigDecimal.class,assessment);
        if(due.signum()>0) jdbc.update("""
            INSERT INTO og_compat.listing_fee_charges(charge_id,assessment_id,amount,command_key)
            VALUES(?,?,?,?)
            """,UUID.randomUUID(),assessment,due,"CHARGE:"+commandKey);
        return byDecision(decisionId);
    }
    private static final String FEE_QUERY="""
        SELECT a.assessment_id,a.product_id,a.revision_id,a.decision_id,a.listed_total,a.computed_fee,
            a.amount_due,c.charge_id,c.state,f.policy_code,
            COALESCE((SELECT sum(receipt.amount) FROM og_compat.listing_fee_receipts receipt
                JOIN og_compat.listing_fee_charges ch USING(charge_id)
                JOIN og_compat.listing_fee_assessments previous ON previous.assessment_id=ch.assessment_id
                WHERE previous.product_id=a.product_id),0) AS total_paid
        FROM og_compat.listing_fee_assessments a
        JOIN og_compat.listing_fee_policies f USING(policy_id)
        LEFT JOIN og_compat.listing_fee_charges c USING(assessment_id)
        """;
    public Fee latest(long productId) { return query(" WHERE a.product_id=? ORDER BY a.assessment_id DESC LIMIT 1",productId); }
    public Fee byDecision(long decisionId) { return query(" WHERE a.decision_id=?",decisionId); }
    private Fee query(String where,long id) {
        var fees=jdbc.query(FEE_QUERY+where,(rs,row)->new Fee(rs.getLong("assessment_id"),rs.getLong("product_id"),
            rs.getLong("revision_id"),rs.getLong("decision_id"),rs.getBigDecimal("listed_total"),
            rs.getBigDecimal("computed_fee"),rs.getBigDecimal("amount_due"),rs.getObject("charge_id",UUID.class),
            rs.getString("state"),rs.getString("policy_code"),rs.getBigDecimal("total_paid")),id);
        return fees.isEmpty()?null:fees.getFirst();
    }
    public record Fee(long assessmentId,long productId,long revisionId,long decisionId,BigDecimal listedTotal,
                      BigDecimal computedFee,BigDecimal assessedAmount,UUID chargeId,String chargeState,
                      String policyCode,BigDecimal totalPaid) {
        public boolean paid() { return assessedAmount.signum()==0 || "CONFIRMED".equals(chargeState); }
        public BigDecimal outstanding() { return paid()?BigDecimal.ZERO:assessedAmount; }
    }
}
