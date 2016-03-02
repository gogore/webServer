package org.gradle;


public class Main {

    public static void main(String[] args) {
        NetworkService networkService = new NetworkService(1234, 100);
        networkService.run();
    }

}
