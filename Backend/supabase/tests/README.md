# Backend tests to add

Before production, add pgTAP tests for:

1. Merchant A cannot read Merchant B's shop.
2. Merchant A cannot read Merchant B's products/variants.
3. Merchant A cannot call inventory RPC for Merchant B's shop.
4. SALE cannot make stock negative.
5. Replaying the same transaction UUID does not change stock twice.
6. PURCHASE increases stock.
7. SALE decreases stock.
8. DAMAGE decreases stock without being counted as a sale.
9. get_my_context() returns only the authenticated merchant.
10. Direct client writes to inventory and stock_transactions are denied.
