public class SleepTarget {
    public static void main(String[] args) throws Exception {
        long pid = ProcessHandle.current().pid();
        System.out.println("Target process started, PID: " + pid);
        while (true) {
            Thread.sleep(5000);
            System.out.println("Still running, PID: " + pid);
        }
    }
}