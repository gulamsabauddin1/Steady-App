class MyThread extends Thread {

    public void run() {

        System.out.println("Thread is Running");

        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            System.out.println(e);
        }

        System.out.println("Thread Finished");
    }
}

public class LifeCycleExample {

    public static void main(String[] args) {

        MyThread t = new MyThread();

        System.out.println(t.getState());

        t.start();

        System.out.println(t.getState());

    }
}