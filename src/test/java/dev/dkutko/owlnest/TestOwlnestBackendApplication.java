package dev.dkutko.owlnest;

import org.springframework.boot.SpringApplication;

public class TestOwlnestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(OwlnestBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
