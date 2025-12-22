package com.roften.avilixeconomy.client;

public class ClientBalance {
    private static long balance = 0;

    public static long get() { return balance; }
    public static void set(long v) { balance = v; }
}
