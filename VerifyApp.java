/**
 * Simple test app for verifying java-tracer agent.
 * Has a method with parameters for testing the param capture feature.
 */
public class VerifyApp {

    private String name;
    private int count;

    public VerifyApp(String name, int count) {
        this.name = name;
        this.count = count;
    }

    public String greet(String greeting, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(greeting).append(", ").append(name).append("! ");
        }
        return sb.toString().trim();
    }

    public static String staticHello(String msg) {
        return "Hello: " + msg;
    }

    public static void main(String[] args) throws Exception {
        VerifyApp app = new VerifyApp("tracer-test", 3);
        System.out.println("VerifyApp started. Calling greet() periodically...");
        while (true) {
            String result = app.greet("Welcome", 2);
            System.out.println(result);
            Thread.sleep(5000);
        }
    }
}
