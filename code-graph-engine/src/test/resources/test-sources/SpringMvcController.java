package com.example.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class SpringMvcController {
    
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return null;
    }
    
    @PostMapping
    public User createUser(@RequestBody User user) {
        return null;
    }
    
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        return null;
    }
    
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
    }
    
    @GetMapping
    public List<User> listUsers() {
        return null;
    }
    
    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public List<User> searchUsers(@RequestParam String keyword) {
        return null;
    }
}

