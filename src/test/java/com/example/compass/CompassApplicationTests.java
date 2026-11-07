package com.example.compass;


import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers=TestDatabase.class)
class CompassApplicationTests {

    @Test
    void contextLoads() {
    }

}
