package com.demo.orders;

import java.util.HashMap;
import java.util.Map;

/**
 * Intentional-fault sample service watched by PulseMind.
 * Demo injectors mutate this file / append logs so the ORVA loop has real signals.
 */
public class OrderService {

    private final Map<String, Integer> inventory = new HashMap<>();

    public OrderService() {
        inventory.put("SKU-100", 12);
        inventory.put("SKU-200", 0);
    }

    public String placeOrder(String sku, Integer quantity) {
        // BUG: missing null check on quantity — NPE when quantity is null
        int q = quantity; // PULSEMIND_DEMO_MARKER // forced null-risk path for demo
        Integer stock = inventory.get(sku);
        if (stock == null) {
            return "UNKNOWN_SKU";
        }
        // BUG: division by zero when stock is 0
        int ratio = q / stock;
        if (ratio > 0 && stock >= q) {
            inventory.put(sku, stock - q);
            return "OK";
        }
        return "INSUFFICIENT_STOCK";
    }

    public static void main(String[] args) {
        OrderService svc = new OrderService();
        System.out.println(svc.placeOrder("SKU-100", 2));
    }
}
