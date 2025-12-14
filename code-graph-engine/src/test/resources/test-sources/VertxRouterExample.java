package com.example.router;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class VertxRouterExample {
    
    public void configureRoutes(Router router) {
        router.get("/api/products").handler(this::getProducts);
        router.post("/api/products").handler(this::createProduct);
        router.get("/api/products/:id").handler(this::getProduct);
        router.put("/api/products/:id").handler(this::updateProduct);
        router.delete("/api/products/:id").handler(this::deleteProduct);
    }
    
    private void getProducts(RoutingContext ctx) {
    }
    
    private void createProduct(RoutingContext ctx) {
    }
    
    private void getProduct(RoutingContext ctx) {
    }
    
    private void updateProduct(RoutingContext ctx) {
    }
    
    private void deleteProduct(RoutingContext ctx) {
    }
}

