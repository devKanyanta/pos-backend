package com.devkanyanta.pos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class testController {
    @GetMapping("/test")
    public String test() {
        String name = "Kanyanta Mapalo";
        return "Hello World this is another test " + name;
    }
}
