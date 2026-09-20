package com.example.gigpoint.domain

enum class InventoryOperation(val stockSign: Int, val destructive: Boolean = false) {
    STOCK_IN(+1),
    SALE(-1),
    DAMAGE(-1),
    EXPIRED(-1),
    CUSTOMER_RETURN(+1),
    SUPPLIER_RETURN(-1),
    ADJUSTMENT_IN(+1),
    ADJUSTMENT_OUT(-1),
    SET_STOCK(0, true),
    SET_REORDER_LEVEL(0),
    CHECK_STOCK(0),
    LIST_PRODUCTS(0),
    LOW_STOCK(0),
    ARCHIVE_PRODUCT(0, true),
    UNDO(0, true),
    UNKNOWN(0);

    val changesStock get() = stockSign != 0 || this == SET_STOCK
}
