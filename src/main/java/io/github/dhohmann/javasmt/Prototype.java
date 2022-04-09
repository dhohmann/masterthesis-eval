package io.github.dhohmann.javasmt;
import  org.spldev.formula.expression.io.ConfiguringConstraintsFormat;

public class Prototype {

    public static void main(String[] args) {
        ModelTest sandwich = new ModelTest("sandwich");
        sandwich.run();

        ModelTest webserver = new ModelTest("webserver");
        webserver.run();

//        System.out.println(new ModelTest("sandwich"));
//        System.out.println(new ModelTest("webserver"));
//        System.out.println(new ModelTest("pc_config"));
//        System.out.println(new ModelTest("busy_box"));
//        System.out.println(new ModelTest("linux"));
    }


}
