package com.roften.avilixeconomy.shop.render;

public enum RenderOverrideScope {
    ITEM,
    TYPE;

    public static RenderOverrideScope fromByte(byte b) {
        return b == 1 ? TYPE : ITEM;
    }

    public byte toByte() {
        return (byte) (this == TYPE ? 1 : 0);
    }
}
