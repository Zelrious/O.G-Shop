package com.oldbutgold.shop.modules.identity.application;
public interface ConversationIdentityFacade {
    record Person(long id,String name,boolean seller,boolean buyer) {}
    Person customer(long id);
    String displayName(long id);
    void lockPair(long buyer,long seller);
    void requireChat(long id);
    void requireTrade(long buyer,long seller,boolean ekyc);
    boolean chatAllowed(long id);
    void requireNewConversation(long buyer,long seller);
    void requireBlockManager(long seller);
}
