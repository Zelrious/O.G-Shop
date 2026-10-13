package com.oldbutgold.shop.modules.identity.application;
import java.util.*;
public interface VoucherIdentityFacade {
    record Customer(long id,String name,String email,String role,String status,boolean eligible) {}
    record Customers(List<Customer> items,long total,int page,int size) {}
    void staff(long actor,String role);
    Customer customer(long id,boolean lock);
    void lockCustomers(Set<Long> ids);
    Customers search(String query,String role,String status,int page,int size,Set<Long> include,Set<Long> exclude);
}
