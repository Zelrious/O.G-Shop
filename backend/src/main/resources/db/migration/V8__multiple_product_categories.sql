-- Catalog: a product can belong to multiple categories.
-- Keep products.category_id as a compatibility projection for old consumers.
CREATE TABLE product_categories (
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT pk_product_categories PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_product_categories_product FOREIGN KEY (product_id)
        REFERENCES products (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_product_categories_category FOREIGN KEY (category_id)
        REFERENCES categories (category_id)
);

CREATE INDEX ix_product_categories_category_product ON product_categories (category_id, product_id);

INSERT INTO product_categories (product_id, category_id)
SELECT product_id, category_id FROM products;

-- At least one membership, including the legacy category. Deferred so Hibernate can
-- insert products then memberships, or replace memberships, in one transaction.
ALTER TABLE products ADD CONSTRAINT fk_products_compatibility_category_membership
    FOREIGN KEY (product_id, category_id) REFERENCES product_categories (product_id, category_id)
    DEFERRABLE INITIALLY DEFERRED;
