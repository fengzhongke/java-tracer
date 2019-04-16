package com.ali.dbtech.test;

public class DynamicCode implements Runnable {

    public void run() {

        MainProcess.print();
    }
    
    public static void main(String[] args){
        new DynamicCode().run();
    }

}
