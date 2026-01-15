package com.roften.avilixeconomy.client;

public class ClientBalance {
    private static double balance = 0.0;

    public static double get() { return balance; }
    public static void set(double v) { balance = v; }
}
