package com.oldbutgold.shop.modules.platform.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

/** Isolated real PostgreSQL databases. Never resets the supplied application database. */
@EnabledIfEnvironmentVariable(named="OGSHOP_EXPANSION_TEST_DB_URL", matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseExpansionPostgresTest {
    private JdbcTemplate root, j;
    private DriverManagerDataSource ds;
    private String database;
    private long admin, ktv, buyer, seller, category;
    private String key() { return UUID.randomUUID().toString(); }
    private UUID uuid() { return UUID.randomUUID(); }
    private DriverManagerDataSource source(String name) {
        String url=System.getenv("OGSHOP_EXPANSION_TEST_DB_URL");
        if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/ogshop_[a-z0-9_]*test[a-z0-9_]*"))
            throw new IllegalStateException("Use an explicit local test database URL without parameters");
        return new DriverManagerDataSource(url.substring(0,url.lastIndexOf('/')+1)+name,
            System.getenv("OGSHOP_TEST_DB_USER"),System.getenv("OGSHOP_TEST_DB_PASSWORD"));
    }
    private Flyway flyway(DriverManagerDataSource data, String target) {
        var c=Flyway.configure().dataSource(data).defaultSchema("public").schemas("public").locations("classpath:db/migration").callbacks(new LegacyModerationHistoryCallback());
        if(target!=null)c.target(target);
        return c.load();
    }
    private long id(String sql,Object... args) { return j.queryForObject(sql,Long.class,args); }
    private <T>T tx(Supplier<T> action) { return new TransactionTemplate(new DataSourceTransactionManager(ds)).execute(s->action.get()); }
    private void rejected(Runnable action) { assertThatThrownBy(action::run).isInstanceOf(DataAccessException.class); }
    @BeforeAll void setup() {
        database="ogshop_expansion_"+key().replace("-","");
        root=new JdbcTemplate(source("postgres"));root.execute("CREATE DATABASE "+database);
        ds=source(database);j=new JdbcTemplate(ds);flyway(ds,null).migrate();
        var properties=new java.util.Properties();properties.setProperty("currentSchema","og_compat,public");ds.setConnectionProperties(properties);
        admin=user("ADMIN",false);ktv=user("KTV",false);category=id("SELECT min(category_id) FROM categories");
    }
    @AfterAll void cleanup() {
        if(root!=null&&database!=null&&database.matches("ogshop_expansion_[0-9a-f]{32}"))root.execute("DROP DATABASE "+database);
    }
    private long user(String role,boolean canonical) {
        return tx(()->{
            long u=id("INSERT INTO users(email,password_hash,full_name,identity_workflow) VALUES(?,'FAKE','Test',?) RETURNING user_id","test-"+key()+"@example.invalid",canonical?"UC83":"LEGACY_V14");
            j.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,role_id FROM roles WHERE role_name=?",u,role);return u;
        });
    }
    private long draft(long u) { return id("INSERT INTO ekyc_profiles(user_id,revision_no) SELECT ?,COALESCE(max(revision_no),0)+1 FROM ekyc_profiles WHERE user_id=? RETURNING profile_id",u,u); }
    private long identity(long u) {
        return tx(()->{
            long p=draft(u);String digest=key().replace("-","").repeat(2);
            j.update("INSERT INTO identity_document_registry(document_digest,digest_key_version,user_id,first_profile_id) VALUES(?,'TEST-HMAC',?,?)",digest,u,p);
            for(String type:new String[]{"CARD_FRONT","CARD_BACK","LIVE_FRAME","REFERENCE"})j.update("INSERT INTO ekyc_private_assets(profile_id,asset_type,object_key,content_digest,expires_at) VALUES(?,?,?, ?,CURRENT_TIMESTAMP+INTERVAL '2 months')",p,type,"private/test/"+key(),digest);
            j.update("UPDATE ekyc_profiles SET document_digest=?,state='PENDING',submitted_at=CURRENT_TIMESTAMP WHERE profile_id=?",digest,p);
            j.update("INSERT INTO ekyc_decisions(profile_id,reviewed_by,outcome,command_key) VALUES(?,?,'VERIFIED',?)",p,ktv,key());return p;
        });
    }
    @BeforeEach void parties() {
        buyer=user("BUYER",true);seller=user("BUYER",true);long p=identity(seller);
        long a=id("INSERT INTO addresses(user_id,recipient_name,phone_number,province,district,ward,detail_address) VALUES(?,'Test','0900000000','P','D','W','A') RETURNING address_id",seller);
        tx(()->{
            long sp=id("INSERT INTO seller_profiles(user_id,state,ekyc_profile_id,pickup_address_id,bank_snapshot) VALUES(?,'PENDING_REVIEW',?,?,?::jsonb) RETURNING seller_profile_id",seller,p,a,"{\"bank_name\":\"Mock\",\"account_number\":\"FAKE\",\"account_holder\":\"Test\"}");
            j.update("INSERT INTO seller_profile_decisions(seller_profile_id,revision_no,reviewed_by,outcome,profile_snapshot,command_key) VALUES(?,1,?,'APPROVED','{}',?)",sp,ktv,key());return null;
        });
    }
    private record Listing(long product,long revision,BigDecimal due) {}
    private void media(long r,String type,int order) { j.update("INSERT INTO revision_media(revision_id,media_type,object_key,content_digest,file_size_bytes,duration_seconds,display_order) VALUES(?,?,?,repeat('a',64),100,10,?)",r,type,"test/"+key(),order); }
    private Listing listing(int quantity,int price) {
        return tx(()->{
            long p=id("INSERT INTO products(seller_id,category_id,title,description,listed_price,condition,workflow_model,quantity_total,return_allowed,delivery_options) VALUES(?,?,'Test','Test',?,'GOOD','UC83',?,true,'[\"SELF_PICKUP\",\"CARRIER\"]') RETURNING product_id",seller,category,price,quantity);
            j.update("INSERT INTO product_categories(product_id,category_id) VALUES(?,?)",p,category);
            return revision(p,quantity,price);
        });
    }
    private Listing revision(long p,int quantity,int price) {
        return tx(()->{
            j.update("UPDATE products SET status='HIDDEN' WHERE product_id=? AND status='ACTIVE'",p);
            long r=id("INSERT INTO product_revisions(product_id,revision_no,provenance,listed_unit_price,quantity,return_allowed,delivery_options,content_snapshot) SELECT ?,COALESCE(max(revision_no),0)+1,'UC83',?,?,true,'[\"SELF_PICKUP\",\"CARRIER\"]','{}' FROM product_revisions WHERE product_id=? RETURNING revision_id",p,price,quantity,p);
            media(r,"IMAGE",0);media(r,"VIDEO",1);j.update("UPDATE product_revisions SET state='SUBMITTED',submitted_at=CURRENT_TIMESTAMP WHERE revision_id=?",r);
            j.update("UPDATE products SET status='PENDING',current_revision_id=? WHERE product_id=?",r,p);
            long d=id("INSERT INTO product_moderation_decisions(product_id,revision_id,reviewer_id,decision,product_version,command_key) VALUES(?,?,?,'APPROVED',0,?) RETURNING decision_id",p,r,ktv,key());
            long a=id("INSERT INTO listing_fee_assessments(product_id,revision_id,decision_id,policy_id,listed_total,computed_fee,cumulative_before,amount_due,command_key) SELECT ?,?,?,policy_id,1,0,0,0,? FROM listing_fee_policies WHERE effective_until IS NULL RETURNING assessment_id",p,r,d,key());
            BigDecimal due=j.queryForObject("SELECT amount_due FROM listing_fee_assessments WHERE assessment_id=?",BigDecimal.class,a);
            if(due.signum()>0){UUID charge=uuid();j.update("INSERT INTO listing_fee_charges(charge_id,assessment_id,amount,command_key) VALUES(?,?,?,?)",charge,a,due,key());j.update("INSERT INTO listing_fee_receipts(receipt_id,charge_id,provider,provider_transaction_id,amount) VALUES(?,?,'BANK_TRANSFER_MOCK',?,?)",uuid(),charge,key(),due);}
            j.update("UPDATE products SET status='ACTIVE',public_revision_id=?,listed_price=?,quantity_total=?,return_allowed=true,delivery_options='[\"SELF_PICKUP\",\"CARRIER\"]' WHERE product_id=?",r,price,quantity,p);
            return new Listing(p,r,due);
        });
    }
    private record Checkout(UUID group,long order,long item,UUID reservation) {}
    private Checkout checkout(Listing p,int quantity) {
        return checkout(p,quantity,null,0);
    }
    private Checkout checkout(Listing p,int quantity,UUID grant,int discount) {
        return tx(()->{
            UUID group=uuid(),reservation=uuid();BigDecimal amount=j.queryForObject("SELECT listed_unit_price*? FROM product_revisions WHERE revision_id=?",BigDecimal.class,quantity,p.revision);
            j.update("INSERT INTO checkout_groups(group_id,buyer_id,command_key,request_digest,address_snapshot,delivery_method,payment_method,expected_total,payment_due_at) VALUES(?,?,?,repeat('a',64),'{\"recipient_name\":\"Test\",\"phone_number\":\"0900000000\",\"province\":\"P\",\"district\":\"D\",\"ward\":\"W\",\"detail_address\":\"A\"}','SELF_PICKUP','BANK_TRANSFER_MOCK',?,CURRENT_TIMESTAMP+INTERVAL '1 hour')",group,buyer,key(),amount.subtract(BigDecimal.valueOf(discount)));
            long o=id("INSERT INTO orders(checkout_group_id,group_id,buyer_id,seller_id,shipping_recipient_name,shipping_phone_number,shipping_province,shipping_district,shipping_ward,shipping_detail_address,subtotal,total_amount,buyer_system_fee,seller_system_fee,seller_proceeds,payment_due_at,workflow_model,return_allowed,delivery_method,handover_day,voucher_discount_amount) VALUES(?,?,?,?,'Test','0900000000','P','D','W','A',?,?,0,0,?,CURRENT_TIMESTAMP+INTERVAL '1 hour','UC83',true,'SELF_PICKUP',CURRENT_DATE,?) RETURNING order_id",group,group,buyer,seller,amount,amount.subtract(BigDecimal.valueOf(discount)),amount,discount);
            long i=id("INSERT INTO order_items(order_id,product_id,product_title,quantity,listed_price,agreed_price,buyer_system_fee,seller_system_fee,buyer_line_total,seller_line_proceeds,workflow_model,product_revision_id,return_allowed,voucher_allocation) SELECT ?,?,'Test',?,listed_unit_price,listed_unit_price,0,0,?,?, 'UC83',revision_id,true,? FROM product_revisions WHERE revision_id=? RETURNING order_item_id",o,p.product,quantity,amount.subtract(BigDecimal.valueOf(discount)),amount,discount,p.revision);
            if(discount>0){
                // All snapshots and source allocations commit atomically with the reservation.
                UUID redemption=uuid();j.update("INSERT INTO checkout_redemptions(redemption_id,group_id,buyer_id,grant_id,policy_snapshot,discount_amount) VALUES(?,?,?,?,'{}',?)",redemption,group,buyer,grant,discount);
                j.update("INSERT INTO discount_allocations(allocation_id,order_item_id,redemption_id,component,amount) VALUES(?,?,?,'GOODS',?)",uuid(),i,redemption,discount);
            }
            j.update("INSERT INTO inventory_reservations(reservation_id,order_item_id,product_id,quantity,expires_at,command_key) SELECT ?,?,?,?,payment_due_at,? FROM orders WHERE order_id=?",reservation,i,p.product,quantity,key(),o);
            return new Checkout(group,o,i,reservation);
        });
    }
    private UUID paid(Checkout c) {
        return tx(()->{
            UUID intent=uuid(),confirmation=uuid(),allocation=uuid();
            j.update("INSERT INTO payment_intents(intent_id,purpose,group_id,expected_amount,payment_method,deadline,command_key) SELECT ?,'CHECKOUT_GROUP',group_id,expected_total,payment_method,payment_due_at,? FROM checkout_groups WHERE group_id=?",intent,key(),c.group);
            long attempt=id("INSERT INTO payment_attempts(intent_id,txn_ref,amount,provider) SELECT ?,?,expected_amount,payment_method FROM payment_intents WHERE intent_id=? RETURNING attempt_id",intent,key(),intent);
            j.update("INSERT INTO payment_confirmations(confirmation_id,intent_id,attempt_id,provider,provider_transaction_id,amount) SELECT ?,?,?,payment_method,?,expected_amount FROM payment_intents WHERE intent_id=?",confirmation,intent,attempt,key(),intent);
            j.update("INSERT INTO payment_allocations(allocation_id,intent_id,confirmation_id,order_id,amount) SELECT ?,?,?,order_id,total_amount FROM orders WHERE order_id=?",allocation,intent,confirmation,c.order);
            j.update("UPDATE orders SET status='PAID_HELD',paid_confirmed_at=(SELECT confirmed_at FROM payment_confirmations WHERE confirmation_id=?),seller_accept_due_at=(SELECT confirmed_at+INTERVAL '2 days' FROM payment_confirmations WHERE confirmation_id=?) WHERE order_id=?",confirmation,confirmation,c.order);
            j.update("UPDATE inventory_reservations SET state='FUNDED' WHERE reservation_id=?",c.reservation);j.update("UPDATE checkout_groups SET state='PAID' WHERE group_id=?",c.group);return allocation;
        });
    }
    private UUID funds(Checkout c,UUID a) { UUID f=uuid();j.update("INSERT INTO order_fund_components(component_id,order_id,allocation_id,component_type,confirmed_amount) SELECT ?,order_id,?,'GOODS_CASH',total_amount FROM orders WHERE order_id=?",f,a,c.order);return f; }
    private long deliver(Checkout c) {
        return tx(()->{
            j.update("UPDATE orders SET status='SELLER_CONFIRMED' WHERE order_id=?",c.order);j.update("UPDATE inventory_reservations SET state='CONSUMED' WHERE reservation_id=?",c.reservation);
            long s=id("INSERT INTO shipments(order_id,carrier,status,workflow_model,delivery_method,fee_snapshot,fee_payer) VALUES(?,'SELF_PICKUP','PENDING','UC83','SELF_PICKUP',0,'BUYER') RETURNING shipment_id",c.order);
            confirmations(s);assertThat(j.queryForObject("SELECT og_confirm_direct_handover(?)",Boolean.class,s)).isTrue();return s;
        });
    }
    private void confirmations(long s) { for(String party:new String[]{"BUYER","SELLER"})j.update("INSERT INTO handover_confirmations(confirmation_id,shipment_id,party,user_id,outcome,command_key) VALUES(?,?,?,?,'SUCCESS',?)",uuid(),s,party,party.equals("BUYER")?buyer:seller,key()); }
    private long returnCase(Checkout c) {return id("INSERT INTO cases(case_type,order_id,created_by,description,command_key) VALUES('RETURN',?,?,'Test',?) RETURNING case_id",c.order,buyer,key());}
    private UUID refund(UUID f) {UUID op=uuid();j.update("INSERT INTO settlement_operations(operation_id,component_id,operation_type,amount,cause_type,recipient_user_id,command_key) SELECT ?,?,'REFUND_BUYER',confirmed_amount,'CANCELLATION',?,? FROM order_fund_components WHERE component_id=?",op,f,buyer,key(),f);return op;}
    private void cancel(Checkout c) { j.update("UPDATE orders SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP,cancellation_reason='Test' WHERE order_id=?",c.order); }
    private int race(Runnable action)throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){Callable<Boolean> task=()->{start.await();try{action.run();return true;}catch(DataAccessException e){return false;}};var a=pool.submit(task);var b=pool.submit(task);start.countDown();return(a.get(20,TimeUnit.SECONDS)?1:0)+(b.get(20,TimeUnit.SECONDS)?1:0);}
    }
    @Test void cleanMigrationsValidate() {flyway(ds,null).validate();assertThat(id("SELECT max(version::int) FROM public.flyway_schema_history WHERE success")).isEqualTo(23);assertThat(id("SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history'")).isEqualTo(48);}
    @Test void upgradePreservesV14WithoutInventingApprovals() {
        String name="ogshop_expansion_"+key().replace("-","");root.execute("CREATE DATABASE "+name);
        try{
            var data=source(name);var old=new JdbcTemplate(data);flyway(data,"14").migrate();
            long u=old.queryForObject("INSERT INTO users(email,password_hash,full_name) VALUES('legacy@example.invalid','FAKE','Legacy') RETURNING user_id",Long.class);
            long p=new TransactionTemplate(new DataSourceTransactionManager(data)).execute(s->{
                long pid=old.queryForObject("INSERT INTO products(seller_id,category_id,title,description,listed_price,condition,status) SELECT ?,min(category_id),'Legacy','Original',123456,'GOOD','SOLD' FROM categories RETURNING product_id",Long.class,u);
                old.update("INSERT INTO product_categories(product_id,category_id) SELECT product_id,category_id FROM products WHERE product_id=?",pid);return pid;
            });
            var before=old.queryForMap("SELECT product_id,title,description,listed_price,status FROM products WHERE product_id=?",p);
            var history=old.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank");
            flyway(data,null).migrate();flyway(data,null).validate();
            var properties=new java.util.Properties();properties.setProperty("currentSchema","og_compat,public");data.setConnectionProperties(properties);
            assertThat(old.queryForMap("SELECT product_id,title,description,listed_price,status FROM products WHERE product_id=?",p)).isEqualTo(before);
            assertThat(old.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version::int<=14 AND success ORDER BY installed_rank")).isEqualTo(history);
            assertThat(old.queryForObject("SELECT provenance FROM product_revisions WHERE product_id=?",String.class,p)).isEqualTo("LEGACY_MIGRATION");
            assertThat(old.queryForObject("SELECT quantity_sold FROM products WHERE product_id=?",Integer.class,p)).isEqualTo(1);
            assertThat(old.queryForObject("SELECT count(*) FROM ekyc_decisions",Long.class)).isZero();assertThat(old.queryForObject("SELECT count(*) FROM listing_fee_receipts",Long.class)).isZero();
        }finally{root.execute("DROP DATABASE "+name);}
    }
    @Test void identityDecisionDoesNotGrantSellerAndCannotBeForged() {
        long u=user("BUYER",true),p=identity(u);assertThat(j.queryForObject("SELECT og_has_role(?,'SELLER')",Boolean.class,u)).isFalse();
        rejected(()->j.update("UPDATE ekyc_profiles SET submitted_data='{\"changed\":true}' WHERE profile_id=?",p));
        long draft=draft(buyer);rejected(()->j.update("UPDATE ekyc_profiles SET state='REJECTED',submitted_at=CURRENT_TIMESTAMP WHERE profile_id=?",draft));
        rejected(()->j.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,role_id FROM roles WHERE role_name='SELLER'",buyer));
    }
    @Test void identityRegistryRejectsDuplicateAndWrongOwner() {
        long p=draft(buyer);String digest=j.queryForObject("SELECT document_digest FROM ekyc_profiles WHERE user_id=?",String.class,seller);
        rejected(()->j.update("INSERT INTO identity_document_registry(document_digest,digest_key_version,user_id,first_profile_id) VALUES(?,'TEST',?,?)",digest,buyer,p));
        rejected(()->j.update("UPDATE ekyc_profiles SET document_digest=? WHERE profile_id=?",digest,p));
        rejected(()->j.update("DELETE FROM identity_document_registry WHERE document_digest=?",digest));
    }
    @Test void ekycCooldownAndServiceFailuresUseSeparateCounters() {
        long p=draft(buyer);j.update("INSERT INTO ekyc_verification_attempts(profile_id,result,command_key) VALUES(?,'SERVICE_ERROR',?)",p,key());assertThat(id("SELECT nonmatch_count FROM ekyc_profiles WHERE profile_id=?",p)).isZero();
        j.update("INSERT INTO ekyc_verification_attempts(profile_id,result,command_key) VALUES(?,'NON_MATCH',?)",p,key());
        rejected(()->j.update("INSERT INTO ekyc_verification_attempts(profile_id,result,command_key) VALUES(?,'NON_MATCH',?)",p,key()));
        rejected(()->j.update("INSERT INTO ekyc_verification_attempts(profile_id,result,fallback_authorized_by,command_key) VALUES(?,'MANUAL_REVIEW',?,?)",p,buyer,key()));assertThat(id("SELECT nonmatch_count FROM ekyc_profiles WHERE profile_id=?",p)).isEqualTo(1);
    }
    @Test void challengesHaveOneOpenCodeAndBoundedAttempts() {
        String subject=key();j.update("INSERT INTO auth_challenges(challenge_id,subject_key,purpose,code_digest,expires_at,next_send_at,command_key) VALUES(?,?,'REGISTER_EMAIL',repeat('a',64),CURRENT_TIMESTAMP+INTERVAL '5 minutes',CURRENT_TIMESTAMP,?)",uuid(),subject,key());
        rejected(()->j.update("INSERT INTO auth_challenges(challenge_id,subject_key,purpose,code_digest,expires_at,next_send_at,command_key) VALUES(?,?,'REGISTER_EMAIL',repeat('b',64),CURRENT_TIMESTAMP+INTERVAL '5 minutes',CURRENT_TIMESTAMP,?)",uuid(),subject,key()));rejected(()->j.update("UPDATE auth_challenges SET attempt_count=6 WHERE subject_key=?",subject));
    }
    @Test void mediaRequiresCompleteSubmissionAndImmutableEvidence() {
        Listing l=listing(2,50000);rejected(()->media(l.revision,"IMAGE",3));rejected(()->j.update("UPDATE revision_media SET object_key='changed' WHERE revision_id=?",l.revision));
        rejected(()->j.update("INSERT INTO product_revisions(product_id,revision_no,provenance,state,listed_unit_price,quantity,return_allowed,delivery_options,content_snapshot,submitted_at) VALUES(?,99,'UC83','SUBMITTED',50000,1,true,'[\"CARRIER\"]','{}',CURRENT_TIMESTAMP)",l.product));
        long r=id("INSERT INTO product_revisions(product_id,revision_no,provenance,listed_unit_price,quantity,return_allowed,delivery_options,content_snapshot) VALUES(?,100,'UC83',50000,1,true,'[\"CARRIER\"]','{}') RETURNING revision_id",l.product);
        for(int i=0;i<5;i++)media(r,"IMAGE",i);rejected(()->media(r,"IMAGE",6));rejected(()->j.update("UPDATE product_revisions SET state='SUBMITTED',submitted_at=CURRENT_TIMESTAMP WHERE revision_id=?",r));
    }
    @Test void feeBoundaryAndTopUpsUseTotalPriceAndConfirmedReceipts() {
        Listing l=listing(2,49999);assertThat(l.due).isEqualByComparingTo("10000");
        l=revision(l.product,2,50000);assertThat(l.due).isEqualByComparingTo("0");l=revision(l.product,2,100000);assertThat(l.due).isEqualByComparingTo("10000");
        l=revision(l.product,2,90000);assertThat(l.due).isEqualByComparingTo("0");l=revision(l.product,2,95000);assertThat(l.due).isEqualByComparingTo("0");
        assertThat(j.queryForObject("SELECT cumulative_confirmed_fee FROM listing_fee_totals WHERE product_id=?",BigDecimal.class,l.product)).isEqualByComparingTo("20000");assertThat(j.queryForObject("SELECT sum(amount) FROM listing_revenue_entries WHERE product_id=?",BigDecimal.class,l.product)).isEqualByComparingTo("20000");
    }
    @Test void stockRaceCannotOversell()throws Exception {Listing l=listing(1,50000);assertThat(race(()->checkout(l,1))).isEqualTo(1);assertThat(id("SELECT quantity_available FROM products WHERE product_id=?",l.product)).isZero();}
    @Test void quantityAndOriginalDeadlineCannotBeSilentlyChanged() {
        Listing l=listing(5,50000);Checkout c=checkout(l,3);assertThat(id("SELECT quantity_held FROM products WHERE product_id=?",l.product)).isEqualTo(3);
        rejected(()->j.update("UPDATE checkout_groups SET payment_due_at=payment_due_at+INTERVAL '1 hour',created_at=created_at+INTERVAL '1 hour' WHERE group_id=?",c.group));rejected(()->j.update("DELETE FROM order_items WHERE order_item_id=?",c.item));rejected(()->j.update("UPDATE inventory_reservations SET quantity=1 WHERE reservation_id=?",c.reservation));
    }
    @Test void blockedBuyerCannotReserveStock() {Listing l=listing(2,50000);j.update("INSERT INTO seller_buyer_blocks(seller_id,buyer_id) VALUES(?,?)",seller,buyer);rejected(()->checkout(l,1));assertThat(id("SELECT quantity_held FROM products WHERE product_id=?",l.product)).isZero();}
    @Test void paymentAllocationAndFundingCannotBeForged() {
        Checkout c=checkout(listing(2,50000),1);UUID a=paid(c),f=funds(c,a);assertThat(id("SELECT count(*) FROM payment_allocations WHERE order_id=?",c.order)).isEqualTo(1);
        rejected(()->j.update("INSERT INTO order_fund_components(component_id,order_id,allocation_id,component_type,confirmed_amount) VALUES(?,?,?,'SHIPPING_CASH',123)",uuid(),c.order,a));rejected(()->j.update("UPDATE order_fund_components SET refunded_amount=confirmed_amount WHERE component_id=?",f));rejected(()->j.update("UPDATE payment_allocations SET amount=1 WHERE allocation_id=?",a));
        rejected(()->j.update("INSERT INTO payment_confirmations(confirmation_id,intent_id,attempt_id,provider,provider_transaction_id,amount) SELECT ?,intent_id,attempt_id,provider,?,1 FROM payment_attempts WHERE intent_id=(SELECT intent_id FROM payment_intents WHERE group_id=?)",uuid(),key(),c.group));
    }
    @Test void refundRaceReservesOnlyOneMoneyPortion()throws Exception {Checkout c=checkout(listing(2,50000),1);UUID f=funds(c,paid(c));cancel(c);assertThat(race(()->refund(f))).isEqualTo(1);assertThat(j.queryForObject("SELECT reserved_amount FROM order_fund_components WHERE component_id=?",BigDecimal.class,f)).isEqualByComparingTo("50000");}
    @Test void confirmedRefundCannotBeRetriedOrRewritten() {
        Checkout c=checkout(listing(2,50000),1);UUID f=funds(c,paid(c));cancel(c);UUID op=refund(f);j.update("UPDATE settlement_operations SET state='CONFIRMED',confirmed_at=CURRENT_TIMESTAMP,provider='MOCK',provider_operation_id=? WHERE operation_id=?",key(),op);
        rejected(()->refund(f));rejected(()->j.update("UPDATE settlement_operations SET state='FAILED' WHERE operation_id=?",op));assertThat(j.queryForObject("SELECT refunded_amount FROM order_fund_components WHERE component_id=?",BigDecimal.class,f)).isEqualByComparingTo("50000");
    }
    @Test void failedRefundCanRetryItsSameOperation() {Checkout c=checkout(listing(2,50000),1);UUID f=funds(c,paid(c));cancel(c);UUID op=refund(f);j.update("UPDATE settlement_operations SET state='FAILED',failure_reason='Mock offline' WHERE operation_id=?",op);assertThat(j.queryForObject("SELECT reserved_amount FROM order_fund_components WHERE component_id=?",BigDecimal.class,f)).isZero();j.update("UPDATE settlement_operations SET state='REQUESTED' WHERE operation_id=?",op);}
    @Test void earlyCompletionNeedsConsentAndClosesReturnRight() {
        Checkout c=checkout(listing(2,50000),1);paid(c);deliver(c);String command=key();rejected(()->j.queryForObject("SELECT og_early_complete_order(?,?,?,?)",Boolean.class,c.order,buyer,"",key()));
        assertThat(j.queryForObject("SELECT og_early_complete_order(?,?,?,?)",Boolean.class,c.order,buyer,"WARNING_V1",command)).isTrue();assertThat(j.queryForObject("SELECT og_early_complete_order(?,?,?,?)",Boolean.class,c.order,buyer,"WARNING_V1",command)).isTrue();rejected(()->returnCase(c));assertThat(id("SELECT count(*) FROM order_events WHERE order_id=? AND event_type='EARLY_COMPLETION_ACCEPTED'",c.order)).isEqualTo(1);
    }
    @Test void returnCaseBlocksEarlyCompletionAndSellerRelease() {
        Checkout c=checkout(listing(2,50000),1);UUID f=funds(c,paid(c));deliver(c);returnCase(c);rejected(()->j.queryForObject("SELECT og_early_complete_order(?,?,?,?)",Boolean.class,c.order,buyer,"WARNING_V1",key()));rejected(()->j.update("INSERT INTO settlement_operations(operation_id,component_id,operation_type,amount,cause_type,recipient_user_id,command_key) VALUES(?,?,'RELEASE_SELLER',50000,'COMPLETION',?,?)",uuid(),f,seller,key()));assertThat(id("SELECT count(*) FROM money_holds WHERE component_id=? AND closed_at IS NULL",f)).isEqualTo(1);
    }
    @Test void postReturnRefundNeedsCorrectFinalAuthority() {
        Checkout c=checkout(listing(2,50000),1);UUID f=funds(c,paid(c));deliver(c);long caseId=returnCase(c);
        tx(()->{long r=id("INSERT INTO case_rounds(case_id,round_no,stage) VALUES(?,1,'RETURN_APPROVAL') RETURNING round_id",caseId);j.update("INSERT INTO case_decisions(round_id,reviewed_by,outcome,reason,command_key) VALUES(?,?,'APPROVE_RETURN','Approved',?)",r,seller,key());return null;});
        long s=id("INSERT INTO shipments(order_id,carrier,status,workflow_model,leg,return_case_id,delivery_method,fee_snapshot,fee_payer) VALUES(?,'SELF_PICKUP','PENDING','UC83','RETURN',?,'SELF_PICKUP',0,'BUYER') RETURNING shipment_id",c.order,caseId);confirmations(s);j.queryForObject("SELECT og_confirm_direct_handover(?)",Boolean.class,s);
        long r=id("INSERT INTO case_rounds(case_id,round_no,stage) VALUES(?,2,'POST_RETURN_RESOLUTION') RETURNING round_id",caseId);rejected(()->j.update("INSERT INTO case_decisions(round_id,reviewed_by,outcome,reason,command_key) VALUES(?,?,'EXEMPT_GOODS_REFUND','Invalid',?)",r,seller,key()));
        long d=id("INSERT INTO case_decisions(round_id,reviewed_by,outcome,reason,command_key) VALUES(?,?,'FULL_GOODS_REFUND','Full',?) RETURNING decision_id",r,seller,key());assertThat(j.queryForObject("SELECT goods_refund_amount FROM case_decisions WHERE decision_id=?",BigDecimal.class,d)).isEqualByComparingTo("50000");
        j.update("INSERT INTO settlement_operations(operation_id,component_id,operation_type,amount,cause_type,case_decision_id,recipient_user_id,command_key) VALUES(?,?,'REFUND_BUYER',50000,'RETURN',?,?,?)",uuid(),f,d,buyer,key());assertThat(id("SELECT count(*) FROM money_holds WHERE component_id=? AND closed_at IS NULL",f)).isZero();assertThat(id("SELECT count(*) FROM completed_case_statistics WHERE case_id=?",caseId)).isEqualTo(1);
    }
    @Test void evidenceRequiresPrivateMetadataAndCorrectAuthor() {
        Checkout c=checkout(listing(2,50000),1);paid(c);deliver(c);long cid=returnCase(c),r=id("INSERT INTO case_rounds(case_id,round_no,stage) VALUES(?,1,'RETURN_APPROVAL') RETURNING round_id",cid);
        rejected(()->j.update("INSERT INTO case_evidence(evidence_id,round_id,submitted_by,submission_no,media_type,object_key) VALUES(?,?,?,1,'IMAGE','private/test')",uuid(),r,buyer));long outsider=user("BUYER",true);rejected(()->j.update("INSERT INTO case_evidence(evidence_id,round_id,submitted_by,submission_no,media_type,text_content) VALUES(?,?,?,1,'TEXT','Test')",uuid(),r,outsider));j.update("INSERT INTO case_evidence(evidence_id,round_id,submitted_by,submission_no,media_type,text_content) VALUES(?,?,?,1,'TEXT','Buyer statement')",uuid(),r,buyer);
    }
    @Test void rewardLedgerSerializesDeductions()throws Exception {
        long p=id("INSERT INTO reward_policies(policy_code,point_value,policy_snapshot,created_by) VALUES(?,1,'{}',?) RETURNING policy_id",key(),admin);j.update("INSERT INTO reward_accounts(user_id) VALUES(?)",buyer);j.update("INSERT INTO reward_ledger(entry_id,user_id,policy_id,entry_type,points_delta,actor_id,reason,command_key) VALUES(?,?,?,'CORRECTION',10,?,'Test seed',?)",uuid(),buyer,p,admin,key());
        assertThat(race(()->j.update("INSERT INTO reward_ledger(entry_id,user_id,policy_id,entry_type,points_delta,actor_id,reason,command_key) VALUES(?,?,?,'CORRECTION',-7,?,'Test deduction',?)",uuid(),buyer,p,admin,key()))).isEqualTo(1);assertThat(id("SELECT balance FROM reward_accounts WHERE user_id=?",buyer)).isEqualTo(3);rejected(()->j.update("UPDATE reward_accounts SET balance=100 WHERE user_id=?",buyer));rejected(()->j.update("DELETE FROM reward_ledger WHERE user_id=?",buyer));
    }
    @Test void restrictionsKeepLoginForOpenObligations() {
        checkout(listing(2,50000),1);rejected(()->j.update("INSERT INTO account_restrictions(restriction_id,user_id,scope,imposed_by,reason,source_key) VALUES(?,?,'LOGIN',?,'Test',?)",uuid(),buyer,ktv,key()));j.update("INSERT INTO account_restrictions(restriction_id,user_id,scope,imposed_by,reason,source_key) VALUES(?,?,'NEW_TRANSACTIONS',?,'Test',?)",uuid(),buyer,ktv,key());rejected(()->checkout(listing(2,50000),1));
    }
    @Test void manualNotificationsAndExportsRequireAdmin() {
        UUID e=uuid();j.update("INSERT INTO outbox_events(event_id,event_type,aggregate_type,aggregate_id,payload) VALUES(?,'TEST','USER',?,'{}')",e,String.valueOf(buyer));rejected(()->j.update("INSERT INTO notification_dispatches(dispatch_id,sender_id,event_id,title,content,command_key) VALUES(?,?,?,'Test','Test',?)",uuid(),ktv,e,key()));j.update("INSERT INTO notification_dispatches(dispatch_id,sender_id,event_id,title,content,command_key) VALUES(?,?,?,'Test','Test',?)",uuid(),admin,e,key());rejected(()->j.update("INSERT INTO report_export_runs(export_id,requested_by,metric,filters,cutoff_at) VALUES(?,?,'LISTING_REVENUE','{}',CURRENT_TIMESTAMP)",uuid(),buyer));
    }
    private long voucherRevision() {
        return voucherRevision(10000);
    }
    private long voucherRevision(int amount) {
        long v=id("INSERT INTO vouchers(code,title,voucher_type,discount_type,discount_value,start_time,end_time) VALUES(?,'Test','ORDER_DISCOUNT','FIXED_AMOUNT',?,CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day') RETURNING voucher_id",key().toUpperCase(),amount);
        return id("INSERT INTO voucher_revisions(voucher_id,revision_no,policy_snapshot,scope,claim_limit,valid_from,valid_until,created_by) VALUES(?,1,'{\"amount\":10000}','CHECKOUT',1,CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day',?) RETURNING revision_id",v,admin);
    }
    private UUID grant(long r) {UUID g=uuid();j.update("INSERT INTO voucher_grants(grant_id,revision_id,user_id,source,issued_by,expires_at,command_key) VALUES(?,?,?,'SELF_CLAIM',?,CURRENT_TIMESTAMP+INTERVAL '12 hours',?)",g,r,buyer,buyer,key());return g;}
    @Test void voucherClaimRaceRespectsQuota()throws Exception {long r=voucherRevision();assertThat(race(()->grant(r))).isEqualTo(1);assertThat(id("SELECT count(*) FROM voucher_grants WHERE revision_id=?",r)).isEqualTo(1);}
    @Test void voucherAllocationConservesNetPaymentAndIsNeverRestored() {
        UUID g=grant(voucherRevision());Checkout c=checkout(listing(2,50000),1,g,10000);UUID f=funds(c,paid(c));cancel(c);refund(f);
        assertThat(j.queryForObject("SELECT confirmed_amount FROM order_fund_components WHERE component_id=?",BigDecimal.class,f)).isEqualByComparingTo("40000");
        assertThat(j.queryForObject("SELECT state FROM voucher_grants WHERE grant_id=?",String.class,g)).isEqualTo("REDEEMED");rejected(()->j.update("UPDATE voucher_grants SET state='AVAILABLE' WHERE grant_id=?",g));
    }
    @Test void incorrectDiscountCannotCommitAndRollsBackGrantUse() {
        UUID g=grant(voucherRevision());Checkout c=checkout(listing(2,50000),1);
        rejected(()->j.update("INSERT INTO checkout_redemptions(redemption_id,group_id,buyer_id,grant_id,policy_snapshot,discount_amount) VALUES(?,?,?,?,'{}',10000)",uuid(),c.group,buyer,g));
        assertThat(j.queryForObject("SELECT state FROM voucher_grants WHERE grant_id=?",String.class,g)).isEqualTo("AVAILABLE");
    }
    @Test void latePaymentIsReconciledAndDoesNotResurrectReservation() {
        Checkout c=checkout(listing(2,50000),1);UUID intent=uuid();
        j.update("INSERT INTO payment_intents(intent_id,purpose,group_id,expected_amount,payment_method,deadline,command_key) SELECT ?,'CHECKOUT_GROUP',group_id,expected_total,payment_method,payment_due_at,? FROM checkout_groups WHERE group_id=?",intent,key(),c.group);
        long attempt=id("INSERT INTO payment_attempts(intent_id,txn_ref,amount,provider) SELECT ?,?,expected_amount,payment_method FROM payment_intents WHERE intent_id=? RETURNING attempt_id",intent,key(),intent);cancel(c);j.update("UPDATE inventory_reservations SET state='RELEASED' WHERE reservation_id=?",c.reservation);
        UUID confirmation=uuid();j.update("INSERT INTO payment_confirmations(confirmation_id,intent_id,attempt_id,provider,provider_transaction_id,amount) VALUES(?,?,?,'BANK_TRANSFER_MOCK',?,50000)",confirmation,intent,attempt,key());
        assertThat(j.queryForObject("SELECT disposition FROM payment_confirmations WHERE confirmation_id=?",String.class,confirmation)).isEqualTo("RECONCILIATION");assertThat(j.queryForObject("SELECT state FROM inventory_reservations WHERE reservation_id=?",String.class,c.reservation)).isEqualTo("RELEASED");
        rejected(()->j.update("INSERT INTO payment_allocations(allocation_id,intent_id,confirmation_id,order_id,amount) VALUES(?,?,?,?,50000)",uuid(),intent,confirmation,c.order));
        j.update("INSERT INTO order_fund_components(component_id,order_id,late_confirmation_id,component_type,confirmed_amount) VALUES(?,?,?,'LATE_PAYMENT_CASH',50000)",uuid(),c.order,confirmation);
    }
    @Test void quickAuthenticationRequiresMatchAndPrivateUnexpiredReference() {
        long p=id("SELECT profile_id FROM ekyc_profiles WHERE user_id=?",seller),a=id("SELECT asset_id FROM ekyc_private_assets WHERE profile_id=? AND asset_type='REFERENCE'",p);UUID session=uuid();
        j.update("INSERT INTO quick_auth_sessions(session_id,user_id,profile_id,reference_asset_id,expires_at,command_key) VALUES(?,?,?,?,CURRENT_TIMESTAMP+INTERVAL '5 minutes',?)",session,seller,p,a,key());
        j.update("INSERT INTO quick_auth_attempts(attempt_id,session_id,result,command_key) VALUES(?,?,'SERVICE_ERROR',?)",uuid(),session,key());assertThat(id("SELECT nonmatch_count FROM quick_auth_sessions WHERE session_id=?",session)).isZero();
        rejected(()->j.update("INSERT INTO quick_auth_results(result_id,session_id,user_id,profile_id,reference_asset_id,matched_at,expires_at,command_key) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 hour',?)",uuid(),session,seller,p,a,key()));
        j.update("INSERT INTO quick_auth_attempts(attempt_id,session_id,result,command_key) VALUES(?,?,'MATCH',?)",uuid(),session,key());j.update("INSERT INTO quick_auth_results(result_id,session_id,user_id,profile_id,reference_asset_id,matched_at,expires_at,command_key) SELECT ?,?,?,?,?,attempted_at,attempted_at+INTERVAL '1 hour',? FROM quick_auth_attempts WHERE session_id=? AND result='MATCH'",uuid(),session,seller,p,a,key(),session);
        rejected(()->j.update("INSERT INTO quick_auth_attempts(attempt_id,session_id,result,command_key) VALUES(?,?,'MATCH',?)",uuid(),session,key()));
    }
    @Test void onePartyHandoverCannotSetValidDelivery() {
        Checkout c=checkout(listing(2,50000),1);paid(c);
        long s=id("INSERT INTO shipments(order_id,carrier,status,workflow_model,delivery_method,fee_snapshot,fee_payer) VALUES(?,'SELF_PICKUP','PENDING','UC83','SELF_PICKUP',0,'BUYER') RETURNING shipment_id",c.order);
        j.update("INSERT INTO handover_confirmations(confirmation_id,shipment_id,party,user_id,outcome,command_key) VALUES(?,?,'BUYER',?,'SUCCESS',?)",uuid(),s,buyer,key());assertThat(j.queryForObject("SELECT og_confirm_direct_handover(?)",Boolean.class,s)).isFalse();assertThat(j.queryForObject("SELECT valid_delivered_at FROM orders WHERE order_id=?",java.sql.Timestamp.class,c.order)).isNull();
    }
    @Test void fullySubsidizedCheckoutDoesNotInventCashReceipt() {
        UUID grant=grant(voucherRevision(50000));Checkout c=checkout(listing(2,50000),1,grant,50000);
        UUID confirmation=uuid();j.update("INSERT INTO zero_checkout_confirmations(confirmation_id,group_id,buyer_id,command_key) VALUES(?,?,?,?)",confirmation,c.group,buyer,key());
        assertThat(j.queryForObject("SELECT state FROM inventory_reservations WHERE reservation_id=?",String.class,c.reservation)).isEqualTo("FUNDED");
        assertThat(id("SELECT count(*) FROM payment_intents WHERE group_id=?",c.group)).isZero();
        j.update("INSERT INTO order_fund_components(component_id,order_id,component_type,confirmed_amount,funding_reference) VALUES(?,?,'PLATFORM_GOODS_SUBSIDY',50000,?)",uuid(),c.order,"VOUCHER:"+grant);
        rejected(()->j.update("INSERT INTO zero_checkout_confirmations(confirmation_id,group_id,buyer_id,command_key) VALUES(?,?,?,?)",uuid(),c.group,buyer,key()));
        deliver(c);assertThat(j.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,c.order)).isEqualTo("DELIVERED");
    }
    @Test void positiveCheckoutCannotUseZeroConfirmation() {
        Checkout c=checkout(listing(2,50000),1);rejected(()->j.update("INSERT INTO zero_checkout_confirmations(confirmation_id,group_id,buyer_id,command_key) VALUES(?,?,?,?)",uuid(),c.group,buyer,key()));
        assertThat(j.queryForObject("SELECT state FROM inventory_reservations WHERE reservation_id=?",String.class,c.reservation)).isEqualTo("HELD");
    }

    @Test void populatedV21UpgradePreservesEverySourceAndContinuesHeldCase() {
        String name="ogshop_expansion_"+key().replace("-","");root.execute("CREATE DATABASE "+name);
        var previousData=ds;var previousJdbc=j;
        long[] previous={admin,ktv,buyer,seller,category};
        try {
            ds=source(name);j=new JdbcTemplate(ds);flyway(ds,"21").migrate();
            admin=user("ADMIN",false);ktv=user("KTV",false);category=id("SELECT min(category_id) FROM categories");parties();
            j.update("INSERT INTO seller_verifications(user_id) VALUES(?)",admin);
            UUID grant=grant(voucherRevision());Listing l=listing(2,50000);Checkout c=checkout(l,1,grant,10000);
            UUID allocation=paid(c),fund=funds(c,allocation);deliver(c);long cid=returnCase(c);
            long round=id("INSERT INTO case_rounds(case_id,round_no,stage) VALUES(?,1,'RETURN_APPROVAL') RETURNING round_id",cid);
            j.update("INSERT INTO case_evidence(evidence_id,round_id,submitted_by,submission_no,media_type,text_content) VALUES(?,?,?,1,'TEXT','Fake test statement')",uuid(),round,buyer);
            j.update("INSERT INTO user_security_settings(user_id,email_2fa_enabled,verified_at) VALUES(?,true,CURRENT_TIMESTAMP)",buyer);
            j.update("INSERT INTO external_identities(user_id,provider,provider_subject) VALUES(?,'GOOGLE',?)",buyer,"fake-"+key());
            long policy=id("INSERT INTO reward_policies(policy_code,point_value,policy_snapshot,created_by) VALUES(?,1,'{}',?) RETURNING policy_id",key(),admin);
            j.update("INSERT INTO reward_accounts(user_id) VALUES(?)",buyer);
            j.update("INSERT INTO reward_ledger(entry_id,user_id,policy_id,entry_type,points_delta,actor_id,reason,command_key) VALUES(?,?,?,'CORRECTION',10,?,'Fake test seed',?)",uuid(),buyer,policy,admin,key());
            var names=j.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename",String.class);
            var before=new java.util.LinkedHashMap<String,java.util.Map<String,Object>>();
            for(String table:names) before.put(table,j.queryForMap("SELECT count(*) AS rows,md5(coalesce(string_agg(md5(to_jsonb(t)::text),'' ORDER BY md5(to_jsonb(t)::text)),'')) AS fingerprint FROM public."+table+" t"));
            var checksums=j.queryForList("SELECT version,checksum FROM public.flyway_schema_history ORDER BY installed_rank");
            flyway(ds,null).migrate();flyway(ds,null).validate();
            var properties=new java.util.Properties();properties.setProperty("currentSchema","og_compat,public");ds.setConnectionProperties(properties);
            assertThat(names).hasSize(104);
            for(String table:names) assertThat(j.queryForMap("SELECT count(*) AS rows,md5(coalesce(string_agg(md5(to_jsonb(t)::text),'' ORDER BY md5(to_jsonb(t)::text)),'')) AS fingerprint FROM og_compat."+table+" t")).as(table).isEqualTo(before.get(table));
            assertThat(j.queryForList("SELECT version,checksum FROM public.flyway_schema_history WHERE version::int<=21 ORDER BY installed_rank")).isEqualTo(checksums);
            assertThat(id("SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history'")).isEqualTo(48);
            assertThat(id("SELECT count(*) FROM pg_namespace WHERE nspname='_og70_v21'")).isZero();
            assertThat(id("SELECT reward_balance FROM public.users WHERE user_id=?",buyer)).isEqualTo(10);
            assertThat(j.queryForObject("SELECT email_2fa_enabled FROM public.users WHERE user_id=?",Boolean.class,buyer)).isTrue();
            assertThat(j.queryForObject("SELECT product_id FROM public.product_media WHERE record_type='revision_media' AND revision_id=? LIMIT 1",Long.class,l.revision)).isEqualTo(l.product);
            rejected(()->j.update("INSERT INTO settlement_operations(operation_id,component_id,operation_type,amount,cause_type,recipient_user_id,command_key) VALUES(?,?,'RELEASE_SELLER',40000,'COMPLETION',?,?)",uuid(),fund,seller,key()));
            long decision=id("INSERT INTO case_decisions(round_id,reviewed_by,outcome,reason,command_key) VALUES(?,?,'APPROVE_RETURN','Fake approval',?) RETURNING decision_id",round,seller,key());
            assertThat(id("SELECT case_id FROM public.case_actions WHERE record_type='case_decisions' AND source_case_decisions_decision_id=?",decision)).isEqualTo(cid);
            assertThat(id("SELECT count(*) FROM money_holds WHERE component_id=? AND closed_at IS NULL",fund)).isEqualTo(1);
            // Imported sibling records share the physical aggregate PK. New
            // writes must advance beyond all subtypes, not just the base view.
            assertThat(draft(buyer)).isPositive();
            long legacy=user("BUYER",false);
            assertThat(id("INSERT INTO seller_verifications(user_id) VALUES(?) RETURNING verification_id",legacy)).isPositive();
            assertThat(voucherRevision()).isPositive();
            assertThat(id("INSERT INTO payments(order_id,amount,payment_method) SELECT order_id,total_amount,'BANK_TRANSFER_MOCK' FROM orders WHERE order_id=? RETURNING payment_id",c.order)).isPositive();
        } finally {
            ds=previousData;j=previousJdbc;admin=previous[0];ktv=previous[1];buyer=previous[2];seller=previous[3];category=previous[4];
            root.execute("DROP DATABASE "+name);
        }
    }

    @Test void physicalFoldedHistoryCannotBypassDecisionAuthority() {
        long profile=id("SELECT profile_id FROM ekyc_profiles WHERE user_id=?",seller);
        rejected(()->j.update("UPDATE public.ekyc_profiles SET ekyc_decisions_history='[]' WHERE profile_id=? AND record_type='ekyc_profiles'",profile));
        assertThat(id("SELECT count(*) FROM ekyc_decisions WHERE profile_id=?",profile)).isEqualTo(1);
    }

    @Test void inheritedNotValidForeignKeyStillRejectsNewWrongBuyerEvidence() {
        Checkout c=checkout(listing(2,50000),1);
        long outsider=user("BUYER",true);
        assertThatThrownBy(()->j.update("INSERT INTO order_unboxing_evidences(order_id,buyer_id) VALUES(?,?)",c.order,outsider))
            .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("og70_fk_order_unboxing_evidences_fk_unboxing_order_buyer");
        j.update("INSERT INTO order_unboxing_evidences(order_id,buyer_id) VALUES(?,?)",c.order,buyer);
    }
}
