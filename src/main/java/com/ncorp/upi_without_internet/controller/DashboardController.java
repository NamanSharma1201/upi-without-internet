package com.ncorp.upi_without_internet.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller

public class DashboardController {
    @GetMapping("/")
    public String home(){
        return "dashboard";
    }

}
